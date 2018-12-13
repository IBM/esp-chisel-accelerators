// Copyright 2018 IBM
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

package esp

import chisel3._
import chisel3.experimental.ChiselAnnotation
import chisel3.util.{Decoupled, Valid}

import firrtl.annotations.Annotation

case class AcceleratorParameter(
  name: String,
  description: Option[String] = None,
  value: Option[Int] = None,
  size: Int = 32) {

  val readOnly = value.isDefined

  require(size >= 0, s"AccleratorParamater '$name' must be greater than 0 bits in size!")
  require(size <= 32, s"AccleratorParamater '$name' must be less than 32 bits in size!")
}


case class AcceleratorConfig(
  name: String,
  description: String,
  memoryFootprintMiB: Int,
  deviceId: Int,
  param: Array[AcceleratorParameter] = Array.empty) {

  require(memoryFootprintMiB >= 0, s"AcceleratorConfig '$name' memory footprint must be greater than 0 MiB!")
}

class Configuration extends Bundle {
  val length = UInt(32.W)
  val batch = UInt(32.W)
}

class DmaControl extends Bundle {
  val index = UInt(32.W)
  val length = UInt(32.W)
}

class DmaIO extends Bundle {
  val Seq(readControl, writeControl) = Seq.fill(2)(Decoupled(new DmaControl))
  val readChannel = Flipped(Decoupled(UInt(32.W)))
  val writeChannel = Decoupled(UInt(32.W))
}

class AcceleratorIO extends Bundle {
  val conf = Input(Valid(new Configuration))
  val dma = new DmaIO
  val done = Output(Bool())
  val debug = Output(UInt(32.W))
}

abstract class Accelerator extends Module with AcceleratorDefaults { self: Accelerator =>
  lazy val io = IO(new AcceleratorIO)

  def config: AcceleratorConfig

  chisel3.experimental.annotate(
    new ChiselAnnotation {
      def toFirrtl: Annotation = EspConfigAnnotation(self.toNamed, config)
    }
  )
}

trait AcceleratorDefaults { this: Accelerator =>
  io.done := false.B
  io.debug := 0.U

  io.dma.readControl.valid := false.B
  io.dma.readControl.bits.index := 0.U
  io.dma.readControl.bits.length := 0.U

  io.dma.writeControl.valid := false.B
  io.dma.writeControl.bits.index := 0.U
  io.dma.writeControl.bits.length := 0.U

  io.dma.readChannel.ready := 0.U

  io.dma.writeChannel.valid := 0.U
  io.dma.writeChannel.bits := 0.U
}
