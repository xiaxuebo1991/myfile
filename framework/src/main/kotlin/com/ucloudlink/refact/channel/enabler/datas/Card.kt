package com.ucloudlink.refact.channel.enabler.datas

import com.ucloudlink.framework.protocol.protobuf.EquivalentPlmnInfo
import com.ucloudlink.refact.channel.enabler.simcard.ApnSetting.Apn
import com.ucloudlink.refact.utils.JLog

/**
 * Created by chentao on 2016/8/24.
 */
/**
 * 注意:状态的修改添加要按照其生命周期顺序添加,不要弄乱了
 */
enum class CardStatus {
    ABSENT,
    INSERTED,
    POWERON,  //注意依赖关系，编程有使用状态的依赖关系
    READY,//此时subid 应有效
    LOAD,
    OUT_OF_SERVICE,
    EMERGENCY_ONLY,
    IN_SERVICE
//    
//    NEEDAUTH, //需要远程鉴权，而不是其他鉴权
//    AUTHSUCC, //远程鉴权完成，而不是其他鉴权
//    AUTHFAIL //远程鉴权完成，而不是其他鉴权

}

enum class CardType {
    PHYSICALSIM,
    SOFTSIM,
    VSIM,
}

enum class CardModel {
    UNKNOWMODEL,
    UICC,
    GSM,
}

// wlmark which time give slot or give slot in where?
data class Card(var imsi: String = "",
                var ki: String? = null,
                var opc: String? = null,

                var cardType: CardType = CardType.PHYSICALSIM,
                var slot: Int = -1,
                var vslot: Int = 0,
                var subId: Int = -1,
                @Volatile var status: CardStatus = CardStatus.ABSENT,
                var atr: ByteArray = ByteArray(0),
        //var apduDelegate: ApduDelegate? = null,
                var eplmnlist: List<EquivalentPlmnInfo>? = null,
                var cardModel: CardModel = CardModel.UNKNOWMODEL,
                var rat: Int = 0,
                var eplmnrat: Int = 0,
                var roamenable: Boolean = false,
                var apn: List<Apn>? = null,
                var numeric: String = "",
                var iccId: String? = null,
                var msisdn: String? = null,
                var imageId: String? = null,
                var vritimei: String = "",
                var fplmn: Array<String>? = null, //目前最多接受4个，再加没效果
                var preferredPlmn: Array<String>? = null, //优选plmn 越前越优先
                var rejectRetryTimes: Int = 0,
                var lastCardExist: Boolean = false,
                var authDcRetryTimes: Int = 0,
                var cardFirstReadyFlag: Boolean = true//此次起云卡首次ready标志
) {
    fun reset() {
        JLog.logv("do reset ${this}")
        imsi = ""
        ki = null
        opc = null
        cardType = CardType.PHYSICALSIM
        slot = -1
        vslot = 0
        subId = -1
        status = CardStatus.ABSENT
        atr = ByteArray(0)
        //apduDelegate = null
        eplmnlist = null
        cardModel = CardModel.UNKNOWMODEL
        apn = null
        numeric = ""
        iccId = null
        msisdn = null
        imageId = null
        vritimei = ""
        rat = 0
        eplmnrat = 0
        rejectRetryTimes = 0
        lastCardExist = false
        authDcRetryTimes = 0
        cardFirstReadyFlag = true
    }

    override fun toString(): String {
        return "Card:(imsi:$imsi subId:$subId cardType:$cardType numeric:$numeric slot:$slot status:$status rat$rat eplmn$eplmnrat roamenable$roamenable eplmnlist:$eplmnlist apn:$apn iccId:$iccId msisdn:$msisdn imageId:$imageId vritimei:$vritimei)"
    }

    fun clone(): Card {
        val card = Card()
        card.imsi = this.imsi
        card.ki = this.ki
        card.opc = this.opc
        card.cardType = this.cardType
        card.slot = this.slot
        card.vslot = this.vslot
        card.subId = this.subId
        card.status = this.status
        card.atr = this.atr
        //card.apduDelegate = this.apduDelegate
        card.eplmnlist = this.eplmnlist
        card.cardModel = this.cardModel
        card.apn = this.apn
        card.vritimei = this.vritimei
        return card
    }
    /* var  cardStatuListen: CardStatusListener?=null
     fun updateStatu(statu: CardStatus){
         if(this.status!=statu){
             if ((statu == CardStatus.READY) && (this.status == CardStatus.AUTHDONE)) {
                 println("it is AUTHDONE, already sim ready, do nothing")
                 return
             }
             println("updateCardStatu old statu:${this.status}  new statu:$statu ")
             this.status=statu
             if(cardStatuListen!=null){
                 (cardStatuListen as CardStatusListener).cardStatuChange(this)
 
             }
         }
     }*/
}

