#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Starting MkDocs Documentation Server"
echo "=========================================="

if ! command -v mkdocs >/dev/null 2>&1; then
    echo "Installing MkDocs, Material Theme, and PyMdown Extensions..."
    pip install mkdocs mkdocs-material pymdown-extensions
else
    echo "Ensuring pymdown-extensions is installed..."
    pip install -q pymdown-extensions || true
fi

echo "Serving documentation portal on http://127.0.0.1:8000 ..."
mkdocs serve
