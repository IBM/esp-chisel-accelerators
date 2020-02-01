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

package esptests

import esp.Implementation

import chisel3._
import chisel3.tester._

object AcceleratorSpec {

  implicit class AcceleratorHelpers[A <: Implementation](dut: A) {
    def doReset() = {
      dut.reset.poke(true.B)
      dut.io.enable.poke(false.B)
      dut.io.dma.readControl.ready.poke(false.B)
      dut.io.dma.writeControl.ready.poke(false.B)
      dut.io.dma.readChannel.valid.poke(false.B)
      dut.io.dma.writeChannel.ready.poke(false.B)
      dut.clock.step(1)
      dut.reset.poke(false.B)
    }
  }

}
