#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_STATIC_ANDROID_LIBRARIES := androidx.core_core \
    androidx.annotation_annotation \
    androidx.appcompat_appcompat \
    androidx.recyclerview_recyclerview \
    androidx.preference_preference \
    com.google.android.material_material

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_SRC_FILES += $(call all-java-files-under, java)

LOCAL_PRODUCT_MODULE := true

LOCAL_REQUIRED_MODULES := privapp_whitelist_org.omnirom.logcat.xml

LOCAL_USE_AAPT2 := true
LOCAL_JAR_EXCLUDE_FILES := none
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_PACKAGE_NAME := MatLog
LOCAL_MODULE_TAGS := optional
LOCAL_PRIVILEGED_MODULE := true

LOCAL_SDK_VERSION := system_current

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_MODULE := privapp_whitelist_org.omnirom.logcat.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/permissions
LOCAL_PRODUCT_MODULE := true
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)
