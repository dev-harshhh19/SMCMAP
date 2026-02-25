@echo off
setlocal

echo =========================================
echo   SMCMAP - Build and Start Script
echo =========================================

echo.
echo [1/3] Building C++ Agent using CMake...
cd agent
if not exist build mkdir build
cd build
cmake ..
if %ERRORLEVEL% neq 0 (
    echo [INFO] Default CMake generator failed (often due to missing NMake).
    echo [INFO] Attempting to use MinGW Makefiles...
    if exist CMakeCache.txt del CMakeCache.txt
    cmake -G "MinGW Makefiles" ..
    if %ERRORLEVEL% neq 0 (
        echo CMake configuration failed. Please ensure CMake and a C++ compiler are installed, or run this from a Developer Command Prompt.
        exit /b 1
    )
)
cmake --build . --config Release
if %ERRORLEVEL% neq 0 (
    echo CMake build failed.
    exit /b %ERRORLEVEL%
)
cd ..\..

echo.
echo [2/3] Starting C++ Agent in a new window...
set AGENT_EXE=agent\build\Release\smcmap_agent.exe
if not exist "%AGENT_EXE%" (
    set AGENT_EXE=agent\build\smcmap_agent.exe
)
if exist "%AGENT_EXE%" (
    start "SMCMAP C++ Agent" cmd /c "%AGENT_EXE%"
    echo Agent started successfully.
) else (
    echo ERROR: Could not find built C++ agent executable.
    exit /b 1
)

echo.
echo [3/3] Building and Starting Java Frontend using Maven...
cd frontend
call mvn clean javafx:run
if %ERRORLEVEL% neq 0 (
    echo Maven build or run failed. Please ensure Maven and JDK 17+ are installed.
    exit /b %ERRORLEVEL%
)
cd ..

echo.
echo SMCMAP Frontend closed.
