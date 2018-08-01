//
// Created by chentao on 2016/7/1.
//

#ifndef VSIMDEMO3_1_SOFTSIMADAPTER_H
#define VSIMDEMO3_1_SOFTSIMADAPTER_H
typedef unsigned char byte;

#ifdef __cplusplus
extern "C" {
#endif

#define IN
#define OUT

#define E_SOFTSIM_SUCCESS (0)
#define E_SOFTSIM_PARAM_INVALID (-1)
#define E_SOFTSIM_ERROR (-101)

/* card status 102 - 119*/
#define E_SOFTSIM_CARD_EXIST (-102)
#define E_SOFTSIM_CARD_NOEXIST (-103)
#define E_SOFTSIM_CARD_INSERTED (-104)
#define E_SOFTSIM_CARD_NOINSERTED (-105)
#define E_SOFTSIM_CARD_NOSLOT (-106)
#define E_SOFTSIM_CARD_POWERUP (-107)
#define E_SOFTSIM_CARD_POWERDOWN (-108)
#define E_SOFTSIM_CARD_NOKIPRESENT (-109)

/* file 120 - 139*/
#define E_SOFTSIM_FILE_NOTFOUND (-120)
#define E_SOFTSIM_FILE_OPENFAIL (-121)
#define E_SOFTSIM_FILE_UNZIPFAIL (-122)
#define E_SOFTSIM_FILE_PARSEFAIL (-123)
#define E_SOFTSIM_FILE_PROTOCOL_INVALID (-124)

/* system 140 - 159 */
#define E_SOFTSIM_OPLIMITED (-140)
#define E_SOFTSIM_NOMEMORY (-141)

/**
2.1.	版本管理接口
1.	int softSim_Version(char *version)
描述：获取softsim.so的版本号
输入：无
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败 E_SOFTSIM_ERROR
*/
extern int SoftSim_Version(char *version);
/**
2.2.	调用模块身份认证接口定义
1.	int softSim_GetChallenge(byte *rand,unsigned int *len)
描述：身份认证,获取挑战随机数
输入：无
输出：rand，len
返回值：成功：E_SOFTSIM_SUCCESS；
失败 E_SOFTSIM_ERROR
*/
extern int SoftSim_GetChallenge(byte *rand,unsigned int *len);

/**
2.	int softSim_ChallengeResult(const byte *res,unsigned int len)
描述：身份认证, 返回挑战结果
输入：res，len
输出：
返回值：成功：E_SOFTSIM_SUCCESS；失败 E_SOFTSIM_ERROR
*/
extern int SoftSim_ChanllengeResult(const byte *res,unsigned int len);

/**
2.3.	Soft sim接口定义
1.	int Softsim_insertCard(char *imsi，int *vslot)
描述：插入一张卡，并返回分配的vslot
输入：imsi
输出：分配的vslot
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_INSERTED
E_SOFTSIM_ NO_SLOT
E_SOFTSIM_CARD_NOEXIST
*/
extern int Softsim_InsertCard(const char *imsi,int *vslot);

/**
2.	int Softsim_removeCard(int vslot)
描述：移除一张卡
输入：vslot
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_NOINSERTED
*/
extern int Softsim_RemoveCard(int vslot);

/**
3.	int Softsim_powerUp(int vslot, void *atr，int *atrLength)
描述：给卡上电
输入：vslot
输出：atr，atrLength
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_NOINSERTED
E_SOFTSIM _POWERUP
*/
extern int Softsim_PowerUp(int vslot, void *atr,int *atrLength);

/**
4.	int Softsim_powerDown(int vslot)
描述：给卡断电
输入：vslot,
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_NOINSERTED
：
*/
extern int Softsim_PowerDown(int vslot);

/**
5.	int Softsim_reset(int vslot, void *atr, int *atrLength)
描述：给卡复位
输入：vslot,
输出：atr，atrLength
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_NOINSERTED
E_SOFTSIM _POWERUP
*/
extern int Softsim_Reset(int vslot, void *atr, int *atrLength);

/**
6.	int Softsim_apdu(int vslot，void *cmdApdu, int cmdLength, void *rspApdu, int *rspLength)
描述：给卡发apdu命令
输入：vslot, cmdApdu，cmdLength
输出：rspApdu，rspLength
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_NOINSERTED
E_SOFTSIM _POWERDOWN
E_SOFTSIM_CARD_NOKIPRESENT
*/
extern int Softsim_Apdu(int vslot, void *cmdApdu, int cmdLength, void *rspApdu, int *rspLength);

/**
2.4.	卡管理接口定义
1.	int Softsim_SetDataPath(char *path)
描述：设置数据存放路径(db,bin)
输入：path
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
*/
extern int Softsim_SetDataPath(const char *path);

/**
2.	int Softsim_addCard(char *imsi)
描述：增加一张vsim卡
输入：imsi
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_EXIST
E_SOFTSIM_FILENOFOUND
E_SOFTSIM_NOMEMORY
E_SOFTSIM_FILEOPENFAIL
E_SOFTSIM_FILEUNZIPFAIL
E_SOFTSIM_FILEPROCOTOLINVALID
*/
extern int Softsim_AddCard(const char *imsi);

/**
3.	int Softsim_addSoftCard(char *imsi,byte *ki,int kiLen,byte *opc,int opcLen)
描述：增加一张本地软卡
输入：imsi，softsimImageId,ki，opc  （softsimImageId格式跟imsi一致，前5位为00001）
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_EXIST
E_SOFTSIM_FILENOFOUND
E_SOFTSIM_NOMEMORY
E_SOFTSIM_FILEOPENFAIL
E_SOFTSIM_FILEUNZIPFAIL
E_SOFTSIM_FILEPROCOTOLINVALID
E_SOFTSIM_SOFSIMIMAGEID_INVALID
*/
extern int Softsim_AddSoftCard(char *imsi,char *softsimImageId,byte *ki,int kiLen,byte *opc,int opcLen,char *iccid,byte *msisdn,int msisdnLen);

/**
4.	int Softsim_deleteCard(char *imsi)
描述：删除一张卡
输入：imsi
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
*/
extern int Softsim_DeleteCard(const char *imsi);

/**
5.	int Softsim_queryCard(char *imsi)
描述：查询卡状态
输入：imsi
输出：返回值成功代表卡存在
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_NOEXIST
*/
extern int Softsim_QueryCard(const char *imsi);

/**
6.	int Softsim_getCardCnt(void)
描述：获取总的卡数量
输入：无
输出: 返回值正值为卡数量
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
*/
extern int Softsim_GetCardCnt();

/**
7.	int Softsim_getCardList(char *cardList,unsigned int size)
描述：获取卡列表
输入：cardList，size
输出: 列表格式=卡1;卡2；    //以分号隔开
        卡格式=imsi，卡类型(gsm 1/uicc 2)，sim类型（vsim 0/ Local 1），创建时间，最后使用时间，使用次数               // 如454190020099102,2,1,3676246261,3676246264,1;
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
*/
extern int Softsim_GetCardList(byte vsimType,char *cardList,unsigned int size);

/**
8.	int Softsim_queryCardType(const char *imsi,byte *cardType,byte *vsimType)
描述：查询卡状态
输入：imsi
输出：cardType,vsimType
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_NOEXIST
*/
extern int Softsim_QueryCardType(const char *imsi,byte *cardType,byte *vsimType);

/**
描述：身份认证,获取挑战随机数
输入：无
输出：rand，len
返回值：成功：E_SOFTSIM_SUCCESS；
失败 E_SOFTSIM_ERROR
*/
//int softSim_GetChallenge(byte *rand,unsigned int *len);

/**
描述：查询软卡镜像是否存在
输入：softsimImageId  （格式跟imsi一致，前5位为00001）
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_CARD_EXIST
E_SOFTSIM_FILENOFOUND
E_SOFTSIM_NOMEMORY
E_SOFTSIM_FILEOPENFAIL
E_SOFTSIM_FILEUNZIPFAIL
E_SOFTSIM_FILEPROCOTOLINVALID
*/
int Softsim_SimImageQuery(char *softsimImageId);

/**
描述：设置数据库访问秘钥
输入：pwd1,pwd2
输出
返回值：成功：E_SOFTSIM_SUCCESS；
失败： E_SOFTSIM_ERROR
E_SOFTSIM_OPLIMITED
E_SOFTSIM_DB_PWD_INVALID
*/
void Softsim_SetDbPwd(byte *pwd1,int pwd1Len,byte *pwd2,int pwd2Len);




#ifdef __cplusplus
}
#endif

#endif //VSIMDEMO3_1_SOFTSIMADAPTER_H
