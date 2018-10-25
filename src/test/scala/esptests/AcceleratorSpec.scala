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

package esptests

import chisel3._
import chisel3.util.Counter
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import esp.Accelerator

/** An ESP accelerator that says it's done a fixed number of cycles after it's enabled
  * @param ticks the number of cycles to count
  */
class TimerAccelerator(val ticks: Int) extends Accelerator {
  val enabled = RegInit(false.B)

  val (_, fire) = Counter(enabled, ticks)
  io.done := fire

  when (io.conf.valid) { enabled := true.B  }
  when (fire)          { enabled := false.B }
}

/** A test that the TimerAccelerator asserts it's done when it should
  * @param dut a [[TimerAccelerator]]
  */
class TimerAcceleratorTester(dut: TimerAccelerator) extends PeekPokeTester(dut) {
  def reset(): Unit = Seq(dut.io.conf.valid,
                          dut.io.dma.readControl.ready,
                          dut.io.dma.writeControl.ready,
                          dut.io.dma.readChannel.ready,
                          dut.io.dma.writeChannel.valid)
    .map(p => poke(p, 0))

  reset()

  step(1)
  poke(dut.io.conf.valid, 1)

  for (i <- 0 to dut.ticks - 2) {
    step(1)
    expect(dut.io.done, 0)
  }
  step(1)
  expect(dut.io.done, 1)

  step(1)
  expect(dut.io.done, 0)
}

class AcceleratorSpec extends ChiselFlatSpec {

  behavior of "A simple timer ESP Accelerator"

  it should "report it's done after 42 cycles" in {
    Driver(() => new TimerAccelerator(42), "firrtl")(dut => new TimerAcceleratorTester(dut)) should be (true)
  }

}
