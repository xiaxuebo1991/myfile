@echo off

gradle assembleRelease -p ../framework -c ../settingsf.gradle -PREINFORCE_ENABLE="true" -PRELEASE_VERSION_CODE=%1 -PRELEASE_VERSION_NAME=%2