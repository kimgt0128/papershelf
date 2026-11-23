#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p models/minilm
cd models/minilm
root=spring-projects/spring-ai/v2.0.2/models/spring-ai-transformers/src/main/resources/onnx/all-MiniLM-L6-v2
curl --fail --location --retry 3 "https://media.githubusercontent.com/media/$root/model.onnx" -o model.onnx
curl --fail --location --retry 3 "https://raw.githubusercontent.com/$root/tokenizer.json" -o tokenizer.json
shasum -a 256 -c <<'SUM'
e3dde332c13808c718680e7bf74a574e7e5d06f55bd6e1527e51509dcb8206f3  model.onnx
da0e79933b9ed51798a3ae27893d3c5fa4a201126cef75586296df9b4d2c62a0  tokenizer.json
SUM
