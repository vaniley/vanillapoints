#!/usr/bin/env bash
set -euo pipefail

MAVEN_VERSION="${MAVEN_VERSION:-3.9.11}"
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="$BASE_DIR/.mvn/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_HOME="$DIST_DIR/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"
ARCHIVE="$DIST_DIR/apache-maven-$MAVEN_VERSION-bin.tar.gz"
URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"

download() {
    if command -v curl >/dev/null 2>&1; then
        curl -fL "$URL" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ARCHIVE" "$URL"
    else
        echo "Neither curl nor wget was found. Install one of them to download Maven." >&2
        exit 127
    fi
}

if [[ ! -x "$MAVEN_BIN" ]]; then
    mkdir -p "$DIST_DIR"
    echo "Downloading Apache Maven $MAVEN_VERSION..."
    download
    tar -xzf "$ARCHIVE" -C "$DIST_DIR"
fi

exec "$MAVEN_BIN" "$@"
