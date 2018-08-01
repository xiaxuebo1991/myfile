package com.ucloudlink.simservice;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import static com.ucloudlink.refact.utils.JLog.logd;

/**
 * Created by jiaming.liang on 2016/8/1.
 */
public class HomeService extends Service /*implements Cmd */{
//
//    private CloudSimState cloudSimState = new CloudSimState();
//    private HomeSerBinder       mHomeSerBinder;
//    private ISimServiceCallBack ClientCallback;
//  
//    private AutoRunListen mAutoListen;
//    private PeriodUpdateCloudSim task;
//
//    private void startTask() {
//        if (task==null) {
//            task = new PeriodUpdateCloudSim() {
//                @Override
//                public void taskRun() {
//                    Card cloudSimCard = CardManager.INSTANCE.getCloudSimCard();
//                    if (cloudSimCard!=null) {
//                        cloudSimState.imsi = cloudSimCard.getImsi();
//                        cloudSimState.LteSignalStrength = OperatorNetworkInfo.INSTANCE.getSignalStrengthCloudSim();
//                    }
//                }
//            };
//        }
//        task.start();
//    }
//
//    private void stopTask() {
//        if (task!=null) {
//            task.stop();
//            task=null;
//        }
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        logd("homeservice onStartCommand");
//        return super.onStartCommand(intent, flags, startId);
//    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        logd("HomeService onBind");
//        mHomeSerBinder = new HomeSerBinder();
//        return mHomeSerBinder;
        return null;
    }
//
//    @Override
//    public boolean onUnbind(Intent intent) {
//        logd("HomeService onUnbind");
//        return super.onUnbind(intent);
//    }
//
//    public void setClientCallback(ISimServiceCallBack clientCallback) {
//        ClientCallback = clientCallback;
//    }
//
//    class AutoRunListen implements AutoListen {
//        @Override
//        @WorkerThread
//        public void newEvent(@NotNull RunStep currStep, @NotNull EventNotice newEvent) {
//            logd("get newEvent currStep:" + currStep + ",EventNotice:" + newEvent);
//            if (ClientCallback == null) {
//                return;
//            }
//            if (currStep == RunStep.SUB_AUTH && newEvent == EventNotice.EV_DEFAULT_NETWORK_CONNECTED) {
//                try {
//                    //                    Bundle mMsg= new Bundle();
//                    String imeiCloudSim = CardManager.INSTANCE.getCloudSimCard().getImsi();
//                    String mccmncCloudSim = OperatorNetworkInfo.INSTANCE.getMccmncCloudSim();
//                    //                    int signalStrengthCloudSim = OperatorNetworkInfo.INSTANCE.getSignalStrengthCloudSim();
//                    int signalStrengthCloudSim = 4;//默认返回4
//                    logd("CHOULD_SIM_CONNECTED>>  imeiCloudSim:" + imeiCloudSim + " mccmncCloudSim:" + mccmncCloudSim + " signalStrengthCloudSim:" + signalStrengthCloudSim);
//                    //                    mMsg.putString(B_KEY_CHOULD_SIM_CONNECTED.CLOUD_SIM_IMSI, imeiCloudSim);
//                    //                    mMsg.putString(B_KEY_CHOULD_SIM_CONNECTED.CLOUD_SIM_MNCMCC, mccmncCloudSim);
//                    //                    mMsg.putInt(B_KEY_CHOULD_SIM_CONNECTED.CLOUD_SIM_SIGNALSTRENGTH, signalStrengthCloudSim);
//                    //                    ClientCallback.ProcessStepChange(S2U.CHOULD_SIM_CONNECTED, mMsg);
//                    cloudSimState.stateOn();
//                    cloudSimState.imsi = imeiCloudSim;
//                    cloudSimState.LteSignalStrength = signalStrengthCloudSim;
//                    startTask();
//                    ClientCallback.cloudSimAvailable(imeiCloudSim, mccmncCloudSim, signalStrengthCloudSim);
//                } catch (RemoteException e) {
//                    e.printStackTrace();
//                }
//            } else if (newEvent == EventNotice.EVENT_STOP) {
//                stopTask();
//                try {
//                    //                    ClientCallback.ProcessStepChange(S2U.CHOULD_SIM_STOP, mMsg);
//                    cloudSimState.close();
//                    ClientCallback.cloudSimStop();
//                    AutoRun.INSTANCE.setAutoListen(null);
//                    ServiceManager.simMonitor.unRegister(HomeService.this);
//                } catch (RemoteException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//
//        @Override
//        public void getException(@NotNull Ept_Event exceptionEvent) {
//            switch (exceptionEvent) {
//                case EXP_LOGIN_EXCEPTION:
//                    try {
//                        ClientCallback.getException(error.NO_MORE_CARD);
//                    } catch (RemoteException e) {
//                        e.printStackTrace();
//                    }
//                    cloudSimState.turningOn();
//                    break;
//                case EXP_SERVER_TIMEOUT:
//                    try {
//                        ClientCallback.getException(error.SERVER_TIMEOUT);
//                    } catch (RemoteException e) {
//                        e.printStackTrace();
//                    }
//                    cloudSimState.close();
//                    break;
//                default:
//                    break;
//            }
//        }
//    }
//
//    //
//    class HomeSerBinder extends ISimService.Stub {
//
//        @Override
//        public void login(String UserName, String password) throws RemoteException {
//            logd("login");
//            if (mAutoListen == null) {
//                mAutoListen = new AutoRunListen();
//            }
//            cloudSimState.turningOn();
//            AutoRun.INSTANCE.setAutoListen(mAutoListen);
//            Framework.INSTANCE.getAccessManager().startSession(UserName, password);
//        }
//
//        @Override
//        public void logout() throws RemoteException {
//            logd("logout");
//            Framework.INSTANCE.getAccessManager().stopSession();
//        }
//
//        @Override
//        public void changeCard() throws RemoteException {
//            logd("changeCard");
//            cloudSimState.switching();
//            Framework.INSTANCE.getAccessManager().switchCloudsim(0, "resever");
//        }
//
//        @Override
//        public boolean changeSlotMode(int apdu, int cloudsim) throws RemoteException {
//            logd("changeSlotMode");
//            //todo 需要对入参进行限定优化
//            Configuration.INSTANCE.setSlots(apdu, cloudsim);
//            return true;
//        }
//
//        @Override
//        public boolean changeApduMode(int mode) throws RemoteException {
//            logd("changeApduMode");
//            //todo 需要对入参进行限定优化
//            Configuration.INSTANCE.setApduMode(mode);
//            return true;
//        }
//
//        @Override
//        public void registerCallBack(ISimServiceCallBack callback) throws RemoteException {
//            logd("registerCallBack callback:" + callback);
//            setClientCallback(callback);
//        }
//
//        @Override
//        public void unRegisterCallBack(ISimServiceCallBack callback) throws RemoteException {
//            logd("unRegisterCallBack");
//            setClientCallback(null);
//        }
//
//        @Override
//        public int getCloudSimStatu() throws RemoteException {
//            return cloudSimState.getStatus();
//        }
//
//        @Override
//        public String getCloudSimImsi() throws RemoteException {
//            return cloudSimState.imsi;
//        }
//
//        @Override
//        public String getCloudSimMccmnc() throws RemoteException {
//            //还没写
//            return "";
//        }
//
//        @Override
//        public int getCloudSimSignalStrength() throws RemoteException {
//            return cloudSimState.LteSignalStrength;
//        }
//    }
}
