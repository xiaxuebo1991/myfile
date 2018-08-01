/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ucloudlink.refact.channel.monitors;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.ucloudlink.framework.protocol.protobuf.PlmnInfo;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessEventId;
import com.ucloudlink.refact.business.netcheck.NetworkDefineKt;
import com.ucloudlink.refact.business.netcheck.NetworkManager;
import com.ucloudlink.refact.business.netcheck.OperatorNetworkInfo;
import com.ucloudlink.refact.business.performancelog.logs.PerfLogTerAccess;
import com.ucloudlink.refact.business.performancelog.logs.TerAccessData;
import com.ucloudlink.refact.business.phonecall.SoftSimStateMark;
import com.ucloudlink.refact.business.realtime.RealTimeManager;
import com.ucloudlink.refact.business.uploadlac.UploadLacTask;
import com.ucloudlink.refact.channel.enabler.datas.Plmn;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.JLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import rx.subjects.BehaviorSubject;

import static com.ucloudlink.refact.utils.JLog.logd;
import static com.ucloudlink.refact.utils.JLog.logv;

/**
 * Created by Tim.wang on 2016/9/14.
 */
public class CardStateMonitor extends BroadcastReceiver {
    private static final String  TAG   = "UcCardStateMonitor";
    private static final boolean DEBUG = true;

    public static final int SIM_STATE_LOAD      = 0;
    public static final int SIM_STATE_READY     = 1;
    public static final int SIM_STATE_ABSENT    = 2;
    public static final int SIM_STATE_NOT_READY = 3;
    public static final int SIM_STATE_PIN_REQUIRED = 4;
    public static final int SIM_STATE_PUK_REQUIRED = 5;
    public static final int SIM_STATE_NETWORK_LOCKED = 6;


    //    public static final int RAT_REG_ON_2G = 0;
    //    public static final int RAT_REG_ON_3G = 1;
    //    public static final int RAT_REG_ON_4G = 2;

    private static final String ACTION_SCAN_NW               = "qualcomm.intent.action.ACTION_INCREMENTAL_NW_SCAN_IND";
    private static final String ACTION_NETWORK_SPECIFIER_SET = "org.codeaurora.intent.action.ACTION_NETWORK_SPECIFIER_SET";
    public static final  String EXTRA_CAPTIVE_PORTAL         = "android.net.extra.NET_INTERFACE";
    public static final  String EXTRA_CAPTIVE_SUBID          = "android.net.extra.NET_SUBID";
    public static final  String ACTION_SP_SCAN_NW            = "android.intent.action.INCREMENTAL_NW_SCAN_IND";

    public final BehaviorSubject<Boolean> planeCallObser                     = BehaviorSubject.create(false);
    public final BehaviorSubject<Boolean> cellLocationChangedBehaviorSubject = BehaviorSubject.create();
    //    public final BehaviorSubject<Boolean> dataSwitchObser = BehaviorSubject.create(true);

    public enum SimStatusEnum {
        SIM_STATUS_UNKNOWN, SIM_STATUS_READY, SIM_STATUS_ABSENT, SIM_STATUS_PIN_LOCK, SIM_STATUS_PUK_LOCK, SIM_STATUS_NETWORK_LOCK
    }

    private final Context mContext;

    private IccCardConstants.State mSimState = IccCardConstants.State.READY;
    private SignalStrength mSignalStrength;
    private ServiceState   mServiceState;
    private       int     mDataState = TelephonyManager.DATA_DISCONNECTED;
    public static boolean mCallState = false;
    TelephonyManager mPhone;
    //    UcNetworkState[] mNetworkState = {IDLE, IDLE};
    private HashMap<Integer, NetworkInfo> mapNetInfo = new HashMap<>();

    public CardStateMonitor(Context context) {
        mContext = context;
        //        dataSwitchObser.onNext(PhoneStateUtil.Companion.isMobileDataEnabled(mContext));
        startMonitoring();
    }

