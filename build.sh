#!/bin/bash
set -e

echo "Setting up environment..."

# Download coursier to install Scala/sbt
curl -fL https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz | gzip -d > cs
chmod +x cs

# Install sbt non-interactively to a local bin directory
./cs install sbt --install-dir ./bin
export PATH="$PATH:$PWD/bin"

echo "Building production bundle..."
sbt fullLinkJS

echo "Preparing Vercel output..."
# Output directory will be 'public'
cp target/scala-3.3.3/functional-task-manager-opt/main.js public/

echo "Build successful!"
