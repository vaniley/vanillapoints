#!/usr/bin/env bash
set -euo pipefail

# Exercise the helper without downloading dependencies or starting a server.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/project/scripts" "$TEST_DIR/jdk/bin"
cp "$ROOT_DIR/scripts/build-plugin.sh" "$TEST_DIR/project/scripts/build-plugin.sh"

cat > "$TEST_DIR/jdk/bin/java" <<'JAVA'
#!/usr/bin/env bash
exit 0
JAVA
cat > "$TEST_DIR/jdk/bin/javac" <<'JAVAC'
#!/usr/bin/env bash
echo "javac ${TEST_JAVA_VERSION:-21.0.1}"
JAVAC
cat > "$TEST_DIR/project/mvnw" <<'MAVEN'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$TEST_MVN_LOG"
if [[ "${TEST_MVN_EXIT:-0}" -ne 0 ]]; then
    exit "$TEST_MVN_EXIT"
fi
mkdir -p target
if [[ "${TEST_NO_JAR:-0}" -eq 0 ]]; then
    printf 'verified plugin\n' > target/vanillapoints-1.5.0.jar
    touch target/original-vanillapoints-1.5.0.jar target/vanillapoints-1.5.0-sources.jar
fi
MAVEN
chmod +x "$TEST_DIR/jdk/bin/"* "$TEST_DIR/project/mvnw"

export JAVA_HOME="$TEST_DIR/jdk"
export TEST_MVN_LOG="$TEST_DIR/maven-args"
BUILD="$TEST_DIR/project/scripts/build-plugin.sh"

expect_status() {
    local expected="$1"
    shift
    local actual=0
    "$@" > "$TEST_DIR/output" 2>&1 || actual="$?"
    if [[ "$actual" -ne "$expected" ]]; then
        cat "$TEST_DIR/output" >&2
        printf 'Expected exit %s, got %s: %s\n' "$expected" "$actual" "$*" >&2
        exit 1
    fi
}

expect_status 0 bash "$BUILD" --help
expect_status 2 bash "$BUILD" --unknown
expect_status 2 bash "$BUILD" --plugins-dir
expect_status 1 env TEST_JAVA_VERSION=20.0.2 bash "$BUILD"
expect_status 127 env JAVA_HOME="$TEST_DIR/no-jdk" bash "$BUILD"

expect_status 0 bash "$BUILD" --clean --plugins-dir "$TEST_DIR/server plugins"
grep -qx clean "$TEST_MVN_LOG"
grep -qx verify "$TEST_MVN_LOG"
if grep -q -- '-DskipTests' "$TEST_MVN_LOG"; then
    echo 'The helper must run tests.' >&2
    exit 1
fi
cmp "$TEST_DIR/project/target/vanillapoints-1.5.0.jar" "$TEST_DIR/server plugins/vanillapoints-1.5.0.jar"
expect_status 0 env TEST_JAVA_VERSION=25.0.1 bash "$BUILD"
grep -qx verify "$TEST_MVN_LOG"

printf 'keep deployed plugin\n' > "$TEST_DIR/server plugins/vanillapoints-1.5.0.jar"
expect_status 1 env TEST_MVN_EXIT=1 bash "$BUILD" --plugins-dir "$TEST_DIR/server plugins"
grep -qx 'keep deployed plugin' "$TEST_DIR/server plugins/vanillapoints-1.5.0.jar"

touch "$TEST_DIR/project/target/vanillapoints-1.4.0.jar"
expect_status 1 bash "$BUILD" --plugins-dir "$TEST_DIR/server plugins"
grep -q 'Re-run with --clean' "$TEST_DIR/output"
grep -qx 'keep deployed plugin' "$TEST_DIR/server plugins/vanillapoints-1.5.0.jar"
rm -f "$TEST_DIR/project/target/"*.jar
expect_status 1 env TEST_NO_JAR=1 bash "$BUILD"

echo 'Build helper checks passed.'
