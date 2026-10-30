@echo off
rem Builds target\risu-ppum-1.0.0.jar with the JDK alone (no Maven, no network).
setlocal
cd /d "%~dp0"
set VERSION=1.0.0
if exist target\classes rmdir /s /q target\classes
mkdir target\classes
set SRC=src\main\java\eg\edu\asu\ppum
set FILES=%SRC%\*.java %SRC%\algorithms\*.java %SRC%\data\*.java %SRC%\evaluation\*.java
javac --release 8 -version >nul 2>&1
if errorlevel 1 (
  javac -source 1.8 -target 1.8 -Xlint:all -Xlint:-options -encoding UTF-8 -d target\classes %FILES% || exit /b 1
) else (
  javac --release 8 -Xlint:all -Xlint:-options -encoding UTF-8 -d target\classes %FILES% || exit /b 1
)
jar cfe target\risu-ppum-%VERSION%.jar eg.edu.asu.ppum.Runner -C target\classes . || exit /b 1
echo built target\risu-ppum-%VERSION%.jar
