package com.ucloudlink.refact.business.keepalive;

import java.io.Serializable;

/**
 * Created by jianguo.he on 2017/10/26.
 */

public class RemoteKeepLiveInfo implements Serializable {

    private static final long serialVersionUID = 4985114958747245597L;

    /** 需要保活的类所在App的包名 */
    public String packageName;
    /** 类的全路径名 */
    public String clsName;
    /** 延迟执行时间 */
    public long delayTimeMillis;
    /** 是否保活 */
    public boolean isKeepLive;

    public RemoteKeepLiveInfo(String packageName, String clsName, long delayTimeMillis, boolean isKeepLive){
        this.packageName = packageName;
        this.clsName = clsName;
        this.delayTimeMillis = delayTimeMillis;
        this.isKeepLive = isKeepLive;
    }

}
