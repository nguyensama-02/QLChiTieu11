package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.TransactionDao;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Transaction;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Giao dịch Thu nhập / Chi tiêu / Chuyển tiền.
 * Chỉ ghi vào SQLite (đánh dấu PENDING) rồi nhờ SyncManager đẩy lên Firestore ở phía sau,
 * nên mọi thao tác dùng được cả khi offline.
 */
public class TransactionRepository {

    public interface Callback {
        void onDone(boolean thanhCong, String loi);
    }

    private final Context appContext;
    private final TransactionDao dao;
    private final FirebaseFirestore firestore;

    public TransactionRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = new TransactionDao(context);
        firestore = FirebaseFirestore.getInstance();
    }

    /** Nhờ SyncManager đẩy các thay đổi PENDING lên cloud (chạy nền). */
    private void dongBo() {
        SyncManager.getInstance(appContext).syncNow();
    }

    /** Đọc lịch sử giao dịch từ SQLite (mới nhất lên đầu). */
    public List<Transaction> getTransactions(String userId) {
        return dao.getAll(userId);
    }

    /**
     * Thêm giao dịch: kiểm tra và cập nhật số dư trong một transaction SQLite
     * (chạy được khi offline). SyncManager sẽ đẩy giao dịch + số dư mới lên Firestore sau.
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

        callback.onDone(true, null);
        dongBo();
    }

    /**
     * Kéo giao dịch từ Firestore về SQLite (do SyncManager gọi). Giao dịch đang PENDING trên máy
     * được giữ nguyên. Dùng Source.SERVER nên khi offline sẽ thất bại và KHÔNG xóa gì ở máy.
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

    /** Như trên nhưng chỉ một ví (maVi = null: tất cả ví). */
    public long[] getIncomeExpense(String userId, long tuNgay, long denNgay, String maVi) {
        return dao.sumIncomeExpense(userId, tuNgay, denNgay, maVi);
    }

    /** Chi tiêu theo danh mục trong [tuNgay, denNgay). Tính bằng SQL. */
    public HashMap<String, Long> getExpenseByCategory(String userId, long tuNgay, long denNgay) {
        return dao.sumExpenseByCategory(userId, tuNgay, denNgay);
    }

    /** Chi tiêu theo danh mục, chỉ một ví (maVi = null: tất cả ví). */
    public HashMap<String, Long> getExpenseByCategory(String userId, long tuNgay, long denNgay,
                                                      String maVi) {
        return dao.sumByCategory(userId, "Chi tiêu", tuNgay, denNgay, maVi);
    }

    /** Thu nhập theo danh mục, chỉ một ví (maVi = null: tất cả ví). */
    public HashMap<String, Long> getIncomeByCategory(String userId, long tuNgay, long denNgay,
                                                     String maVi) {
        return dao.sumByCategory(userId, "Thu nhập", tuNgay, denNgay, maVi);
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
     * (chạy được khi offline). SyncManager sẽ đẩy lên Firestore sau.
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

        callback.onDone(true, null);
        dongBo();
    }

    // ------------------------------------------------------------------
    //  Xóa giao dịch
    // ------------------------------------------------------------------

    /**
     * Xóa giao dịch: hoàn tác số dư + ẩn giao dịch (xóa mềm) trong một transaction SQLite
     * (chạy được khi offline). SyncManager sẽ xóa trên Firestore, ghi nhật ký xóa rồi mới
     * xóa hẳn dòng ở SQLite.
     */
    public void deleteTransaction(String userId, String maGiaoDich, Callback callback) {

        long now = System.currentTimeMillis();

        try {
            dao.deleteAndRevert(userId, maGiaoDich, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }
    /**
     * Thu, chi của soThang tháng liên tiếp, tháng cuối là tháng chứa thangCuoi.
     * Kết quả xếp từ tháng cũ nhất đến tháng mới nhất, mỗi phần tử là {thu, chi}.
     * Tháng không có giao dịch cho {0, 0}.
     */
    public List<long[]> getMonthlyIncomeExpense(String userId, Calendar thangCuoi, int soThang) {
        return getMonthlyIncomeExpense(userId, thangCuoi, soThang, null);
    }

    /** Như trên nhưng chỉ một ví (maVi = null: tất cả ví). */
    public List<long[]> getMonthlyIncomeExpense(String userId, Calendar thangCuoi, int soThang,
                                                String maVi) {

        List<long[]> ketQua = new ArrayList<>();

        for (int i = soThang - 1; i >= 0; i--) {

            Calendar batDau = (Calendar) thangCuoi.clone();
            batDau.set(Calendar.DAY_OF_MONTH, 1);
            batDau.set(Calendar.HOUR_OF_DAY, 0);
            batDau.set(Calendar.MINUTE, 0);
            batDau.set(Calendar.SECOND, 0);
            batDau.set(Calendar.MILLISECOND, 0);
            batDau.add(Calendar.MONTH, -i);

            Calendar ketThuc = (Calendar) batDau.clone();
            ketThuc.add(Calendar.MONTH, 1);

            ketQua.add(dao.sumIncomeExpense(
                    userId,
                    batDau.getTimeInMillis(),
                    ketThuc.getTimeInMillis(),
                    maVi
            ));
        }

        return ketQua;
    }

    /** Chi tiêu từng ngày trong [tuNgay, denNgay): phần tử i là ngày (i + 1). */
    public long[] getDailyExpense(String userId, long tuNgay, long denNgay,
                                  String maVi, int soNgay) {
        return dao.sumExpenseByDay(userId, tuNgay, denNgay, maVi, soNgay);
    }

    /**
     * Chi tiêu từng ngày kể từ tuNgay: phần tử i là ngày thứ (i + 1) của khoảng.
     * Dùng cho khoảng không theo tháng (vd: 30 ngày qua).
     */
    public long[] getDailyExpenseFrom(String userId, long tuNgay, long denNgay,
                                      String maVi, int soNgay) {
        return dao.sumExpenseByDayFrom(userId, tuNgay, denNgay, maVi, soNgay);
    }

    /** Các khoản chi lớn nhất trong [tuNgay, denNgay), lớn đến nhỏ. */
    public List<Transaction> getLargestExpenses(String userId, long tuNgay, long denNgay,
                                                String maVi, int soLuong) {
        return dao.getLargestExpenses(userId, tuNgay, denNgay, maVi, soLuong);
    }
}