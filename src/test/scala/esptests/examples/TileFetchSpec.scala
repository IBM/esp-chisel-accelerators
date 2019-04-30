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

import esp.examples.TileFetch

import java.io.File

class TileFetchTester(dut: TileFetch) extends AdvTester(dut) {
  def reset(): Unit = wire_poke(dut.io.datain.valid, false.B)

  def write(value: Int): Unit = {
    wire_poke(dut.io.datain.valid, 1)
    wire_poke(dut.io.datain.bits, value)
    step(1)
    wire_poke(dut.io.datain.valid, 0)
  }

  reset()
  step(1)

  write(8)
}

class TileFetchSpec extends ChiselFlatSpec {

  val memFile: Option[String] = {
    val resourceDir: File = new File(System.getProperty("user.dir"), "src/test/resources")
    Some(new File(resourceDir, "linear-mem.txt").toString)
  }

  behavior of "TileFetch"

  it should "do some stuff" in {
    Driver(() => new TileFetch(3, 6, 4, 32), "treadle") {
      dut => new TileFetchTester(dut)
    } should be (true)
  }

}
