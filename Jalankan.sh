#!/bin/sh
set -eu
cd "$(dirname "$0")"
echo 'KawalPorsi · buka http://localhost:8080 setelah muncul "Started FlowApplication".'
exec java -jar KawalPorsi.jar "$@"
