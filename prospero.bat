@echo off

if "x%JAVA_HOME%" == "x" (
  set  JAVA=java
  echo JAVA_HOME is not set. Unexpected results may occur.
  echo Set JAVA_HOME to the directory of your local JDK to avoid this message.
) else (
  if not exist "%JAVA_HOME%" (
    echo JAVA_HOME "%JAVA_HOME%" path doesn't exist
    goto END
   ) else (
     if not exist "%JAVA_HOME%\bin\java.exe" (
       echo "%JAVA_HOME%\bin\java.exe" does not exist
       goto END_NO_PAUSE
     )
      echo Setting JAVA property to "%JAVA_HOME%\bin\java"
    set "JAVA=%JAVA_HOME%\bin\java"
  )
)

"%JAVA%" --add-modules=java.se -version >nul 2>&1 && (set MODULAR_JDK=true) || (set MODULAR_JDK=false)

set SCRIPT_HOME=%~dp0

if  "x%PROSPERO_HOME%" == "x" (
  set PROSPERO_HOME=%SCRIPT_HOME%
)

FOR /F "tokens=*" %%g IN ('dir %PROSPERO_HOME%\prospero-cli\target /b ^| findstr \.*-shaded.jar$') do (SET PROSPERO_JAR=%%g)

set "CLASSPATH=%PROSPERO_HOME%\prospero-cli\target\%PROSPERO_JAR%"

"%JAVA%" -jar "%CLASSPATH%" %*
