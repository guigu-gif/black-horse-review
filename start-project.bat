@echo off
chcp 65001 >nul
REM ========================================
REM 黑马点评项目一键启动脚本
REM 功能：检查环境并启动完整项目
REM ========================================

title 黑马点评项目启动
color 0A

echo ========================================
echo   黑马点评项目一键启动脚本
echo ========================================
echo.

REM 步骤1：检查MySQL
echo [1/4] 检查MySQL服务...
sc query MySQL80 2>nul | find "RUNNING" >nul
if %ERRORLEVEL% EQU 0 (
    echo [✓] MySQL服务正在运行
) else (
    echo [✗] MySQL服务未运行
    echo [提示] 请先启动MySQL服务: net start MySQL80
    echo.
    pause
    exit /b 1
)

REM 步骤2：检查Redis
echo [2/4] 检查Redis服务...
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul
if %ERRORLEVEL% EQU 0 (
    echo [✓] Redis服务正在运行
) else (
    echo [✗] Redis服务未运行
    echo [提示] 请先启动Redis服务
    echo.
    pause
    exit /b 1
)

REM 步骤3：启动Nginx
echo [3/4] 启动Nginx前端服务...
call "%~dp0start-nginx-safe.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [✗] Nginx启动失败
    pause
    exit /b 1
)

REM 步骤4：提示启动后端
echo [4/4] 启动Spring Boot后端...
echo.
echo ========================================
echo   环境检查完成！
echo ========================================
echo.
echo [✓] MySQL    - 运行中 (端口3306)
echo [✓] Redis    - 运行中 (端口6379)
echo [✓] Nginx    - 运行中 (端口8080)
echo.
echo [下一步] 请在IDEA中启动 HmDianPingApplication
echo.
echo [访问地址]
echo   - 前端: http://localhost:8080
echo   - 后端: http://localhost:8081
echo.
echo ========================================
echo.
pause
