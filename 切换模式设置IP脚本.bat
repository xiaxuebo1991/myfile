@Rem Here is the description.
@echo off  
echo . 
echo     1-�ر��˿���
echo . 
echo     2-�л�ģʽ(100 - BUSINESS ��101 - SAAS2 ��102 -SAAS3 ��103 - FACTORY)
echo . 
echo     3-���ù���ģʽ·��ip
echo . 
echo     4-�����ƿ�(ֻ����U3C)
echo . 
echo ע��:�л�ģʽ�����ù���ģʽ·��ip���ȹر��ƿ�
echo .
:start
echo .
echo .

echo ================1-�ر��˿���2-�л�ģʽ 3-���ù���ģʽ·��ip 4-�����ƿ�(ֻ����U3C) =================
echo .
set /p type=��������(1234)��

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
	echo �ر��ƿ�
	adb shell am broadcast -a com.ucloudlink.cmd.logout
	goto :start 
:changmode
	@echo off
	adb shell am broadcast -a com.ucloudlink.cmd.logout
	echo .
	echo �л�ģʽ(100 - BUSINESS ��101 - SAAS2 ��102 -SAAS3 ��103 - FACTORY)
	set /p modes=������ģʽ:
	adb shell am broadcast -a com.ucloudlink.cmd.change.mode --es mode \"%modes%\"
	goto :start 
:setip
	@echo off
	adb shell am broadcast -a com.ucloudlink.cmd.logout
	echo .
	echo ���ù���ģʽ·��ip��ip:�˿ںţ����磺10.1.1.1��8080
	set /p ip=����IPPort��
	adb shell am broadcast -a com.ucloudlink.cmd.set.factory.ip --es factory_ip \"%ip%\"
	goto :start 
:lougin
	@echo off
	echo �����ƿ���ֻ����U3C��
	adb shell am broadcast -a com.ucloudlink.cmd.login
	goto :start 



  