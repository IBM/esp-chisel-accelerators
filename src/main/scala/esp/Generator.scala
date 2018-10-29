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

package esp

import chisel3.Driver

import esp.examples.CounterAccelerator

object Generator {

  def main(args: Array[String]): Unit = {
    val examples: Seq[(String, () => Accelerator)] = Seq(
      ("CounterAccelerator42", () => new CounterAccelerator(42)) )

    examples.map { case (name, gen) =>
      val argsx = args ++ Array("--target-dir", s"build/$name")
      Driver.execute(argsx, () => new AcceleratorWrapper(gen())) }
  }

}
