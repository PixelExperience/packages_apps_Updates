LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_CERTIFICATE := platform

LOCAL_MANIFEST_FILE := app/src/main/AndroidManifest.xml

LOCAL_SRC_FILES := $(call all-java-files-under, app/src/main/java)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-design \
    android-support-annotations \
    android-support-v4 \
    volley \
    commons-io-2.6

LOCAL_RESOURCE_DIR := \
    $(TOP)/frameworks/support/v7/appcompat/res \
    $(TOP)/frameworks/support/cardview/res \
    $(TOP)/frameworks/support/design/res \
    $(LOCAL_PATH)/app/src/main/res

LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.appcompat:android.support.v7.cardview:android.support.design

include frameworks/opt/setupwizard/library/common-gingerbread.mk

LOCAL_PACKAGE_NAME := Updates

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_USE_AAPT2 := true

LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DEX_PREOPT := false

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    libs/commons-io-2.6.jar

include $(BUILD_MULTI_PREBUILT)
