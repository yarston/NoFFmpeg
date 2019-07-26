#include <jni.h>
#include <time.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>

#define  LOG_TAG    "libplasma"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)


JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_cvtYUV_1420_1888_1to_1RGBA(JNIEnv *env,
                                                                               jobject obj,
                                                                               jobject bitmap,
                                                                               jobject buff_y,
                                                                               jobject buff_u,
                                                                               jobject buff_v) {
    AndroidBitmapInfo info;
    void *pixels;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888 !");
        return;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    for (uint32_t *p = pixels, *e = p + info.width * info.height; p < e; p++) *p = 0xFF00FF00;
    AndroidBitmap_unlockPixels(env, bitmap);
}