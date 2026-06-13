@echo off
chcp 65001 >nul
echo ============================================
echo   AivisSpeech Engine + TTS Bridge Launcher
echo ============================================
echo.

cd /d "%~dp0"

REM 启动 AivisSpeech Engine (port 10101)
echo [1/2] Starting AivisSpeech Engine on port 10101...
start "AivisSpeech-Engine" cmd /c "cd /d AivisSpeech-Engine-master && python run.py --host 127.0.0.1 --port 10101"

REM 等待 Engine 启动
echo Waiting for AivisSpeech Engine to start...
timeout /t 5 /nobreak >nul

REM 启动 TTS Bridge (port 5000)
echo [2/2] Starting TTS Bridge on port 5000...
start "TTS-Bridge" cmd /c "cd /d %~dp0 && pip install -r tts_bridge_requirements.txt -q && python tts_bridge.py"

echo.
echo ============================================
echo   All services started!
echo   - AivisSpeech Engine: http://127.0.0.1:10101
echo   - TTS Bridge:         http://127.0.0.1:5000
echo ============================================
echo.
echo Press any key to exit this window (services will keep running)...
pause >nul