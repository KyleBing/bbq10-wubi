#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./gradlew :app:assembleDebug --no-daemon -q
python3 scripts/prepare_site.py --local
echo "Open with: python3 -m http.server 8765 --directory dist-local --bind 127.0.0.1"
