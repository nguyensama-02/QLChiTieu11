package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.BalanceAdjustmentDao;
import com.example.qlchitieu.data.local.WalletDao;
import com.example.qlchitieu.model.Wallet;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity chỉ gọi lớp này, không gọi Firestore hay SQLite trực tiếp.
 *
 * Đây là bản CHUYỂN TIẾP: ghi SQLite trước, rồi đẩy luôn lên Firestore bằng cùng ID.
 * Khi viết xong SyncManager, phần đẩy lên cloud sẽ chuyển sang SyncManager.
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

    private final WalletDao dao;
    private final BalanceAdjustmentDao adjustmentDao;
    private final FirebaseFirestore firestore;

    public WalletRepository(Context context) {
        dao = new WalletDao(context);
        adjustmentDao = new BalanceAdjustmentDao(context);
        firestore = FirebaseFirestore.getInstance();
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
        pushWallet(userId, vi, now);
    }

    private void pushWallet(String userId, Wallet vi, long ngayTao) {
        Map<String, Object> map = new HashMap<>();
        map.put("tenVi", vi.getTenVi());
        map.put("loaiVi", vi.getLoaiVi());
        map.put("soDu", vi.getSoDu());
        map.put("trangThai", vi.getTrangThai());
        map.put("ngayTao", new Timestamp(new Date(ngayTao)));
        map.put("ngayLuuTru", null);

        walletsOf(userId).document(vi.getMaVi())
                .set(map)
                .addOnSuccessListener(unused -> dao.markSynced(vi.getMaVi()));
        // Thất bại: ví vẫn ở trạng thái PENDING. Việc thử lại sẽ do SyncManager đảm nhiệm.
    }

    /**
     * TẠM THỜI: kéo ví từ Firestore về SQLite để các ví cũ (tạo trước khi có SQLite)
     * và các thay đổi từ màn hình chưa chuyển sang Repository được cập nhật.
     */
    public void pullFromCloud(String userId, Callback callback) {
        walletsOf(userId).get()
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

        // Chỉ gửi 2 trường đã đổi (không gửi soDu để không ghi đè số dư mà
        // các màn hình chưa chuyển sang SQLite vừa cập nhật trên cloud).
        // update() sẽ thất bại nếu document chưa có trên cloud -> ví giữ PENDING,
        // SyncManager sau này sẽ đẩy cả ví lên.
        Map<String, Object> map = new HashMap<>();
        map.put("tenVi", tenVi);
        map.put("loaiVi", loaiVi);

        walletsOf(userId).document(maVi)
                .update(map)
                .addOnSuccessListener(unused -> dao.markSynced(maVi));
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

        Map<String, Object> map = new HashMap<>();
        map.put("trangThai", dangLuuTru ? "HOAT_DONG" : "LUU_TRU");
        map.put("ngayLuuTru", dangLuuTru ? null : new Timestamp(new Date(now)));

        walletsOf(userId).document(maVi)
                .update(map)
                .addOnSuccessListener(unused -> dao.markSynced(maVi));

        callback.onDone(true, null);
    }

    /**
     * Kiểm tra ví có được xóa không: số dư phải bằng 0 và chưa có giao dịch / điều chỉnh nào.
     *
     * TẠM THỜI: giao dịch chưa chuyển sang SQLite nên phải hỏi Firestore (cần mạng).
     * Sau khi chuyển xong nhóm giao dịch, phần này đổi sang kiểm tra bằng SQLite.
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

        if (adjustmentDao.existsForWallet(userId, maVi)) {
            callback.onRefused(MSG_CO_LICH_SU);
            return;
        }

        CollectionReference giaoDichRef = firestore.collection("users")
                .document(userId).collection("transactions");

        CollectionReference dieuChinhRef = firestore.collection("users")
                .document(userId).collection("balance_adjustments");

        Task<QuerySnapshot> giaoDichNguon = giaoDichRef
                .whereEqualTo("maViNguon", maVi).limit(1).get(Source.SERVER);

        Task<QuerySnapshot> giaoDichDich = giaoDichRef
                .whereEqualTo("maViDich", maVi).limit(1).get(Source.SERVER);

        Task<QuerySnapshot> lichSuDieuChinh = dieuChinhRef
                .whereEqualTo("maVi", maVi).limit(1).get(Source.SERVER);

        Tasks.whenAllSuccess(giaoDichNguon, giaoDichDich, lichSuDieuChinh)
                .addOnSuccessListener(ketQua -> {
                    if (!giaoDichNguon.getResult().isEmpty()
                            || !giaoDichDich.getResult().isEmpty()
                            || !lichSuDieuChinh.getResult().isEmpty()) {
                        callback.onRefused(MSG_CO_LICH_SU);
                    } else {
                        callback.onOk();
                    }
                })
                .addOnFailureListener(e ->
                        callback.onError("Không thể kiểm tra lịch sử: " + e.getMessage()));
    }

    /**
     * Xóa ví: ẩn khỏi SQLite ngay (xóa mềm), xóa trên Firestore, xong thì xóa hẳn dòng ở SQLite.
     * Nếu xóa cloud thất bại, ví vẫn ẩn và giữ PENDING để SyncManager xử lý sau.
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

        walletsOf(userId).document(maVi)
                .delete()
                .addOnSuccessListener(unused -> dao.purge(maVi));

        callback.onDone(true, null);
    }
}