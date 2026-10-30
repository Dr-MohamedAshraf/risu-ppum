#!/usr/bin/env sh
# Builds target/risu-ppum-1.0.0.jar with the JDK alone (no Maven, no network).
set -eu
cd "$(dirname "$0")"
VERSION=1.0.0
rm -rf target/classes
mkdir -p target/classes
find src/main/java -name '*.java' > target/sources.txt
if javac --release 8 -version >/dev/null 2>&1; then
  javac --release 8 -Xlint:all -Xlint:-options -encoding UTF-8 -d target/classes @target/sources.txt
else
  javac -source 1.8 -target 1.8 -Xlint:all -Xlint:-options -encoding UTF-8 -d target/classes @target/sources.txt
fi
jar cfe "target/risu-ppum-$VERSION.jar" eg.edu.asu.ppum.Runner -C target/classes .
echo "built target/risu-ppum-$VERSION.jar"
