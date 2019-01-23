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
import chisel3.util.Counter

import esp.{Config, Implementation, Parameter, Specification}

import sys.process._

/** An ESP accelerator that is done a parameterized number of clock ticks in the future
  * @param ticks the number of clock ticks until done
  */
trait CounterSpecification extends Specification {

  def ticks: Int

  /* This defines the abstract member config that provides necessary information for the ESP framework to generate an XML
   * accelerator configuration. At the Chisel level, this will be used to emit an [[esp.EspConfigAnnotation]] which will
   * be converted to an XML description by a custom FIRRTL transform, [[esp.transforms.EmitXML]]. */
  override lazy val config: Config = Config(
    name = "CounterAccelerator",
    description = s"Simple accelerator that reports being done a fixed number of cycles after being enabled",
    memoryFootprintMiB = 0,
    deviceId = 0xC,
    param = Array(
      Parameter(
        name = "gitHash",
        description = Some("Git short SHA hash of the repo used to generate this accelerator"),
        value = Some(Integer.parseInt(("git log -n1 --format=%h" !!).filter(_ >= ' '), 16))
      ),
      Parameter(
        name = "ticks",
        description = Some("read only tick count"),
        value = Some(ticks))
    )
  )

}

class CounterAccelerator(dmaWidth: Int) extends Implementation(dmaWidth) with CounterSpecification {

  override val ticks: Int = 42
  override val implementationName: String = "Default"

  val enabled = RegInit(false.B)

  val (_, fire) = Counter(enabled, 42)
  io.done := fire

  when (io.conf.valid) { enabled := true.B  }
  when (fire)          { enabled := false.B }

}
