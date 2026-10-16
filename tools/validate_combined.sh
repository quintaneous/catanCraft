#!/usr/bin/env bash
# Isolated native development worlds only. Retain earlier worlds and all failed logs.
set -euo pipefail
cd "$(dirname "$0")/.."
evidence=${1:?Usage: tools/validate_combined.sh EVIDENCE_DIRECTORY [--offline]}
mkdir -p "$evidence"
evidence=$(realpath "$evidence")
offline=()
if [[ ${2:-} == --offline ]]; then offline=(--offline); fi
git rev-parse HEAD > "$evidence/SOURCE_COMMIT.txt"
git status --porcelain > "$evidence/WORKTREE.txt"
java -version > "$evidence/java.txt" 2>&1
gradle --version > "$evidence/gradle.txt" 2>&1
printf '%s\n' '39f2fda6a74d0d891d45b37d111079905a6f67e4261b591c13902b4215342c4b  tests/fixtures/thall4.schem' '1ea2fd544c32ed5506a553bf6c56628ecd4b7ac8d29257009e7bd84a427bbc06  tests/fixtures/thall5.schem' | sha256sum -c - > "$evidence/fixture-hashes.txt"
failed=0
run_gradle() {
    local name=$1; shift
    printf 'gradle'; printf ' %q' "$@" "${offline[@]}" --no-daemon --max-workers=4; printf '\n'
    local status=0
    gradle "$@" "${offline[@]}" --no-daemon --max-workers=4 > "$evidence/$name.log" 2>&1 || status=$?
    echo "$status" > "$evidence/$name.exit"
    if ((status)); then failed=1; fi
}
run_gradle build build
for spec in catancraft:50 catancraft_public27:11 catancraft_integration:12 catancraft_producer_r2:9 catancraft_houses:11 catancraft_defense:8; do
    ns=${spec%:*}; count=${spec#*:}
    case "$ns" in
        catancraft) dir=build/gametest-run;;
        catancraft_public27) dir=build/public-gametest-run;;
        *) dir="build/$ns-run";;
    esac
    if [[ -e "$dir" ]]; then
        mkdir -p build/test-world-history
        mv "$dir" "build/test-world-history/$ns-$(date -u +%Y%m%dT%H%M%S)-$$"
    fi
    mkdir -p "$dir/config/catancraft/schematics"
    cp tests/fixtures/thall4.schem tests/fixtures/thall5.schem "$dir/config/catancraft/schematics/"
    run_gradle "$ns" runGameTestServer "-PgameTestNamespace=$ns"
    if ! rg -F "$count GAME TESTS COMPLETE" "$evidence/$ns.log" || ! rg -F "All $count required tests passed" "$evidence/$ns.log"; then failed=1; fi
done
echo "$failed" > "$evidence/validation.exit"
exit "$failed"
