#!/usr/bin/env bash
set -euo pipefail

# Backwards-compatible wrapper; canonical script is singular.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/create-ds-and-template.sh" "$@"
