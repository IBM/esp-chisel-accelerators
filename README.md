# ESP Accelerators in Chisel

[![Build Status](https://travis-ci.org/IBM/esp-chisel-accelerators.svg?branch=master)](https://travis-ci.org/IBM/esp-chisel-accelerators)

This project provides an example Embedded Scalable Platform (ESP) Accelerator socket that can be used for writing ESP-compatible accelerators in [chisel3](https://github.com/freechipsproject/chisel3).

This provides an `esp.Accelerator` class that can be used to build ESP-compliant accelerators. This is then wrapped in an `esp.AcceleratorWrapper` that maps port connections to expected top-level Verilog ports.

To get up and running with some tests:
```bash
sbt test
```
