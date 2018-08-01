package com.ucloudlink.ucapp;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by jiaming.liang on 2016/11/2.
 */
public class mOBserver extends ContentObserver {
    /**
     * Creates a content observer.
     *
     * @param handler The handler to run {@link #onChange} on, or null if none.
     */
    public mOBserver(Handler handler) {
        super(handler);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
       logd("onChange: "+selfChange+"uri:"+uri);
    }
}
