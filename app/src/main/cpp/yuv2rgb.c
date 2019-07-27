#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <omp.h>

#define  LOG_TAG    "libplasma"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define CLAMP(x) if (x > 255) {x = 255;} else if (x < 0) { x = 0;}


JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_cvtYUV_1420_1888_1to_1RGBA(JNIEnv *env,
                                                                               jobject obj,
                                                                               jobject bitmap,
                                                                               jobject buff_y,
                                                                               jobject buff_u,
                                                                               jobject buff_v) {
    int8_t *y = (*env)->GetDirectBufferAddress(env, buff_y);
    int8_t *u = (*env)->GetDirectBufferAddress(env, buff_u);
    int8_t *v = (*env)->GetDirectBufferAddress(env, buff_v);

    AndroidBitmapInfo info;
    uint32_t *pixels;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888 !");
        return;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, (void**) &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    //for (uint32_t *p = pixels, *e = p + info.width * info.height; p < e; p++) *p = 0xFF00FF00;

    int width = info.width, height = info.height;

    #pragma omp parallel for
    for (uint32_t i = 0; i < height; i++) {
        for (uint32_t j = 0; j < width; j++) {
            uint32_t pos1 = i * width + j;
            uint32_t pos2 = (i / 2) * (width / 2) + j / 2;
            int Y = y[pos1] & 0xFF;
            int U = u[pos2] & 0xFF;
            int V = v[pos2] & 0xFF;
            U = U - 128;
            V = V - 128;
            int32_t R, G, B;
            R = (int32_t) (Y + 1.140 * V);
            G = (int32_t) (Y - 0.395 * U - 0.581 * V);
            B = (int32_t) (Y + 2.032 * U);

            CLAMP(R)
            CLAMP(G)
            CLAMP(B)
            pixels[i * width + j] = R | (G << 8) | (B << 16) | 0xFF000000;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}