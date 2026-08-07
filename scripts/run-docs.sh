#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Starting MkDocs Documentation Server"
echo "=========================================="

if ! command -v mkdocs >/dev/null 2>&1; then
    echo "MkDocs is not installed locally. Installing mkdocs-material..."
    pip install mkdocs mkdocs-material
fi

echo "Serving documentation portal on http://127.0.0.1:8000 ..."
mkdocs serve
