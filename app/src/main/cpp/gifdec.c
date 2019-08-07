#include "gifdec.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>


#define  LOG_TAG    "GifJniProvider"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define MIN(A, B) ((A) < (B) ? (A) : (B))
#define MAX(A, B) ((A) > (B) ? (A) : (B))

typedef struct Entry {
    uint16_t length;
    uint16_t prefix;
    uint8_t suffix;
} Entry;

typedef struct Table {
    int bulk;
    int nentries;
    Entry *entries;
} Table;

static uint16_t read_num(FILE *fd) {
    uint8_t bytes[2];
    fread(bytes, 1, 2, fd);
    return bytes[0] + (((uint16_t) bytes[1]) << 8);
}

gd_GIF *gd_open_gif(const int fd, long offset) {
    uint8_t sigver[3];
    uint16_t width, height, depth;
    uint8_t fdsz, bgidx, aspect;
    int gct_sz;
    gd_GIF *gif = NULL;

    FILE *myFile = fdopen(fd, "rb");
    fseek(myFile, offset, SEEK_SET);

    if (myFile == NULL) goto fail;
    // Header
    fread(sigver, 1, 3, myFile);
    if (memcmp(sigver, "GIF", 3) != 0) {
        LOGE("invalid signature\n");
        goto fail;
    }
    fread(sigver, 1, 3, myFile);
    if (memcmp(sigver, "89a", 3) != 0) {
        LOGE("invalid version\n");
        goto fail;
    }
    width = read_num(myFile);
    height = read_num(myFile);
    LOGE("width: %d height %d\n", width, height);

    fread(&fdsz, 1, 1, myFile);
    // Presence of GCT
    if (!(fdsz & 0x80)) {
        LOGE("no global color table\n");
        goto fail;
    }
    // Color Space's Depth
    depth = ((fdsz >> 4) & 7) + 1;
    // Ignore Sort Flag.
    // GCT Size
    gct_sz = 1 << ((fdsz & 0x07) + 1);
    // Background Color Index
    fread(&bgidx, 1, 1, myFile);
    // Aspect Ratio
    fread(&aspect, 1, 1, myFile);
    // Create gd_GIF Structure.
    gif = calloc(1, sizeof(*gif) + 4 * width * height);
    if (!gif) goto fail;
    gif->fd = myFile;
    gif->width = width;
    gif->height = height;
    gif->depth = depth;
    // Read GCT
    gif->gct.size = gct_sz;
    fread(gif->gct.colors, 3, gif->gct.size, myFile);
    gif->palette = &gif->gct;
    gif->bgindex = bgidx;
    gif->canvas = (uint8_t *) &gif[1];
    gif->frame = &gif->canvas[3 * width * height];
    if (gif->bgindex) memset(gif->frame, gif->bgindex, gif->width * gif->height);
    gif->anim_start = ftell(myFile);
    goto ok;
    fail:
    fclose(myFile);
    ok:
    return gif;
}

static void discard_sub_blocks(gd_GIF *gif) {
    uint8_t size;
    do {
        fread(&size, 1, 1, gif->fd);
        fseek(gif->fd, size, SEEK_CUR);
    } while (size);
}

static void read_plain_text_ext(gd_GIF *gif) {
    if (gif->plain_text) {
        uint16_t tx, ty, tw, th;
        uint8_t cw, ch, fg, bg;
        fseek(gif->fd, 1, SEEK_CUR);
        tx = read_num(gif->fd);
        ty = read_num(gif->fd);
        tw = read_num(gif->fd);
        th = read_num(gif->fd);
        fread(&cw, 1, 1, gif->fd);
        fread(&ch, 1, 1, gif->fd);
        fread(&fg, 1, 1, gif->fd);
        fread(&bg, 1, 1, gif->fd);
        long sub_block = ftell(gif->fd);
        gif->plain_text(gif, tx, ty, tw, th, cw, ch, fg, bg);
        fseek(gif->fd, sub_block, SEEK_SET);
    } else {
        // Discard plain text metadata.
        fseek(gif->fd, 13, SEEK_CUR);
    }
    /* Discard plain text sub-blocks. */
    discard_sub_blocks(gif);
}

static void read_graphic_control_ext(gd_GIF *gif) {
    uint8_t rdit;
    /* Discard block size (always 0x04). */
    fseek(gif->fd, 1, SEEK_CUR);
    fread(&rdit, 1, 1, gif->fd);
    gif->gce.disposal = (rdit >> 2) & 3;
    gif->gce.input = rdit & 2;
    gif->gce.transparency = rdit & 1;
    gif->gce.delay = read_num(gif->fd);
    fread(&gif->gce.tindex, 1, 1, gif->fd);
    /* Skip block terminator. */
    fseek(gif->fd, 1, SEEK_CUR);
}

