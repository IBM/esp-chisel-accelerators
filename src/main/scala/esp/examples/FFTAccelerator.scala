// Copyright 2018-2019 IBM
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package esp.examples

import chisel3._
import chisel3.experimental.{ChiselEnum, FixedPoint}
import chisel3.util.{Queue, Valid}

import dsptools.numbers._

import esp.{Config, Implementation, Parameter, Specification}

import java.io.File

import ofdm.fft.{DISOIO, FFT, FFTParams, PacketSerializer, PacketSerDesParams}

import sys.process.Process

/* 64-bit, 40 binary point */

trait FFTSpecification extends Specification {

  val params: FFTParams[_]

  private def gitHash(dir: Option[File] = None): Int = {
    val cmd = Seq("git", "log", "-n1", "--format=%h")

    val hash = dir match {
      case Some(file) => Process(cmd, file).!!
      case None       => Process(cmd).!!
    }

    java.lang.Integer.parseInt(hash.filter(_ >= ' '), 16)
  }

  override lazy val config: Config = Config(
    name = "FFTAccelerator",
    description = params.protoIQ.real match {
      case a: FixedPoint => s"${params.numPoints}-point ${a.getWidth}.${a.binaryPoint.get} FFT"
    },
    memoryFootprintMiB = 1,
    deviceId = 0xD,
    param = Array(
      Parameter(
        name = "gitHash",
        description = Some("Git short SHA hash of the repo used to generate this accelerator"),
        value = Some(gitHash())
      ),
      Parameter(
        name = "ofdmGitHash",
        description = Some("Git short SHA hash of the grebe/ofdm repo used to generate this accelerator"),
        value = Some(gitHash(Some(new File("ofdm"))))
      ),
      Parameter(
        name = "startAddr",
        description = Some("The memory address to start the FFT (output written here)")
      ),
      Parameter(
        name = "count",
        description = Some("The number of 1D FFTs to do (only 1 supported)")
      ),
      Parameter(
        name = "stride",
        description = Some("The stride between each FFT (must be point size)")
      )
    )
  )

}

object FFTAccelerator {

  /** FFTAccelerator states for internal state machines */
  object S extends ChiselEnum {
    val Idle, DMALoad, DMAStore, Done = Value
  }

  /** FFTAccelerator error codes */
  object Errors extends ChiselEnum {
    val None = Value(0.U)
    val InvalidWidth, Unimplemented = Value
  }

}

/** An ESP accelerator that performs an N-point Fast Fourier Transform (FFT)
  * @param dmaWidth the width of the ESP DMA bus
  * @param params parameters describing the FFT
  */
class FFTAccelerator[A <: Data : Real : BinaryRepresentation](dmaWidth: Int, val params: FFTParams[A])
    extends Implementation(dmaWidth) with FFTSpecification {

  require(params.protoIQ.real.getWidth <= 32, "This FFT has bugs for bit widths > 32 bits!")

  import FFTAccelerator._

  private def unimplemented(): Unit = {
    state := S.Done
    debug := Errors.Unimplemented
  }

  override val implementationName: String = "Default_fft" + dmaWidth

  /** The underlying FFT hardware */
  val fft = Module(
    new MultiIOModule {
      val underlyingFFT = Module(new FFT(params))
      val desser = Module(new PacketSerializer(PacketSerDesParams(params.protoIQ, params.numPoints)))
      val in = IO(chiselTypeOf(underlyingFFT.io.in))
      underlyingFFT.io.in <> in
      desser.io.in <> underlyingFFT.io.out
      val out = IO(chiselTypeOf(desser.io.out))
      out <> desser.io.out
    }
  )
  dontTouch(fft.in)
  dontTouch(fft.out)

  /** Indicates that this unit is busy computing a computation */
  val state = RegInit(S.Idle)
  val addr = Reg(chiselTypeOf(io.config.get("startAddr")).asUInt)
  val count = Reg(chiselTypeOf(io.config.get("count")).asUInt)
  val stride = Reg(chiselTypeOf(io.config.get("stride")).asUInt)
  val debug = RegInit(Errors.None)

  io.debug := debug.asUInt
  io.done := state === S.Done

  fft.in.bits.real := DontCare
  fft.in.bits.imag := DontCare
  fft.in.valid := false.B

  fft.out.ready := false.B

  val dmaRead, dmaWrite = Reg(Valid(UInt(32.W)))

  when (io.enable && state === S.Idle) {
    addr := io.config.get("startAddr")
    count := io.config.get("count")
    stride := io.config.get("stride")

    when (io.config.get("stride").asUInt =/= params.numPoints.U) {
      state := S.Done
      debug := Errors.InvalidWidth
    }.elsewhen(io.config.get("count").asUInt =/= 1.U) {
      unimplemented()
    }.otherwise {
      state := S.DMALoad
      Seq(dmaRead, dmaWrite).foreach { a =>
        a.valid := false.B
        a.bits := 0.U
      }
    }
  }

  /* @todo cleanup this jank */
  val real_d = Reg(params.protoIQ.real)
  val readQueue = Module(new Queue(params.protoIQ, entries=2))
  io.dma.readChannel.ready := readQueue.io.enq.ready
  readQueue.io.enq.valid := io.dma.readChannel.valid && dmaRead.bits(0)
  readQueue.io.enq.bits := DspComplex.wire(real_d, io.dma.readChannel.bits.asTypeOf(params.protoIQ.imag))
  fft.in <> readQueue.io.deq

  when (state === S.DMALoad) {
    io.dma.readControl.valid := ~dmaRead.valid
    io.dma.readControl.bits.index := addr
    io.dma.readControl.bits.length := stride * count

    when (io.dma.readControl.fire) {
      dmaRead.valid := true.B
    }

    when (io.dma.readChannel.fire) {
      dmaRead.bits := dmaRead.bits + 1.U
      when (~dmaRead.bits(0)) {
        real_d := io.dma.readChannel.bits.asTypeOf(real_d)
      }
      when (dmaRead.bits === stride * count * 2 - 1) {
        state := S.DMAStore
      }
    }
  }

  io.dma.writeChannel.valid := dmaWrite.valid && fft.out.valid
  fft.out.ready := dmaWrite.valid && dmaWrite.bits(0) && io.dma.writeChannel.ready
  io.dma.writeChannel.bits := Mux(dmaWrite.bits(0), fft.out.bits.imag.asUInt, fft.out.bits.real.asUInt)

  when (state === S.DMAStore) {
    io.dma.writeControl.valid := ~dmaWrite.valid
    io.dma.writeControl.bits.index := addr
    io.dma.writeControl.bits.length := stride * count

    when (io.dma.writeControl.fire) {
      dmaWrite.valid := true.B
    }

    when (io.dma.writeChannel.fire) {
      dmaWrite.bits := dmaWrite.bits + 1.U
    }
    when (dmaWrite.bits === stride * count * 2 - 1) {
      state := S.Done
    }
  }

  when (state === S.Done) {
    state := S.Idle
    debug := Errors.None
  }

}

/** A 32-point 64.40 fixed point FFT accelerator */
class DefaultFFTAccelerator(dmaWidth: Int) extends FFTAccelerator(dmaWidth, FFTParams.fixed(32, 20, 32, 32))
