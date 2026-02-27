@echo off
chcp 65001 >nul
REM ========================================
REM Nginx + Redis + Qdrant 安全启动脚本
REM ========================================

REM --- 启动 Qdrant ---
echo [启动脚本] 检查Qdrant运行状态...
tasklist /FI "IMAGENAME eq qdrant.exe" 2>NUL | find /I "qdrant.exe" >NUL
if %ERRORLEVEL% EQU 0 (
    echo [提示] Qdrant已经在运行
) else (
    echo [启动] 正在启动Qdrant...
    start /min "Qdrant" D:\qdrant\qdrant.exe
    timeout /t 2 /nobreak >nul
    echo [成功] Qdrant已启动
)

REM --- 启动 Redis ---
echo [启动脚本] 检查Redis运行状态...
tasklist /FI "IMAGENAME eq redis-server.exe" 2>NUL | find /I "redis-server.exe" >NUL
if %ERRORLEVEL% EQU 0 (
    echo [提示] Redis已经在运行
) else (
    echo [启动] 正在启动Redis...
    start /min "Redis" D:\redis\redis-server.exe D:\redis\redis.conf
    timeout /t 2 /nobreak >nul
    echo [成功] Redis已启动
)

REM --- 启动 Nginx ---
echo [启动脚本] 检查nginx运行状态...

REM 检查nginx进程是否存在
tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL

if %ERRORLEVEL% EQU 0 (
    echo [提示] nginx已经在运行，无需重复启动
    exit /b 0
) else (
    echo [启动] nginx未运行，正在启动...
    cd /d "%~dp0dianping-applet"
    start nginx.exe
    timeout /t 2 /nobreak >nul

    REM 验证是否启动成功
    tasklist /FI "IMAGENAME eq nginx.exe" 2>NUL | find /I "nginx.exe" >NUL
    if %ERRORLEVEL% EQU 0 (
        echo [成功] nginx启动成功！
        echo [信息] 前端访问地址: http://localhost:8080
    ) else (
        echo [错误] nginx启动失败，请检查配置文件
        exit /b 1
    )
)

exit /b 0
