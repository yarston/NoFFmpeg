package com.slava.noffmpeg.mediaworkers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.Image;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.Type;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cc.eevee.turbo.ui.widget.hardware.ScriptC_yuv420888;

public class VideoProcessor {

    private final Size mSize;
    private List<Bitmap> mBitmaps = new ArrayList<>();
    private final int IMG_WIDTH = 100;
    private final int IMG_HEIGHT = 100;
    private Bitmap mImageBuffer;
    private Paint mPaint = new Paint();

    public VideoProcessor(List<String> imagePathes, Size size) {
        mSize = size;
        mImageBuffer = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
        for (String path : imagePathes)
            mBitmaps.add(Bitmap.createBitmap(BitmapFactory.decodeFile(path), 0, 0, IMG_WIDTH, IMG_HEIGHT));
    }

    public void process(Canvas canvas, Image img) {
        Image.Plane[] planes = img.getPlanes();
        cvtYUV_420_888_to_RGBA(mImageBuffer, planes[0].getBuffer(), planes[1].getBuffer(), planes[2].getBuffer());
        canvas.drawBitmap(mImageBuffer, 0, 0, mPaint);

        for(int i = 0; i < mBitmaps.size(); i++) {
            canvas.drawBitmap(mBitmaps.get(i), (i / 2 == 0) ? 0 : mSize.width - IMG_WIDTH, (i % 2 == 0) ? 0 : mSize.height - IMG_HEIGHT, mPaint);
        }
    }

    private static native void cvtYUV_420_888_to_RGBA(Bitmap  bitmap, ByteBuffer buff_y, ByteBuffer buff_u, ByteBuffer buff_v);

    static {
        System.loadLibrary("yuv2rgb");
    }

    // 100frames - 57sec
    public static Bitmap YUV_420_888_toRGB_Java(Context ctx, Image image, int width, int height) {
        // Get the three image planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] y = new byte[buffer.remaining()];
        buffer.get(y);

        buffer = planes[1].getBuffer();
        byte[] u = new byte[buffer.remaining()];
        buffer.get(u);

        buffer = planes[2].getBuffer();
        byte[] v = new byte[buffer.remaining()];
        buffer.get(v);

        int[] ImageRGB = new int[width * height];

        for (int i = 0; i < height - 1; i++) {
            for (int j = 0; j < width; j++) {
                int Y = y[i * width + j] & 0xFF;
                int U = u[(i / 2) * (width / 2) + j / 2] & 0xFF;
                int V = v[(i / 2) * (width / 2) + j / 2] & 0xFF;
                U = U - 128;
                V = V - 128;
                int R, G, B;
                R = (int) (Y + 1.140 * V);
                G = (int) (Y - 0.395 * U - 0.581 * V);
                B = (int) (Y + 2.032 * U);
                if (R > 255) {
                    R = 255;
                } else if (R < 0) {
                    R = 0;
                }
                if (G > 255) {
                    G = 255;
                } else if (G < 0) {
                    G = 0;
                }
                if (B > 255) {
                    R = 255;
                } else if (B < 0) {
                    B = 0;
                }
                ImageRGB[i * width + j] = R | (G << 8) | (B << 16) | 0xFF000000;
            }
        }
        return Bitmap.createBitmap(ImageRGB, width, height, Bitmap.Config.ARGB_8888);
    }

    public static Bitmap YUV_420_888_toRGB(Context ctx, Image image, int width, int height) {
        // Get the three image planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] y = new byte[buffer.remaining()];
        buffer.get(y);

        buffer = planes[1].getBuffer();
        byte[] u = new byte[buffer.remaining()];
        buffer.get(u);

        buffer = planes[2].getBuffer();
        byte[] v = new byte[buffer.remaining()];
        buffer.get(v);

        // get the relevant RowStrides and PixelStrides
        // (we know from documentation that PixelStride is 1 for y)
        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();  // we know from documentation that RowStride is the same for u and v.
        int uvPixelStride = planes[1].getPixelStride();  // we know from documentation that PixelStride is the same for u and v.

        // rs creation just for demo. Create rs just once in onCreate and use it again.
        RenderScript rs = RenderScript.create(ctx);
        ScriptC_yuv420888 mYuv420 = new ScriptC_yuv420888(rs);

        // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
        // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
        Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));
        typeUcharY.setX(yRowStride).setY(height);
        Allocation yAlloc = Allocation.createTyped(rs, typeUcharY.create());
        yAlloc.copyFrom(y);
        mYuv420.set_ypsIn(yAlloc);

        Type.Builder typeUcharUV = new Type.Builder(rs, Element.U8(rs));
        // note that the size of the u's and v's are as follows:
        //      (  (width/2)*PixelStride + padding  ) * (height/2)
        // =    (RowStride                          ) * (height/2)
        // but I noted that on the S7 it is 1 less...
        typeUcharUV.setX(u.length);
        Allocation uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        uAlloc.copyFrom(u);
        mYuv420.set_uIn(uAlloc);

        Allocation vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        vAlloc.copyFrom(v);
        mYuv420.set_vIn(vAlloc);

        // handover parameters
        mYuv420.set_picWidth(width);
        mYuv420.set_uvRowStride(uvRowStride);
        mYuv420.set_uvPixelStride(uvPixelStride);

        Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Allocation outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

        Script.LaunchOptions lo = new Script.LaunchOptions();
        lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
        lo.setY(0, height);

        mYuv420.forEach_doConvert(outAlloc, lo);
        outAlloc.copyTo(outBitmap);

        return outBitmap;
    }
}
