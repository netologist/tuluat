#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FQCN_PATTERN='new com\.tuluat\.|com\.tuluat\.[a-z]+\.[a-z]+\s+[A-Z]|com\.tuluat\.[a-z]+\.[A-Z][a-zA-Z]*\s|com\.tuluat\.[a-z]+\.[A-Z][a-zA-Z]*\('

echo "=== Checking for unnecessary FQCN usage (com.tuluat.* in code) ==="

violations=""
while IFS= read -r file; do
  while IFS=: read -r line_num content; do
    # Skip package declarations and import statements
    [[ "$content" =~ ^[[:space:]]*package ]] && continue
    [[ "$content" =~ ^[[:space:]]*import ]] && continue
    # Skip string literals and comments
    [[ "$content" =~ \"com\.tuluat\. ]] && continue
    [[ "$content" =~ // ]] && continue
    if echo "$content" | grep -qE "$FQCN_PATTERN"; then
      violations+="$file:$line_num:$content"$'\n'
    fi
  done < <(grep -nE "$FQCN_PATTERN" "$file" 2>/dev/null || true)
done < <(find "$ROOT_DIR" -name '*.java' -not -path '*/target/*')

if [ -n "$violations" ]; then
  echo "ERROR: Found unnecessary fully-qualified class name usage:"
  echo ""
  echo "$violations"
  echo ""
  echo "Replace with short name + import statement instead of FQCN in code."
  exit 1
fi

echo "No FQCN violations found."
