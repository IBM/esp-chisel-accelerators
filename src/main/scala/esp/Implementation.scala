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
import chisel3.util.{Decoupled, Valid, Enum}

import firrtl.annotations.Annotation

import scala.collection.immutable

class ConfigIO private (espConfig: Config) extends Record {
  val elements = immutable.ListMap(espConfig.param.collect{ case a if !a.readOnly => a.name -> UInt(a.size.W)}: _*)
  override def cloneType: this.type = (new ConfigIO(espConfig)).asInstanceOf[this.type]
  def apply(a: String): Data = elements(a)
}

object ConfigIO {

  def apply(espConfig: Config): Option[ConfigIO] = {
    val rwParameters = espConfig.param.collect{ case a if !a.readOnly => a.name -> UInt(a.size.W) }
    if (rwParameters.isEmpty) { None                          }
    else                      { Some(new ConfigIO(espConfig)) }
  }

}

object DmaSize {
  private val enums = Enum(8)
  val Seq(bytes, wordHalf, word, wordDouble, wordQuad, word8, word16, word32) = enums
  def gen: UInt = chiselTypeOf(enums.head)
}

class DmaControl extends Bundle {
  val index = UInt(32.W)
  val length = UInt(32.W)
  val size = DmaSize.gen
}

class DmaIO(val dmaWidth: Int) extends Bundle {
  val Seq(readControl, writeControl) = Seq.fill(2)(Decoupled(new DmaControl))
  val readChannel = Flipped(Decoupled(UInt(dmaWidth.W)))
  val writeChannel = Decoupled(UInt(dmaWidth.W))
}

class AcceleratorIO(val dmaWidth: Int, val espConfig: Config) extends Bundle {
  val enable = Input(Bool())
  val config = ConfigIO(espConfig).map(Input(_))
  val dma = new DmaIO(dmaWidth)
  val done = Output(Bool())
  val debug = Output(UInt(32.W))
}


/** This contains the underlying hardware that implements an ESP accelerator [[Specification]]. A concrete subclass of
  * [[Implementation]] represents one point in the design space for all accelerators meeting the [[Specification]].
  * @param dmaWidth the width of the connection to the memory bus
  */
abstract class Implementation(val dmaWidth: Int) extends Module with Specification { self: Implementation =>

  lazy val io = IO(new AcceleratorIO(dmaWidth, config))

  /** This defines a name describing this implementation. */
  def implementationName: String

  chisel3.experimental.annotate(
    new ChiselAnnotation {
      def toFirrtl: Annotation = EspConfigAnnotation(self.toNamed, config)
    }
  )

  io.done := false.B
  io.debug := 0.U

  io.dma.readControl.valid := false.B
  io.dma.readControl.bits.index := 0.U
  io.dma.readControl.bits.length := 0.U
  io.dma.readControl.bits.size := DmaSize.word

  io.dma.writeControl.valid := false.B
  io.dma.writeControl.bits.index := 0.U
  io.dma.writeControl.bits.length := 0.U
  io.dma.writeControl.bits.size := DmaSize.word

  io.dma.readChannel.ready := 0.U

  io.dma.writeChannel.valid := 0.U
  io.dma.writeChannel.bits := 0.U
}
