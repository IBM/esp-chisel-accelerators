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

import chisel3._
import chisel3.tester._
import chisel3.tester.experimental.TestOptionBuilder._
import chisel3.tester.internal.WriteVcdAnnotation

import org.scalatest._

import esp.examples.CounterAccelerator

class CounterAcceleratorSpec extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "CounterAccelerator"

  Seq(8, 64, 512).foreach{ cycles =>
    it should s"assert done after $cycles cycles" in {
      test(new CounterAccelerator(32))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
          dut.io.enable.poke(false.B)
          dut.io.dma.readControl.ready.poke(false.B)
          dut.io.dma.writeControl.ready.poke(false.B)
          dut.io.dma.readChannel.valid.poke(false.B)
          dut.io.dma.writeChannel.ready.poke(false.B)

          dut.clock.step(1)

          dut.io.config.get("ticks").poke(cycles.U)
          dut.io.enable.poke(true.B)

          dut.clock.step(1)
          dut.io.enable.poke(false.B)

          for (i <- 0 to cycles - 2) {
            dut.clock.step(1)
            dut.io.done.expect(false.B)
          }

          dut.clock.step(1)
          dut.io.done.expect(true.B)

          dut.clock.step(1)
          dut.io.done.expect(false.B)
        }
    }
  }

}
