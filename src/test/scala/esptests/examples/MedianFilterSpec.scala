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

package esptests.examples

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, Driver, AdvTester}

import esp.examples.MedianFilter
import esp.simulation.Tile

import java.io.File

/** A test that the [[CounterAccelerator]] asserts it's done when it should
  * @param dut a [[CounterAccelerator]]
  */
class MedianFilterTester(dut: Tile) extends AdvTester(dut) {
  def reset(): Unit = Seq(dut.io.enable).map(p => wire_poke(p, false))

  def config(nRows: Int, nCols: Int): Unit = {
    wire_poke(dut.io.config.get("nRows").asUInt, nRows)
    wire_poke(dut.io.config.get("nCols").asUInt, nCols)
  }

  reset()
  step(1)

  config(3, 3)
  step(1)

  wire_poke(dut.io.enable, 1)

  eventually(peek(dut.io.done) == 1)
}

class MedianFilterSpec extends ChiselFlatSpec {

  val memFile: Option[String] = {
    val resourceDir: File = new File(System.getProperty("user.dir"), "src/test/resources")
    Some(new File(resourceDir, "linear-mem.txt").toString)
  }

  behavior of "MedianFilter"

  it should "filter a 3x3 image to 1 pixel" in {
    Driver(() => new Tile(1024, new MedianFilter(32, 1024, UInt(32.W)), memFile), "treadle") {
      dut => new MedianFilterTester(dut)
    } should be (true)
  }

}
