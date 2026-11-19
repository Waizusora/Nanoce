#include <jni.h>
#include <tree_sitter/api.h>

#include "nanoce/nanoce.hpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeVersion(
    JNIEnv* env,
    jobject /* thiz */
) {
    const auto version = nanoce::version();
    return env->NewStringUTF(version.data());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeTreeSitterLanguageAbi(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return TREE_SITTER_LANGUAGE_VERSION;
}
