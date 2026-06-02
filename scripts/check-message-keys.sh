#!/usr/bin/env bash
set -euo pipefail

resources_dir="${1:-src/main/resources}"
default_file="$resources_dir/messages.yml"

if [[ ! -f "$default_file" ]]; then
  printf 'Missing default messages file: %s\n' "$default_file" >&2
  exit 1
fi

extract_keys() {
  local file="$1"
  while IFS= read -r line; do
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" =~ ^[[:space:]]*$ ]] && continue
    if [[ "$line" =~ ^([A-Za-z0-9_-]+): ]]; then
      printf '%s\n' "${BASH_REMATCH[1]}"
    fi
  done < "$file" | sort
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

extract_keys "$default_file" > "$tmp_dir/default.keys"
status=0

for file in "$resources_dir"/messages_*.yml; do
  [[ -e "$file" ]] || continue
  extract_keys "$file" > "$tmp_dir/$(basename "$file").keys"
  if ! diff -u "$tmp_dir/default.keys" "$tmp_dir/$(basename "$file").keys"; then
    printf 'Message key mismatch in %s\n' "$file" >&2
    status=1
  fi
done

exit "$status"
