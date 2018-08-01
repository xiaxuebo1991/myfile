package com.ucloudlink.framework.remoteuim

/**
 * Created by chentao on 2016/6/23.
 */
object SoftSimNative{
    const val E_SOFTSIM_SUCCESS = (0)
    const val E_SOFTSIM_PARAM_INVALID = (-1)
    const val E_SOFTSIM_ERROR = (-101)

    const val MODE_SOFTSIM_UNKONW = 0
    const val MODE_SOFTSIM_GSM = 1
    const val MODE_SOFTSIM_UICC = 2
    const val CARD_VSIM = 0
    const val CARD_SOFTSIM = 1

/* card status 102 - 119*/
    const val E_SOFTSIM_CARD_EXIST = (-102)
    const val E_SOFTSIM_CARD_NOEXIST = (-103)
    const val E_SOFTSIM_CARD_INSERTED = (-104)
    const val E_SOFTSIM_CARD_NOINSERTED = (-105)
    const val E_SOFTSIM_CARD_NOSLOT = (-106)
    const val E_SOFTSIM_CARD_POWERUP = (-107)
    const val E_SOFTSIM_CARD_POWERDOWN = (-108)
    const val E_SOFTSIM_CARD_NOKIPRESENT = (-109)

/* file 120 - 139*/
    const val E_SOFTSIM_FILE_NOTFOUND = (-120)
    const val E_SOFTSIM_FILE_OPENFAIL = (-121)
    const val E_SOFTSIM_FILE_UNZIPFAIL = (-122)
    const val E_SOFTSIM_FILE_PARSEFAIL = (-123)
    const val E_SOFTSIM_FILE_PROTOCOL_INVALID = (-124)

/* system 140 - 159 */
    const val E_SOFTSIM_OPLIMITED = (-140)
    const val E_SOFTSIM_NOMEMORY = (-141)

    external fun insertCard(imsi: String, vslot: IntArray): Int
    external fun removeCard(vslot: Int): Int
    external fun powerUp(vslot: Int, atr: ByteArray, atrLen: IntArray): Int
    external fun powerDown(vslot: Int): Int
    external fun resetCard(vslot: Int, atr: ByteArray, atrLen: IntArray): Int
    external fun apdu(vslot: Int, cmd: ByteArray, response: ByteArray, rspLen: IntArray): Int

    external fun queryCard(imsi: String?): Int
    external fun setDataPath(path: String): Int
    external fun addCard(imsi: String,softSimImageId: String, iccId: String,msisdn: ByteArray?,ki: ByteArray, opc: ByteArray): Int
    external fun addCard(imsi: String?): Int
    external fun deleteCard(imsi: String): Int
    external fun getCardCount(): Int
    external fun getCardList(cardList: String, size: Int): Int
    external fun queryCardType(imsi: String?,cardType: ByteArray,vsimType: ByteArray): Int
    external fun setDbPwd(pwd1:ByteArray,pwd2: ByteArray)//密码长度建议16字节,设置数据库访问密码
    external fun simImageQuery(softSimImageId: String)
    //身份校验
    external fun getChallenge(rand: ByteArray, randLen: IntArray):Int
    external fun challengeResult(ret: ByteArray, randLen: Int):Int
    external fun calcPin(iccid: String, pin: ByteArray):Int
    external fun updateSoftCardImage(imsi: String, softsimImageId: String):Int
}