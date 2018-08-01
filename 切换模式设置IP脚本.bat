@Rem Here is the description.
@echo off  
echo . 
echo     1-关闭运卡，
echo . 
echo     2-切换模式(100 - BUSINESS ，101 - SAAS2 ，102 -SAAS3 ，103 - FACTORY)
echo . 
echo     3-设置工厂模式路由ip
echo . 
echo     4-启动云卡(只用于U3C)
echo . 
echo 注意:切换模式和设置工厂模式路由ip会先关闭云卡
echo .
:start
echo .
echo .

echo ================1-关闭运卡，2-切换模式 3-设置工厂模式路由ip 4-启动云卡(只用于U3C) =================
echo .
set /p type=输入数字(1234)：

if %type% == 1 (
	goto :logout
)  
if %type% == 2 (
	goto :changmode 
)
if %type% == 3 (
	goto :setip 
)
if %type% == 4 (
	goto :lougin 
)
:logout
	@echo off
	echo 关闭云卡
	adb shell am broadcast -a com.ucloudlink.cmd.logout
	goto :start 
:changmode
	@echo off
	adb shell am broadcast -a com.ucloudlink.cmd.logout
	echo .
	echo 切换模式(100 - BUSINESS ，101 - SAAS2 ，102 -SAAS3 ，103 - FACTORY)
	set /p modes=请输入模式:
	adb shell am broadcast -a com.ucloudlink.cmd.change.mode --es mode \"%modes%\"
	goto :start 
:setip
	@echo off
	adb shell am broadcast -a com.ucloudlink.cmd.logout
	echo .
	echo 设置工厂模式路由ip：ip:端口号，例如：10.1.1.1：8080
	set /p ip=输入IPPort：
	adb shell am broadcast -a com.ucloudlink.cmd.set.factory.ip --es factory_ip \"%ip%\"
	goto :start 
:lougin
	@echo off
	echo 启动云卡（只用于U3C）
	adb shell am broadcast -a com.ucloudlink.cmd.login
	goto :start 



  