@echo off
setlocal

cd /d "%~dp0"

:: Maven is only needed for the initial build. After that, lib\ and
:: thirdparty\ant\lib\ contain everything and the JVM is launched directly.
set NEEDS_BUILD=0
set "PKG_PATH=${package}"
set "PKG_PATH=%PKG_PATH:.=\%"
if not exist "target\classes\%PKG_PATH%\Start.class" set NEEDS_BUILD=1
dir /b "lib\*.jar" >nul 2>&1
if errorlevel 1 set NEEDS_BUILD=1
if not exist "thirdparty\ant\lib\ant-launcher.jar" set NEEDS_BUILD=1

if not "%NEEDS_BUILD%"=="1" goto :build_done
echo Building (compile + package)...
call mvn -q package -DskipTests
if errorlevel 1 exit /b 1
:build_done

if exist "log\" goto :log_done
echo Creating log directory...
mkdir log
:log_done

if exist "jsystem.properties" goto :props_done
echo Creating jsystem.properties...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$d = (Get-Location).Path.Replace('\', '\\').Replace(':', '\:'); @('sutClassName=jsystem.framework.sut.SutImpl', 'logger=true', 'max.building.blocks.number=500', '.level=INFO', 'jsystem.level=INFO', 'org.apache.http.client.level=INFO', 'org.apache.http.impl.level=INFO', 'org.apache.http.wire.level=INFO', 'org.apache.http.level=INFO', ('tests.src=' + $d + '\\src\\main\\java'), ('tests.dir=' + $d + '\\target\\classes'), ('resources.src=' + $d + '\\src\\main\\resources'), 'htmlReportDir=log', 'sutFile=default.xml', 'currentScenario=scenarios/default', 'convert.old.scenarios=true', 'agent.client.list=local', 'reporter.classes=jsystem.extensions.report.simpleHtmlReporter.SimpleHtmlReporter') | Set-Content -Encoding ascii jsystem.properties"
if errorlevel 1 exit /b 1
:props_done

echo Launching JSystem Test Runner...
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -Djsystem.main=jsystem.treeui.TestRunner -classpath "target\classes;lib\*;thirdparty\ant\lib\*" ${package}.Start %*

exit /b %ERRORLEVEL%
