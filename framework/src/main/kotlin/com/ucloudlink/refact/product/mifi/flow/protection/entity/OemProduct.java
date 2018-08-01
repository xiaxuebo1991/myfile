package com.ucloudlink.refact.product.mifi.flow.protection.entity;

import com.google.common.base.Strings;
import com.ucloudlink.refact.product.mifi.flow.protection.MifiXMLUtils;
import com.ucloudlink.refact.utils.JLog;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 作者： 陈涛.
 * 创建日期: 2015/11/16
 */

public class OemProduct {

    public static final String DEFAULT = "0";
    public static final String GCBU = "1";
    public static final String TELECOM = "2";
    public static final String VISONDATA = "3";
    public static final String GTBU = "4";
    public static final String DAJIANGJUN = "5";
    public static final String HUANQIUMANYOU = "6";
    public static final String WTT = "7";
    public static final String GWIFI = "8";
    public static final String YROAM = "9";
    public static final String ING = "10";//预留
    public static final String JIEFENG = "12";//预留

    String id;
    String vendor;
    String devName;
    String devType;
    String url;
    String defaultIp;

    public String getAutoLogin() {
        return autoLogin;
    }

    public void setAutoLogin(String autoLogin) {
        this.autoLogin = autoLogin;
    }

    String autoLogin;
    String enableLocalSim;
    String econDataMode;
    List<Contact> contacts = new ArrayList<>();


    public class ContactEntry{
        @Override
        public String toString() {
            return "ContactEntry{" +
                    "type='" + type + '\'' +
                    ", name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }

        String type;
        String name;
        String value;

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public ContactEntry(String type, String name, String value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }
    }

    public class Contact{
        String id;

        @Override
        public String toString() {
            return "Contact{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", entries=" + entries +
                    '}';
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public List<ContactEntry> getEntries() {
            return entries;
        }

        public Contact(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        String name;
        List<ContactEntry> entries = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getDevName() {
        return devName;
    }

    public void setDevName(String devName) {
        this.devName = devName;
    }
    public void setDefaultIp(String defaultIp) {
        this.defaultIp = defaultIp;
    }
    public String getDefaultIp() {
        return defaultIp;
    }

    public String getDevType() {
        return devType;
    }

    public void setDevType(String devType) {
        this.devType = devType;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }

    //public static final String OEM_PRODUCT_SETTINGS_FILE = "/persist/ucloud/oem_config.cfg";
    public static final String OEM_PRODUCT_SETTINGS_FILE = "/productinfo/ucloud/oem_config.cfg";


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEnableLocalSim() {
        return enableLocalSim;
    }

    public void setEnableLocalSim(String enableLocalSim) {
        this.enableLocalSim = enableLocalSim;
    }

    public String getEconDataMode() {
        return econDataMode;
    }

    public void setEconDataMode(String econDataMode) {
        this.econDataMode = econDataMode;
    }

    @Override
    public String toString() {
        return "OemProduct{" +
                "id='" + id + '\'' +
                ", vendor='" + vendor + '\'' +
                ", devName='" + devName + '\'' +
                ", devType='" + devType + '\'' +
                ", url='" + url + '\'' +
                ", autoLogin='" + autoLogin + '\'' +
                ", enableLocalSim='" + enableLocalSim + '\'' +
                ", econDataMode=" + econDataMode +
                ", contacts=" + contacts + '\'' +
                ", defaultIp=" + defaultIp +
                '}';
    }


    public void initFromXml() {
        File file = new File(OEM_PRODUCT_SETTINGS_FILE);
        FileReader reader = null;
        String version;
        Contact contact = null;
        ContactEntry entry = null;
        if (file.exists()) {
            JLog.logi(MifiXMLUtils.TAG + ", initFromXml() filePath = " + OEM_PRODUCT_SETTINGS_FILE);
            try {
                reader = new FileReader(file);
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                parser.setInput(reader);
                int eventCode = parser.getEventType();
                while (eventCode != XmlPullParser.END_DOCUMENT)  {
                    switch (eventCode){
                        case XmlPullParser.START_DOCUMENT:
                            break;
                        case XmlPullParser.START_TAG: {
                            if ("products".equals(parser.getName())) {
                                version = parser.getAttributeValue(null, "version");
                            } else if ("product".equals(parser.getName())) {
                                String id = parser.getAttributeValue(null, "id");
                                String url = parser.getAttributeValue(null, "url");
                                String vendor = parser.getAttributeValue(null, "vendor");
                                String deviceType = parser.getAttributeValue(null, "device_type");
                                String deviceName = parser.getAttributeValue(null, "device_name");
                                String autoLogin = parser.getAttributeValue(null, "auto_login");
                                String enableLocalSim = parser.getAttributeValue(null, "localsim_enable");
                                String defaultIp = parser.getAttributeValue(null, "default_ip");
                                String econdatamode = parser.getAttributeValue(null, "econ_data_mode");
                                setId(id);
                                setUrl(url);
                                setVendor(vendor);
                                setDevName(deviceName);

                                if (Strings.isNullOrEmpty(defaultIp)) {
                                    setDefaultIp("https://s.glocalme.com");
                                } else {
                                    setDefaultIp(defaultIp);
                                }

                                if (Strings.isNullOrEmpty(deviceType)) {
                                    setDevType("personal");
                                } else {
                                    setDevType(deviceType);
                                }

                                if (Strings.isNullOrEmpty(autoLogin)) {
                                    setAutoLogin("true");
                                } else {
                                    setAutoLogin(autoLogin);
                                }

                                if (Strings.isNullOrEmpty(enableLocalSim)){
                                    setEnableLocalSim("true");
                                } else {
                                    setEnableLocalSim(enableLocalSim);
                                }
                                setEconDataMode(econdatamode);

                            } else if ("contact".equals(parser.getName())) {
                                String id = parser.getAttributeValue(null, "id");
                                String name = parser.getAttributeValue(null, "name");
                                contact = new Contact(id, name);
                            } else if ("entry".equals(parser.getName())) {
                                String type = parser.getAttributeValue(null, "type");
                                String name = parser.getAttributeValue(null, "name");
                                String value = parser.getAttributeValue(null, "value");
                                entry = new ContactEntry(type, name, value);
                            }
                            break;
                        }
                        case XmlPullParser.END_TAG: {
                            if ("contact".equals(parser.getName())) {
                                if (contact != null){
                                    contacts.add(contact);
                                    contact = null;
                                }
                            } else if ("entry".equals(parser.getName())) {
                                if (contact != null && entry != null) {
                                    contact.entries.add(entry);
                                    entry = null;
                                }
                            }
                            break;
                        }
                        default:
                            break;
                    }
                    eventCode = parser.next();
                }
            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
                JLog.logi(MifiXMLUtils.TAG + ", initFromXml parse Exception: " + e.toString());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            JLog.logi(MifiXMLUtils.TAG + ", initFromXml file not exist, user default config. filePath = "+OEM_PRODUCT_SETTINGS_FILE);
            useDefaultConfig();
        }
        JLog.logi(MifiXMLUtils.TAG + ", initFromXml result:  " + OemProduct.this.toString());
    }

    private void useDefaultConfig() {
        setId("4"); // GTBU
        setDevName("E1");
        setDevType("lease");
        setAutoLogin("false");
        setEnableLocalSim("true");
        setVendor("E-connections");
        setUrl("www.default.com");
        setDefaultIp("https://s.glocalme.com");
        setEconDataMode("0");
    }

}
