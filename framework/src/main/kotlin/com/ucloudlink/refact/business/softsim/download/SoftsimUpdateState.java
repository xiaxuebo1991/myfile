package com.ucloudlink.refact.business.softsim.download;

import android.content.Context;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.ucloudlink.refact.business.softsim.CardRepository;
import com.ucloudlink.refact.channel.enabler.datas.Card;
import com.ucloudlink.refact.channel.enabler.datas.CardType;
import com.ucloudlink.refact.config.Configuration;
import com.ucloudlink.refact.utils.EncryptUtils;
import com.ucloudlink.refact.utils.HexUtil;
import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.framework.protocol.protobuf.DispatchSoftsimRspOrder;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimBinRsp;
import com.ucloudlink.framework.protocol.protobuf.GetSoftsimInfoRsp;
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinInfo;
import com.ucloudlink.framework.protocol.protobuf.SoftsimBinType;
import com.ucloudlink.framework.protocol.protobuf.SoftsimInfo;
import com.ucloudlink.framework.protocol.protobuf.UpdateSoftsimStatusRsp;
import com.ucloudlink.refact.access.ErrorCode;
import com.ucloudlink.refact.channel.transceiver.protobuf.MessagePacker;
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimBinInfoSingleReq;
import com.ucloudlink.refact.business.softsim.download.struct.SoftsimUpdateParam;
import com.ucloudlink.refact.business.softsim.manager.SoftsimManager;
import com.ucloudlink.refact.business.softsim.struct.OrderInfo;
import com.ucloudlink.refact.business.softsim.struct.SoftsimBinLocalFile;
import com.ucloudlink.refact.business.softsim.struct.SoftsimLocalInfo;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okio.BufferedSink;
import okio.Okio;
import rx.Single;
import rx.Subscription;
import rx.functions.Action1;

import static com.ucloudlink.refact.utils.JLog.logk;

/**
 * Created by shiqianhua on 2016/12/29.
 */

public abstract class SoftsimUpdateState extends StateMachine{
    private String TAG = "SoftsimUpdate";

    private Context context;
    private SoftsimManager softsimManager;
    private SoftsimUpdateParam startParam;

    private State mDefaultState = new DefaultState();
    private State mInitState = new InitState();
    private State mUpdatingState = new UpdatingState();
    private State mGetSimInfoState = new GetSimInfoState();
    private State mGetBinInfoState = new GetBinInfoState();
    private State mSuccState = new SuccState();
    private State mFailState = new FailState();

    private State curState = null;
    private State lastState = null;
    private State nextState = null;

    private static final int EV_START = 1;
    private static final int EV_STOP = 2;
    private static final int EV_UPDATE_RSP = 3;
    private static final int EV_SIMINFO_RSP = 4;
    private static final int EV_BININFO_RSP = 5;

    private UpdateSoftsimStatusRsp softsimStatusRsp;
    private GetSoftsimInfoRsp softsimInfoRsp;
    private GetSoftsimBinRsp softsimBinRsp;
    private ArrayList<OrderInfo> addOrderList = null;
    private ArrayList<String> updateSoftsims = null;
    private ArrayList<SoftsimBinInfoSingleReq> needUpdateBinList = null;

    private int terCode;
    private int lastStage;

    private Subscription updateOb;
    private Subscription softsimInfoOb;
    private Subscription softsimBinOb;

    public SoftsimUpdateState(Context ctx, SoftsimManager manager, Looper looper){
        super("SoftsimUpdateState", looper);
        context = ctx;
        softsimManager = manager;

        addState(mDefaultState);
            addState(mInitState, mDefaultState);
                addState(mUpdatingState, mInitState);
                addState(mGetSimInfoState, mInitState);
                addState(mGetBinInfoState, mInitState);
            addState(mSuccState, mDefaultState);
            addState(mFailState, mDefaultState);
        setInitialState(mDefaultState);
//        setDbg(true);
        start();
    }

    private void safeUndescribe(Subscription sub){
        if (sub != null && !sub.isUnsubscribed()) {
            sub.unsubscribe();
        }
    }

