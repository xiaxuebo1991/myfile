package com.ucloudlink.refact.business.flow.protection;

import com.ucloudlink.refact.channel.enabler.IDataEnabler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by jianguo.he on 2018/2/5.
 */

public class CloudFlowProtectionCtrlImpl implements ICloudFlowProtectionCtrl {

    @Override
    public void init(@NotNull IDataEnabler cloudSimEnabler) {

    }

    @Override
    public void setRetrict(@Nullable String tag) {

    }

    @Override
    public void clearRetrict(@Nullable String tag) {

    }

    @Override
    public void updateXML(@NotNull String xml) {

    }

    @NotNull
    @Override
    public ICloudFlowProtection getICloudFlowProtection() {
        return null;
    }
}
