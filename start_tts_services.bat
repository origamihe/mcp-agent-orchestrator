@echo off
chcp 65001 >nul
echo ============================================
echo   CosyVoice TTS Server Launcher
echo ============================================
echo.

cd /d "%~dp0"

REM 启动 CosyVoice TTS Server (port 5001)
echo [1/1] Starting CosyVoice TTS Server on port 5001...
cd cosyvoice-server
if exist ".venv\Scripts\activate.bat" (
    echo Activating virtual environment...
    call .venv\Scripts\activate.bat
) else (
    echo Creating virtual environment...
    python -m venv .venv
    call .venv\Scripts\activate.bat
    echo Installing dependencies...
    pip install fastapi uvicorn soundfile numpy torch librosa -q
)
start "CosyVoice-TTS" cmd /c "cd /d %~dp0cosyvoice-server && .venv\Scripts\python.exe main.py"

echo.
echo ============================================
echo   CosyVoice TTS Server started!
echo   - Health Check: http://127.0.0.1:5001/health
echo ============================================
echo.
echo Press any key to exit this window (service will keep running)...
pause >nul