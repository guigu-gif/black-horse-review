@echo off
chcp 65001 >nul
REM ========================================
REM Nginx + Redis 停止脚本
REM ========================================

REM --- 停止 Nginx ---
echo [停止脚本] 正在停止nginx...

REM 检查nginx是否在运行
tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL

if %ERRORLEVEL% EQU 0 (
    echo [停止] 尝试优雅停止nginx...
    cd /d "%~dp0dianping-applet"
    nginx.exe -s quit
    timeout /t 2 /nobreak >nul

    REM 检查是否还有残留进程
    tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL
    if %ERRORLEVEL% EQU 0 (
        echo [警告] 优雅停止失败，正在强制结束...
        taskkill /F /IM nginx.exe >nul 2>&1
        timeout /t 1 /nobreak >nul
    )

    REM 最终验证
    tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL
    if %ERRORLEVEL% EQU 0 (
        echo [错误] nginx停止失败，请手动检查
        exit /b 1
    ) else (
        echo [成功] nginx已停止
    )
) else (
    echo [提示] nginx未在运行
)

REM --- 停止 Qdrant ---
echo [停止脚本] 正在停止Qdrant...
tasklist /FI "IMAGENAME eq qdrant.exe" 2>NUL | find /I "qdrant.exe" >NUL
if %ERRORLEVEL% EQU 0 (
    taskkill /F /IM qdrant.exe >nul 2>&1
    echo [成功] Qdrant已停止
) else (
    echo [提示] Qdrant未在运行
)

REM --- 停止 Redis ---
echo [停止脚本] 正在停止Redis...
tasklist /FI "IMAGENAME eq redis-server.exe" 2>NUL | find /I "redis-server.exe" >NUL
if %ERRORLEVEL% EQU 0 (
    D:\redis\redis-cli.exe shutdown nosave >nul 2>&1
    timeout /t 2 /nobreak >nul
    tasklist /FI "IMAGENAME eq redis-server.exe" 2>NUL | find /I "redis-server.exe" >NUL
    if %ERRORLEVEL% EQU 0 (
        taskkill /F /IM redis-server.exe >nul 2>&1
    )
    echo [成功] Redis已停止
) else (
    echo [提示] Redis未在运行
)

exit /b 0
