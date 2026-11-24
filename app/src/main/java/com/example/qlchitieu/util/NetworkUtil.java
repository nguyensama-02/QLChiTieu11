package com.example.qlchitieu.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Kiểm tra nhanh máy có đang nối mạng không (cần quyền ACCESS_NETWORK_STATE). */
public final class NetworkUtil {

    private NetworkUtil() {
    }

    /**
     * true nếu có kết nối Internet. Không xác định được thì trả về true để vẫn thử gọi,
     * nếu mạng hỏng thì Firebase sẽ báo lỗi và AuthErrorMapper xử lý tiếp.
     */
    public static boolean coMang(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return true;
        }

        Network mang = cm.getActiveNetwork();
        if (mang == null) {
            return false;
        }

        NetworkCapabilities nl = cm.getNetworkCapabilities(mang);
        return nl != null && nl.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
