#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <omp.h>

#define  LOG_TAG    "libyuv2rgb"
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

    int width = info.width, height = info.height, width1 = width - 1, height1 = height - 1;

    #pragma omp parallel for
    for (uint32_t i = 0; i < height1; i += 2) {

        uint32_t idx_y1 = i * width, idx_y2 = idx_y1 + width, idx_uv = (i / 2) * (width / 2);
        uint32_t *row_out1 = pixels + idx_y1, *row_out2 = pixels + idx_y2;
        uint8_t *row_y1 = y + idx_y1, *row_y2 = y + idx_y2;
        uint8_t *row_u = u + idx_uv;
        uint8_t *row_v = v + idx_uv;

        for (uint32_t j = 0; j < width1; j += 2) {
            float U = (float) *row_u++ - 128;
            float V = (float) *row_v++ - 128;

            float UgVg = -0.395f * U - 0.581f * V;
            float Ub = 2.032f * U;
            float Vr = 1.140f * V;

            float Y = *row_y1++;

            int32_t R = (int32_t) (Y + Vr);
            int32_t G = (int32_t) (Y + UgVg);
            int32_t B = (int32_t) (Y + Ub);

            CLAMP(R)
            CLAMP(G)
            CLAMP(B)

            *row_out1++ = R | (G << 8) | (B << 16) | 0xFF000000;

            Y = *row_y1++;

            R = (int32_t) (Y + Vr);
            G = (int32_t) (Y + UgVg);
            B = (int32_t) (Y + Ub);

            CLAMP(R)
            CLAMP(G)
            CLAMP(B)

            *row_out1++ = R | (G << 8) | (B << 16) | 0xFF000000;

            Y = *row_y2++;

            R = (int32_t) (Y + Vr);
            G = (int32_t) (Y + UgVg);
            B = (int32_t) (Y + Ub);

            CLAMP(R)
            CLAMP(G)
            CLAMP(B)

            *row_out2++ = R | (G << 8) | (B << 16) | 0xFF000000;

            Y = *row_y2++;

            R = (int32_t) (Y + Vr);
            G = (int32_t) (Y + UgVg);
            B = (int32_t) (Y + Ub);

            CLAMP(R)
            CLAMP(G)
            CLAMP(B)

            *row_out2++ = R | (G << 8) | (B << 16) | 0xFF000000;
        }
    }
#endif

    AndroidBitmap_unlockPixels(env, bitmap);
}

/*
https://www.pcmag.com/encyclopedia/term/55166/yuv-rgb-conversion-formulas
   Y = 0.299R + 0.587G + 0.114B
   U = 0.492 (B-Y)
   V = 0.877 (R-Y)

   It can also be represented as:

   Y =  0.299R + 0.587G + 0.114B
   U = -0.147R - 0.289G + 0.436B
   V =  0.615R - 0.515G - 0.100B

   From YUV to RGB

   R = Y + 1.140V
   G = Y - 0.395U - 0.581V
   B = Y + 2.032U
*/
JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_drawYUV(JNIEnv *env, jobject obj,
                                                            jobject bitmap, jint bx, jint by,
                                                            jint w, jint h, jint off, jobject buff_) {

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

    int bw = info.width, bh = info.height;

    uint8_t *buff_y = (*env)->GetDirectBufferAddress(env, buff_);
    uint8_t *buff_u = buff_y + w * h;
    uint8_t *buff_v = buff_u + w * h / 4;

    for(int i = 0; i < bh; i++) {
        for(int j = 0; j < bw; j++) {
            uint32_t pix = pixels[j + i * bw];
            float r = pix & 0xFF;
            float g = (pix >> 8) & 0xFF;
            float b = (pix >> 16) & 0xFF;

            int32_t y = (int32_t) (0.299 * r + 0.587 * g + 0.114 * b);
            int32_t u = (int32_t) (128 + 0.492 * (b - y));
            int32_t v = (int32_t) (128 + 0.877 * (r - y));

            CLAMP(y);
            CLAMP(u);
            CLAMP(v);

            buff_y[j + bx + (i + by) * w] = (uint8_t) (y);
            buff_u[(((j + bx) / 2) + ((i + by) / 2) * (w / 2))] = (uint8_t) (u);
            buff_v[(((j + bx) / 2) + ((i + by) / 2) * (w / 2))] = (uint8_t) (v);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}