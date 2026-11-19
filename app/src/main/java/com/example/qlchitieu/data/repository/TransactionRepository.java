package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.TransactionDao;
import com.example.qlchitieu.data.local.WalletDao;
import com.example.qlchitieu.model.Transaction;
import com.example.qlchitieu.model.Wallet;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Giao dịch Thu nhập / Chi tiêu / Chuyển tiền.
 * Bản CHUYỂN TIẾP: ghi SQLite trước, rồi đẩy luôn lên Firestore.
 * Khi có SyncManager, phần đẩy lên cloud sẽ chuyển sang SyncManager.
 */
public class TransactionRepository {

    public interface Callback {
        void onDone(boolean thanhCong, String loi);
    }

    private final TransactionDao dao;
    private final WalletDao walletDao;
    private final FirebaseFirestore firestore;

    public TransactionRepository(Context context) {
        dao = new TransactionDao(context);
        walletDao = new WalletDao(context);
        firestore = FirebaseFirestore.getInstance();
    }

    /** Đọc lịch sử giao dịch từ SQLite (mới nhất lên đầu). */
    public List<Transaction> getTransactions(String userId) {
        return dao.getAll(userId);
    }

    /**
     * Thêm giao dịch: kiểm tra và cập nhật số dư trong một transaction SQLite
     * (chạy được khi offline), rồi đẩy giao dịch + số dư mới lên Firestore bằng một WriteBatch.
     */
    public void addTransaction(String userId, String loaiGiaoDich, String danhMuc,
                               String ghiChu, String maViNguon, String maViDich,
                               long soTien, Date ngayGiaoDich, Callback callback) {

        // document().getId() sinh ID ngay trên máy, không cần mạng
        String maGiaoDich = firestore.collection("users")
                .document(userId)
                .collection("transactions")
                .document().getId();

        Transaction giaoDich = new Transaction(
                maGiaoDich, loaiGiaoDich, danhMuc, ghiChu,
                maViNguon, maViDich, soTien, ngayGiaoDich);

        long now = System.currentTimeMillis();

        try {
            dao.insertAndApply(userId, giaoDich, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        // Đẩy lên cloud TRƯỚC khi báo thành công để các màn hình còn đọc Firestore thấy ngay
        pushTransaction(userId, giaoDich, now);
        callback.onDone(true, null);
    }

    private void pushTransaction(String userId, Transaction t, long now) {

        DocumentReference giaoDichRef = firestore.collection("users")
                .document(userId).collection("transactions").document(t.getMaGiaoDich());

        Map<String, Object> map = new HashMap<>();
        map.put("loaiGiaoDich", t.getLoaiGiaoDich());
        map.put("soTien", t.getSoTien());
        map.put("maViNguon", t.getMaViNguon());
        map.put("maViDich", t.getMaViDich());
        map.put("danhMuc", t.getDanhMuc());
        map.put("ghiChu", t.getGhiChu());
        map.put("ngayGiaoDich", t.getNgayGiaoDich());
        map.put("ngayTao", new Timestamp(new Date(now)));

        WriteBatch batch = firestore.batch();
        batch.set(giaoDichRef, map);

        // Số dư mới của các ví liên quan (đã được tính trong SQLite)
        capNhatSoDuTrenBatch(batch, userId, t.getMaViNguon());
        if (t.getMaViDich() != null) {
            capNhatSoDuTrenBatch(batch, userId, t.getMaViDich());
        }

        // Thất bại (mất mạng, ví chưa có trên cloud...): giữ PENDING,
        // SyncManager sau này sẽ thử lại.
        batch.commit().addOnSuccessListener(unused ->
                dao.markSynced(t.getMaGiaoDich(), t.getMaViNguon(), t.getMaViDich()));
    }

    private void capNhatSoDuTrenBatch(WriteBatch batch, String userId, String maVi) {
        Wallet vi = walletDao.getById(userId, maVi);

        if (vi == null) {
            return;
        }

        batch.update(
                firestore.collection("users").document(userId)
                        .collection("wallets").document(maVi),
                "soDu", vi.getSoDu());
    }

    /**
     * TẠM THỜI: kéo giao dịch từ Firestore về SQLite (giao dịch cũ, và thay đổi từ các màn hình
     * chưa chuyển sang SQLite như sửa/xóa giao dịch). Dùng Source.SERVER nên khi offline sẽ
     * thất bại và KHÔNG xóa gì ở máy.
     */
    public void pullFromCloud(String userId, Callback callback) {
        firestore.collection("users")
                .document(userId)
                .collection("transactions")
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {

                    Set<String> idTrenCloud = new HashSet<>();

                    for (QueryDocumentSnapshot doc : snapshot) {

                        idTrenCloud.add(doc.getId());

                        Long soTien = doc.getLong("soTien");
                        Date ngay = doc.getDate("ngayGiaoDich");

                        if (soTien == null || ngay == null) {
                            continue;
                        }

                        dao.upsertFromCloud(userId, new Transaction(
                                doc.getId(),
                                doc.getString("loaiGiaoDich"),
                                doc.getString("danhMuc"),
                                doc.getString("ghiChu"),
                                doc.getString("maViNguon"),
                                doc.getString("maViDich"),
                                soTien,
                                ngay));
                    }

                    dao.deleteSyncedNotIn(userId, idTrenCloud);
                    callback.onDone(true, null);
                })
                .addOnFailureListener(e -> callback.onDone(false, e.getMessage()));
    }

    /** Tổng thu và tổng chi trong [tuNgay, denNgay): {thu, chi}. Tính bằng SQL. */
    public long[] getIncomeExpense(String userId, long tuNgay, long denNgay) {
        return dao.sumIncomeExpense(userId, tuNgay, denNgay);
    }

    /** Chi tiêu theo danh mục trong [tuNgay, denNgay). Tính bằng SQL. */
    public HashMap<String, Long> getExpenseByCategory(String userId, long tuNgay, long denNgay) {
        return dao.sumExpenseByCategory(userId, tuNgay, denNgay);
    }

    /** Các giao dịch mới nhất. */
    public List<Transaction> getRecent(String userId, int soLuong) {
        return dao.getRecent(userId, soLuong);
    }

    /** Đọc một giao dịch từ SQLite (null nếu không có). */
    public Transaction getTransaction(String userId, String maGiaoDich) {
        return dao.getById(userId, maGiaoDich);
    }

    // ------------------------------------------------------------------
    //  Sửa giao dịch
    // ------------------------------------------------------------------

    /**
     * Sửa giao dịch: tính lại số dư + ghi nhật ký trong một transaction SQLite
     * (chạy được khi offline), rồi đẩy lên Firestore bằng một WriteBatch.
     *
     * @param cu giá trị cũ mà màn hình đang dựa vào (để phát hiện giao dịch đã bị đổi).
     */
    public void editTransaction(String userId, Transaction cu, long soTienMoi,
                                String danhMucMoi, String ghiChuMoi, Date ngayMoi,
                                Callback callback) {

        String maChinhSua = firestore.collection("users")
                .document(userId)
                .collection("transaction_edits")
                .document().getId();

        long now = System.currentTimeMillis();

        try {
            dao.updateAndRecalculate(userId, cu, soTienMoi, danhMucMoi,
                    ghiChuMoi, ngayMoi, maChinhSua, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        pushEdit(userId, cu, soTienMoi, danhMucMoi, ghiChuMoi, ngayMoi, maChinhSua, now);
        callback.onDone(true, null);
    }

    private void pushEdit(String userId, Transaction cu, long soTienMoi,
                          String danhMucMoi, String ghiChuMoi, Date ngayMoi,
                          String maChinhSua, long now) {

        DocumentReference giaoDichRef = firestore.collection("users")
                .document(userId).collection("transactions").document(cu.getMaGiaoDich());

        DocumentReference lichSuRef = firestore.collection("users")
                .document(userId).collection("transaction_edits").document(maChinhSua);

        Map<String, Object> capNhat = new HashMap<>();
        capNhat.put("soTien", soTienMoi);
        capNhat.put("danhMuc", danhMucMoi);
        capNhat.put("ghiChu", ghiChuMoi);
        capNhat.put("ngayGiaoDich", ngayMoi);
        capNhat.put("ngayCapNhat", new Timestamp(new Date(now)));

        Map<String, Object> lichSu = new HashMap<>();
        lichSu.put("maGiaoDich", cu.getMaGiaoDich());
        lichSu.put("loaiGiaoDich", cu.getLoaiGiaoDich());
        lichSu.put("maViNguon", cu.getMaViNguon());
        lichSu.put("maViDich", cu.getMaViDich());
        lichSu.put("soTienCu", cu.getSoTien());
        lichSu.put("soTienMoi", soTienMoi);
        lichSu.put("danhMucCu", cu.getDanhMuc());
        lichSu.put("danhMucMoi", danhMucMoi);
        lichSu.put("ghiChuCu", cu.getGhiChu());
        lichSu.put("ghiChuMoi", ghiChuMoi);
        lichSu.put("ngayGiaoDichCu", cu.getNgayGiaoDich());
        lichSu.put("ngayGiaoDichMoi", ngayMoi);
        lichSu.put("ngayChinhSua", new Timestamp(new Date(now)));

        WriteBatch batch = firestore.batch();
        batch.update(giaoDichRef, capNhat);
        capNhatSoDuTrenBatch(batch, userId, cu.getMaViNguon());
        if (cu.getMaViDich() != null) {
            capNhatSoDuTrenBatch(batch, userId, cu.getMaViDich());
        }
        batch.set(lichSuRef, lichSu);

        // Thất bại (mất mạng, giao dịch chưa có trên cloud...): giữ PENDING,
        // SyncManager sau này sẽ thử lại.
        batch.commit().addOnSuccessListener(unused ->
                dao.markEditSynced(maChinhSua, cu.getMaGiaoDich(),
                        cu.getMaViNguon(), cu.getMaViDich()));
    }

    // ------------------------------------------------------------------
    //  Xóa giao dịch
    // ------------------------------------------------------------------

    /**
     * Xóa giao dịch: hoàn tác số dư + ẩn giao dịch trong một transaction SQLite
     * (chạy được khi offline), rồi đẩy lên Firestore: cập nhật số dư ví, ghi nhật ký xóa
     * và xóa giao dịch trong một WriteBatch.
     */
    public void deleteTransaction(String userId, String maGiaoDich, Callback callback) {

        long now = System.currentTimeMillis();
        Transaction daXoa;

        try {
            daXoa = dao.deleteAndRevert(userId, maGiaoDich, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        pushDeletion(userId, daXoa, now);
        callback.onDone(true, null);
    }

    private void pushDeletion(String userId, Transaction t, long now) {

        DocumentReference giaoDichRef = firestore.collection("users")
                .document(userId).collection("transactions").document(t.getMaGiaoDich());

        DocumentReference lichSuXoaRef = firestore.collection("users")
                .document(userId).collection("transaction_deletions").document();

        Wallet viNguon = walletDao.getById(userId, t.getMaViNguon());

        Map<String, Object> lichSu = new HashMap<>();
        lichSu.put("maGiaoDich", t.getMaGiaoDich());
        lichSu.put("loaiGiaoDich", t.getLoaiGiaoDich());
        lichSu.put("soTien", t.getSoTien());
        lichSu.put("maViNguon", t.getMaViNguon());
        lichSu.put("maViDich", t.getMaViDich());
        lichSu.put("danhMuc", t.getDanhMuc());
        lichSu.put("ghiChu", t.getGhiChu());
        lichSu.put("ngayGiaoDich", t.getNgayGiaoDich());
        lichSu.put("soDuNguonSauXoa", viNguon == null ? 0L : viNguon.getSoDu());
        lichSu.put("ngayXoa", new Timestamp(new Date(now)));

        WriteBatch batch = firestore.batch();
        capNhatSoDuTrenBatch(batch, userId, t.getMaViNguon());
        if (t.getMaViDich() != null) {
            capNhatSoDuTrenBatch(batch, userId, t.getMaViDich());
        }
        batch.set(lichSuXoaRef, lichSu);
        batch.delete(giaoDichRef);

        // Thất bại: giao dịch vẫn ẩn và giữ PENDING, SyncManager sau này sẽ thử lại.
        batch.commit().addOnSuccessListener(unused ->
                dao.purgeDeleted(t.getMaGiaoDich(), t.getMaViNguon(), t.getMaViDich()));
    }
}