    public void startMonitoring() {
        mPhone = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPhone.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | PhoneStateListener.LISTEN_DATA_ACTIVITY | PhoneStateListener.LISTEN_CELL_LOCATION);
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        //        filter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(ACTION_SCAN_NW);
        filter.addAction(ACTION_SP_SCAN_NW);
        //        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        mContext.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        logd("onReceive action=" + intent.getAction());
        final String action = intent.getAction();
        switch (action) {
            case TelephonyIntents.ACTION_SIM_STATE_CHANGED:
                updateSimState(intent);
                break;
            case ConnectivityManager.CONNECTIVITY_ACTION:
                updateDataConnectionState(intent);
                break;
            case TelephonyManager.ACTION_PHONE_STATE_CHANGED:
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                String incomingNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                logd("CALL onReceive: state is " + state);
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    mCallState = true;
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_PHONECALL_START);
                    ServiceManager.accessMonitor.startPhoneCall();
                } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    mCallState = false;
                    ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_PHONECALL_STOP);
                    ServiceManager.accessMonitor.stopPhoneCall();
                }
                SoftSimStateMark.INSTANCE.handlePauseCloudSimAction(mCallState); //清除状态
                planeCallObser.onNext(mCallState);
                logd("CALL onReceive: incomingNum is " + incomingNum);
                break;
            case TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED:
                logv("ANY_DATA_CHANGED", PhoneConstants.STATE_KEY + ":" + intent.getStringExtra(PhoneConstants.STATE_KEY));
                //            logv("ANY_DATA_CHANGED",PhoneConstants.NETWORK_UNAVAILABLE_KEY+":"+intent.getBooleanExtra(PhoneConstants.NETWORK_UNAVAILABLE_KEY,false));
                String reseon = intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY);
                logv("ANY_DATA_CHANGED", PhoneConstants.STATE_CHANGE_REASON_KEY + ":" + reseon);
                //            logv("ANY_DATA_CHANGED",PhoneConstants.DATA_LINK_PROPERTIES_KEY+":"+intent.getExtra(PhoneConstants.DATA_LINK_PROPERTIES_KEY));
                //            logv("ANY_DATA_CHANGED",PhoneConstants.DATA_NETWORK_CAPABILITIES_KEY+":"+intent.getExtra(PhoneConstants.DATA_NETWORK_CAPABILITIES_KEY));
                logv("ANY_DATA_CHANGED", PhoneConstants.DATA_NETWORK_ROAMING_KEY + ":" + intent.getBooleanExtra(PhoneConstants.DATA_NETWORK_ROAMING_KEY, false));
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                logv("ANY_DATA_CHANGED", PhoneConstants.DATA_APN_TYPE_KEY + ":" + apnType);
                logv("ANY_DATA_CHANGED", PhoneConstants.SUBSCRIPTION_KEY + ":" + intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1));
                //表示只听这一种类型，一变化，所有类型都广播一次，所有随便选一种就行了
                if ("dun".equalsIgnoreCase(apnType) && ("specificDisabled".equalsIgnoreCase(reseon) || "dataEnabled".equalsIgnoreCase(reseon))) {
                    updateDataEnaber();
                }
                break;
            case ACTION_SCAN_NW:
                int result = intent.getIntExtra("scan_result", -1);
                int phoneId = intent.getIntExtra("sub_id", -1);
                String[] scanInfo = intent.getStringArrayExtra("incr_nw_scan_data");
                if (scanInfo.length >= 4) {
                    ArrayList<Plmn> plmns = new ArrayList<>();
                    int resultCount = scanInfo.length / 4;
                    for (int i = 0; i < resultCount; i++) {
                        String scanInfoString = scanInfo[i * 4 + 2];
                        if (!TextUtils.isEmpty(scanInfoString)) {
                            String[] results = scanInfoString.split("\\+");
                            if (results.length >= 4) {
                                String mncmcc = results[0];
                                int rat = Integer.parseInt(results[1]);
                                int signalQuality = Integer.parseInt(results[2]);
                                int signalStrength = Integer.parseInt(results[3]);
                                if (signalStrength > 0) {
                                    Plmn plmn = new Plmn(mncmcc, rat, signalQuality, signalStrength, SystemClock.elapsedRealtime());
                                    plmns.add(plmn);
                                    PlmnInfo plmnmb = new PlmnInfo(mncmcc, NetworkManager.INSTANCE.getRadioRatMap(rat), NetworkManager.INSTANCE.getSignalMap(3), 3);  //TODO 网络频段参数不正确,目前暂未有获取该参数的方法
                                    //Public OperatorNetworkInfo mOperatorNetworkInfo = new OperatorNetworkInfo();
                                    OperatorNetworkInfo.INSTANCE.addScanNetworkPlmnList(plmnmb);
                                    Log.i(TAG, "scan netwrok end,addplmn:" + plmn);
                                }

                            }
                        }
                    }

                    updateScanNw(phoneId, plmns);
                }
                Log.i(TAG, "scan netwrok end:result:" + result + " phoneId :" + phoneId);

                break;
            case ACTION_SP_SCAN_NW:
                handleSpScanNw(intent);
                break;
        }
    }

    // [45406-7-1-53, 45412-7-1-56, 45419-7-1-53, 45400-7-1-53]
    private void handleSpScanNw(Intent intent) {
        int phoneId = intent.getIntExtra("phoneId", -1);
        String[] nwScanData = intent.getStringArrayExtra("incr_nw_scan_data");
        if (nwScanData == null || nwScanData.length == 0) return;
        logv("handleSpScanNw phoneId:" + phoneId);
        logv("handleSpScanNw nwScanData size: " + nwScanData.length);
        logv("handleSpScanNw nwScanData: " + Arrays.toString(nwScanData));
        ArrayList<Plmn> plmns = new ArrayList<>();
        for (String nwScanDatum : nwScanData) {
            String[] data = nwScanDatum.split("-");
            if (data.length != 4) continue;
            String mncmcc = data[0];
            int rat = Integer.parseInt(data[1]);

            int signalQuality = Integer.parseInt(data[2]);
            int signalStrength = Integer.parseInt(data[3]);
            Plmn plmn = new Plmn(mncmcc, rat, signalQuality, signalStrength, SystemClock.elapsedRealtime());
            plmns.add(plmn);
        }
        if (plmns.size() > 0) {
            updateScanNw(phoneId, plmns);
            logv("handleSpScanNw nwScanData:" + Arrays.toString(nwScanData));
        }
    }

    private void updateDataConnectionState(Intent intent) {
        final NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        //        final int currDdsId = intent.getIntExtra(PhoneConstants.SLOT_KEY, SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultSubId()));
        final int currDdsId = SubscriptionManager.getPhoneId(ServiceManager.systemApi.getDefaultDataSubId());
        //        final int subId = SubscriptionManager.getSubId(slotId)[0];
        NetworkInfo.State state = ni.getState(); // isConnected();
        int type = ni.getType();
        //        UcNetworkState networkState = IDLE;
        logd("updateDataConnectionState slotId: " + currDdsId + ", state:" + state + ", type: " + type);
        //        publishDataConnectionState(slotId, isConnected, type);
        // 通过广播获取参数，如果获取不到参数的话，需要获取/设置接口名称,以兼容老版本
        String ifName = "";
        boolean isExistIfNameExtra = intent.hasExtra(EXTRA_CAPTIVE_PORTAL);
        boolean isExistSubIdExtra = intent.hasExtra(EXTRA_CAPTIVE_SUBID);
        int subId = -1;
        String strSubId = "";

        if (type == ConnectivityManager.TYPE_MOBILE || type == ConnectivityManager.TYPE_MOBILE_DUN) {
            if (isExistIfNameExtra) {
                ifName = intent.getStringExtra(EXTRA_CAPTIVE_PORTAL);
            }

            if (isExistSubIdExtra) {
                strSubId = isExistSubIdExtra ? intent.getStringExtra(EXTRA_CAPTIVE_SUBID) : "-1";
                if (!TextUtils.isEmpty(strSubId)) {
                    try {
                        subId = Integer.parseInt(strSubId);
                    } catch (Exception e) {
                        subId = -1;
                    }
                }
            }
        }

        JLog.logd("SeedCardNetLog updateDataConnectionState() -> currDdsId = " + currDdsId + ", networkState = " + state + ", type = " + type + ", ifName = " + ifName + ", isExistIfNameExtra = " + isExistIfNameExtra + ", subId = " + subId + ", isExistSubIdExtra = " + isExistSubIdExtra + ", strSubId = " + (strSubId == null ? "null" : strSubId));

        updateNetworkState(currDdsId, state, type, ifName, isExistIfNameExtra, subId);
//        boolean isNeedUpdateNetworkState = (type == ConnectivityManager.TYPE_MOBILE || type == ConnectivityManager.TYPE_MOBILE_DUN);
//        isNeedUpdateNetworkState = (isNeedUpdateNetworkState && (subId > -1)) || (type == ConnectivityManager.TYPE_WIFI);
//        if (isNeedUpdateNetworkState) {
//            final NetworkInfo info = mapNetInfo.get(subId);
//            mapNetInfo.put(subId, ni);// Note: get 后 再 put
//            JLog.logd("updateDataConnectionState NetworkInfo = " + (info==null?"null": info.toString()));
//            if(info == null){
//                updateNetworkState(currDdsId, state, type, ifName, isExistIfNameExtra, subId);
//            }else if(!(type == info.getType() && state == info.getState())){
//                updateNetworkState(currDdsId, state, type, ifName, isExistIfNameExtra, subId);
//            } else {
//                JLog.logd("updateDataConnectionState not need updateNetworkState!!");
//            }
//        }
        if (state == NetworkInfo.State.CONNECTED) {
            RealTimeManager.INSTANCE.setRealTimeCount();
        }
    }

    private void publishServiceState() {
        // 根据发布给Subscribe的对象
        logd("updateServiceState slotId: " + mServiceState);
        //        int subId = SubscriptionManager.getDefaultDataSubId();
        int subId = ServiceManager.systemApi.getDefaultDataSubId();
        int slotId = SubscriptionManager.getPhoneId(subId);
        //updateServiceState(slotId, subId, mServiceState.getState()); //错误
        updateServiceState(slotId, subId, mServiceState.getDataRegState());
    }

    private final void updateSimState(Intent intent) {
        int slotId = intent.getIntExtra(PhoneConstants.SLOT_KEY, SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        //        int slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY, SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            JLog.loge(TAG, "updateSimState: get invalid sim slot index");
            slotId = SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultSubId());
        }
        if (slotId == Configuration.INSTANCE.getSeedSimSlot()) {
            ServiceManager.productApi.pinVerify();
        }

        final int currSubId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        //        if (currSubId > 0x40000000) // 0x7FFFFFFB 2147483643
        //            return;
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        int simState = SIM_STATE_NOT_READY;
        if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            simState = SIM_STATE_READY;
            mSimState = IccCardConstants.State.READY;
        } else if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra) || IccCardConstants.INTENT_VALUE_ICC_UNKNOWN.equals(stateExtra) || IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)) {
            simState = SIM_STATE_ABSENT;
            mSimState = IccCardConstants.State.ABSENT;
        } else if (IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra) || IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
            simState = SIM_STATE_LOAD; //
        } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(stateExtra)) {
            simState = SIM_STATE_PIN_REQUIRED;
            mSimState = IccCardConstants.State.PIN_REQUIRED;
        } else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(stateExtra)) {
            simState = SIM_STATE_PUK_REQUIRED;
            mSimState = IccCardConstants.State.PUK_REQUIRED;
        } else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(stateExtra)) {
            simState = SIM_STATE_NETWORK_LOCKED;
            mSimState = IccCardConstants.State.NETWORK_LOCKED;
        } else {
            simState = SIM_STATE_NOT_READY;
            mSimState = IccCardConstants.State.NOT_READY;
        }
        // publish
        updateCardState(slotId, currSubId, simState);
        //        publishSimState(slotId, simState);
    }

    private boolean hasService() {
        return mServiceState != null && mServiceState.getState() != ServiceState.STATE_OUT_OF_SERVICE && mServiceState.getState() != ServiceState.STATE_POWER_OFF;
    }

    /*
     * 更新保存主动上报的CELLID LAC信息
     * */
    public void updateCellInfo() {
        //        int dds = SubscriptionManager.getDefaultDataSubId();
        int dds = ServiceManager.systemApi.getDefaultDataSubId();
        int slotId = SubscriptionManager.getPhoneId(dds);
        Configuration configuration = Configuration.INSTANCE;
        int seedSimSlot = configuration.getSeedSimSlot();
        int cloudSimSlot = configuration.getCloudSimSlot();
        String seedOperator = mPhone.getNetworkOperatorForPhone(seedSimSlot);
        String vsimOperator = mPhone.getNetworkOperatorForPhone(cloudSimSlot);
        if (!TextUtils.isEmpty(seedOperator) && !seedOperator.equals("00000") && !seedOperator.equals("000000")) {
            OperatorNetworkInfo.INSTANCE.setMccmnc(seedOperator);
        }
        if (!TextUtils.isEmpty(vsimOperator) && !seedOperator.equals("00000") && !seedOperator.equals("000000")) {
            OperatorNetworkInfo.INSTANCE.setMccmncCloudSim(vsimOperator);
        }
        @SuppressLint("MissingPermission") List<CellInfo> cellInfoValue = mPhone.getAllCellInfo();
        logd("updateCellInfo DDS is " + dds + " slotId: " + slotId + ", CellInfoValue: " + cellInfoValue);
        boolean netInfoChanged = false; // false：表示种子卡  true：表示云卡
        if (cellInfoValue != null) {
            for (CellInfo ci : cellInfoValue) {
                if (ci.isRegistered()) {
                    if (ci instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfo = (CellInfoWcdma) ci;
                        CellIdentityWcdma cellIdentity = cellInfo.getCellIdentity();
                        CellSignalStrengthWcdma cellSignalStrength = cellInfo.getCellSignalStrength();
                        int mcc = cellIdentity.getMcc();
                        int mnc = cellIdentity.getMnc();
                        String operator = getOperator(mcc, mnc);
                        if (operator != null) {
                            int cid = cellIdentity.getCid();
                            int lac = cellIdentity.getLac();
                            int level = cellSignalStrength.getLevel();
                            if (operator.equals(seedOperator)) {
                                PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_LAC_CELL_CHANGE(),0,new TerAccessData(lac,cid,0,0));
                                setSeedResult(cid, lac, level, NetworkDefineKt.RAT_TYPE_WCDMA);
                                netInfoChanged = false;
                            }
                            if (operator.equals(vsimOperator)) {
                                setVsimResult(cid, lac, level, NetworkDefineKt.RAT_TYPE_WCDMA);
                                netInfoChanged = true;
                            }
                        }
                    } else if (ci instanceof CellInfoGsm) {
                        CellInfoGsm cellInfo = (CellInfoGsm) ci;
                        CellIdentityGsm cellIdentity = cellInfo.getCellIdentity();
                        CellSignalStrengthGsm cellSignalStrength = cellInfo.getCellSignalStrength();
                        int mcc = cellIdentity.getMcc();
                        int mnc = cellIdentity.getMnc();
                        String operator = getOperator(mcc, mnc);
                        if (operator != null) {
                            int cid = cellIdentity.getCid();
                            int lac = cellIdentity.getLac();
                            int level = cellSignalStrength.getLevel();
                            if (operator.equals(seedOperator)) {
                                PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_LAC_CELL_CHANGE(),0,new TerAccessData(lac,cid,0,0));
                                setSeedResult(cid, lac, level, NetworkDefineKt.RAT_TYPE_GSM);
                                netInfoChanged = false;
                            }
                            if (operator.equals(vsimOperator)) {
                                setVsimResult(cid, lac, level, NetworkDefineKt.RAT_TYPE_GSM);
                                netInfoChanged = true;
                            }
                        }
                    } else if (ci instanceof CellInfoLte) {
                        CellInfoLte cellInfo = (CellInfoLte) ci;
                        CellIdentityLte cellIdentity = cellInfo.getCellIdentity();
                        CellSignalStrengthLte cellSignalStrength = cellInfo.getCellSignalStrength();
                        int mcc = cellIdentity.getMcc();
                        int mnc = cellIdentity.getMnc();
                        String operator = getOperator(mcc, mnc);
                        if (operator != null) {
                            int cid = cellIdentity.getCi();
                            int lac = cellIdentity.getTac();
                            int level = cellSignalStrength.getLevel();
                            if (operator.equals(seedOperator)) {
                                PerfLogTerAccess.INSTANCE.create(PerfLogTerAccess.INSTANCE.getID_LAC_CELL_CHANGE(),0,new TerAccessData(lac,cid,0,0));
                                setSeedResult(cid, lac, level, NetworkDefineKt.RAT_TYPE_LTE);
                                netInfoChanged = false;
                            }
                            if (operator.equals(vsimOperator)) {
                                setVsimResult(cid, lac, level, NetworkDefineKt.RAT_TYPE_LTE);
                                netInfoChanged = true;
                            }
                        }
                    } else if (ci instanceof CellInfoCdma) {
                        /*
                        fixme 由于目前不支持CDMA作为种子或者云卡，所以这里可能有问题，请注意！！！
                         */
                        CellInfoCdma cellInfoCdma = (CellInfoCdma) ci;
                        CellIdentityCdma cellIdentityCdma = cellInfoCdma.getCellIdentity();
                        CellSignalStrengthCdma cellSignalStrengthCdma = cellInfoCdma.getCellSignalStrength();
                        if (slotId == seedSimSlot) {
                            OperatorNetworkInfo.INSTANCE.setSeedSingalByLevel(cellSignalStrengthCdma.getLevel());
                            OperatorNetworkInfo.INSTANCE.setCellid(cellIdentityCdma.getBasestationId());
                            OperatorNetworkInfo.INSTANCE.setLac(cellIdentityCdma.getSystemId());
                            OperatorNetworkInfo.INSTANCE.setRat(NetworkDefineKt.RAT_TYPE_CDMA);
                            //cdma has no mcc mnc report
                        } else if (slotId == cloudSimSlot) {
                            OperatorNetworkInfo.INSTANCE.setCloudSignalByLevel(cellSignalStrengthCdma.getLevel());
                            OperatorNetworkInfo.INSTANCE.setCellidCloudSim(cellIdentityCdma.getBasestationId());
                            OperatorNetworkInfo.INSTANCE.setLacCloudSim(cellIdentityCdma.getSystemId());
                            OperatorNetworkInfo.INSTANCE.setRatCloudSim(NetworkDefineKt.RAT_TYPE_CDMA);
                        }
                    }
                }
            }
            String cardMode = (slotId == seedSimSlot) ? "seed card" : "cloud card";
            logd("OperatorNetworkInfo:" + "cardMode=" + cardMode + " mccmnc=" + OperatorNetworkInfo.INSTANCE.getMccmnc() + " seed_lac=" + OperatorNetworkInfo.INSTANCE.getLac() + " seed_cellid=" + OperatorNetworkInfo.INSTANCE.getCellid() + " seed_signal=" + OperatorNetworkInfo.INSTANCE.getSignalStrength() + " cloud_mcc=" + OperatorNetworkInfo.INSTANCE.getMccmncCloudSim() + " cloud_lac=" + OperatorNetworkInfo.INSTANCE.getLacCloudSim() + " cloud_cellid=" + OperatorNetworkInfo.INSTANCE.getCellidCloudSim() + " cloud_signal=" + OperatorNetworkInfo.INSTANCE.getSignalStrengthCloudSim());
        } else {
            logd("cellInfoValue == null");
        }

        UploadLacTask.INSTANCE.uploadLacChange();
        // 上报最新的location给UI
        logd("cellLocationChangedBehaviorSubject", "isCloudVsim:" + netInfoChanged);
        cellLocationChangedBehaviorSubject.onNext(netInfoChanged);
    }

    private void setVsimResult(int cid, int lac, int level, int ratGeneration) {
        OperatorNetworkInfo.INSTANCE.setCellidCloudSim(cid);
        OperatorNetworkInfo.INSTANCE.setLacCloudSim(lac);
        OperatorNetworkInfo.INSTANCE.setCloudSignalByLevel(level);
        OperatorNetworkInfo.INSTANCE.setRatCloudSim(ratGeneration);
        OperatorNetworkInfo.INSTANCE.reflashCloudPlmnList();
    }

    private void setSeedResult(int cid, int lac, int level, int ratGeneration) {
        OperatorNetworkInfo.INSTANCE.setCellid(cid);
        OperatorNetworkInfo.INSTANCE.setLac(lac);
        OperatorNetworkInfo.INSTANCE.setSeedSingalByLevel(level);
        OperatorNetworkInfo.INSTANCE.setRat(ratGeneration);
        OperatorNetworkInfo.INSTANCE.reflashSeedPlmnList();
    }

    private String getOperator(int mcc, int mnc) {
        if (mcc != Integer.MAX_VALUE && mnc != Integer.MAX_VALUE) {
            return String.format("%03d", mcc) + String.format("%02d", mnc);
        }
        return null;
    }

    private class CellInfoProxy {
        private CellInfo ci;

        public CellInfoProxy(CellInfo ci) {
            this.ci = ci;
        }
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (signalStrength.equals(mSignalStrength))
                return;
            mSignalStrength = signalStrength;
            Log.d(TAG, "onSignalStrengthsChanged: "+mSignalStrength);
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            mServiceState = state;
            if (hasService()) {
            }
            publishServiceState();
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            mDataState = state;
        }

        @Override
        public void onCellLocationChanged(CellLocation location) {
            logd("onCellLocationChanged () called");
            ExecutorService appThreadPool = ServiceManager.INSTANCE.getAppThreadPool();
            boolean shutdown = appThreadPool.isShutdown();
            if (shutdown) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        updateCellInfo();
                    }
                }).start();
            } else {
                appThreadPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        updateCellInfo();
                    }
                });
            }
        }
    };

    /***********
     * Network begin
     * network state:CONNECTING, CONNECTED, SUSPENDED, DISCONNECTING, DISCONNECTED, UNKNOWN
     **************/
    private final Object netlock = new Object();
    private Set<NetworkStateListen> networkStateListens;

    public interface NetworkStateListen {
        void NetworkStateChange(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId);
    }

    public void updateNetworkState(int ddsId, NetworkInfo.State state, int type, String ifName, boolean isExistIfNameExtra, int subId) {
        synchronized (netlock) {
            if (networkStateListens == null)
                return;
            for (NetworkStateListen stateListen : networkStateListens) {
                logd("updateNetworkState: ddsId: " + ddsId + ", state: " + state + ", type: " + type + ", ifName: " + ifName + ", isExistIfaceExtra: " + isExistIfNameExtra + ", subId = " + subId);
                stateListen.NetworkStateChange(ddsId, state, type, ifName, isExistIfNameExtra, subId);
            }
        }
    }

    public void addNetworkStateListen(NetworkStateListen l) {
        JLog.logd(TAG, "addNetworkStateListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (netlock) {
            if (networkStateListens == null) {
                networkStateListens = new HashSet<>();
            }
            if (l == null)
                return;
            networkStateListens.add(l);
        }
    }

    public void removeStatuListen(NetworkStateListen l) {
        JLog.logd(TAG, "removeStatuListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (netlock) {
            if (networkStateListens == null)
                return;
            if (l == null)
                return;
            networkStateListens.remove(l);
        }
    }
    /*********** Network end**************/

    /***********
     * Card begin
     *
     * @ state
     * SIM_STATE_LOAD
     * SIM_STATE_NOT_READY
     * SIM_STATE_READY
     * SIM_STATE_ABSENT
     **************/
    private final Object cardlock = new Object();
    private Set<CardStateListen> cardStateListens;

    public interface CardStateListen {
        void CardStateChange(int slotId, int subId, int state);
    }

    public void updateCardState(int slotId, int subId, int state) {
        synchronized (cardlock) {
            if (cardStateListens == null)
                return;
            logd("updateCardState: slotId:" + slotId + " subId:" + subId + " state:" + state + " cardStateListens size " + cardStateListens.size());
            try {
                for (CardStateListen listen : cardStateListens) {
                    listen.CardStateChange(slotId, subId, state);
                }
            }catch (Exception e){
                JLog.loge("updateCardState Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * 注意subId 有可能为无效值
     *
     * @param l
     */
    public void addCardStateListen(CardStateListen l) {
        JLog.logd(TAG, "addCardStateListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (cardlock) {
            if (cardStateListens == null) {
                cardStateListens = new HashSet<>();
            }
            cardStateListens.add(l);
        }
    }

    public void removeStatuListen(CardStateListen l) {
        JLog.logd(TAG, "removeStatuListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (cardlock) {
            if (cardStateListens == null)
                return;
            cardStateListens.remove(l);
            JLog.logd(TAG, "removeStatuListen: cardStateListens.size " + cardStateListens.size());
        }
    }
    /***********
     * Card end
     **************/

    /***********
     * 监听数据开关发生变化
     * DataEnaber listen start
     **************/
    private final Object dataEnblerlock = new Object();
    private Set<DataEnaberListen> dataEnaberListens;

    public interface DataEnaberListen {
        void onDataEnablerChanged();
    }

    public void updateDataEnaber() {
        synchronized (dataEnblerlock) {
            if (dataEnaberListens == null)
                return;
            logd("updateDataEnaber");
            for (DataEnaberListen stateListen : dataEnaberListens) {
                stateListen.onDataEnablerChanged();
            }
        }
    }

    public void addDataEnaberListen(DataEnaberListen l) {
        JLog.logd(TAG, "addDataEnaberListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (dataEnblerlock) {
            if (dataEnaberListens == null) {
                dataEnaberListens = new HashSet<>();
            }
            dataEnaberListens.add(l);
        }
    }

    public void removeDataEnaberListen(DataEnaberListen l) {
        JLog.logd(TAG, "removeDataEnaberListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (dataEnblerlock) {
            if (dataEnaberListens == null)
                return;
            dataEnaberListens.remove(l);
        }
    }

    /*********** DataEnaber listen end**************/

    /***********
     * 监听搜网记录变化
     * DataEnaber listen start
     **************/
    private final Object scanNwlock = new Object();
    private Set<ScanNwlockListen> scanNwListens;

    public interface ScanNwlockListen {
        void onScanNwChanged(int phoneId, ArrayList<Plmn> plmns);
    }

    public void updateScanNw(int phoneId, ArrayList<Plmn> plmns) {
        synchronized (scanNwlock) {
            if (scanNwListens == null)
                return;
            for (ScanNwlockListen listen : scanNwListens) {
                logd("updateDataEnaber");
                listen.onScanNwChanged(phoneId, plmns);
            }
        }
    }

    public void addScanNwlockListen(ScanNwlockListen l) {
        JLog.logd(TAG, "addScanNwlockListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (scanNwlock) {
            if (scanNwListens == null) {
                scanNwListens = new HashSet<>();
            }
            scanNwListens.add(l);
        }
    }

    public void removeScanNwlockListen(ScanNwlockListen l) {
        JLog.logd(TAG, "removeScanNwlockListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        synchronized (scanNwlock) {
            if (scanNwListens == null)
                return;
            scanNwListens.remove(l);
        }
    }

    /*********** DataEnaber listen end**************/

    /*
     ** service
     *  @ state
     *  ServiceState.STATE_IN_SERVICE
     *  ServiceState.STATE_OUT_OF_SERVICE
     *  ServiceState.STATE_EMERGENCY_ONLY
     *  ServiceState.STATE_POWER_OFF
     */
    private Set<ServiceStateListen> serviceStateListens;

    public interface ServiceStateListen {
        void serviceStateChange(int slotId, int subId, int state);
    }

    public void updateServiceState(int slotId, int subId, int state) {
        if (serviceStateListens == null) {
            return;
        }
        for (ServiceStateListen stateListen : serviceStateListens) {
            stateListen.serviceStateChange(slotId, subId, state);
        }
    }

    public void addServiceStateListen(ServiceStateListen l) {
        JLog.logd(TAG, "addServiceStateListen: " + l.hashCode() + ", " + l.getClass().getSimpleName());
        if (serviceStateListens == null) {
            serviceStateListens = new HashSet<>();
        }
        serviceStateListens.add(l);
    }

    /*
     * 主动获取对饮卡槽的SIM卡状态 ，如卡1卡2状态 READY，ABSENT
     * */
    public static SimStatusEnum getSimStatusOnSlot(int slot) {
        String prop = SystemProperties.get(TelephonyProperties.PROPERTY_SIM_STATE);
        JLog.logd(TAG, "ucGetSimStatusOnSlot:" + slot + "=" + prop);
        String[] simSlotStateList = prop.split(",");
        JLog.logd(TAG, "ucGetSimStatusOnSlot ARRAY = " + Arrays.toString(simSlotStateList));
        if (slot > simSlotStateList.length - 1)
            return SimStatusEnum.SIM_STATUS_UNKNOWN;
        String state = simSlotStateList[slot];
        JLog.logd(TAG, "sim slot " + slot + " state " + state);
        if ("ABSENT".equals(state)) {
            return SimStatusEnum.SIM_STATUS_ABSENT;
        } else if ("PIN_REQUIRED".equals(state)) {
            return SimStatusEnum.SIM_STATUS_PIN_LOCK;
        } else if ("PUK_REQUIRED".equals(state)) {
            return SimStatusEnum.SIM_STATUS_PUK_LOCK;
        } else if ("NETWORK_LOCKED".equals(state)) {
            return SimStatusEnum.SIM_STATUS_NETWORK_LOCK;
        } else if ("READY".equals(state)) {
            return SimStatusEnum.SIM_STATUS_READY;
        } else {
            return SimStatusEnum.SIM_STATUS_UNKNOWN;
        }
    }

}
