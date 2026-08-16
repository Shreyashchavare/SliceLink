@ECHO OFF
SETLOCAL
SET "WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
SET "WRAPPER_HOME=%~dp0."
java "-Duser.home=%WRAPPER_HOME%" "-Dmaven.multiModuleProjectDirectory=%WRAPPER_HOME%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
ENDLOCAL
