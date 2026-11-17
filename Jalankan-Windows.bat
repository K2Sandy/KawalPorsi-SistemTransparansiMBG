@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
  echo Pasang JDK 25 atau Java 21 ke atas, lalu buka kembali file ini.
  pause
  exit /b 1
)
echo KawalPorsi - buka http://localhost:8080 setelah muncul "Started FlowApplication".
echo Biarkan jendela ini terbuka selama menggunakan aplikasi.
java -jar KawalPorsi.jar
pause
