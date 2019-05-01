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

package esp

import chisel3._

import esp.examples.{CounterAccelerator, MedianFilter}

object Generator {

  def main(args: Array[String]): Unit = {
    val examples: Seq[(String, String, () => AcceleratorWrapper)] =
      Seq( ("CounterAccelerator", "Default", (a: Int) => new CounterAccelerator(a)),
           ("MedianFilterAccelerator", "Default", (a: Int) => new MedianFilter(a, 1024, UInt(a.W))))
        .flatMap( a => Seq(32, 64, 128).map(b => (a._1, s"${a._2}_dma$b", () => new AcceleratorWrapper(b, a._3))) )

    examples.map { case (name, impl, gen) =>
      val argsx = args ++ Array("--target-dir", s"build/$name/${name}_$impl",
                                "--custom-transforms", "esp.transforms.EmitXML")
      Driver.execute(argsx, gen)
    }

  }

}
