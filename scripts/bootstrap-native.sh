#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
revision=7ab4ee7baad2d920464cbacfad4f4b07cf111fd2
destination="$project_root/.native/llama.cpp-$revision"
if [ -f "$destination/.phone-source-ready" ]; then
    [ "$(cat "$destination/.phone-source-ready")" = "$revision" ]
    exit 0
fi
mkdir -p "$destination"
if [ ! -d "$destination/.git" ]; then
    git -C "$destination" init -q
    git -C "$destination" remote add origin https://github.com/ggml-org/llama.cpp.git
fi
git -C "$destination" -c protocol.version=2 fetch --depth=1 --filter=blob:none origin "$revision"
git -C "$destination" sparse-checkout init --cone
git -C "$destination" sparse-checkout set cmake src include ggml common tools/mtmd vendor
git -C "$destination" checkout --detach FETCH_HEAD
[ "$(git -C "$destination" rev-parse HEAD)" = "$revision" ]
printf '%s\n' "$revision" > "$destination/.phone-source-ready"
printf 'Pinned llama.cpp ready: %s\n' "$revision"
