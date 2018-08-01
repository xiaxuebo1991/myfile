package com.ucloudlink.refact.product.mifi.flow.protection.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jianguo.he on 2018/2/5.
 */

public class MifiCloudFlowProtectionXML implements Serializable {

    private static final long serialVersionUID = -7661846221525907403L;


    public String version;

    public List<String> listFlowname;

    public List<MifiCloudFlowProtectionItem> listItem;

    public MifiCloudFlowProtectionXML copyOf(){
        MifiCloudFlowProtectionXML mU3CFlowProtectionXML = new MifiCloudFlowProtectionXML();
        mU3CFlowProtectionXML.version = version;
        mU3CFlowProtectionXML.listFlowname = new ArrayList<String>();
        if(listFlowname!=null && listFlowname.size() > 0){
            mU3CFlowProtectionXML.listFlowname.addAll(listFlowname);
        }
        mU3CFlowProtectionXML.listItem = new ArrayList<>();
        if(listItem!=null && listItem.size() > 0){
            mU3CFlowProtectionXML.listItem.addAll(listItem);
        }
        return mU3CFlowProtectionXML;
    }

    @Override
    public String toString() {
        return "MifiCloudFlowProtectionXML{" +
                "version='" + version + '\'' +
                ", listFlowname=" + listFlowname +
                ", listItem=" + listItem +
                '}';
    }
}
