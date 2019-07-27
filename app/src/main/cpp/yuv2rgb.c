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

//#define ONLY_FILL_COLOR

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_cvtYUV_1420_1888_1to_1RGBA(JNIEnv *env,
                                                                               jobject obj,
                                                                               jobject bitmap,
                                                                               jobject buff_y,
                                                                               jobject buff_u,
                                                                               jobject buff_v) {

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
#ifdef ONLY_FILL_COLOR
    for (uint32_t *p = pixels, *e = p + info.width * info.height; p < e; p++) *p = 0xFF00FF00;
#else
    uint8_t *y = (*env)->GetDirectBufferAddress(env, buff_y);
    uint8_t *u = (*env)->GetDirectBufferAddress(env, buff_u);
    uint8_t *v = (*env)->GetDirectBufferAddress(env, buff_v);

    int width = info.width, height = info.height, width1 = width - 1;

    #pragma omp parallel for
    for (uint32_t i = 0; i < height; i++) {

        uint32_t idx_y = i * width, idx_uv = (i / 2) * (width / 2);
        uint32_t *row_out = pixels + idx_y;
        uint8_t *row_y = y + idx_y;
        uint8_t *row_u = u + idx_uv;
        uint8_t *row_v = v + idx_uv;

        for (uint32_t j = 0; j < width1; j += 2) {
            int Y = *row_y++;
            int U = *row_u++ - 128;
            int V = *row_v++ - 128;

            int32_t R = (int32_t) (Y + 1.140 * V);
            int32_t G = (int32_t) (Y - 0.395 * U - 0.581 * V);
            int32_t B = (int32_t) (Y + 2.032 * U);

            CLAMP(R)
            CLAMP(G)
            CLAMP(B)
            *row_out++ = R | (G << 8) | (B << 16) | 0xFF000000;

            Y = *row_y++;

            R = (int32_t) (Y + 1.140 * V);
            G = (int32_t) (Y - 0.395 * U - 0.581 * V);
            B = (int32_t) (Y + 2.032 * U);

            CLAMP(R)
            CLAMP(G)
            CLAMP(B)
            *row_out++ = R | (G << 8) | (B << 16) | 0xFF000000;
        }
    }
#endif

    AndroidBitmap_unlockPixels(env, bitmap);
}