#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -d "$test_dir" src/main/java/com/quin/catancraft/world/VehicleFuel49.java src/main/java/com/quin/catancraft/world/VehicleRefueling49.java tools/vehicle49-service/VehicleRefueling49Test.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$test_dir" VehicleRefueling49Test
