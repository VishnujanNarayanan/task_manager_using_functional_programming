#!/bin/bash
set -euo pipefail

# Every tool this build downloads is version-locked. Previously coursier was
# fetched from /releases/latest and sbt was installed unpinned, so the toolchain
# was whatever happened to be current on the day the deploy ran. That broke the
# build with no change to this repo when sbt 2.x shipped: it requires JDK 17,
# and the deploy image provides JDK 11.
COURSIER_VERSION="v2.1.24"

# project/build.properties is the single source of truth for the sbt version --
# it is what the sbt launcher itself reads. Deriving it here keeps the installed
# launcher and the sbt that actually runs the build from drifting apart.
SBT_VERSION="$(sed -n 's/^sbt\.version=//p' project/build.properties)"

if [ -z "$SBT_VERSION" ]; then
  echo "Could not read sbt.version from project/build.properties" >&2
  exit 1
fi

echo "Setting up environment (coursier ${COURSIER_VERSION}, sbt ${SBT_VERSION})..."

# Download coursier to install Scala/sbt
curl -fL "https://github.com/coursier/coursier/releases/download/${COURSIER_VERSION}/cs-x86_64-pc-linux.gz" | gzip -d > cs
chmod +x cs

# Install sbt non-interactively to a local bin directory
./cs install "sbt:${SBT_VERSION}" --install-dir ./bin
export PATH="$PWD/bin:$PATH"

echo "Building production bundle..."
sbt fullLinkJS

echo "Preparing Vercel output..."
# Output directory will be 'public'. The bundle path carries the Scala version
# (target/scala-3.3.3/...), so locate it rather than hardcoding that version --
# bumping scalaVersion in build.sbt would otherwise fail at the copy, after a
# full successful compile.
BUNDLE="$(find target -type f -path '*-opt/main.js' | head -1)"

if [ -z "$BUNDLE" ]; then
  echo "Linked bundle not found under target/ -- did fullLinkJS run?" >&2
  exit 1
fi

cp "$BUNDLE" public/

echo "Build successful!"