void read_comment_ext(gd_GIF *gif) {
    if (gif->comment) {
        long sub_block = ftell(gif->fd);
        gif->comment(gif);
        fseek(gif->fd, sub_block, SEEK_SET);
    }
    /* Discard comment sub-blocks. */
    discard_sub_blocks(gif);
}

static void read_application_ext(gd_GIF *gif) {
    char app_id[8];
    char app_auth_code[3];

    /* Discard block size (always 0x0B). */
    fseek(gif->fd, 1, SEEK_CUR);
    /* Application Identifier. */
    fread(app_id, 1, 8, gif->fd);
    /* Application Authentication Code. */
    fread(app_auth_code, 1, 3, gif->fd);
    if (!strncmp(app_id, "NETSCAPE", sizeof(app_id))) {
        /* Discard block size (0x03) and constant byte (0x01). */
        fseek(gif->fd, 2, SEEK_CUR);
        gif->loop_count = read_num(gif->fd);
        /* Skip block terminator. */
        fseek(gif->fd, 1, SEEK_CUR);
    } else if (gif->application) {
        long sub_block = ftell(gif->fd);
        gif->application(gif, app_id, app_auth_code);
        fseek(gif->fd, sub_block, SEEK_SET);
        discard_sub_blocks(gif);
    } else {
        discard_sub_blocks(gif);
    }
}

static void read_ext(gd_GIF *gif) {
    uint8_t label;
    fread(&label, 1, 1, gif->fd);
    switch (label) {
        case 0x01:
            read_plain_text_ext(gif);
            break;
        case 0xF9:
            read_graphic_control_ext(gif);
            break;
        case 0xFE:
            read_comment_ext(gif);
            break;
        case 0xFF:
            read_application_ext(gif);
            break;
        default:
            fprintf(stderr, "unknown extension: %02X\n", label);
    }
}

static Table *new_table(int key_size) {
    int key;
    int init_bulk = MAX(1 << (key_size + 1), 0x100);
    Table *table = malloc(sizeof(*table) + sizeof(Entry) * init_bulk);
    if (table) {
        table->bulk = init_bulk;
        table->nentries = (1 << key_size) + 2;
        table->entries = (Entry *) &table[1];
        for (key = 0; key < (1 << key_size); key++)
            table->entries[key] = (Entry) {1, 0xFFF, key};
    }
    return table;
}

/* Add table entry. Return value:
 *  0 on success
 *  +1 if key size must be incremented after this addition
 *  -1 if could not realloc table */
static int add_entry(Table **tablep, uint16_t length, uint16_t prefix, uint8_t suffix) {
    Table *table = *tablep;
    if (table->nentries == table->bulk) {
        table->bulk *= 2;
        table = realloc(table, sizeof(*table) + sizeof(Entry) * table->bulk);
        if (!table) return -1;
        table->entries = (Entry *) &table[1];
        *tablep = table;
    }
    table->entries[table->nentries] = (Entry) {length, prefix, suffix};
    table->nentries++;
    if ((table->nentries & (table->nentries - 1)) == 0) return 1;
    return 0;
}

static uint16_t get_key(gd_GIF *gif, int key_size, uint8_t *sub_len, uint8_t *shift, uint8_t *byte) {
    int bits_read;
    int rpad;
    int frag_size;
    uint16_t key;

    key = 0;
    for (bits_read = 0; bits_read < key_size; bits_read += frag_size) {
        rpad = (*shift + bits_read) % 8;
        if (rpad == 0) {
            /* Update byte. */
            if (*sub_len == 0)
                fread(sub_len, 1, 1, gif->fd); /* Must be nonzero! */
            fread(byte, 1, 1, gif->fd);
            (*sub_len)--;
        }
        frag_size = MIN(key_size - bits_read, 8 - rpad);
        key |= ((uint16_t) ((*byte) >> rpad)) << bits_read;
    }
    /* Clear extra bits to the left. */
    key &= (1 << key_size) - 1;
    *shift = (*shift + key_size) % 8;
    return key;
}

