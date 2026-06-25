@echo off
where mvn >nul 2>&1
if %ERRORLEVEL%==0 (
    if "%~1"=="" (
        mvn clean test
    ) else (
        mvn %*
    )
    exit /b %ERRORLEVEL%
)

echo Maven not found. Install Maven 3.9+ and add it to PATH.
echo Then run: mvn clean test
exit /b 1
