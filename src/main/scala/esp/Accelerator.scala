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
import chisel3.util.{Decoupled, Valid}

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

class Accelerator extends Module with AcceleratorDefaults {
  lazy val io = IO(new AcceleratorIO)
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
