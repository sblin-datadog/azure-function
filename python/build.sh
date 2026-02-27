#!/bin/bash
# Build a deployment zip for Azure Functions Python (Linux x64, Python 3.11)
set -e

cd "$(dirname "$0")"

echo "==> Cleaning previous build..."
rm -rf .python_packages deploy.zip

echo "==> Installing dependencies for Linux x64 / Python 3.11..."
pip install \
  --platform manylinux2014_x86_64 \
  --python-version 3.11 \
  --implementation cp \
  --only-binary :all: \
  --target .python_packages/lib/site-packages \
  -r requirements.txt

echo "==> Creating deploy.zip..."
zip -r deploy.zip \
  function_app.py \
  host.json \
  requirements.txt \
  .python_packages

echo "==> Done: $(du -sh deploy.zip | cut -f1) — deploy.zip"
