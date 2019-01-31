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

package esp

import chisel3._
import chisel3.experimental.ChiselAnnotation
import chisel3.util.{Decoupled, Valid}

import firrtl.annotations.Annotation

class DmaControl extends Bundle {
  val index = UInt(32.W)
  val length = UInt(32.W)
}

class DmaIO(width: Int) extends Bundle {
  val Seq(readControl, writeControl) = Seq.fill(2)(Decoupled(new DmaControl))
  val readChannel = Flipped(Decoupled(UInt(width.W)))
  val writeChannel = Decoupled(UInt(width.W))
}

class AcceleratorIO(val dmaWidth: Int) extends Bundle {
  val enable = Input(Bool())
  val dma = new DmaIO(dmaWidth)
  val done = Output(Bool())
  val debug = Output(UInt(32.W))
}

/** This contains the underlying hardware that implements an ESP accelerator [[Specification]]. A concrete subclass of
  * [[Implementation]] represents one point in the design space for all accelerators meeting the [[Specification]].
  * @param dmaWidth the width of the connection to the memory bus
  */
abstract class Implementation(dmaWidth: Int) extends Module with Specification { self: Implementation =>

  /** This defines a name describing this implementation.  */
  def implementationName: String

  chisel3.experimental.annotate(
    new ChiselAnnotation {
      def toFirrtl: Annotation = EspConfigAnnotation(self.toNamed, config)
    }
  )

  def InitCommonIo(io: AcceleratorIO) {
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
}
