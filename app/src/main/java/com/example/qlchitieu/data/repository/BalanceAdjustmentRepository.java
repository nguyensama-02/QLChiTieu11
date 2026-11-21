package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.BalanceAdjustmentDao;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.BalanceAdjustment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.Date;
import java.util.List;

/**
 * Lịch sử điều chỉnh số dư: đọc lịch sử và thực hiện điều chỉnh số dư.
 * Chỉ ghi vào SQLite (đánh dấu PENDING) rồi nhờ SyncManager đẩy lên Firestore ở phía sau.
 */
public class BalanceAdjustmentRepository {

    public interface Callback {
        void onDone(boolean thanhCong, String loi);
    }

    private final Context appContext;
    private final BalanceAdjustmentDao dao;
    private final FirebaseFirestore firestore;

    public BalanceAdjustmentRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = new BalanceAdjustmentDao(context);
        firestore = FirebaseFirestore.getInstance();
    }

    /** Nhờ SyncManager đẩy các thay đổi PENDING lên cloud (chạy nền). */
    private void dongBo() {
        SyncManager.getInstance(appContext).syncNow();
    }

    /** Đọc lịch sử điều chỉnh của một ví từ SQLite (mới nhất lên đầu). */
    public List<BalanceAdjustment> getAdjustments(String userId, String maVi) {
        return dao.getByWallet(userId, maVi);
    }

    /**
     * Kéo lịch sử điều chỉnh của một ví từ Firestore về SQLite (do SyncManager gọi).
     * Dùng Source.SERVER nên khi offline sẽ thất bại và không đổi gì ở máy.
     */
    public void pullFromCloud(String userId, String maVi, Callback callback) {
        firestore.collection("users")
                .document(userId)
                .collection("balance_adjustments")
                .whereEqualTo("maVi", maVi)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {

                        Long soDuCu = doc.getLong("soDuCu");
                        Long soDuMoi = doc.getLong("soDuMoi");
                        Long chenhLech = doc.getLong("chenhLech");

                        if (soDuCu == null || soDuMoi == null || chenhLech == null) {
                            continue;
                        }

                        Date ngay = doc.getDate("ngayDieuChinh");

                        dao.upsertFromCloud(userId, new BalanceAdjustment(
                                doc.getId(),
                                maVi,
                                doc.getString("lyDo"),
                                soDuCu,
                                soDuMoi,
                                chenhLech,
                                ngay));
                    }
                    callback.onDone(true, null);
                })
                .addOnFailureListener(e -> callback.onDone(false, e.getMessage()));
    }

    /**
     * Điều chỉnh số dư ví: ghi SQLite (số dư + lịch sử cùng một transaction, chạy được khi offline).
     * SyncManager sẽ đẩy cả số dư ví lẫn lịch sử lên Firestore sau.
     */
    public void adjustBalance(String userId, String maVi, long soDuMoi,
                              String lyDo, Callback callback) {

        // document().getId() sinh ID ngay trên máy, không cần mạng
        String maDieuChinh = firestore.collection("users")
                .document(userId)
                .collection("balance_adjustments")
                .document().getId();

        long now = System.currentTimeMillis();

        try {
            dao.adjustBalance(userId, maVi, soDuMoi, lyDo, maDieuChinh, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }
}