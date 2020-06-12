@echo off
rem @echo off

if not exist "%JAVA_HOME%\bin\java.exe" echo Please set the JAVA_HOME variable in your environment, We need java(x64)! & EXIT /B 1
set "JAVA=%JAVA_HOME%\bin\java.exe"

setlocal
set basedir=%~dp0
set basedir=%BASE_DIR:~0,-1%
for %%d in (%basedir%) do set basedir=%%~dpd

set CLASSPATH=".;%JAVA_HOME%\libs;%JAVA_HOME%\libs\tools.jar;%basedir%cmd-1.0-SNAPSHOT.jar;%basedir%modules\core-1.0-SNAPSHOT.jar;%basedir%modules\filters\filter-plugins-grok-1.0-SNAPSHOT.jar;%basedir%modules\filters\filter-plugins-javascript-1.0-SNAPSHOT.jar;%basedir%modules\filters\filter-plugins-remove-1.0-SNAPSHOT.jar;%basedir%modules\inputs\input-plugins-redis-1.0-SNAPSHOT.jar;%basedir%modules\inputs\input-plugins-stdin-1.0-SNAPSHOT.jar;%basedir%modules\outputs\output-plugins-elasticsearch-1.0-SNAPSHOT.jar;%basedir%modules\outputs\output-plugins-stdout-1.0-SNAPSHOT.jar;%basedir%libs\args4j-2.33.jar;%basedir%libs\joni-2.1.8.jar;%basedir%libs\jcodings-1.0.13.jar;%basedir%libs\jedis-2.10.2.jar;%basedir%libs\commons-pool2-2.4.3.jar;%basedir%libs\logback-classic-1.2.3.jar;%basedir%libs\logback-core-1.2.3.jar;%basedir%libs\slf4j-api-1.7.25.jar;%basedir%libs\metrics-core-3.1.2.jar;%basedir%libs\jackson-databind-2.9.10.4.jar;%basedir%libs\jackson-annotations-2.9.10.jar;%basedir%libs\jackson-core-2.9.10.jar;%basedir%libs\httpclient-4.5.12.jar;%basedir%libs\httpcore-4.4.13.jar;%basedir%libs\commons-logging-1.2.jar;%basedir%libs\commons-codec-1.11.jar;%basedir%libs\commons-compress-1.20.jar"

rem ===========================================================================================
rem  JVM Configuration
rem ===========================================================================================
set "JAVA_OPTS=%JAVA_OPTS% -server -Xms2g -Xmx2g -Xmn1g -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=320m"
set "JAVA_OPTS=%JAVA_OPTS% -XX:-OmitStackTraceInFastThrow"
set "JAVA_OPTS=%JAVA_OPTS% -XX:-UseLargePages"
set "JAVA_OPTS=%JAVA_OPTS% -cp %CLASSPATH%"
"%JAVA%" %JAVA_OPTS% com.serene.tube.Main -f %basedir%config\simple.json %*
