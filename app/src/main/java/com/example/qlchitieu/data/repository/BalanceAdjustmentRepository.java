package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.BalanceAdjustmentDao;
import com.example.qlchitieu.model.BalanceAdjustment;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lịch sử điều chỉnh số dư: đọc lịch sử và thực hiện điều chỉnh số dư.
 */
public class BalanceAdjustmentRepository {

    public interface Callback {
        void onDone(boolean thanhCong, String loi);
    }

    private final BalanceAdjustmentDao dao;
    private final FirebaseFirestore firestore;

    public BalanceAdjustmentRepository(Context context) {
        dao = new BalanceAdjustmentDao(context);
        firestore = FirebaseFirestore.getInstance();
    }

    /** Đọc lịch sử điều chỉnh của một ví từ SQLite (mới nhất lên đầu). */
    public List<BalanceAdjustment> getAdjustments(String userId, String maVi) {
        return dao.getByWallet(userId, maVi);
    }

    /**
     * TẠM THỜI: kéo lịch sử điều chỉnh của một ví từ Firestore về SQLite, vì
     * AdjustBalanceActivity vẫn đang ghi thẳng lên Firestore.
     */
    public void pullFromCloud(String userId, String maVi, Callback callback) {
        firestore.collection("users")
                .document(userId)
                .collection("balance_adjustments")
                .whereEqualTo("maVi", maVi)
                .get()
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
     * Điều chỉnh số dư ví: ghi SQLite (số dư + lịch sử cùng một transaction, chạy được khi offline),
     * rồi đẩy lên Firestore bằng một WriteBatch (cả hai cùng thành công hoặc cùng thất bại).
     *
     * Bản CHUYỂN TIẾP: khi có SyncManager, phần đẩy lên cloud sẽ chuyển sang đó.
     */
    public void adjustBalance(String userId, String maVi, long soDuMoi,
                              String lyDo, Callback callback) {

        // document().getId() sinh ID ngay trên máy, không cần mạng
        String maDieuChinh = firestore.collection("users")
                .document(userId)
                .collection("balance_adjustments")
                .document().getId();

        long now = System.currentTimeMillis();
        BalanceAdjustment dieuChinh;

        try {
            dieuChinh = dao.adjustBalance(userId, maVi, soDuMoi, lyDo, maDieuChinh, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        // Đẩy lên cloud TRƯỚC khi báo thành công, để màn hình quay lại (đang đọc Firestore)
        // luôn thấy số dư mới.
        pushAdjustment(userId, dieuChinh, now);
        callback.onDone(true, null);
    }

    private void pushAdjustment(String userId, BalanceAdjustment a, long ngay) {
        DocumentReference viRef = firestore.collection("users")
                .document(userId)
                .collection("wallets")
                .document(a.getMaVi());

        DocumentReference lichSuRef = firestore.collection("users")
                .document(userId)
                .collection("balance_adjustments")
                .document(a.getMaDieuChinh());

        Map<String, Object> lichSu = new HashMap<>();
        lichSu.put("maVi", a.getMaVi());
        lichSu.put("soDuCu", a.getSoDuCu());
        lichSu.put("soDuMoi", a.getSoDuMoi());
        lichSu.put("chenhLech", a.getChenhLech());
        lichSu.put("lyDo", a.getLyDo());
        lichSu.put("ngayDieuChinh", new Timestamp(new Date(ngay)));

        WriteBatch batch = firestore.batch();
        batch.update(viRef, "soDu", a.getSoDuMoi());
        batch.set(lichSuRef, lichSu);

        // Thất bại (ví chưa có trên cloud, mất mạng...): giữ PENDING,
        // SyncManager sau này sẽ thử lại.
        batch.commit().addOnSuccessListener(unused ->
                dao.markSynced(a.getMaVi(), a.getMaDieuChinh()));
    }
}