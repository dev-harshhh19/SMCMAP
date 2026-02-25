@echo off
setlocal

echo Building C++ Agent...
cd agent
mkdir build 2>nul
cd build
cmake ..
cmake --build . --config Release
cd ..\..

echo Building Java Frontend...
cd frontend
call mvn clean package
call mvn dependency:copy-dependencies -DoutputDirectory=target/lib
copy target\smcmap-frontend-1.0-SNAPSHOT.jar target\lib\smcmap-frontend-1.0-SNAPSHOT.jar
cd ..

echo Creating App Image with jpackage...
rmdir /S /Q portable_build 2>nul
rmdir /S /Q installer_build 2>nul
mkdir portable_build
mkdir installer_build

jpackage --type app-image ^
    --name SMCMAP ^
    --input frontend/target/lib ^
    --main-jar smcmap-frontend-1.0-SNAPSHOT.jar ^
    --main-class com.smcmap.Launcher ^
    --dest portable_build ^
    --vendor "Harshad Nikam" ^
    --app-version "1.0"

echo Copying C++ Agent to the app image...
copy agent\build\Release\smcmap_agent.exe portable_build\SMCMAP\smcmap_agent.exe

echo Creating MSI Installer Package (Requires WiX Toolset 3.0+)...
jpackage --type msi ^
    --app-image portable_build\SMCMAP ^
    --name SMCMAP-Setup ^
    --dest installer_build ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser

echo Done! The MSI installer is located in the installer_build folder.
