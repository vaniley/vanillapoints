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
  keys_file="$tmp_dir/$(basename "$file").keys"
  extract_keys "$file" > "$keys_file"

  # Missing translations are valid: MessageService falls back to messages.yml.
  # Unknown keys are usually typos and can never be resolved, so keep those fatal.
  comm -23 "$keys_file" "$tmp_dir/default.keys" > "$tmp_dir/unknown.keys"
  if [[ -s "$tmp_dir/unknown.keys" ]]; then
    printf 'Unknown message keys in %s:\n' "$file" >&2
    sed 's/^/  /' "$tmp_dir/unknown.keys" >&2
    status=1
  fi
done

exit "$status"
