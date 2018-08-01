package com.ucloudlink.ucapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ucloudlink.framework.ui.FlowOrder;
import com.ucloudlink.refact.business.flow.FlowBandWidthControl;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.ServiceManager;
import com.ucloudlink.refact.access.AccessEventId;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.access.StateMessageId;
import com.ucloudlink.refact.business.routetable.ServerRouter;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.utils.TcpdumpHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by shiqianhua on 2016/12/19.
 */

public class TestReceiver extends BroadcastReceiver {
    private String ACTION_DDS = "com.ucloudlink.cmd.dds";
    private String ACTION_SWITCH_VSIM = "com.ucloudlink.cmd.switchvsim";
    private String ACTION_PHONCECALL_ON = "com.ucloudlink.cmd.phonecall.on";
    private String ACTION_PHONCECALL_OFF = "com.ucloudlink.cmd.phonecall.off";
    private String ACTION_RELOGIN = "com.ucloudlink.cmd.relogin";
    private String ACTION_RECONNECT_FAIL = "com.ucloudlink.cmd.reconnectfail";
    private String ACTION_APDU_FAIL = "com.ucloudlink.cmd.apdufail";
    private String ACTION_APDU_INVALID = "com.ucloudlink.cmd.apduinvalid";
	private String ACTION_PACK_TCPDUMP_FILE = "com.ucloudlink.cmd.tcpdump";
    private String ACTION_SOFT_DOWNLOAD_1 = "com.ucloudlink.cmd.softsim.1";
    private String ACTION_SOFT_DOWNLOAD_2 = "com.ucloudlink.cmd.softsim.2";
    private String ACTION_SOFT_DOWNLOAD_3 = "com.ucloudlink.cmd.softsim.3";
    private String ACTION_SEED_NETWORK_START = "com.ucloudlink.cmd.seed.start";
    private String ACTION_SEED_NETWORK_STOP = "com.ucloudlink.cmd.seed.stop";

    private String ACTION_ORDER_NEW = "com.ucloudlink.cmd.order.new";
    private String ACTION_ORDER_ACTIVATE = "com.ucloudlink.cmd.order.active";
    private String ACTION_ORDER_READ = "com.ucloudlink.cmd.order.read";

    private String ACTION_DISABLE_DOWNLOAD = "com.ucloudlink.cmd.download.disable";
    private String ACTION_ENABLE_DOWNLOAD = "com.ucloudlink.cmd.download.enable";
    private String ACTION_CHANGE_SEED_CARD = "com.ucloudlink.cmd.seed.change";
    private String ACTION_SOFTSIM_UPDATE = "com.ucloudlink.cmd.softsim.update";
    private String ACTION_SOFTSIM_FLOW_UP = "com.ucloudlink.cmd.softsim.flowup";
    private String ACTION_SOFTSIM_LOGOUT_FLAG = "com.ucloudlink.cmd.softsim.logout.flag";
    private String ACTION_SOFTSIM_LOGOUT_FORCE = "com.ucloudlink.cmd.softsim.logout.force";
    private String ACTION_FLOW_SET_BANDWIDTH = "com.ucloudlink.cmd.flow.band.set";
    private String ACTION_FLOW_CLEAR_BANDWIDTH = "com.ucloudlink.cmd.flow.band.clear";
    private String ACTION_FLOW_SET_PERMIT = "com.ucloudlink.cmd.flow.permit.set";
    private String ACTION_FLOW_CLEAR_PERMIT = "com.ucloudlink.cmd.flow.permit.clear";

