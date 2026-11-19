package com.example.qlchitieu.data.repository;

import android.content.Context;

/**
 * TẠM THỜI: kéo ví, giao dịch và ngân sách từ Firestore về SQLite trong một lần gọi.
 *
 * Dùng cho các màn hình chỉ đọc (Dashboard, Ngân sách, Thống kê) để dữ liệu cũ tạo trước khi có
 * SQLite vẫn hiện ra. Khi viết xong SyncManager: xóa file này và thay các chỗ gọi
 * bằng SyncManager.syncNow().
 *
 * Gọi lại onDone đúng một lần, trên main thread, sau khi cả ba lần kéo đều có kết quả.
 * thanhCong = false nếu có ít nhất một lần thất bại (ví dụ mất mạng); dữ liệu SQLite vẫn dùng được.
 */
public final class LegacyPull {

    public interface Callback {
        void onDone(boolean thanhCong, String loi);
    }

    private LegacyPull() {
    }

    public static void pullAll(Context context, String userId, Callback callback) {

        // Listener của Firestore chạy trên main thread nên không cần đồng bộ hóa các biến này.
        final int[] conLai = {3};
        final boolean[] tatCaThanhCong = {true};
        final String[] loiDauTien = {null};

        Runnable xong = () -> {
            conLai[0]--;
            if (conLai[0] == 0) {
                callback.onDone(tatCaThanhCong[0], loiDauTien[0]);
            }
        };

        new WalletRepository(context).pullFromCloud(userId, (thanhCong, loi) -> {
            ghiNhan(thanhCong, loi, tatCaThanhCong, loiDauTien);
            xong.run();
        });

        new TransactionRepository(context).pullFromCloud(userId, (thanhCong, loi) -> {
            ghiNhan(thanhCong, loi, tatCaThanhCong, loiDauTien);
            xong.run();
        });

        new BudgetRepository(context).pullFromCloud(userId, (thanhCong, loi) -> {
            ghiNhan(thanhCong, loi, tatCaThanhCong, loiDauTien);
            xong.run();
        });
    }

    private static void ghiNhan(boolean thanhCong, String loi,
                                boolean[] tatCaThanhCong, String[] loiDauTien) {
        if (!thanhCong) {
            tatCaThanhCong[0] = false;
            if (loiDauTien[0] == null) {
                loiDauTien[0] = loi;
            }
        }
    }
}
