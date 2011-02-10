rem set classpath
SET CLASSPATH=%SEN_HOME%\lib\sen.jar
SET CLASSPATH=%CLASSPATH%;%SEN_HOME%\lib\commons-logging.jar

@%JAVA_HOME%\bin\java -Dsen.home=%SEN_HOME% -classpath %CLASSPATH% StringTaggerDemo ${1+"$@"}
