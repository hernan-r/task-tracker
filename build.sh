#!/usr/bin/env bash
# Compila el proyecto y genera task-tracker.jar (sin dependencias externas).
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out
mkdir -p out

find src/main/java -name '*.java' -print0 \
  | xargs -0 javac --release 17 -encoding UTF-8 -Xlint:all -d out

jar --create --file task-tracker.jar --main-class tasktracker.Main -C out .

echo "Compilado: $(pwd)/task-tracker.jar"
echo "Prueba:   java -jar task-tracker.jar help"