/* Compute output index of y-th input line, in frame of height h. */
static int interlaced_line_index(int h, int y) {
    int p; /* number of lines in current pass */

    p = (h - 1) / 8 + 1;
    if (y < p) /* pass 1 */
        return y * 8;
    y -= p;
    p = (h - 5) / 8 + 1;
    if (y < p) /* pass 2 */
        return y * 8 + 4;
    y -= p;
    p = (h - 3) / 4 + 1;
    if (y < p) /* pass 3 */
        return y * 4 + 2;
    y -= p;
    /* pass 4 */
    return y * 2 + 1;
}

/* Decompress image pixels.
 * Return 0 on success or -1 on out-of-memory (w.r.t. LZW code table). */
static int read_image_data(gd_GIF *gif, int interlace) {
    uint8_t sub_len, shift, byte;
    int init_key_size, key_size, table_is_full;
    int frm_off, str_len, p, x, y;
    uint16_t key, clear, stop;
    int ret;
    Table *table;
    Entry entry;

    fread(&byte, 1, 1, gif->fd);
    key_size = (int) byte;
    long start = ftell(gif->fd);
    discard_sub_blocks(gif);
    long end = ftell(gif->fd);
    fseek(gif->fd, start, SEEK_SET);
    clear = 1 << key_size;
    stop = clear + 1;
    table = new_table(key_size);
    key_size++;
    init_key_size = key_size;
    sub_len = shift = 0;
    key = get_key(gif, key_size, &sub_len, &shift, &byte); /* clear code */
    frm_off = 0;
    ret = 0;
    while (1) {
        if (key == clear) {
            key_size = init_key_size;
            table->nentries = (1 << (key_size - 1)) + 2;
            table_is_full = 0;
        } else if (!table_is_full) {
            ret = add_entry(&table, str_len + 1, key, entry.suffix);
            if (ret == -1) {
                free(table);
                return -1;
            }
            if (table->nentries == 0x1000) {
                ret = 0;
                table_is_full = 1;
            }
        }
        key = get_key(gif, key_size, &sub_len, &shift, &byte);
        if (key == clear) continue;
        if (key == stop) break;
        if (ret == 1) key_size++;
        entry = table->entries[key];
        str_len = entry.length;
        while (1) {
            p = frm_off + entry.length - 1;
            x = p % gif->fw;
            y = p / gif->fw;
            if (interlace) y = interlaced_line_index((int) gif->fh, y);
            gif->frame[(gif->fy + y) * gif->width + gif->fx + x] = entry.suffix;
            if (entry.prefix == 0xFFF) break;
            else entry = table->entries[entry.prefix];
        }
        frm_off += str_len;
        if (key < table->nentries - 1 && !table_is_full)
            table->entries[table->nentries - 1].suffix = entry.suffix;
    }
    free(table);
    fread(&sub_len, 1, 1, gif->fd); /* Must be zero! */
    fseek(gif->fd, end, SEEK_SET);
    return 0;
}

/* Read image.
 * Return 0 on success or -1 on out-of-memory (w.r.t. LZW code table). */
static int read_image(gd_GIF *gif) {
    uint8_t fisrz;
    int interlace;

    /* Image Descriptor. */
    gif->fx = read_num(gif->fd);
    gif->fy = read_num(gif->fd);
    gif->fw = read_num(gif->fd);
    gif->fh = read_num(gif->fd);
    fread(&fisrz, 1, 1, gif->fd);
    interlace = fisrz & 0x40;
    /* Ignore Sort Flag. */
    /* Local Color Table? */
    if (fisrz & 0x80) {
        /* Read LCT */
        gif->lct.size = 1 << ((fisrz & 0x07) + 1);
        fread(gif->lct.colors, 3, gif->lct.size, gif->fd);
        gif->palette = &gif->lct;
    } else
        gif->palette = &gif->gct;
    /* Image Data. */
    return read_image_data(gif, interlace);
}

static void render_frame_argb(gd_GIF *gif, uint32_t *buffer) {
    int offset = gif->fy * gif->width + gif->fx, w = gif->fw, h = gif->fh;
    buffer += offset;
    if (gif->gce.transparency) {
        uint8_t tindex = gif->gce.tindex;
        for (int y = 0; y < h; y++) {
            for (int x = 0, pos = offset; x < w; x++, pos++) {
                uint8_t index = gif->frame[pos];
                uint8_t *color = (index == tindex) ? gif->canvas + pos * 3 : gif->palette->colors + index * 3;
                uint32_t r = (uint32_t) color[0];
                uint32_t g = (uint32_t) color[1];
                uint32_t b = (uint32_t) color[2];
                buffer[x] = 0xFF000000 | r | (g << 8) | (b << 16);
            }
            offset += gif->width;
            buffer += gif->width;
        }
    } else {
        for (int y = 0; y < h; y++) {
            for (int x = 0, pos = offset; x < w; x++, pos++) {
                uint8_t index = gif->frame[pos];
                uint8_t *color = gif->palette->colors + index * 3;
                uint32_t r = (uint32_t) color[0];
                uint32_t g = (uint32_t) color[1];
                uint32_t b = (uint32_t) color[2];
                buffer[x] = 0xFF000000 | r | (g << 8) | (b << 16);
            }
            offset += gif->width;
            buffer += gif->width;
        }
    }
}

