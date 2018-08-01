LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := softsim-adapter
LOCAL_SRC_FILES := SoftSimAdapter.cpp
LOCAL_SHARED_LIBRARIES := libsoftsim

LOCAL_LDLIBS := -llog
LOCAL_CFLAGS    += -UNDEBUG
#LOCAL_ALLOW_UNDEFINED_SYMBOLS := true
include $(BUILD_SHARED_LIBRARY)
LOCAL_LDFLAGS += -fuse-ld=bfd

#如果要依赖第三方so 需要对第三方so进行定义！！
include $(CLEAR_VARS)
LOCAL_MODULE := softsim
LOCAL_SRC_FILES := libsoftsim.so
include $(PREBUILT_SHARED_LIBRARY)