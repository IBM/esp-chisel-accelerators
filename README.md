# ESP Accelerators in Chisel

[![Build Status](https://travis-ci.com/IBM/esp-chisel-accelerators.svg?branch=master)](https://travis-ci.org/IBM/esp-chisel-accelerators)

This project provides an Embedded Scalable Platform (ESP) Accelerator socket that can be used for writing ESP-compatible accelerators in [chisel3](https://github.com/freechipsproject/chisel3).

A concrete ESP-compliant accelerator is composed from an [`esp.Implementation`](../master/src/main/scala/esp/Implementation.scala) that aligns to an [`esp.Specification`](../master/src/main/scala/esp/Specification.scala). The resulting accelerator is then wrapped with an [`esp.AcceleratorWrapper`](../master/src/main/scala/esp/AcceleratorWrapper.scala) that maps the interfaces of the accelerator to the expected top-level interface. The `esp.Specification` is abstract in a configuration that defines metadata that the ESP framework requires.

When generating Verilog from an `esp.Implementation`, a FIRRTL annotation is emitted containing the accelerator configuration. A custom FIRRTL transform [`EmitXML`](../master/src/main/scala/esp/transforms/EmitXML.scala) will convert this configuration information to XML that the ESP framework needs.

We currently provide one example accelerator, [`esp.examples.CounterAccelerator`](../master/src/main/scala/esp/examples/CounterAccelerator.scala) that always reports as being finished a run-time configurable number of cycles in the future.

To build the example accelerator, simply run:

```bash
sbt run
```

To run our existing tests use:

```bash
sbt test
```
