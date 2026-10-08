# Third-Party Notices

jsyn itself is Apache 2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)). The engine it
runs downloads model files into the model store at startup, only those the license's plan
includes, and the `jsyn-natives-*` jars contain code and data from the works below. Their
license terms apply.

## Models

Each model's licence notice ships inside its download, as `<model id>-NOTICE.txt` in the
model store; `JSyn.modelNotices(store)` returns them. Pass a model's notice on with the
model.

## Code and data in the natives jars

The `jsyn-natives-*` jars carry their own notice, `META-INF/NOTICE` inside each jar, for
the code and data compiled into the natives. Its terms apply to them.

## Bundled native libraries

### ONNX Runtime

- **Files:** `libonnxruntime.so` / `onnxruntime.dll` (in the natives jars)
- **License:** MIT
- **Source:** https://github.com/microsoft/onnxruntime
- **Attribution:** Copyright (c) Microsoft Corporation.
