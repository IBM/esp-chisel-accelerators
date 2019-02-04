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

/** A parameter used to configure the [[Specification]].
  * @param name the name of the parameter
  * @param description an optional string describing what this parameter sets/controls
  * @param value an optional read-only, default value for this parameter
  * @param size the width of this parameter in bits (1--32)
  */
case class Parameter(
  name: String,
  description: Option[String] = None,
  value: Option[Int] = None,
  size: Int = 32) {

  val readOnly = value.isDefined

  require(size >= 0, s"AccleratorParamater '$name' must be greater than 0 bits in size!")
  require(size <= 32, s"AccleratorParamater '$name' must be less than or equal to 32 bits in size!")

  def espString: String = name + (if (value.isDefined) s"_${value.get}" else "")
}

/** Mandatory configuration information that defines an ESP accelerator [[Specification]].
  * @param name the specification name
  * @param description a string describing what this specification does
  * @param memoryFootprintMiB the accelerator's memory footprint
  * @param deviceId a unique device identifier
  * @param param an optional array of parameters describing configuration registers
  */
case class Config(
  name: String,
  description: String,
  memoryFootprintMiB: Int,
  deviceId: Int,
  param: Array[Parameter] = Array.empty) {

  require(memoryFootprintMiB >= 0, s"AcceleratorConfig '$name' memory footprint must be greater than 0 MiB!")

  def espString: String = (name +: param).mkString("_")

  val paramMap: Map[String, Parameter] = param
    .groupBy(_.name)
    .map{ case (k, v) =>
      require(v.size == 1, s"AcceleratorConfig '$name' has non-uniquely named parameter '$k'")
      k -> v.head
    }

}

/** This defines ESP configuration information shared across a range of accelerator [[Implementation]]s. */
trait Specification {

  /** An ESP [[Config]] that provides information to the ESP framework necessary to insert an accelerator into an ESP
    * SoC.
    */
  def config: Config

}
