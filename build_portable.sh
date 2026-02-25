#!/bin/bash
set -e

echo "Building C++ Agent..."
cd agent
mkdir -p build
cd build
cmake ..
cmake --build . --config Release
cd ../..

echo "Building Java Frontend..."
cd frontend
mvn clean package
mvn dependency:copy-dependencies -DoutputDirectory=target/lib
cp target/smcmap-frontend-1.0-SNAPSHOT.jar target/lib/smcmap-frontend-1.0-SNAPSHOT.jar
cd ..

echo "Creating Portable Package with jpackage..."
rm -rf portable_build
mkdir -p portable_build

jpackage --type app-image \
    --name SMCMAP \
    --input frontend/target/lib \
    --main-jar smcmap-frontend-1.0-SNAPSHOT.jar \
    --main-class com.smcmap.Launcher \
    --dest portable_build \
    --vendor "Harshad NIkam" \
    --app-version "2.1"

echo "Copying C++ Agent to the portable directory..."
if [[ "$OSTYPE" == "darwin"* ]]; then
  cp agent/build/smcmap_agent portable_build/SMCMAP.app/Contents/MacOS/smcmap_agent 2>/dev/null || cp agent/build/smcmap_agent portable_build/SMCMAP/smcmap_agent
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
  cp agent/build/smcmap_agent portable_build/SMCMAP/smcmap_agent
else
  cp agent/build/Release/smcmap_agent.exe portable_build/SMCMAP/smcmap_agent.exe 2>/dev/null || cp agent/build/smcmap_agent.exe portable_build/SMCMAP/smcmap_agent.exe
fi

echo "Done! The portable application is located in the portable_build folder."
echo "You can zip this folder and share it as a standalone app!"
