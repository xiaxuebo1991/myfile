//
// Created by chentao on 2016/7/1.
//

#include <assert.h>
#include <stdio.h>
#include <string.h>
//#include <stddef.h>
//#include <unistd.h>
//#include <sys/types.h>
//#include <sys/stat.h>
//#include <fcntl.h>
//#include <errno.h>
//#include <limits.h>

#include <android/log.h>

#include "SoftSimAdapter.h"
#include "com_ucloudlink_framework_remoteuim_SoftSimNative.h"

#define TAG "SoftSimAdapter"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)


extern "C" {
jint Java_com_ucloudlink_framework_remoteuim_SoftSimNative_setDataPath
        (JNIEnv *env, jobject obj, jstring path) {
    const char *str;
    jboolean b;

    str = env->GetStringUTFChars(path, &b);
    if (str == NULL) {
        return -1000; /* OutOfMemoryError already thrown */
    }

    int ret = Softsim_SetDataPath(str);

    env->ReleaseStringUTFChars(path, str);

    return ret;
}

jint Java_com_ucloudlink_framework_remoteuim_SoftSimNative_queryCard
        (JNIEnv *env, jobject obj, jstring imsi) {
    const char *str;
    jboolean b;
    str = env->GetStringUTFChars(imsi, &b);
    if (str == NULL) {
        return -1000; /* OutOfMemoryError already thrown */
    }

    int ret = Softsim_QueryCard(str);

    env->ReleaseStringUTFChars(imsi, str);

    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    addCard
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint
JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_addCard__Ljava_lang_String_2
        (JNIEnv *env, jobject obj, jstring imsi) {
    const char *imsi_ptr;
    jboolean b;
    imsi_ptr = env->GetStringUTFChars(imsi, &b);
    if (imsi_ptr == NULL) {
        return -1000; /* OutOfMemoryError already thrown */
    }

    int ret = Softsim_AddCard(imsi_ptr);

    env->ReleaseStringUTFChars(imsi, imsi_ptr);
    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    addCard
 * Signature: (Ljava/lang/String;[B[B)I
 * (JNIEnv *, jobject, jstring, jstring, jbyteArray, jbyteArray);
 */
JNIEXPORT jint
JNICALL
Java_com_ucloudlink_framework_remoteuim_SoftSimNative_addCard__Ljava_lang_String_2Ljava_lang_String_2Ljava_lang_String_2_3B_3B_3B
        (JNIEnv *env, jobject obj, jstring imsi, jstring imageid,jstring iccid, jbyteArray msisdn,jbyteArray ki, jbyteArray opc) {
    const char *imsi_ptr;
    const char *imageid_ptr;
    const char *iccid_ptr;
    
    jboolean b;
    imsi_ptr = env->GetStringUTFChars(imsi, &b);
    imageid_ptr = env->GetStringUTFChars(imageid, &b);
    iccid_ptr = env->GetStringUTFChars(iccid, &b);
    
    if (imsi_ptr == NULL) {
        return -1000; /* OutOfMemoryError already thrown */
    }

    jbyte *ki_ptr = env->GetByteArrayElements(ki, 0);
    jsize kiLen = env->GetArrayLength(ki); 
    
     jbyte *msisdn_ptr =NULL;
     jsize msisdnLen = 0;
     
    if(msisdn!=NULL){
        msisdn_ptr = env->GetByteArrayElements(msisdn, 0);
        msisdnLen = env->GetArrayLength(msisdn);
    }

    jbyte *opc_ptr = env->GetByteArrayElements(opc, 0);
    jsize opcLen = env->GetArrayLength(opc);
//      (char *imsi,char *softsimImageId,byte *ki,int kiLen,byte *opc,int opcLen,
// char *iccid,byte *msisdn,int msisdnLen);
    int ret = Softsim_AddSoftCard((char *) imsi_ptr, (char *) imageid_ptr, (byte *) ki_ptr,
                                  (int) kiLen, (byte *) opc_ptr, (int) opcLen, (char *)iccid_ptr,  (byte *)msisdn_ptr, (int)msisdnLen);

    env->ReleaseStringUTFChars(imsi, imsi_ptr);
    env->ReleaseStringUTFChars(imageid, imageid_ptr);
    env->ReleaseStringUTFChars(iccid, iccid_ptr);
    env->ReleaseByteArrayElements(ki, ki_ptr, 0);
    
    //env->DeleteLocalRef(ki);

    env->ReleaseByteArrayElements(opc, opc_ptr, 0);
    //env->DeleteLocalRef(opc);
    if(msisdn_ptr!=NULL){
        env->ReleaseByteArrayElements(msisdn, msisdn_ptr, 0);
    }
    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    insertCard
 * Signature: (Ljava/lang/String;[I)I
 */
JNIEXPORT jint JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_insertCard
        (JNIEnv *env, jobject obj, jstring imsi, OUT jintArray vslot) {
    const char *imsi_ptr;
    jboolean b;
    imsi_ptr = env->GetStringUTFChars(imsi, &b);
    if (imsi_ptr == NULL) {
        return -1000; /* OutOfMemoryError already thrown */
    }

    jint * vslot_ptr = env->GetIntArrayElements(vslot, 0);
    jsize vslot_len = env->GetArrayLength(vslot);

    int ret = Softsim_InsertCard(imsi_ptr, (int *) vslot_ptr);

    env->ReleaseStringUTFChars(imsi, imsi_ptr);
    env->ReleaseIntArrayElements(vslot, vslot_ptr, 0);
    //env->DeleteLocalRef(vslot);

    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    removeCard
 * Signature: (I)I
 */
JNIEXPORT jint
JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_removeCard
        (JNIEnv *env, jobject obj, jint vslot) {
    return Softsim_RemoveCard((int) vslot);
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    powerUp
 * Signature: (I[B[I)I
 */
JNIEXPORT jint
JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_powerUp
        (JNIEnv *env, jobject obj, jint vslot, OUT jbyteArray atr, OUT jintArray atrLen) {
    jbyte *atr_ptr = env->GetByteArrayElements(atr, 0);
    //jsize atrLen = env->GetArrayLength(atr);

    jint * atrLen_ptr = env->GetIntArrayElements(atrLen, 0);

    int ret = Softsim_PowerUp((int) vslot, (void *) atr_ptr, (int *) atrLen_ptr);

    env->ReleaseByteArrayElements(atr, atr_ptr, 0);
    env->ReleaseIntArrayElements(atrLen, atrLen_ptr, 0);

    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    powerDown
 * Signature: (I)I
 */
JNIEXPORT jint
JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_powerDown
        (JNIEnv *env, jobject obj, jint vslot) {
    return Softsim_PowerDown((int) vslot);
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    resetCard
 * Signature: (I[B[I)I
 */
JNIEXPORT jint
JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_resetCard
        (JNIEnv *env, jobject obj, jint vslot, jbyteArray atr, jintArray atrLen) {
    jbyte *atr_ptr = env->GetByteArrayElements(atr, 0);
    //jsize atrLen = env->GetArrayLength(atr);

    jint * atrLen_ptr = env->GetIntArrayElements(atrLen, 0);

    int ret = Softsim_Reset((int) vslot, (void *) atr_ptr, (int *) atrLen_ptr);

    env->ReleaseByteArrayElements(atr, atr_ptr, 0);
    env->ReleaseIntArrayElements(atrLen, atrLen_ptr, 0);

    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    apdu
 * Signature: (I[B[B[I)I
 */
JNIEXPORT jint
JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_apdu
        (JNIEnv *env, jobject obj, jint vslot, jbyteArray cmd, OUT jbyteArray rsp, OUT
         jintArray rspLen) {
         
    
    jbyte *cmdApdu = env->GetByteArrayElements(cmd, 0);
    
    jsize cmdLength = env->GetArrayLength(cmd);
    
    jbyte *rspApdu = env->GetByteArrayElements(rsp, 0);
    
    jint * rspLength = env->GetIntArrayElements(rspLen, 0);
    
    int ret = Softsim_Apdu((int) vslot, (void *) cmdApdu, cmdLength, (void *) rspApdu,
                           (int *) rspLength);
    
    env->ReleaseByteArrayElements(cmd, cmdApdu, 0);
    
    env->ReleaseByteArrayElements(rsp, rspApdu, 0);
    
    env->ReleaseIntArrayElements(rspLen, rspLength, 0);
    

    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    deleteCard
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint
JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_deleteCard
        (JNIEnv *env, jobject obj, jstring imsi) {
    const char *imsi_ptr;
    jboolean b;
    imsi_ptr = env->GetStringUTFChars(imsi, &b);
    if (imsi_ptr == NULL) {
        return -1000; /* OutOfMemoryError already thrown */
    }

    int ret = Softsim_DeleteCard(imsi_ptr);

      env->ReleaseStringUTFChars(imsi, imsi_ptr);
      return ret;
  }

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    getCardCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_getCardCount
  (JNIEnv *env, jobject obj)
  {
    return Softsim_GetCardCnt();
  }

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    getCardList
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_getCardList
        (JNIEnv *env, jobject obj, jstring list, jint size) {
    return 0;
  }


jint Java_com_ucloudlink_framework_remoteuim_SoftSimNative_queryCardType
       (JNIEnv *env, jobject obj, jstring imsi,jbyteArray cardType,jbyteArray vsimType)
 {
    const char* str;
    jboolean b;
    str = env->GetStringUTFChars(imsi, &b);
    if(str == NULL) {
        return -1000; /* OutOfMemoryError already thrown */
    }
    jbyte* cardType_ptr = env->GetByteArrayElements(cardType,0);
    jbyte* vsimType_ptr = env->GetByteArrayElements(vsimType,0);

    int ret = Softsim_QueryCardType(str,(byte*)cardType_ptr,(byte*)vsimType_ptr);

    env->ReleaseStringUTFChars(imsi, str);
    env->ReleaseByteArrayElements(cardType, cardType_ptr, 0);
    env->ReleaseByteArrayElements(vsimType, vsimType_ptr, 0);
    return ret;
 }
/*
 * void Softsim_SetDbPwd(byte *pwd1,int pwd1Len,byte *pwd2,int pwd2Len);
 */
JNIEXPORT void JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_setDbPwd
    (JNIEnv *env, jobject obj, jbyteArray pwd1, jbyteArray pwd2){

        jbyte *pwd1_ptr = env->GetByteArrayElements(pwd1, 0);
        jsize pwd1Len = env->GetArrayLength(pwd1);
        
        jbyte *pwd2_ptr = env->GetByteArrayElements(pwd2, 0);
        jsize pwd2Len = env->GetArrayLength(pwd2);

        Softsim_SetDbPwd((byte *) pwd1_ptr,pwd1Len,(byte *) pwd2_ptr,pwd2Len);
        
        env->ReleaseByteArrayElements(pwd1, pwd1_ptr, 0);
        env->ReleaseByteArrayElements(pwd2, pwd2_ptr, 0);
}

JNIEXPORT void JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_simImageQuery
    (JNIEnv *env, jobject obj, jstring imageId){


}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    getChallenge
 * Signature: ([B[I)V
 */
JNIEXPORT jint JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_getChallenge
(JNIEnv *env, jobject obj, jbyteArray rand, jintArray randlen){
    jbyte *rand_ptr = env->GetByteArrayElements(rand, 0);
    jint *randLength = env->GetIntArrayElements(randlen, 0);
    
    int ret =SoftSim_GetChallenge((byte *)rand_ptr,(unsigned int *)randLength);
    
      env->ReleaseByteArrayElements(rand, rand_ptr, 0);
      env->ReleaseIntArrayElements(randlen, randLength, 0);
    return ret;
}

/*
 * Class:     com_ucloudlink_framework_remoteuim_SoftSimNative
 * Method:    challengeResult
 * Signature: ([BI)V
 */
JNIEXPORT jint JNICALL Java_com_ucloudlink_framework_remoteuim_SoftSimNative_challengeResult
(JNIEnv *env, jobject obj, jbyteArray ret, jint retlen){
    jbyte *ret_ptr = env->GetByteArrayElements(ret, 0);
    //jint *retlenth = env->GetIntArrayElements(retlen, 0);
    
    int ret1 =SoftSim_ChanllengeResult((byte *)ret_ptr,retlen);
    
      env->ReleaseByteArrayElements(ret, ret_ptr, 0);
      //env->ReleaseIntArrayElements(retlen, retlenth, 0);
    return ret1;
}

}//extern "C"