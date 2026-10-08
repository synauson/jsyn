# Third-Party Notices

jsyn itself is Apache 2.0 (see [LICENSE](LICENSE) and [NOTICE](NOTICE)). The engine it
runs downloads model files into the model store at startup, only those the license's plan
includes, and the `jsyn-natives-*` jars contain code and data from the works below. Their
license terms apply.

## Models

Each model's license notice ships inside its download, not in this repository.

## Code and data in the natives jars

### spaCy

- **License:** MIT
- **Source:** https://github.com/explosion/spaCy (3.8)
- **Attribution:** Copyright (C) 2016-2024 ExplosionAI GmbH, 2016 spaCy GmbH,
  2015 Matthew Honnibal.
- **Notes:** the English tokenizer's special cases, exported from spaCy, are
  compiled into the natives, and the engine's tokenizer ports spaCy's affix rules.
