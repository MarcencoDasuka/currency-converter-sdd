@echo off
setlocal
echo ========================================================
echo Running Currency Converter Test Suite (Backend + Frontend)
echo ========================================================

echo.
echo [1/2] Executing Spring Boot 3 / Java 21 Tests (JUnit 5 + Mockito)...
cd /d "%~dp0backend"
call mvnw.cmd test
if errorlevel 1 (
    echo [ERROR] Backend tests failed!
    exit /b 1
)

echo.
echo [2/2] Executing Vue 3 Frontend Tests (Vitest)...
cd /d "%~dp0frontend"
call npm.cmd test
if errorlevel 1 (
    echo [ERROR] Frontend tests failed!
    exit /b 1
)

echo.
echo ========================================================
echo ALL TESTS PASSED SUCCESSFULLY!
echo ========================================================
exit /b 0
