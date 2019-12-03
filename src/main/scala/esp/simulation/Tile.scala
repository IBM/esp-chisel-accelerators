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

package esp.simulation

import chisel3._

import esp.{Config, ConfigIO, Implementation, Specification}

class TileIO(val espConfig: Config) extends Bundle {
  val enable = Input(Bool())
  val config = ConfigIO(espConfig).map(Input(_))
  val done = Output(Bool())
  val debug = Output(UInt(32.W))
}

class Tile(memorySize: Int, gen: => Specification with Implementation, initFile: Option[String] = None) extends Module {

  lazy val io = IO(new TileIO(accelerator.config))

  val accelerator: Implementation = Module(gen)
  val dma = Module(new Dma(memorySize, UInt(accelerator.dmaWidth.W), initFile))

  accelerator.io.dma <> dma.io
  accelerator.io.enable := io.enable
  accelerator.io.config.zip(io.config).map{ case (a, b) => a := b }
  io.done := accelerator.io.done
  io.debug := accelerator.io.debug

}