static void render_frame_rect(gd_GIF *gif, uint8_t *buffer) {
    int i = gif->fy * gif->width + gif->fx;
    for (int j = 0; j < gif->fh; j++) {
        for (int k = 0; k < gif->fw; k++) {
            uint8_t index = gif->frame[(gif->fy + j) * gif->width + gif->fx + k];
            uint8_t *color = &gif->palette->colors[index * 3];
            if (!gif->gce.transparency || index != gif->gce.tindex)
                memcpy(buffer + (i + k) * 3, color, 3);
        }
        i += gif->width;
    }
}

static void dispose(gd_GIF *gif) {
    int i, j, k;
    uint8_t *bgcolor;
    switch (gif->gce.disposal) {
        case 2: /* Restore to background color. */
            bgcolor = &gif->palette->colors[gif->bgindex * 3];
            i = gif->fy * gif->width + gif->fx;
            for (j = 0; j < gif->fh; j++) {
                for (k = 0; k < gif->fw; k++)
                    memcpy(&gif->canvas[(i + k) * 3], bgcolor, 3);
                i += gif->width;
            }
            break;
        case 3: /* Restore to previous, i.e., don't update canvas.*/
            break;
        default:
            /* Add frame non-transparent pixels to canvas. */
            render_frame_rect(gif, gif->canvas);
    }
}

/* Return 1 if got a frame; 0 if got GIF trailer; -1 if error. */
int gd_get_frame(gd_GIF *gif) {
    char sep;
    dispose(gif);
    fread(&sep, 1, 1, gif->fd);
    while (sep != ',') {
        if (sep == ';') return 0;
        if (sep == '!') read_ext(gif);
        else return -1;
        fread(&sep, 1, 1, gif->fd);
    }
    if (read_image(gif) == -1) return -1;
    return 1;
}

void gd_rewind(gd_GIF *gif) {
    fseek(gif->fd, gif->anim_start, SEEK_SET);
}

void gd_close_gif(gd_GIF *gif) {
    fclose(gif->fd);
    free(gif);
}

gd_GIF *data;


JNIEXPORT jint JNICALL
Java_com_slava_noffmpeg_frameproviders_GifFramesProvider_getWidth(JNIEnv *env, jobject obj) {
    return (jint) data->width;
}

JNIEXPORT jint JNICALL
Java_com_slava_noffmpeg_frameproviders_GifFramesProvider_getHeight(JNIEnv *env, jobject obj) {
    return (jint) data->height;
}

JNIEXPORT jint JNICALL
Java_com_slava_noffmpeg_frameproviders_GifFramesProvider_fillNextBitmap(JNIEnv *env, jobject obj, jobject bitmap) {
    AndroidBitmapInfo info;
    uint32_t *pixels;
    int ret = -1;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return ret;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888 !");
        return ret;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, (void **) &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    ret = gd_get_frame(data);
    if (ret == -1) return ret;
    render_frame_argb(data, pixels);
    if (ret == 0) gd_rewind(data);
    return ret;
}

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_frameproviders_GifFramesProvider_openGifFd(JNIEnv *env, jclass type, jobject fd_, jlong off, jlong len) {
    jclass fdClass = (*env)->FindClass(env, "java/io/FileDescriptor");
    if (fdClass != NULL) {
        jfieldID fdClassDescriptorFieldID = (*env)->GetFieldID(env, fdClass, "descriptor", "I");

        if (fdClassDescriptorFieldID != NULL && fd_ != NULL) {
            if (data) {
                fclose(data->fd);
                free(data);
                data = NULL;
            }
            int fd = (*env)->GetIntField(env, fd_, fdClassDescriptorFieldID);
            int myfd = dup(fd);
            data = gd_open_gif(myfd, off);
        }
    }
}

JNIEXPORT void JNICALL
Java_com_slava_noffmpeg_frameproviders_GifFramesProvider_closeGifFd(JNIEnv *env, jclass type) {
    gd_close_gif(data);
    data = NULL;
}