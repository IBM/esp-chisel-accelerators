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

import chisel3._
import firrtl.{ir => fir}
import org.scalatest.{FlatSpec, Matchers}
import scala.io.Source
import scala.util.matching.Regex
import esp.{AcceleratorWrapper, Config, Implementation, Parameter, Specification}

class AcceleratorWrapperSpec extends FlatSpec with Matchers {

  /** Extract the port definitions from Verilog strings
    * @param strings some Verilog strings
    * @return the equivalent FIRRTL [[firrtl.ir.Port Port]]s
    */
  def collectVerilogIO(strings: Seq[String]): Seq[fir.Port] = {

    def n2z(s: String): Int = s match {
      case null => 0
      case x    => x.toInt
    }

    /* Match a one-line input/output statement in Verilog */
    val regex = new Regex(raw"^\s*(input|output)\s*(\[(\d+):(\d+)\])?\s*(\w+)", "direction", "width", "high", "low", "name")

    strings
      .map(regex.findFirstMatchIn)
      .flatten
      .map(m => fir.Port(info=fir.NoInfo,
                         name=m.group("name"),
                         direction=m.group("direction") match { case "input" => fir.Input; case _ => fir.Output },
                         tpe=fir.UIntType(fir.IntWidth(math.abs(n2z(m.group("high")) - n2z(m.group("low")) + 1)))))
  }

  trait FooSpecification extends Specification {
    override lazy val config: Config = Config(
      name = "foo",
      description = "a dummy accelerator used for unit tests",
      memoryFootprintMiB = 0,
      deviceId = 0,
      param = Array(
        Parameter("len"),
        Parameter("batch")
      )
    )
  }

  class BarImplementation(dmaWidth: Int) extends Implementation(dmaWidth: Int) with FooSpecification {

    override val implementationName: String = "bar"

  }

  behavior of "AcceleratorWrapper"

  it should "have the expected top-level IO when lowered to Verilog" in {
    val targetDir = "test_run_dir/AcceleratorWrapper"

    info("Verilog generation okay")
    Driver.execute(Array("-X", "verilog", "--target-dir", targetDir),
                   () => new AcceleratorWrapper(32, (a: Int) => new BarImplementation(a)))

    val expectedIO = collectVerilogIO(Source.fromFile("src/main/resources/esp_acc_iface.v").getLines.toSeq)
    val generatedIO = collectVerilogIO(Source.fromFile(s"$targetDir/foo_bar_dma32.v").getLines.toSeq).toSet

    for (g <- expectedIO) {
      info(s"Contains: ${g.serialize}")
      generatedIO should contain (g)
    }
  }
}
