#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/../.." && pwd)"
classes_dir="$(mktemp -d)"
trap 'rm -rf "$classes_dir"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -d "$classes_dir" \
  "$repo_dir/src/main/java/com/quin/catancraft/world/VehicleFuel49.java" \
  "$repo_dir/tools/vehicle49/VehicleFuel49Test.java"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$classes_dir" VehicleFuel49Test
