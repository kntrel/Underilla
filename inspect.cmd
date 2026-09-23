@echo off
setlocal

pushd "%~dp0"
if "%~1"=="" (
    call gradlew.bat :underilla-core:inspect --stacktrace
) else (
    call gradlew.bat :underilla-core:inspect --args="%*" --stacktrace
)
set "inspect_exit_code=%ERRORLEVEL%"
popd

exit /b %inspect_exit_code%
