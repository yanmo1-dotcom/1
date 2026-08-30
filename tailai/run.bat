@echo off
title Terraria Lite
cd /d %~dp0

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java not found. Please install JDK 26 and add it to PATH.
    pause
    exit /b 1
)

if not exist out mkdir out
echo Compiling with JDK 26 ...
javac -encoding UTF-8 -d out src\tailai\*.java
if errorlevel 1 (
    echo [ERROR] Compile failed. See messages above.
    pause
    exit /b 1
)

echo Compile OK. Starting game ...
java -cp out tailai.Main
