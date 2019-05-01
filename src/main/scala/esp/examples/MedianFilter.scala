// Copyright 2019 IBM
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
import chisel3.util.{log2Up, Enum, Valid}

import esp.{Config, ConfigIO, Implementation, PackUnpack, Parameter, Specification}

import sys.process._

trait MedianFilterSpecification extends Specification {

  override lazy val config: Config = Config(
    name = "MedianFilter",
    description = "Bitonic sort median filter",
    memoryFootprintMiB = 1,
    deviceId = 0xD,
    param = Array(
      Parameter(
        name = "git Hash",
        description = Some("Git short SHA hash of the repo used to generate this accelerator"),
        value = Some(Integer.parseInt(("git log -n1 --format=%h" !!).filter(_ >= ' '), 16))),
      Parameter(
        name = "nRows",
        description = Some("Number of rows in the input image")),
      Parameter(
        name = "nCols",
        description = Some("Number of columns in the input image"))))

}

class MedianFilterState extends Bundle {
  val dmaReadReq = Bool()
  val dmaReadResp = Bool()
  val dmaWriteReq = Bool()
  val compute = Bool()
}

class MedianFilterRequest(val configIO: ConfigIO) extends Bundle {
  val config = configIO
  /* @todo brittle */
  val readLength = UInt(64.W)
  val respLength = UInt(64.W)
  val state = new MedianFilterState
}

object MedianFilterRequest {

  def init(configIO: ConfigIO) = {
    val a = Wire(new Valid(new MedianFilterRequest(configIO)))
    a.valid := false.B
    a.bits.config.getElements.foreach(_ := DontCare)
    a.bits.readLength := DontCare
    a.bits.respLength := DontCare
    a.bits.state := (new MedianFilterState).fromBits(0.U)
    a
  }

}

class MedianFilter[A <: Data](dmaWidth: Int, scratchpadSize: Int, dataType: A)
    extends Implementation(dmaWidth) with MedianFilterSpecification {

  require(dmaWidth == dataType.getWidth, "MedianFilter requires data type width to match dmaWidth")

  override val implementationName: String = "Default_medianFilter" + dmaWidth

  val scratchpad = SyncReadMem[A](scratchpadSize, dataType.cloneType)

  val req = RegInit(MedianFilterRequest.init(io.config.get.cloneType))

  /* Compute the maximum address that needs to be read via DMA. This is rounded up if needed. */
  def maxAddr: UInt = {
    val (remainder, dividend) = (io.config.get("nRows").asUInt * io.config.get("nCols").asUInt)
      .toBools
      .splitAt(dmaWidth / dataType.getWidth - 1)
    if (remainder.isEmpty) {
      Vec(dividend).asUInt
    } else {
      Vec(dividend).asUInt + Mux(remainder.reduce(_ || _), 1.U, 0.U)
    }
  }

  when (!req.valid && io.enable) {
    printf {
      val a: Printable = io.config.get.elements
        .map{ case (name, data) => p"[info]  - $name: $data\n" }
        .reduce(_ + _)
      p"[info] enabled:\n" + a
    }
    req.valid := true.B
    req.bits.config := io.config.get
    req.bits.readLength := maxAddr
    req.bits.respLength := 1.U
    req.bits.state.getElements.map(_ := false.B)
    req.bits.state.dmaReadReq := true.B
  }

  io.dma.readControl.valid := req.valid && req.bits.state.dmaReadReq
  io.dma.readControl.bits.index := 0.U
  io.dma.readControl.bits.length := req.bits.readLength
  when (io.dma.readControl.fire) {
    req.bits.state.dmaReadReq := false.B
    req.bits.state.dmaReadResp := true.B
  }

  io.dma.readChannel.ready := req.valid && req.bits.state.dmaReadResp
  when (req.valid && req.bits.state.dmaReadResp && io.dma.readChannel.fire) {
    printf(p"[info] Read: ${io.dma.readChannel.bits}\n")
    req.bits.respLength := req.bits.respLength + 1.U
    scratchpad(req.bits.respLength) := io.dma.readChannel.bits
    when (req.bits.respLength === req.bits.readLength) {
      printf(p"[info] done\n")
      req.bits.state.dmaReadResp := false.B
      req.bits.state.compute := true.B
    }
  }

  io.done := req.bits.state.compute
  when (req.valid && req.bits.state.compute) {
    req.valid := false.B
    req.bits.state.getElements.map(_ := 0.U)
  }

}
