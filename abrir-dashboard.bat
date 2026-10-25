@echo off
rem Sincroniza com o Hevy e abre o dashboard com os SEUS dados. Basta dar dois cliques neste arquivo.
rem Precisa do Java 21 e das variaveis de ambiente HEVY_API_KEY, GOOGLE_APPLICATION_CREDENTIALS e SPREADSHEET_ID
rem (veja o README). Para parar o dashboard, feche esta janela ou aperte Ctrl+C.
chcp 65001 >nul
cd /d "%~dp0"

set JAR=
for %%f in (target\hevy-dashboard-*.jar) do set JAR=%%f

if "%JAR%"=="" (
    echo Compilando o projeto pela primeira vez, pode levar um minuto...
    call mvn -q -B package -DskipTests
    for %%f in (target\hevy-dashboard-*.jar) do set JAR=%%f
)

if "%JAR%"=="" (
    echo.
    echo Nao consegui compilar o projeto. Confira se o Java 21 e o Maven estao instalados.
    pause
    exit /b 1
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%JAR%" dashboard
echo.
pause
