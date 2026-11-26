@echo off
setlocal

if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe" (
    set "JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory=. -cp .mvn\wrapper\maven-wrapper.jar org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
