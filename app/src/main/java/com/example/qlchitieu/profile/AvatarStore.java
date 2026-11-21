package com.example.qlchitieu.profile;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lưu ảnh đại diện trên máy, mỗi tài khoản một file riêng (theo uid) trong bộ nhớ riêng của app.
 * Ảnh được cắt vuông ở giữa, thu về CANH_LUU px rồi lưu JPEG, nên file chỉ vài chục KB.
 *
 * Ảnh KHÔNG được đồng bộ lên cloud: đổi máy hoặc xóa app thì mất.
 */
public final class AvatarStore {

    private static final String THU_MUC = "avatars";

    /** Cạnh ảnh lưu lại (px). */
    private static final int CANH_LUU = 256;

    /** Giải mã ảnh gốc ở cỡ vừa đủ lớn hơn mức này, tránh nạp ảnh nhiều chục megapixel vào RAM. */
    private static final int CANH_GIAI_MA_TOI_THIEU = 512;

    private static final ExecutorService LUONG = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface KetQua {
        /** Được gọi trên main thread. */
        void onXong(boolean thanhCong);
    }

    private AvatarStore() {
    }

    public static File tep(Context context, String uid) {
        File thuMuc = new File(context.getFilesDir(), THU_MUC);
        return new File(thuMuc, "avatar_" + uid + ".jpg");
    }

    public static boolean coAnh(Context context, String uid) {
        return tep(context, uid).exists();
    }

    /** Đọc ảnh đã lưu (nhỏ, giải mã nhanh). Trả null nếu chưa có ảnh hoặc file lỗi. */
    public static Bitmap tai(Context context, String uid) {

        File f = tep(context, uid);

        if (!f.exists()) {
            return null;
        }

        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    public static void xoa(Context context, String uid) {
        File f = tep(context, uid);

        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** Xử lý và lưu ảnh ở luồng nền, báo kết quả trên main thread. */
    public static void luuBatDongBo(Context context, String uid, Uri uri, KetQua ketQua) {

        Context appContext = context.getApplicationContext();

        LUONG.execute(() -> {
            boolean ok = luu(appContext, uid, uri);

            if (ketQua != null) {
                MAIN.post(() -> ketQua.onXong(ok));
            }
        });
    }

    /**
     * Cắt vuông, thu nhỏ và lưu ảnh từ uri. Chạy lâu hơn mức cho phép trên main thread:
     * chỉ gọi từ luồng nền (hoặc dùng luuBatDongBo).
     */
    public static boolean luu(Context context, String uid, Uri uri) {

        ContentResolver cr = context.getContentResolver();

        try {
            // 1) Đọc kích thước ảnh gốc
            BitmapFactory.Options kichThuoc = new BitmapFactory.Options();
            kichThuoc.inJustDecodeBounds = true;

            try (InputStream in = cr.openInputStream(uri)) {
                if (in == null) {
                    return false;
                }
                BitmapFactory.decodeStream(in, null, kichThuoc);
            }

            if (kichThuoc.outWidth <= 0 || kichThuoc.outHeight <= 0) {
                return false;
            }

            // 2) Giải mã ở cỡ vừa đủ
            BitmapFactory.Options giaiMa = new BitmapFactory.Options();
            giaiMa.inSampleSize = tinhSampleSize(
                    Math.min(kichThuoc.outWidth, kichThuoc.outHeight),
                    CANH_GIAI_MA_TOI_THIEU
            );

            Bitmap goc = null;

            try (InputStream in = cr.openInputStream(uri)) {
                if (in == null) {
                    return false;
                }
                goc = BitmapFactory.decodeStream(in, null, giaiMa);
            }

            if (goc == null) {
                return false;
            }

            // 3) Cắt vuông ở giữa, xoay đúng chiều, thu về CANH_LUU
            int canh = Math.min(goc.getWidth(), goc.getHeight());
            int x = (goc.getWidth() - canh) / 2;
            int y = (goc.getHeight() - canh) / 2;

            Matrix matran = new Matrix();
            float tiLe = (float) CANH_LUU / canh;
            matran.postScale(tiLe, tiLe);

            int goc90 = docGocXoay(cr, uri);

            if (goc90 != 0) {
                matran.postRotate(goc90);
            }

            Bitmap vuong = Bitmap.createBitmap(goc, x, y, canh, canh, matran, true);

            if (vuong != goc) {
                goc.recycle();
            }

            // 4) Ghi ra file tạm rồi đổi tên, để lỗi giữa chừng không làm hỏng ảnh cũ
            File dich = tep(context, uid);
            File thuMuc = dich.getParentFile();

            if (thuMuc != null && !thuMuc.exists() && !thuMuc.mkdirs()) {
                vuong.recycle();
                return false;
            }

            File tam = new File(thuMuc, dich.getName() + ".tmp");

            try (FileOutputStream out = new FileOutputStream(tam)) {
                vuong.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }

            vuong.recycle();

            if (!tam.renameTo(dich)) {
                //noinspection ResultOfMethodCallIgnored
                tam.delete();
                return false;
            }

            return true;

        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return false;
        }
    }

    private static int tinhSampleSize(int canhNhoNhat, int mucTieu) {

        int sample = 1;

        while (canhNhoNhat / (sample * 2) >= mucTieu) {
            sample *= 2;
        }

        return sample;
    }

    /** Góc cần xoay (0, 90, 180, 270) theo thông tin EXIF của ảnh. */
    private static int docGocXoay(ContentResolver cr, Uri uri) {

        try (InputStream in = cr.openInputStream(uri)) {

            if (in == null) {
                return 0;
            }

            ExifInterface exif = new ExifInterface(in);

            int huong = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            switch (huong) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }

        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}