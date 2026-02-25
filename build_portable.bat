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

echo Creating Portable Package with jpackage...
rmdir /S /Q portable_build 2>nul
mkdir portable_build

jpackage --type app-image ^
    --name SMCMAP ^
    --input frontend/target/lib ^
    --main-jar smcmap-frontend-1.0-SNAPSHOT.jar ^
    --main-class com.smcmap.Launcher ^
    --dest portable_build ^
    --vendor "Harshad NIkam" ^
    --app-version "2.1"

echo Copying C++ Agent to the portable directory...
copy agent\build\Release\smcmap_agent.exe portable_build\SMCMAP\smcmap_agent.exe

echo Done! The portable application is located in the portable_build\SMCMAP folder.
echo You can zip this folder and share it as a standalone app!
