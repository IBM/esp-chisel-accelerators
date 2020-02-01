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

package esp.examples

import chisel3._
import chisel3.experimental.ChiselEnum

import esp.{Config, Implementation, Parameter, Specification}

trait AdderSpecification extends Specification {

  override lazy val config = Config(
    name = "AdderAccelerator",
    description = "Reduces a vector via addition",
    memoryFootprintMiB = 1,
    deviceId = 0xF,
    param = Array(
      Parameter( name = "readAddr" ),
      Parameter( name = "size" ),
      Parameter( name = "writeAddr" )
    )
  )

}

object AdderAccelerator {

  private object S extends ChiselEnum {
    val Idle, DMALoad, Compute, DMAStore, Done = Value
  }

  /** FFTAccelerator error codes */
  object Errors extends ChiselEnum {
    val None = Value(0.U)
    val InvalidSize, Unimplemented = Value
  }

}

class AdderAccelerator(dmaWidth: Int) extends Implementation(dmaWidth) with AdderSpecification {
  require(dmaWidth == 32)

  import AdderAccelerator._

  override val implementationName = "AdderAccelerator"

  private val readAddr, size, writeAddr = Reg(UInt(32.W))

  private val state = RegInit(S.Idle)

  private val acc, count = Reg(UInt(32.W))

  private val storeReqSent = RegInit(false.B)

  when (io.enable && state === S.Idle) {
    Seq((readAddr, "readAddr"), (size, "size"), (writeAddr, "writeAddr")).foreach{
      case (lhs, name) => lhs := io.config.get(name).asUInt
    }
    when (io.config.get("size").asUInt === 0.U) {
      state := S.DMAStore
    }.otherwise {
      state := S.DMALoad
    }
    acc := 0.U
    count := 0.U
    storeReqSent := false.B
  }

  when (state === S.DMALoad) {
    io.dma.readControl.valid := true.B
    io.dma.readControl.bits.index := readAddr
    io.dma.readControl.bits.length := size
    when (io.dma.readControl.fire) {
      state := S.Compute
    }
  }

  when (state === S.Compute) {
    io.dma.readChannel.ready := true.B
    when (io.dma.readChannel.fire) {
      acc := acc + io.dma.readChannel.bits
      count := count + (dmaWidth / 32).U
      when (count === size - 1.U) {
        state := S.DMAStore
      }
    }
  }

  when (state === S.DMAStore) {
    io.dma.writeChannel.bits := acc
    when (storeReqSent =/= true.B) {
      io.dma.writeControl.valid := true.B
      io.dma.writeControl.bits.index := writeAddr
      io.dma.writeControl.bits.length := 1.U
      storeReqSent := io.dma.writeControl.fire
    }.otherwise {
      io.dma.writeChannel.valid := true.B
      when (io.dma.writeChannel.fire) {
        state := S.Done
      }
    }
  }

  when (state === S.Done) {
    io.done := true.B
    state := S.Idle
  }

}
