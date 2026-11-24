package com.example.qlchitieu.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.biometric.BiometricManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Trạng thái "Khóa ứng dụng". Cài đặt lưu theo từng tài khoản (uid), nên đổi tài khoản
 * trên cùng máy không dùng chung cài đặt khóa.
 *
 * Việc mở khóa chỉ được nhớ trong bộ nhớ (không ghi ra đĩa): tắt hẳn app rồi mở lại
 * luôn phải xác thực lại.
 */
public final class AppLock {

    /** Vân tay / khuôn mặt, hoặc mã PIN / hình vẽ / mật khẩu của màn hình khóa thiết bị. */
    public static final int KIEU_XAC_THUC =
            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    public static final long[] MOC_THOI_GIAN = {0L, 30_000L, 60_000L, 5 * 60_000L};
    public static final String[] NHAN_THOI_GIAN = {
            "Ngay khi thoát ứng dụng", "Sau 30 giây", "Sau 1 phút", "Sau 5 phút"
    };

    private static final long MAC_DINH = 30_000L;

    private static final String PREFS = "phinova_security";
    private static final String KEY_BAT = "lock_enabled_";
    private static final String KEY_THOI_GIAN = "lock_timeout_ms_";

    /** uid đã mở khóa trong lần chạy app này (null = đang khóa). */
    private static String uidDaMoKhoa = null;
    private static long thoiDiemRaNen = 0;
    private static boolean manKhoaDangMo = false;

    private AppLock() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    //  Cài đặt
    // ------------------------------------------------------------------

    public static boolean isEnabled(Context context, String uid) {
        return prefs(context).getBoolean(KEY_BAT + uid, false);
    }

    public static void setEnabled(Context context, String uid, boolean bat) {
        prefs(context).edit().putBoolean(KEY_BAT + uid, bat).apply();
    }

    public static long getTimeout(Context context, String uid) {
        return prefs(context).getLong(KEY_THOI_GIAN + uid, MAC_DINH);
    }

    public static void setTimeout(Context context, String uid, long mili) {
        prefs(context).edit().putLong(KEY_THOI_GIAN + uid, mili).apply();
    }

    /** Vị trí của mốc thời gian hiện tại trong MOC_THOI_GIAN (để chọn sẵn trong hộp thoại). */
    public static int chiSoThoiGian(Context context, String uid) {
        long hienTai = getTimeout(context, uid);

        for (int i = 0; i < MOC_THOI_GIAN.length; i++) {
            if (MOC_THOI_GIAN[i] == hienTai) {
                return i;
            }
        }
        return 1;
    }

    /** Thiết bị có vân tay / khuôn mặt hoặc khóa màn hình để dùng không. */
    public static boolean thietBiHoTro(Context context) {
        return BiometricManager.from(context).canAuthenticate(KIEU_XAC_THUC)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    // ------------------------------------------------------------------
    //  Trạng thái khóa trong lúc app chạy
    // ------------------------------------------------------------------

    /** Cần hiện màn hình khóa cho tài khoản này không. */
    public static synchronized boolean canMoKhoa(Context context, String uid) {
        return isEnabled(context, uid) && !uid.equals(uidDaMoKhoa);
    }

    public static synchronized void daMoKhoa(String uid) {
        uidDaMoKhoa = uid;
    }

    /** App vừa ra nền (không còn màn hình nào hiện). */
    public static synchronized void onRaNen() {
        if (uidDaMoKhoa != null) {
            thoiDiemRaNen = SystemClock.elapsedRealtime();
        }
    }

    /** App vừa quay lại từ nền: quá hạn thì khóa lại. */
    public static synchronized void onVaoLai(Context context) {
        FirebaseUser nguoiDung = FirebaseAuth.getInstance().getCurrentUser();

        if (uidDaMoKhoa == null || nguoiDung == null) {
            return;
        }

        long daQua = SystemClock.elapsedRealtime() - thoiDiemRaNen;

        if (daQua >= getTimeout(context, nguoiDung.getUid())) {
            uidDaMoKhoa = null;
        }
    }

    public static synchronized boolean isManKhoaDangMo() {
        return manKhoaDangMo;
    }

    public static synchronized void setManKhoaDangMo(boolean dangMo) {
        manKhoaDangMo = dangMo;
    }
}
