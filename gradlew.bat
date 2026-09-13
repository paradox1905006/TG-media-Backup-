@echo off
:: Gradle wrapper for Windows
:: Usage: gradlew.bat assembleDebug
setlocal
set DIRNAME=%~dp0
if "%JAVA_HOME%"=="" (
  set JAVA_CMD=java
) else (
  set JAVA_CMD=%JAVA_HOME%\bin\java.exe
)
%JAVA_CMD% -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