    private String ACTION_BAND_WIDTH_FALSE = "com.ucloudlink.cmd.flow.permit.bandwidth_false";
    private String ACTION_BAND_WIDTH_TRUE = "com.ucloudlink.cmd.flow.permit.bandwidth_true";
    private String jsonStrFalse ="[{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"54.169.189.76\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"13.228.38.28\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"119.145.40.140\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"119.145.40.138\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"119.145.40.138\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"13.228.38.28\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"54.169.189.76\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":false,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"13.228.38.28\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0}]";

    private String jsonStrTrue = "[{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"54.169.189.76\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"54.169.189.76\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"119.145.40.140\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"119.145.40.138\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"119.145.40.138\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"13.228.38.28\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"13.228.38.28\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0},{\"BAND_WIDTH_IS_IP\":true,\"BAND_WIDTH_IS_SET\":true,\"BAND_WIDTH_UID\":-1,\"BAND_WIDTH_IP\":\"13.228.38.28\",\"BAND_WIDTH_TX_BYTES\":0,\"BAND_WIDTH_RX_BYTES\":0}]";

    public static final String TAG="TestReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(ACTION_DDS)){
//            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_SET_DDS_ILLEGALITY);
        }else if(intent.getAction().equals(ACTION_SWITCH_VSIM)){
            ServiceManager.accessEntry.switchVsimReq(3);
        }else if(intent.getAction().equals(ACTION_PHONCECALL_ON)){
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_PHONECALL_START);
        }else if(intent.getAction().equals(ACTION_PHONCECALL_OFF)){
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_EXCEPTION_PHONECALL_STOP);
        }else if(intent.getAction().equals(ACTION_RELOGIN)){
            ServiceManager.accessEntry.notifyEvent(StateMessageId.USER_RELOGIN_REQ_CMD);
        }else if ("android.intent.action.ANY_DATA_STATE".equals(intent.getAction())){
            //do nothing but very importance
//            Log.d(TAG, "onReceive: ANY_DATA_STATE");
        }else if(intent.getAction().equals(ACTION_RECONNECT_FAIL)){
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_RECONNECT_MSG_FAIL, -1, 0,
                    new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ErrorCode.INSTANCE.getRPC_INVALID_SESSION()));
        }else if(intent.getAction().equals(ACTION_APDU_FAIL)){
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_NET_APDU_MSG_FAIL, -1, 0,
                    new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ErrorCode.INSTANCE.getRPC_INVALID_SESSION()));
        }else if(intent.getAction().equals(ACTION_APDU_INVALID)){
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_CLOUDSIM_APDU_INVALID);
        }else if(intent.getAction().equals(ACTION_PACK_TCPDUMP_FILE)){
            TcpdumpHelper.getInstance().sendPackageFiles();
        }else if(intent.getAction().equals(ACTION_SOFT_DOWNLOAD_1)){
            int [] mccList = {460,470,480};
            FlowOrder flowOrder = new FlowOrder("1234567890", mccList, 12345, 33, 2);
            ArrayList<FlowOrder> orders = new ArrayList<>();
            orders.add(flowOrder);
            ServiceManager.accessEntry.softsimEntry.startDownloadSoftsim("8613510662117", "123456", orders);
        }else if(intent.getAction().equals(ACTION_SOFT_DOWNLOAD_2)){
            Configuration.INSTANCE.setOrderId("1234567890");
            Configuration.INSTANCE.setUsername("8613510662117");
            Configuration.INSTANCE.setSlots(1,0);
            ServerRouter.INSTANCE.setIpMode(101);
            ServiceManager.accessEntry.loginReq("8613510662117","123456");
        }else if(intent.getAction().equals(ACTION_SOFT_DOWNLOAD_3)){
            int type = intent.getIntExtra("type", 0);
            Log.d(TAG, "onReceive: type param:" + type);
            String curOrder = Configuration.INSTANCE.getOrderId();
            OrderInfo orderInfo = ServiceManager.accessEntry.softsimEntry.getOrderInfoByUserOrderId(Configuration.INSTANCE.getUsername(), curOrder);
            orderInfo.setSimUsePolicy(type);
            ServiceManager.accessEntry.softsimEntry.updateUserOrderInfo(Configuration.INSTANCE.getUsername(), orderInfo);
        }else if(intent.getAction().equals(ACTION_SEED_NETWORK_START)){
            int ret = ServiceManager.accessEntry.startSeedNetwork("8613966661112", "59648bf7a1fbd8115ab2b8da", 200);
            Log.d(TAG, "ServiceManager.accessEntry.startSeedNetwork: " + ret);
        }else if(intent.getAction().equals(ACTION_SEED_NETWORK_STOP)){
            ServiceManager.accessEntry.stopSeedNetwork();
        }else if(intent.getAction().equals(ACTION_ORDER_NEW)){
            OrderInfo info = new OrderInfo("123456789", null, new Date().getTime() - TimeUnit.DAYS.toMillis(1) + TimeUnit.MINUTES.toMillis(20),
                    1, null, OrderInfo.PHY_SIM_ONLY);
            ServiceManager.accessEntry.softsimEntry.updateUserOrderInfo("test", info);
            Log.d(TAG, "onReceive: OrderInfo, " + info);
        }else if(intent.getAction().equals(ACTION_ORDER_ACTIVATE)){
            OrderInfo info = ServiceManager.accessEntry.softsimEntry.getOrderInfoByUserOrderId("test", "123456789");
            Log.d(TAG, "onReceive: before activate:" + info);
            ServiceManager.accessEntry.softsimEntry.activateUserOrder("test", "123456789",
                    new Date().getTime() - TimeUnit.DAYS.toMillis(1) + TimeUnit.MINUTES.toMillis(20), 1);
            info = ServiceManager.accessEntry.softsimEntry.getOrderInfoByUserOrderId("test", "123456789");
            Log.d(TAG, "onReceive: after activate:" + info);
        }else if(intent.getAction().equals(ACTION_ORDER_READ)){
            OrderInfo info = ServiceManager.accessEntry.softsimEntry.getOrderInfoByUserOrderId("test", "123456789");
            Log.d(TAG, "onReceive: orderlist:" + info);
        }else if (intent.getAction().equals(ACTION_DISABLE_DOWNLOAD)){
            ServiceManager.accessEntry.softsimEntry.startSocket();
        }else if(intent.getAction().equals(ACTION_ENABLE_DOWNLOAD)){
            ServiceManager.accessEntry.softsimEntry.stopSocket();
        }else if(intent.getAction().equals(ACTION_CHANGE_SEED_CARD)){
            ServiceManager.accessEntry.notifyEvent(AccessEventId.EVENT_SEEDSIM_RESET_CLOUD_SIM);
        }else if(intent.getAction().equals(ACTION_SOFTSIM_UPDATE)){
            ServiceManager.accessEntry.softsimEntry.startUpdateAllSoftsim("", "460", "01");
        }else if(intent.getAction().equals(ACTION_SOFTSIM_FLOW_UP)){
            ServiceManager.accessEntry.softsimEntry.startUploadSoftsimFlowTest();
        }else if(intent.getAction().equals(ACTION_SOFTSIM_LOGOUT_FLAG)){
            boolean value = intent.getBooleanExtra("logout", false);
            Log.d(TAG, "onReceive: get logout value:" + value);
            ServiceManager.accessEntry.softsimEntry.setDonotLogout(value);
        }else if(intent.getAction().equals(ACTION_SOFTSIM_LOGOUT_FORCE)){
            ServiceManager.accessEntry.softsimEntry.forLogoutMsg();
        }else if(intent.getAction().equals(ACTION_FLOW_SET_BANDWIDTH)){
            int value = intent.getIntExtra("value", 100);
            //FlowBandWidthControl.getInstance().setInterfaceThrottle("rmnet_data0", value, value);
        }else if(intent.getAction().equals(ACTION_FLOW_CLEAR_BANDWIDTH)){
            //FlowBandWidthControl.getInstance().resetInterfaceThrottle("rmnet_data0", 1000, 1000);
        }else if(intent.getAction().equals(ACTION_FLOW_SET_PERMIT)){
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().getINetSpeed().setFlowPermitByPassUid(1001);
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().getINetSpeed().setFlowPermitByPassIpstr("163.177.151.110"); // www.baidu.com
        }else if(intent.getAction().equals(ACTION_FLOW_CLEAR_PERMIT)){
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().getINetSpeed().clearFlowPermitByPassUid(1001);
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().getINetSpeed().clearFlowPermitByPassIpstr("163.177.151.110"); // www.baidu.com
        } else if(intent.getAction().equals(ACTION_BAND_WIDTH_FALSE)){
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().configNetworkBandWidth(jsonStrFalse);
        } else if(intent.getAction().equals(ACTION_BAND_WIDTH_TRUE)){
            FlowBandWidthControl.getInstance().getINetSpeedCtrl().configNetworkBandWidth(jsonStrTrue);
        }
    }
}
