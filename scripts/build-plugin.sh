#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CLEAN=0
PLUGINS_DIR="${PLUGINS_DIR:-}"

usage() {
    cat <<'USAGE'
Usage:
  scripts/build-plugin.sh [--clean] [--plugins-dir PATH]
  PLUGINS_DIR=/path/to/server/plugins scripts/build-plugin.sh

Runs the full Maven verification lifecycle (including tests) and builds the
Paper/Folia plugin jar. Requires JDK 21+; respects JAVA_HOME. If a plugins
directory is provided, the verified jar is copied there for local testing.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean)
            CLEAN=1
            shift
            ;;
        --plugins-dir)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --plugins-dir" >&2
                exit 2
            fi
            PLUGINS_DIR="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ -x "$ROOT_DIR/mvnw" ]]; then
    MVN="$ROOT_DIR/mvnw"
elif command -v mvn >/dev/null 2>&1; then
    MVN="mvn"
else
    echo "Maven was not found. Install Maven or add a Maven wrapper (./mvnw)." >&2
    exit 127
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
    if [[ ! -x "$JAVA_HOME/bin/java" || ! -x "$JAVA_HOME/bin/javac" ]]; then
        echo "JAVA_HOME must point to a JDK with bin/java and bin/javac." >&2
        exit 127
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
    cat >&2 <<'ERROR'
Java and javac are both required to build VanillaPoints.

Install JDK 21 or newer, not only a Java runtime, then run this script again.
Set JAVA_HOME to that JDK when multiple Java versions are installed.
ERROR
    exit 127
fi

JAVAC_MAJOR="$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)"
if [[ ! "$JAVAC_MAJOR" =~ ^[0-9]+$ ]] || (( JAVAC_MAJOR < 21 )); then
    printf 'Building VanillaPoints requires JDK 21 or newer (found %s).\n' \
        "$(javac -version 2>&1)" >&2
    exit 1
fi

GOALS=(verify)
if [[ "$CLEAN" -eq 1 ]]; then
    GOALS=(clean verify)
fi

"$MVN" --batch-mode --no-transfer-progress -Dstyle.color=never "${GOALS[@]}"

JARS=()
shopt -s nullglob
for candidate in "$ROOT_DIR"/target/*.jar; do
    case "$(basename "$candidate")" in
        original-*|*-sources.jar|*-javadoc.jar|*-tests.jar) continue ;;
    esac
    JARS+=("$candidate")
done

if [[ "${#JARS[@]}" -ne 1 ]]; then
    printf 'Expected one plugin jar in target/, found %s. Re-run with --clean to remove stale builds.\n' \
        "${#JARS[@]}" >&2
    exit 1
fi
JAR_PATH="${JARS[0]}"

echo "Built: $JAR_PATH"

if [[ -n "$PLUGINS_DIR" ]]; then
    mkdir -p "$PLUGINS_DIR"
    cp "$JAR_PATH" "$PLUGINS_DIR/"
    echo "Copied to: $PLUGINS_DIR/$(basename "$JAR_PATH")"
fi
