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
#define CLAMP18(x) if (x > 0x3FC0000) {x = 0x3FC0000;} else if (x < 0) { x = 0;}

//#define ONLY_FILL_COLOR

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_convert_1YUV420Planar_1to_1RGBA(JNIEnv *env, jobject obj, jobject bitmap, jobject buff_y) {

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

    int width = info.width, height = info.height, width1 = width - 1, height1 = height - 1;

    uint8_t *y = (*env)->GetDirectBufferAddress(env, buff_y);
    uint8_t *u = y + width * height;
    uint8_t *v = u + width * height / 4;

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

    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_convert_1YUV420SemiPlanar_1to_1RGBA(JNIEnv *env, jclass type, jobject bitmap, jobject buff_y) {

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

    int width = info.width, height = info.height, width1 = width - 1, height1 = height - 1;

    uint8_t *y = (*env)->GetDirectBufferAddress(env, buff_y);
    uint8_t *uv = y + width * height;

#pragma omp parallel for
    for (uint32_t i = 0; i < height1; i += 2) {

        uint32_t idx_y1 = i * width, idx_y2 = idx_y1 + width, idx_uv = (i / 2) * (width / 2);
        uint32_t *row_out1 = pixels + idx_y1, *row_out2 = pixels + idx_y2;
        uint8_t *row_y1 = y + idx_y1, *row_y2 = y + idx_y2;
        uint8_t *row_uv = uv + idx_uv * 2;
        //uint8_t *row_v = v + idx_uv;

        for (uint32_t j = 0; j < width1; j += 2) {
            float U = (float) *row_uv++ - 128;
            float V = (float) *row_uv++ - 128;

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

//Это не оптимизированный метод для рисования RGB изображений поверх YUV420 буффера, как отправная точка для дальнейшей доработки
JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_drawRGBoverYUV(JNIEnv *env, jobject obj, jobject bitmap, jint bx, jint by, jint w, jint h, jint off, jobject buff_) {

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

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_drawYUVAoverYUV420Planar(JNIEnv *env, jobject obj, jobject src , jobject dst, jint px, jint py, jint imgw, jint imgh, jint w, jint h) {

    uint8_t *src_y = (*env)->GetDirectBufferAddress(env, src);

    int imgw_half = imgw / 2, imgh_half = imgh / 2;
    uint8_t *src_u = src_y + imgw * imgh;
    uint8_t *src_v = src_u + imgw_half * imgh_half;
    uint8_t *src_a = src_v + imgw_half * imgh_half;

    uint8_t *dst_y = (*env)->GetDirectBufferAddress(env, dst);
    uint8_t *dst_u = dst_y + w * h;
    uint8_t *dst_v = dst_u + w * h / 4;

    for(int i = 0; i < imgh_half; i++) {
        uint8_t *dst_y_line0 = dst_y + px + (i * 2 + py) * w, *dst_y_line1 = dst_y_line0 + w;
        uint8_t *src_y_line0 = src_y + i * 2 * imgw, *src_y_line1 = src_y_line0 + imgw;

        int quartPositionSrc = i * imgw_half;
        int quartPositionDst = (px / 2) + (i + py / 2) * w / 2;

        uint8_t *src_u_line = src_u + quartPositionSrc;
        uint8_t *dst_u_line = dst_u + quartPositionDst;
        uint8_t *src_v_line = src_v + quartPositionSrc;
        uint8_t *dst_v_line = dst_v + quartPositionDst;
        uint8_t *src_a_line = src_a + quartPositionSrc;

        for(int j = 0; j < imgw_half; j++) {
            uint32_t a = *src_a_line, ra = 255 - a;
            *dst_u_line = (uint8_t) ((((uint32_t) *src_u_line) * a + ((uint32_t) *dst_u_line) * ra) >> 8);
            *dst_v_line = (uint8_t) ((((uint32_t) *src_v_line) * a + ((uint32_t) *dst_v_line) * ra) >> 8);
            src_u_line++;
            dst_u_line++;
            src_v_line++;
            dst_v_line++;
            src_a_line++;

            *dst_y_line0 = (uint8_t) ((((uint32_t) *src_y_line0) * a + ((uint32_t) *dst_y_line0) * ra) >> 8);
            dst_y_line0++;
            src_y_line0++;
            *dst_y_line0 = (uint8_t) ((((uint32_t) *src_y_line0) * a + ((uint32_t) *dst_y_line0) * ra) >> 8);
            dst_y_line0++;
            src_y_line0++;
            *dst_y_line1 = (uint8_t) ((((uint32_t) *src_y_line1) * a + ((uint32_t) *dst_y_line1) * ra) >> 8);
            dst_y_line1++;
            src_y_line1++;
            *dst_y_line1 = (uint8_t) ((((uint32_t) *src_y_line1) * a + ((uint32_t) *dst_y_line1) * ra) >> 8);
            dst_y_line1++;
            src_y_line1++;
        }
    }
}

#define GETR(x) ((x & 0xFF))
#define GETG(x) (((x >> 8) & 0xFF))
#define GETB(x) (((x >> 16) & 0xFF))
#define GETA(x) (((x >> 24) & 0xFF))

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_bitmapRGBA8888toYUV420Planar(JNIEnv *env, jobject obj, jobject bmp, jobject yuva, jint w, jint h, jboolean toRight, jboolean toBottom) {

    AndroidBitmapInfo info;
    uint32_t *pixels;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bmp, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888 !");
        return;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bmp, (void**) &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    uint8_t *buff_y = (*env)->GetDirectBufferAddress(env, yuva);
    uint8_t *buff_u = buff_y + w * h;
    uint8_t *buff_v = buff_u + w * h / 4;
    uint8_t *buff_a = buff_v + w * h / 4;

    for(int i = 0; i < h - 1; i += 2) {

        int position = i * w;
        int quartPosition = (i / 2) * (w / 2);

        uint32_t *src_line0   = pixels + position, *src_line1 = src_line0 + w;
        uint8_t  *dst_y_line0 = buff_y + position, *dst_y_line1 = dst_y_line0 + w;
        uint8_t  *dst_v_line  = buff_v + quartPosition;
        uint8_t  *dst_u_line  = buff_u + quartPosition;
        uint8_t  *dst_a_line  = buff_a + quartPosition;

        for(int j = 0; j < w - 1; j += 2) {

            uint32_t srcpix0 = *src_line0++;
            uint32_t srcpix1 = *src_line0++;
            uint32_t srcpix2 = *src_line1++;
            uint32_t srcpix3 = *src_line1++;

            int32_t r0 = GETR(srcpix0), g0 = GETG(srcpix0), b0 = GETB(srcpix0);
            int32_t r1 = GETR(srcpix1), g1 = GETG(srcpix1), b1 = GETB(srcpix1);
            int32_t r2 = GETR(srcpix2), g2 = GETG(srcpix2), b2 = GETB(srcpix2);
            int32_t r3 = GETR(srcpix3), g3 = GETG(srcpix3), b3 = GETB(srcpix3);

            int32_t r_sum = (r0 + r1 + r2 + r3);
            int32_t g_sum = (g0 + g1 + g2 + g3);
            int32_t b_sum = (b0 + b1 + b2 + b3);

            int32_t u_mid = (0x2000000 -  9634 * r_sum - 18940 * g_sum + 28574 * b_sum);
            int32_t v_mid = (0x2000000 + 40305 * r_sum - 33751 * g_sum -  6554 * b_sum) ;
            //(u_mid >> 18) гарантировано вписывается в 0 < x < 255, поэтому его ограничивать не нужно
            CLAMP18(v_mid);

            *dst_u_line++ = (uint8_t) (u_mid >> 18);
            *dst_v_line++ = (uint8_t) (v_mid >> 18);

            *dst_a_line++  = (uint8_t) ((GETA(srcpix0) + GETA(srcpix1) + GETA(srcpix2) + GETA(srcpix3)) >> 2);
            *dst_y_line0++ = (uint8_t) ((r0 * 19595 + g0 * 38470 + b0 * 7471) >> 16);
            *dst_y_line0++ = (uint8_t) ((r1 * 19595 + g1 * 38470 + b1 * 7471) >> 16);
            *dst_y_line1++ = (uint8_t) ((r2 * 19595 + g2 * 38470 + b2 * 7471) >> 16);
            *dst_y_line1++ = (uint8_t) ((r3 * 19595 + g3 * 38470 + b3 * 7471) >> 16);
        }
    }

    AndroidBitmap_unlockPixels(env, bmp);
}

//https://wiki.videolan.org/YUV
JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_bitmapRGBA8888toYUV420SemiPlanar(JNIEnv *env, jclass type, jobject bmp, jobject yuva, jint w, jint h, jboolean toRight, jboolean toBottom) {

    AndroidBitmapInfo info;
    uint32_t *pixels;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bmp, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888 !");
        return;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bmp, (void**) &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    uint8_t *buff_y = (*env)->GetDirectBufferAddress(env, yuva);
    uint8_t *buff_uv = buff_y + w * h;
    uint8_t *buff_a = buff_uv + w * h / 2;

    //For 1 NV12 pixel: YYYYYYYY UVUV

    for(int i = 0; i < h - 1; i += 2) {

        int position = i * w;
        int quartPosition = (i / 2) * (w / 2);

        uint32_t *src_line0   = pixels + position, *src_line1 = src_line0 + w;
        uint8_t  *dst_y_line0 = buff_y + position, *dst_y_line1 = dst_y_line0 + w;
        uint8_t  *dst_uv_line  = buff_uv + quartPosition * 2;
        uint8_t  *dst_a_line  = buff_a + quartPosition;

        for(int j = 0; j < w - 1; j += 2) {

            uint32_t srcpix0 = *src_line0++;
            uint32_t srcpix1 = *src_line0++;
            uint32_t srcpix2 = *src_line1++;
            uint32_t srcpix3 = *src_line1++;

            int32_t r0 = GETR(srcpix0), g0 = GETG(srcpix0), b0 = GETB(srcpix0);
            int32_t r1 = GETR(srcpix1), g1 = GETG(srcpix1), b1 = GETB(srcpix1);
            int32_t r2 = GETR(srcpix2), g2 = GETG(srcpix2), b2 = GETB(srcpix2);
            int32_t r3 = GETR(srcpix3), g3 = GETG(srcpix3), b3 = GETB(srcpix3);

            int32_t r_sum = (r0 + r1 + r2 + r3);
            int32_t g_sum = (g0 + g1 + g2 + g3);
            int32_t b_sum = (b0 + b1 + b2 + b3);

            int32_t u_mid = (0x2000000 -  9634 * r_sum - 18940 * g_sum + 28574 * b_sum);
            int32_t v_mid = (0x2000000 + 40305 * r_sum - 33751 * g_sum -  6554 * b_sum) ;
            //(u_mid >> 18) гарантировано вписывается в 0 < x < 255, поэтому его ограничивать не нужно
            CLAMP18(v_mid);

            *dst_uv_line++ = (uint8_t) (u_mid >> 18);
            *dst_uv_line++ = (uint8_t) (v_mid >> 18);

            *dst_a_line++  = (uint8_t) ((GETA(srcpix0) + GETA(srcpix1) + GETA(srcpix2) + GETA(srcpix3)) >> 2);
            *dst_y_line0++ = (uint8_t) ((r0 * 19595 + g0 * 38470 + b0 * 7471) >> 16);
            *dst_y_line0++ = (uint8_t) ((r1 * 19595 + g1 * 38470 + b1 * 7471) >> 16);
            *dst_y_line1++ = (uint8_t) ((r2 * 19595 + g2 * 38470 + b2 * 7471) >> 16);
            *dst_y_line1++ = (uint8_t) ((r3 * 19595 + g3 * 38470 + b3 * 7471) >> 16);
        }
    }

    AndroidBitmap_unlockPixels(env, bmp);
}

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_mediaworkers_VideoProcessor_drawYUVAoverYUV420Semilanar(JNIEnv *env, jobject obj, jobject src , jobject dst, jint px, jint py, jint imgw, jint imgh, jint w, jint h) {

    uint8_t *src_y = (*env)->GetDirectBufferAddress(env, src);
    int imgw_half = imgw / 2, imgh_half = imgh / 2;
    uint8_t *src_uv = src_y + imgw * imgh;
    uint8_t *src_a = src_uv + imgw_half * imgh_half * 2;
    uint8_t *dst_y = (*env)->GetDirectBufferAddress(env, dst);
    uint8_t *dst_uv = dst_y + w * h;

    for(int i = 0; i < imgh_half; i++) {
        uint8_t *dst_y_line0 = dst_y + px + (i * 2 + py) * w, *dst_y_line1 = dst_y_line0 + w;
        uint8_t *src_y_line0 = src_y + i * 2 * imgw, *src_y_line1 = src_y_line0 + imgw;

        int quartPositionSrc = i * imgw_half;
        int quartPositionDst = (px / 2) + (i + py / 2) * w / 2;

        uint8_t *src_uv_line = src_uv + quartPositionSrc * 2;
        uint8_t *dst_uv_line = dst_uv + quartPositionDst * 2;
        uint8_t *src_a_line = src_a + quartPositionSrc;

        for(int j = 0; j < imgw_half; j++) {
            uint32_t a = *src_a_line, ra = 255 - a;
            *dst_uv_line = (uint8_t) ((((uint32_t) *src_uv_line) * a + ((uint32_t) *dst_uv_line) * ra) >> 8);
            src_uv_line++;
            *dst_uv_line = (uint8_t) ((((uint32_t) *src_uv_line) * a + ((uint32_t) *dst_uv_line) * ra) >> 8);
            src_uv_line++;
            src_a_line++;

            *dst_y_line0 = (uint8_t) ((((uint32_t) *src_y_line0) * a + ((uint32_t) *dst_y_line0) * ra) >> 8);
            dst_y_line0++;
            src_y_line0++;
            *dst_y_line0 = (uint8_t) ((((uint32_t) *src_y_line0) * a + ((uint32_t) *dst_y_line0) * ra) >> 8);
            dst_y_line0++;
            src_y_line0++;
            *dst_y_line1 = (uint8_t) ((((uint32_t) *src_y_line1) * a + ((uint32_t) *dst_y_line1) * ra) >> 8);
            dst_y_line1++;
            src_y_line1++;
            *dst_y_line1 = (uint8_t) ((((uint32_t) *src_y_line1) * a + ((uint32_t) *dst_y_line1) * ra) >> 8);
            dst_y_line1++;
            src_y_line1++;
        }
    }

}