@echo off

REM
REM dex2jar - Tools to work with android .dex and java .class files
REM Copyright (c) 2009-2012 Panxiaobo
REM 
REM Licensed under the Apache License, Version 2.0 (the "License");
REM you may not use this file except in compliance with the License.
REM You may obtain a copy of the License at
REM 
REM      http://www.apache.org/licenses/LICENSE-2.0
REM 
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.
REM
set subdix=-jar2dex.dex
set inputfile=%1
set jarname=%~n1
set dexname=%jarname%%subdix%
set outname=uc-asset0.bin

echo "input file:" %inputfile%
echo "dexname file:" %dexname%
echo "output file:" %outname%




set CLASSPATH=
FOR %%i IN ("%~dp0lib\*.jar") DO CALL "%~dp0setclasspath.bat" "%%i"
echo %CLASSPATH% 
java -Xms512m -Xmx1024m -cp %CLASSPATH% "com.googlecode.dex2jar.tools.Jar2Dex" %*

echo "Start Jiagu..."
java -jar %~dp0Jiagu.jar -t dex -i %dexname% -o %outname%
del %dexname%
echo Done