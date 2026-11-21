package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.BalanceAdjustmentDao;
import com.example.qlchitieu.data.local.TransactionDao;
import com.example.qlchitieu.data.local.WalletDao;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Wallet;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.List;

/**
 * Activity chỉ gọi lớp này, không gọi Firestore hay SQLite trực tiếp.
 *
 * Mọi thao tác chỉ ghi vào SQLite (đánh dấu PENDING) rồi nhờ SyncManager đẩy lên Firestore
 * ở phía sau, nên dùng được cả khi offline.
 */
public class WalletRepository {

    public interface Callback {
        void onDone(boolean thanhCong, String loi);
    }

    /** Kết quả kiểm tra "có được xóa ví không". */
    public interface DeleteCheckCallback {
        void onOk();
        void onRefused(String lyDo);
        void onError(String loi);
    }

    private static final String MSG_CO_LICH_SU =
            "Ví đã có giao dịch hoặc lịch sử điều chỉnh số dư.\n\n"
                    + "Hãy sử dụng chức năng Lưu trữ ví để giữ lại dữ liệu tài chính.";

    private final Context appContext;
    private final WalletDao dao;
    private final TransactionDao transactionDao;
    private final BalanceAdjustmentDao adjustmentDao;
    private final FirebaseFirestore firestore;

    public WalletRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = new WalletDao(context);
        transactionDao = new TransactionDao(context);
        adjustmentDao = new BalanceAdjustmentDao(context);
        firestore = FirebaseFirestore.getInstance();
    }

    /** Nhờ SyncManager đẩy các thay đổi PENDING lên cloud (chạy nền). */
    private void dongBo() {
        SyncManager.getInstance(appContext).syncNow();
    }

    private CollectionReference walletsOf(String userId) {
        return firestore.collection("users").document(userId).collection("wallets");
    }

    /** Đọc danh sách ví từ SQLite (nhanh, dùng được khi offline). */
    public List<Wallet> getWallets(String userId) {
        return dao.getAll(userId);
    }

    /** Thêm ví: lưu SQLite ngay, báo thành công ngay, đẩy lên cloud ở phía sau. */
    public void addWallet(String userId, String tenVi, String loaiVi,
                          long soDu, Callback callback) {

        // document().getId() sinh ID ngay trên máy, không cần mạng
        String maVi = walletsOf(userId).document().getId();

        Wallet vi = new Wallet(maVi, tenVi, loaiVi, soDu);
        vi.setTrangThai("HOAT_DONG");

        long now = System.currentTimeMillis();

        try {
            dao.insert(userId, vi, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }

    /**
     * Kéo ví từ Firestore về SQLite (do SyncManager gọi). Ví đang PENDING trên máy được giữ nguyên.
     * Dùng Source.SERVER nên khi offline sẽ thất bại và không đổi gì ở máy.
     */
    public void pullFromCloud(String userId, Callback callback) {
        walletsOf(userId).get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {

                        String ten = doc.getString("tenVi");
                        String loai = doc.getString("loaiVi");
                        String trangThai = doc.getString("trangThai");
                        Long soDu = doc.getLong("soDu");

                        Wallet vi = new Wallet(
                                doc.getId(),
                                ten == null ? "Ví chưa đặt tên" : ten,
                                loai == null ? "Khác" : loai,
                                soDu == null ? 0L : soDu);
                        vi.setTrangThai(trangThai == null ? "HOAT_DONG" : trangThai);

                        Timestamp tao = doc.getTimestamp("ngayTao");
                        Timestamp luuTru = doc.getTimestamp("ngayLuuTru");

                        dao.upsertFromCloud(
                                userId, vi,
                                tao == null ? System.currentTimeMillis() : tao.toDate().getTime(),
                                luuTru == null ? 0 : luuTru.toDate().getTime());
                    }
                    callback.onDone(true, null);
                })
                .addOnFailureListener(e -> callback.onDone(false, e.getMessage()));
    }

    /** Tổng số dư các ví đang hoạt động, tính bằng SQL. */
    public long getTotalBalance(String userId) {
        return dao.sumActiveBalance(userId);
    }

    /** Đọc một ví từ SQLite (null nếu không có). */
    public Wallet getWallet(String userId, String maVi) {
        return dao.getById(userId, maVi);
    }

    /** Sửa tên/loại ví: lưu SQLite ngay, báo thành công ngay, cập nhật cloud ở phía sau. */
    public void updateWalletInfo(String userId, String maVi, String tenVi,
                                 String loaiVi, Callback callback) {

        int soDong;

        try {
            soDong = dao.updateInfo(userId, maVi, tenVi, loaiVi, System.currentTimeMillis());
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        if (soDong == 0) {
            callback.onDone(false, "Ví không tồn tại");
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }

    /**
     * Lưu trữ / khôi phục ví.
     * @param dangLuuTru trạng thái hiện tại của ví trên màn hình (true = đang lưu trữ).
     */
    public void setArchived(String userId, String maVi, boolean dangLuuTru, Callback callback) {

        long now = System.currentTimeMillis();

        try {
            dao.toggleArchive(userId, maVi, dangLuuTru, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }

    /**
     * Kiểm tra ví có được xóa không: số dư phải bằng 0 và chưa có giao dịch / điều chỉnh nào.
     * Kiểm tra hoàn toàn bằng SQLite nên dùng được cả khi offline và luôn thấy giao dịch
     * vừa tạo (kể cả khi chưa kịp đẩy lên cloud).
     */
    public void checkCanDelete(String userId, String maVi, DeleteCheckCallback callback) {

        Wallet vi = dao.getById(userId, maVi);

        if (vi == null) {
            callback.onError("Ví không tồn tại");
            return;
        }

        if (vi.getSoDu() != 0) {
            callback.onRefused("Chỉ có thể xóa ví khi số dư bằng 0 ₫.");
            return;
        }

        if (adjustmentDao.existsForWallet(userId, maVi)
                || transactionDao.existsForWallet(userId, maVi)) {
            callback.onRefused(MSG_CO_LICH_SU);
            return;
        }

        callback.onOk();
    }

    /**
     * Xóa ví: xóa mềm ở SQLite (ẩn khỏi app ngay). SyncManager sẽ xóa trên Firestore,
     * xong mới xóa hẳn dòng ở SQLite; nếu thất bại thì ví vẫn ẩn và giữ PENDING để thử lại.
     */
    public void deleteWallet(String userId, String maVi, Callback callback) {

        int soDong;

        try {
            soDong = dao.softDelete(userId, maVi, System.currentTimeMillis());
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        if (soDong == 0) {
            callback.onDone(false, "Ví không tồn tại");
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }
}