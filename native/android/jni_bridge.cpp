#include <jni.h>

#include "nanoce/nanoce.hpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeVersion(
    JNIEnv* env,
    jobject /* thiz */
) {
    const auto version = nanoce::version();
    return env->NewStringUTF(version.data());
}
