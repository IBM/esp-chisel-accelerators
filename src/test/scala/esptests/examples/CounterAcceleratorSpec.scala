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

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import esp.examples.CounterAccelerator

/** A test that the [[CounterAccelerator]] asserts it's done when it should
  * @param dut a [[CounterAccelerator]]
  */
class CounterAcceleratorTester(dut: CounterAccelerator, ticks: Int) extends PeekPokeTester(dut) {
  def reset(): Unit = Seq(dut.io.enable,
                          dut.io.dma.readControl.ready,
                          dut.io.dma.writeControl.ready,
                          dut.io.dma.readChannel.ready,
                          dut.io.dma.writeChannel.valid)
    .map(p => poke(p, 0))

  reset()

  step(1)
  poke(dut.io.config.get("ticks").asUInt, ticks)
  poke(dut.io.enable, 1)

  step(1)
  poke(dut.io.enable, 0)

  for (i <- 0 to ticks - 2) {
    step(1)
    expect(dut.io.done, 0)
  }
  step(1)
  expect(dut.io.done, 1)

  step(1)
  expect(dut.io.done, 0)
}

class CounterAcceleratorSpec extends ChiselFlatSpec {

  behavior of "CounterAccelerator"

  def doneInNCycles(cycles: Int): Unit = {
    it should s"assert done after $cycles cycles" in {
      Driver(() => new CounterAccelerator(32), "firrtl") {
        dut => new CounterAcceleratorTester(dut, cycles)
      } should be (true)
    }
  }

  Seq(8, 64, 512).foreach(doneInNCycles(_))

}
