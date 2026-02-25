#!/bin/bash

echo "========================================="
echo "  SMCMAP - Build and Start Script        "
echo "========================================="

echo ""
echo "[1/3] Building C++ Agent using CMake..."
cd agent || exit 1
mkdir -p build && cd build || exit 1
cmake ..
if [ $? -ne 0 ]; then
    echo "CMake configuration failed. Please ensure cmake and a C++ compiler are installed."
    exit 1
fi
cmake --build . --config Release
if [ $? -ne 0 ]; then
    echo "CMake build failed."
    exit 1
fi
cd ../.. || exit 1

echo ""
echo "[2/3] Starting C++ Agent in the background..."
AGENT_EXE=""
if [ -f "agent/build/Release/smcmap_agent.exe" ]; then
    AGENT_EXE="agent/build/Release/smcmap_agent.exe"
elif [ -f "agent/build/smcmap_agent.exe" ]; then
    AGENT_EXE="agent/build/smcmap_agent.exe"
elif [ -f "agent/build/smcmap_agent" ]; then
    AGENT_EXE="agent/build/smcmap_agent"
fi

if [ -n "$AGENT_EXE" ]; then
    # Run in background
    ./$AGENT_EXE &
    AGENT_PID=$!
    echo "Agent started with PID $AGENT_PID."
else
    echo "ERROR: Could not find built C++ agent executable."
    exit 1
fi

echo ""
echo "[3/3] Building and Starting Java Frontend using Maven..."
cd frontend || { kill $AGENT_PID; exit 1; }
mvn clean javafx:run
cd ..

echo ""
echo "Stopping C++ Agent..."
kill $AGENT_PID
echo "SMCMAP closed."
