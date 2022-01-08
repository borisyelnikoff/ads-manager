//@ECHO OFF
dir /B /S src\*.java > src.txt
javac -d out\adscommod -p lib\TcJavaToAds.jar @src.txt
pause
