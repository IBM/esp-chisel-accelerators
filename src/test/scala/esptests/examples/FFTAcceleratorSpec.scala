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

package esptests.examples

import breeze.linalg.{DenseVector, randomDouble}
import breeze.signal.fourierTr
import breeze.math.Complex

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import chisel3.tester._
import chisel3.tester.experimental.TestOptionBuilder._
import chisel3.tester.internal.WriteVcdAnnotation

import dsptools.numbers.DspComplex

import esp.{DmaControl, DmaSize}
import esp.examples.FFTAccelerator

import firrtl.options.OptionsException

import ofdm.fft.FFTParams

import org.scalatest._

import org.scalactic.Equality
import org.scalactic.TripleEquals._
import org.scalactic.Tolerance._

class FFTAcceleratorSpec extends FlatSpec with ChiselScalatestTester with Matchers {

  private implicit class FFTAcceleratorHelpers(dut: FFTAccelerator[_]) {
    def doReset() = {
      dut.reset.poke(true.B)
      dut.io.enable.poke(false.B)
      dut.io.dma.readControl.ready.poke(false.B)
      dut.io.dma.writeControl.ready.poke(false.B)
      dut.io.dma.readChannel.valid.poke(false.B)
      dut.io.dma.writeChannel.ready.poke(false.B)
      dut.clock.step(1)
      dut.reset.poke(false.B)
    }
  }

  private implicit class BigIntHelpers(a: BigInt) {
    /** Convert from a BigInt to two's complement BigInt */
    def toTwosComplement(width: Int, binaryPoint: BigInt) = a match {
      case _ if a < 0 => a + BigInt(2).pow(width)
      case _          => a
    }

    /** Convert from a BigInt in two's complement to a signed BigInt */
    def fromTwosComplement(width: Int, binaryPoint: BigInt) = a match {
      case _ if a >= BigInt(2).pow(width - 1) => a - BigInt(2).pow(width)
      case _                                  => a
    }
  }

  private implicit class FixedPointHelpers(a: FixedPoint) {
    def toDouble = a.binaryPoint match {
      case KnownBinaryPoint(value) => a.litValue.toDouble / math.pow(2, value)
    }
  }

  private class ToleranceFixture(t: Double) {
    implicit val theTolerance = new Equality[Double] {
      def areEqual(a: Double, b: Any): Boolean = b match {
        case bb: Double =>a === bb +- t
        case _          => false
      }
    }
  }

  behavior of "FFTAccelerator"

  it should "fail to elaborate for non-power-of-2 numbers of points" in {
    /* @todo: It would be better to verify that this was more than just an OptionsException */
    assertThrows[OptionsException] {
      (new ChiselStage)
        .execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new FFTAccelerator(32, FFTParams.fixed(8, 0, 8, 3)))))
    }
  }

  it should "error for an FFT with stride not equal to its points size" in {
    val numPoints = 4

    info("errors for stride < points size")
    test(new FFTAccelerator(32, FFTParams.fixed(32, 16, 32, numPoints))){ dut =>
      dut.doReset()
      dut.io.config.get("stride").poke((numPoints - 1).U)
      dut.io.enable.poke(true.B)

      dut.clock.step(1)
      dut.io.done.expect(true.B)
      dut.io.debug.expect(1.U) // @todo: Change this to check the enum vlaue
    }

    info("errors for stride > points size")
    test(new FFTAccelerator(32, FFTParams.fixed(32, 16, 32, numPoints))){ dut =>
      dut.doReset()
      dut.io.config.get("stride").poke((numPoints + 1).U)
      dut.io.enable.poke(true.B)

      dut.clock.step(1)
      dut.io.done.expect(true.B)
      dut.io.debug.expect(1.U) // @todo: Change this to check the enum vlaue
    }
  }

  it should "fail to elaborate for bit widths > 32" in {
    assertThrows[OptionsException] {
      (new ChiselStage)
        .execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new FFTAccelerator(32, FFTParams.fixed(33, 16, 32, 8)))))
    }
  }

  def testRandom(numPoints: Int, width: Int, binaryPoint: Int, tolerance: Double): Unit = {
    val description = s"do a 1-D $numPoints-point $width.$binaryPoint fixed point FFT within $tolerance of double"
    it should description in new ToleranceFixture(tolerance) {
      val input       = DenseVector.fill(numPoints) { Complex(randomDouble() * 2 - 1, 0) }
      val output      = fourierTr(input).toScalaVector

      test(new FFTAccelerator(32, FFTParams.fixed(width, binaryPoint, width, numPoints)))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
          dut.doReset()

          dut.io.dma.readControl.initSink().setSinkClock(dut.clock)
          dut.io.dma.readChannel.initSource().setSourceClock(dut.clock)
          dut.io.dma.writeControl.initSink().setSinkClock(dut.clock)
          dut.io.dma.writeChannel.initSink().setSinkClock(dut.clock)

          dut.io.config.get("startAddr").poke(0.U)
          dut.io.config.get("count").poke(1.U)
          dut.io.config.get("stride").poke(numPoints.U)
          dut.io.enable.poke(true.B)
          dut.clock.step(1)

          dut.io.enable.poke(false.B)

          dut.io.dma.readControl
            .expectDequeue((new DmaControl).Lit(_.index -> 0.U, _.length -> (numPoints * 2).U, _.size -> DmaSize.word))

          {
            val inputx = input
              .toArray
              .flatMap(a => Seq(a.real, a.imag))
              .map(FixedPoint.toBigInt(_, binaryPoint))
              .map(_.toTwosComplement(width, binaryPoint))
              .map(_.U)
            dut.io.dma.readChannel.enqueueSeq(inputx)
          }

          dut.io.dma.writeControl
            .expectDequeue((new DmaControl).Lit(_.index -> 0.U, _.length -> (numPoints * 2).U, _.size -> DmaSize.word))

          {
            val outputx = output
              .toArray
              .flatMap(a => Seq(a.real, a.imag))
              .map(FixedPoint.fromDouble(_, width.W, binaryPoint.BP))
            val fftOut = for (i <- 0 until numPoints * 2) yield {
              dut.io.dma.writeChannel.ready.poke(true.B)
              while (dut.io.dma.writeChannel.valid.peek().litToBoolean == false) {
                dut.clock.step(1)
              }
              dut.io.dma.writeChannel.valid.expect(true.B)
              val tmp = FixedPoint
                .fromBigInt(dut.io.dma.writeChannel.bits.peek().litValue.fromTwosComplement(width, binaryPoint), width, binaryPoint)
              dut.clock.step(1)
              tmp
            }
            fftOut.zip(outputx).foreach{ case (a: FixedPoint, b: FixedPoint) =>
              val Seq(ax, bx) = Seq(a, b).map(_.toDouble)
              ax should === (bx)
            }
          }

          dut.io.done.expect(true.B)
          dut.io.debug.expect(0.U)

        }
    }
  }

  Seq(
    (2,   32, 20, 0.001),
    (4,   32, 20, 0.001),
    (8,   32, 20, 0.001),
    (16,  32, 20, 0.001),
    (32,  32, 20, 0.001),
    (64,  32, 20, 0.001),
    (128, 32, 20, 0.001)).foreach((testRandom _).tupled)

  it should "perform a 2-D convolution" in (pending)

}
