//@ECHO OFF
dir /B /S src\*.java > src.txt
javac -d out -p lib\TcJavaToAds.jar --module-source-path src @src.txt
pause
