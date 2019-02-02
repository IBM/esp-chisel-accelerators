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

import firrtl.annotations.{ModuleName, SingleTargetAnnotation}

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.{HierarchicalStreamReader, HierarchicalStreamWriter}
import com.thoughtworks.xstream.io.xml.{DomDriver, XmlFriendlyNameCoder}
import com.thoughtworks.xstream.converters.{Converter, MarshallingContext, UnmarshallingContext}

class ParameterConverter extends Converter {

  override def marshal(source: scala.Any, writer: HierarchicalStreamWriter, context: MarshallingContext): Unit = {
    val c = source.asInstanceOf[Parameter]
    writer.addAttribute("name", c.name)
    if (c.description.isDefined) { writer.addAttribute("desc", c.description.get) }
    if (c.value.isDefined) { writer.addAttribute("value", c.value.get.toString) }
  }

  override def unmarshal(reader: HierarchicalStreamReader, context: UnmarshallingContext): AnyRef = {
    ??? /* This is currently unimplemented */
  }

  override def canConvert(c: Class[_]): Boolean = c.isAssignableFrom(classOf[Parameter])

}

/** Encodes ESP configuration and can serialize to SLD-compatible XML.
  * @param target the module this configuration applies to
  * @param config the ESP accelerator configuration
  * @param dir either a (left) absolute path or (right) a path relative to a [[TargetDirAnnotation]]
  */
case class EspConfigAnnotation(target: ModuleName, config: Config, dir: Either[String, String] = Right(".."))
    extends SingleTargetAnnotation[ModuleName] {

  def duplicate(targetx: ModuleName): EspConfigAnnotation = this.copy(target=targetx)

  def toXML: String = {
    val xs = new XStream(new DomDriver("UTF-8", new XmlFriendlyNameCoder("_", "_")))

    xs.registerConverter(new ParameterConverter)
    // xs.aliasSystemAttribute(null, "class")
    xs.alias("sld", this.getClass)
    xs.aliasField("accelerator", this.getClass, "config")
    xs.useAttributeFor(config.getClass, "name")
    xs.useAttributeFor(config.getClass, "description")
    xs.aliasField("desc", config.getClass, "description")
    xs.useAttributeFor(config.getClass, "memoryFootprintMiB")
    xs.aliasField("data_size", config.getClass, "memoryFootprintMiB")
    xs.useAttributeFor(config.getClass, "deviceId")
    xs.aliasField("device_id", config.getClass, "deviceId")
    xs.addImplicitArray(config.getClass, "param")
    xs.alias("param", classOf[Parameter])
    xs.useAttributeFor(classOf[Parameter], "name")
    xs.aliasField("desc", classOf[Parameter], "description")
    xs.useAttributeFor(classOf[Parameter], "description")
    xs.omitField(classOf[Parameter], "readOnly")
    xs.omitField(config.getClass, "paramMap")
    xs.omitField(this.getClass, "target")
    xs.omitField(this.getClass, "dir")
    xs.toXML(this)
  }
}
