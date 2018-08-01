// IUcloudDownloadCloudSimCallback.aidl
package com.ucloudlink.framework.ui;

// Declare any non-default types here with import statements

interface IUcloudDownloadSoftsimCallback {
    /**上报 软卡下载结果*/
       void eventSoftsimDownloadResult(String order,int errorCode);

    /**启动种子卡结果*/
       void eventStartSeedSimResult(String packageName, int errcode);
}