val MCCMNC_CODES_HAVING_3DIGITS_MNC = arrayOf("302370", "302720", "310260",
        "405025", "405026", "405027", "405028", "405029", "405030", "405031", "405032",
        "405033", "405034", "405035", "405036", "405037", "405038", "405039", "405040",
        "405041", "405042", "405043", "405044", "405045", "405046", "405047", "405750",
        "405751", "405752", "405753", "405754", "405755", "405756", "405799", "405800",
        "405801", "405802", "405803", "405804", "405805", "405806", "405807", "405808",
        "405809", "405810", "405811", "405812", "405813", "405814", "405815", "405816",
        "405817", "405818", "405819", "405820", "405821", "405822", "405823", "405824",
        "405825", "405826", "405827", "405828", "405829", "405830", "405831", "405832",
        "405833", "405834", "405835", "405836", "405837", "405838", "405839", "405840",
        "405841", "405842", "405843", "405844", "405845", "405846", "405847", "405848",
        "405849", "405850", "405851", "405852", "405853", "405875", "405876", "405877",
        "405878", "405879", "405880", "405881", "405882", "405883", "405884", "405885",
        "405886", "405908", "405909", "405910", "405911", "405912", "405913", "405914",
        "405915", "405916", "405917", "405918", "405919", "405920", "405921", "405922",
        "405923", "405924", "405925", "405926", "405927", "405928", "405929", "405930",
        "405931", "405932", "502142", "502143", "502145", "502146", "502147", "502148"
        /*from qc*/
        , "405840", "405854", "405855", "405856", "405857", "405858", "405859", "405860", "405861"
        , "405862", "405863", "405864", "405865", "405866", "405867", "405868", "405869", "405870"
        , "405871", "405872", "405873", "405874"
)
/*open class CardStatusListener {
    open fun cardStatuChange(card: Card) {}
}*/
//wlmark modify org card data struct
//data class Card(val imsi:String?,
//                  val apduDelegate: ApduDelegate,var status: CardStatus)

data class RemoteUimEvent(val slot: Int = UIM_REMOTE_SLOT0,
                          val event: Int,
                          val atr: ByteArray,
                          val errorCode: Int = UIM_REMOTE_CARD_ERROR_NONE,
                          var usage: Int = UIM_REMOTE_USAGE_NORMAL,
                          var transport: Int = UIM_REMOTE_TRANSPORT_IP,
                          val apduTimeout: Int = DEFAULT_APDU_TIMEOUT,
                          val disableAllPolling: Int = DISABLE_POLLING_FALSE,
                          val pollTimer: Int = POLL_TIMER) {
    companion object {
        const val UIM_REMOTE_CONNECTION_UNAVAILABLE = 0
        const val UIM_REMOTE_CONNECTION_AVAILABLE = 1
        const val UIM_REMOTE_CARD_INSERTED = 2
        const val UIM_REMOTE_CARD_REMOVED = 3
        const val UIM_REMOTE_CARD_ERROR = 4
        const val UIM_REMOTE_CARD_RESET = 5
        const val UIM_REMOTE_CONNECT_SOCKET = 6
        const val UIM_REMOTE_DISCONNECT_SOCKET = 7


        const val UIM_REMOTE_SLOT0 = 0;
        const val UIM_REMOTE_SLOT1 = 1;
        const val UIM_REMOTE_SLOT2 = 2;

        //      This param will be non-zero only for UIM_REMOTE_CARD_ERROR event
        const val UIM_REMOTE_CARD_ERROR_NONE = 0;
        const val UIM_REMOTE_CARD_ERROR_UNKNOWN = 1;
        const val UIM_REMOTE_CARD_ERROR_NO_LINK_EST = 2;
        const val UIM_REMOTE_CARD_ERROR_CMD_TIMEOUT = 3;
        const val UIM_REMOTE_CARD_ERROR_POWER_DOWN = 4;

        //  * 	@Transport
        const val UIM_REMOTE_TRANSPORT_OTHER = 0;
        const val UIM_REMOTE_TRANSPORT_BLUETOOTH = 1;
        const val UIM_REMOTE_TRANSPORT_IP = 2;

        //*  @Usage
        const val UIM_REMOTE_USAGE_REDUCED = 0
        const val UIM_REMOTE_USAGE_NORMAL = 1

        //polling time ms
//        const val POLL_TIMER = 180000
        const val POLL_TIMER = (45 * 60 * 1000)

        /* @return*/
        const val UIM_REMOTE_SUCCESS = 0
        const val UIM_REMOTE_ERROR = 1

        const val DISABLE_POLLING_TRUE = 1
        const val DISABLE_POLLING_FALSE = 0

        const val DEFAULT_APDU_TIMEOUT = 14000 //in miliseconds

        const val UIM_REMOTE_APDU_EXCHANGE_SUCCESS = 0
        const val UIM_REMOTE_APDU_EXCHANGE_FAILURE = 1
    }
}