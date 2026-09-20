@echo off
setlocal

pushd "%~dp0"
call gradlew.bat :underilla-core:inspect --args="%*"
set "inspect_exit_code=%ERRORLEVEL%"
popd

exit /b %inspect_exit_code%