    private void transToNextState(State next) {
        logk("state change: " + curState.getName() + " -> " + next.getName());
        nextState = next;
        transitionTo(next);
    }

    private class DefaultState extends State{
        @Override
        public void enter() {
            super.enter();
            curState = this;
        }

        @Override
        public void exit() {
            lastState = this;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what){
                case EV_START:
                    startParam = (SoftsimUpdateParam)msg.obj;
                    JLog.logd("recv EV_START " + startParam);
                    transToNextState(mUpdatingState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class InitState extends State{
        @Override
        public void enter() {
            super.enter();
            curState = this;
            clearAllData();
        }

        @Override
        public void exit() {
            lastState = this;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {

                case EV_STOP:
                    terCode = ErrorCode.INSTANCE.getINNER_USER_CANCEL();
                    transToNextState(mFailState);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class UpdatingState extends State{
        private int retryCount = 0;
        private void startUpdateAction(){
            retryCount++;
            JLog.logd("startParam " + startParam);
            ArrayList<SoftsimLocalInfo> softsims = softsimManager.getSoftsimInfoListByUser(startParam.username);
            if(softsims == null || softsims.size() == 0){
                transToNextState(mSuccState);
                return;
            }
            if(retryCount > 3){
                JLog.loge("startUpdateAction retryCount!" + retryCount + " so change to fail");
                terCode = ErrorCode.INSTANCE.getLOCAL_TIMEOUT();
                transToNextState(mFailState);
            }else {
                sendUpdateMsg(startParam.username, startParam.mcc, startParam.mnc, softsims, startParam.curImsi);
            }
        }

        private void updateUserOrderList(UpdateSoftsimStatusRsp rsp) {
            boolean find = false;

            ArrayList<String> needUpdateSoftsims = new ArrayList<>();
            // get new softsim list
            ArrayList<String> sims =  MessagePacker.INSTANCE.getSoftsimListByDispatchRsp(rsp.orders);
            ArrayList<SoftsimLocalInfo> localImsi = softsimManager.getSoftsimInfoListByUser(startParam.username);
            for(String imsi: sims){
                find = false;
                for(SoftsimLocalInfo simINfo: localImsi){
                    if(simINfo.getImsi().equals(imsi)){
                        find = true;
                        break;
                    }
                }
                if(!find){
                    needUpdateSoftsims.add(imsi);
                }
            }

            for(Long imsi: rsp.needUpdateSims) {
                find = false;
                for(String t: needUpdateSoftsims) {
                    if(t.equals(imsi.toString())) {
                        find = true;
                        break;
                    }
                }
                if(!find) {
                    needUpdateSoftsims.add(imsi.toString());
                }
            }
            updateSoftsims = needUpdateSoftsims;


            ArrayList<OrderInfo> updateList = new ArrayList<>();
            for(DispatchSoftsimRspOrder t: rsp.orders){
                OrderInfo info = softsimManager.getOrderInfoByUserOrder(rsp.username, t.order);
                if(info != null){
                    info.setSofsimList(MessagePacker.INSTANCE.getStringListByLongList(t.softsims));
                    updateList.add(info);
                }
            }
            addOrderList = updateList;
        }

        private void updateActionRsp(Message msg){
            if(msg.arg1 == 0){
                softsimStatusRsp = (UpdateSoftsimStatusRsp) msg.obj;
                updateUserOrderList((UpdateSoftsimStatusRsp) msg.obj);
                transToNextState(mGetSimInfoState);
            }else {
                Throwable t = (Throwable)msg.obj;
                String err = ErrorCode.INSTANCE.getErrString(t);
                ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
                if(info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN
                        || info.getAction() == ErrorCode.ErrActType.ACT_TER
                        || info.getAction() == ErrorCode.ErrActType.ACT_EXIT){
                    JLog.loge("updateActionRsp failed!" + t.getMessage());
                    terCode = info.getCode();
                    transToNextState(mFailState);
                }else {
                    startUpdateAction();
                }
            }
        }
        @Override
        public void enter() {
            super.enter();
            curState = this;
            retryCount = 0;
            lastStage = 1;
            startUpdateAction();
        }

        @Override
        public void exit() {
            lastState = this;
            retryCount = 0;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EV_UPDATE_RSP:
                    updateActionRsp(msg);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class GetSimInfoState extends State{
        private int retryCount = 0;
        private void updateSoftsimInfoAction(){
            if(updateSoftsims == null || updateSoftsims.size() == 0){
                JLog.logd("no need to get softsim info!");
                transToNextState(mSuccState);
                return;
            }
            if(retryCount > 3){
                JLog.loge("updateSoftsimInfoAction retryCount!" + retryCount + " so change to fail");
                terCode = ErrorCode.INSTANCE.getLOCAL_TIMEOUT();
                transToNextState(mFailState);
            }else {
                sendGetSimInfoMsg(updateSoftsims);
            }
        }

        private void updateSoftsimInfoRspProc(GetSoftsimInfoRsp rsp){
            ArrayList<SoftsimBinInfoSingleReq> binList = new ArrayList<>();
            for(SoftsimInfo sim: rsp.softsims){
                if(softsimManager.getSoftsimBinByRef(sim.plmnBinRef) == null){
                    binList.add(new SoftsimBinInfoSingleReq(1, sim.plmnBinRef));
                }
//                if(softsimManager.getSoftsimBinByRef(sim.feeBinRef) == null){
//                    binList.add(new SoftsimBinInfoSingleReq(2, sim.feeBinRef));
//                }
            }
            needUpdateBinList = binList;
        }

        private void updateSoftsimInfoRsp(Message msg){
            //该log存在ki，opc原值，去掉打印
            //JLog.logd(TAG, "updateSoftsimInfoRsp: " + msg);
            if(msg.arg1 == 0){
                softsimInfoRsp = (GetSoftsimInfoRsp)msg.obj;
                int errcode = MessagePacker.INSTANCE.softsimListValid(softsimInfoRsp);
                if(errcode == 0) {
                    updateSoftsimInfoRspProc((GetSoftsimInfoRsp) msg.obj);
                    transToNextState(mGetBinInfoState);
                }else {
                    terCode = errcode;
                    transToNextState(mFailState);
                }
            }else {
                Throwable t = (Throwable)msg.obj;
                String err = ErrorCode.INSTANCE.getErrString(t);
                ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
                if(info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN
                        || info.getAction() == ErrorCode.ErrActType.ACT_TER
                        || info.getAction() == ErrorCode.ErrActType.ACT_EXIT){
                    JLog.loge("updateSoftsimInfoRsp failed!" + t.getMessage());
                    terCode = info.getCode();
                    transToNextState(mFailState);
                } else {
                    updateSoftsimInfoAction();
                }
            }
        }

        @Override
        public void enter() {
            super.enter();
            curState = this;
            lastStage = 2;
            updateSoftsimInfoAction();
        }

        @Override
        public void exit() {
            lastState = this;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what){
                case EV_SIMINFO_RSP:
                    updateSoftsimInfoRsp(msg);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class GetBinInfoState extends State{
        private int retryCount = 0;
        private void updateSoftsimBinAction(){
            if(needUpdateBinList == null || needUpdateBinList.size() == 0){
                transToNextState(mSuccState);
                return;
            }
            if(retryCount > 3){
                JLog.loge("updateSoftsimBinAction retryCount!" + retryCount + " so change to fail");
                terCode = ErrorCode.INSTANCE.getLOCAL_TIMEOUT();
                transToNextState(mFailState);
            }else {
                sendGetSimBinMsg(needUpdateBinList);
            }
        }

        private void updateSoftsimBinRsp(Message msg){
            if(msg.arg1== 0){
                softsimBinRsp = (GetSoftsimBinRsp)msg.obj;
                transToNextState(mSuccState);
            }else{
                // // TODO: 2016/12/30  error
                Throwable t = (Throwable)msg.obj;
                String err = ErrorCode.INSTANCE.getErrString(t);
                ErrorCode.ErrCodeInfo info = ErrorCode.INSTANCE.getErrInfoByStr(err);
                if(info.getAction() == ErrorCode.ErrActType.ACT_RELOGIN
                        || info.getAction() == ErrorCode.ErrActType.ACT_TER
                        || info.getAction() == ErrorCode.ErrActType.ACT_EXIT){
                    JLog.loge("updateSoftsimBinRsp failed!" + t.getMessage());
                    terCode = info.getCode();
                    transToNextState(mFailState);
                } else {
                    updateSoftsimBinAction();
                }
            }
        }
        @Override
        public void enter() {
            super.enter();
            curState = this;
            lastStage = 3;
            updateSoftsimBinAction();
        }

        @Override
        public void exit() {
            lastState = this;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what){
                case EV_BININFO_RSP:
                    updateSoftsimBinRsp(msg);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private class SuccState extends State{
        private void updateDataToDB(){

            if(softsimInfoRsp != null) {
                ArrayList<SoftsimLocalInfo> localInfos = MessagePacker.INSTANCE.getSoftsimInfoListFromGetInfoRsp(softsimInfoRsp);
                for (SoftsimLocalInfo sim : localInfos) {
                    String binname = sim.getPlmnBin();
                    String fileName = "00001" + binname.substring(binname.length() - 12, binname.length());
                    if (softsimBinRsp != null) {
                        for (SoftsimBinInfo binInfo : softsimBinRsp.bins) {
                            if (binInfo.binref.equals(sim.getPlmnBin())) {
                                String dirName = Configuration.INSTANCE.getSimDataDir() + fileName + ".bin";
                                JLog.logd("write softsim bin to file:" + dirName);
                                File file = new File(dirName);
                                try {
                                    BufferedSink writer = Okio.buffer(Okio.sink(file));
                                    writer.write(binInfo.data);
                                    writer.flush();
                                    writer.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                break;
                            }
                        }
                    }
                    CardRepository.INSTANCE.deleteCard(sim.getImsi());
                    final Card sCard = new Card();
                    sCard.setCardType(CardType.SOFTSIM);
                    sCard.setImsi(sim.getImsi());
                    sCard.setKi(sim.getKi());
                    sCard.setOpc(sim.getOpc());
                    sCard.setImageId(fileName);
                    sCard.setIccId(sim.getIccid());
                    sCard.setMsisdn(sim.getMsisdn());
                    boolean ret = CardRepository.INSTANCE.fetchSoftCard(sCard);
                    if(ret ) {
                        sim.setKi(EncryptUtils.getMd5Digest(sim.getKi()));
                        sim.setOpc(EncryptUtils.getMd5Digest(sim.getOpc()));
                        softsimManager.updateSoftsimInfo(sim);
                    }else {
                        Log.e(TAG, "updateDataToDB: add softsim " + sCard + " to database failed!");
                    }
                }
            }

            if(addOrderList != null && addOrderList.size() != 0) {
                for(OrderInfo info: addOrderList) {
                    softsimManager.updateUserOrderInfo(startParam.username, info);
                }
            }
            if(softsimBinRsp != null) {
                for (SoftsimBinInfo info : softsimBinRsp.bins) {
                    softsimManager.updateSoftsimBinFile(new SoftsimBinLocalFile(info.binref, (info.type == SoftsimBinType.PLMN_LIST_BIN) ? 1 : 2, info.data.toByteArray()));
                }
            }
        }

        @Override
        public void enter() {
            super.enter();
            curState = this;
            updateDataToDB();
            onResult(0,3);
        }

        @Override
        public void exit() {
            lastState = this;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            return super.processMessage(msg);
        }
    }

    private class FailState extends State{
        @Override
        public void enter() {
            curState = this;
            super.enter();
            onResult(terCode, lastStage);
        }

        @Override
        public void exit() {
            lastState = this;
            super.exit();
        }

        @Override
        public boolean processMessage(Message msg) {
            return super.processMessage(msg);
        }
    }

    private void clearAllData(){
        softsimStatusRsp = null;
        softsimInfoRsp = null;
        softsimBinRsp = null;
        addOrderList = null;
        updateSoftsims = null;
        needUpdateBinList = null;
        terCode = 0;
        lastStage = 0;
    }


    public void startUpdate(SoftsimUpdateParam param){
        sendMessage(EV_START, param);
    }

    public void stopUpdate(){
        sendMessage(EV_STOP);
    }

    public abstract void onResult(int errcode, int stage);
    public abstract void sendUpdateMsg(String username, String mcc, String mnc, ArrayList<SoftsimLocalInfo> sims, String curImsi);
    public abstract void sendGetSimInfoMsg(ArrayList<String>  sims);
    public abstract void sendGetSimBinMsg(ArrayList<SoftsimBinInfoSingleReq> sims);

    public void setUpdateMsgOb(Single<Object> single){
        safeUndescribe(updateOb);
        updateOb = single.timeout(35, TimeUnit.SECONDS)
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        if(o instanceof UpdateSoftsimStatusRsp){
                            if(((UpdateSoftsimStatusRsp) o).errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()){
                                logd("recv update rsp :" + o);
                                sendMessage(EV_UPDATE_RSP, 0, 0, o);
                            }else{
                                sendMessage(EV_UPDATE_RSP, -1, 0, new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ((UpdateSoftsimStatusRsp) o).errorCode));
                            }
                        }else{
                            sendMessage(EV_UPDATE_RSP, -1, 0, new Exception(ErrorCode.INSTANCE.getPARSE_HEADER_STR() + o.toString()));
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if(throwable instanceof  TimeoutException){
                            throwable = new TimeoutException("TIMEOUT: business timeout!");
                        }
                        sendMessage(EV_UPDATE_RSP, -1, 0, throwable);
                    }
                });
    }

    public void setGetSimInfoMsgOb(Single<Object> single){
        safeUndescribe(softsimInfoOb);
        softsimInfoOb = single.timeout(35, TimeUnit.SECONDS)
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        if(o instanceof GetSoftsimInfoRsp){
                            if(((GetSoftsimInfoRsp) o).errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()){
                                sendMessage(EV_SIMINFO_RSP, 0, 0, o);
                            }else{
                                sendMessage(EV_SIMINFO_RSP, -1, 0, new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ((GetSoftsimInfoRsp) o).errorCode));
                            }
                        }else {
                            sendMessage(EV_SIMINFO_RSP, -1, 0, new Exception(ErrorCode.INSTANCE.getPARSE_HEADER_STR() + o.toString()));
                        }

                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if(throwable instanceof  TimeoutException){
                            throwable = new TimeoutException("TIMEOUT: business timeout!");
                        }
                        sendMessage(EV_SIMINFO_RSP, -1, 0, throwable);
                    }
                });
    }

    public void setGetSimBinMsgOb(Single<Object> single){
        safeUndescribe(softsimBinOb);
        softsimBinOb = single.timeout(35, TimeUnit.SECONDS)
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        if(o instanceof GetSoftsimBinRsp){
                            if(((GetSoftsimBinRsp) o).errorCode == ErrorCode.INSTANCE.getRPC_RET_OK()){
                                sendMessage(EV_BININFO_RSP, 0, 0, o);
                            }else {
                                sendMessage(EV_BININFO_RSP, -1, 0, new Exception(ErrorCode.INSTANCE.getRPC_HEADER_STR() + ((GetSoftsimBinRsp) o).errorCode));
                            }
                        }else {
                            sendMessage(EV_BININFO_RSP, -1, 0, new Exception(ErrorCode.INSTANCE.getPARSE_HEADER_STR() + o.toString()));
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if(throwable instanceof  TimeoutException){
                            throwable = new TimeoutException("TIMEOUT: business timeout!");
                        }
                        sendMessage(EV_BININFO_RSP, -1, 0, throwable);
                    }
                });
    }
}
