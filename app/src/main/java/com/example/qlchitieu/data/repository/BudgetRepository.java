package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.BudgetDao;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Budget;
import com.example.qlchitieu.util.MonthRange;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ngân sách theo tháng. Activity chỉ gọi lớp này.
 * Chỉ ghi vào SQLite (đánh dấu PENDING) rồi nhờ SyncManager đẩy lên Firestore ở phía sau.
 */
public class BudgetRepository {

    public interface Callback {
        void onDone(boolean thanhCong, String loi);
    }

    private final Context appContext;
    private final BudgetDao dao;
    private final FirebaseFirestore firestore;

    public BudgetRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = new BudgetDao(context);
        firestore = FirebaseFirestore.getInstance();
    }

    /** Nhờ SyncManager đẩy các thay đổi PENDING lên cloud (chạy nền). */
    private void dongBo() {
        SyncManager.getInstance(appContext).syncNow();
    }

    private CollectionReference budgetsOf(String userId) {
        return firestore.collection("users").document(userId).collection("budgets");
    }

    /** Mã ngân sách, giữ đúng định dạng đang dùng trên Firestore: 2026_11_an_uong. */
    public static String taoMaNganSach(int thang, int nam, String maDanhMuc) {
        return String.format(Locale.US, "%04d_%02d_%s", nam, thang, maDanhMuc);
    }

    // ------------------------------------------------------------------
    //  Đọc
    // ------------------------------------------------------------------

    /** Ngân sách của một tháng kèm số đã chi, sắp theo tên danh mục. Đọc từ SQLite. */
    public List<Budget> getBudgets(String userId, int thang, int nam) {
        List<Budget> ds = dao.getByMonth(
                userId, thang, nam,
                MonthRange.start(thang, nam),
                MonthRange.end(thang, nam));

        ds.sort((a, b) -> a.getDanhMuc().compareToIgnoreCase(b.getDanhMuc()));
        return ds;
    }

    // ------------------------------------------------------------------
    //  Thêm / sửa / xóa
    // ------------------------------------------------------------------

    public void addBudget(String userId, String danhMuc, String maDanhMuc,
                          long soTienGioiHan, int thang, int nam, Callback callback) {

        Budget nganSach = new Budget(
                taoMaNganSach(thang, nam, maDanhMuc),
                danhMuc, soTienGioiHan, 0, thang, nam);

        long now = System.currentTimeMillis();

        try {
            dao.insert(userId, nganSach, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }

    /** Đổi hạn mức. nganSachCu là ngân sách mà màn hình đang hiển thị (để phát hiện dữ liệu cũ). */
    public void updateLimit(String userId, Budget nganSachCu, long gioiHanMoi, Callback callback) {

        long now = System.currentTimeMillis();

        try {
            dao.updateLimit(userId, nganSachCu.getMaNganSach(),
                    nganSachCu.getSoTienGioiHan(), gioiHanMoi, now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }

    /** Xóa ngân sách: xóa mềm ở SQLite (ẩn ngay). SyncManager sẽ xóa trên Firestore rồi xóa hẳn dòng. */
    public void deleteBudget(String userId, Budget nganSach, Callback callback) {

        long now = System.currentTimeMillis();

        try {
            dao.softDelete(userId, nganSach.getMaNganSach(), nganSach.getSoTienGioiHan(), now);
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
            return;
        }

        callback.onDone(true, null);
        dongBo();
    }

    // ------------------------------------------------------------------
    //  Kéo từ Firestore (do SyncManager gọi)
    // ------------------------------------------------------------------

    /**
     * Kéo ngân sách từ Firestore về SQLite. Ngân sách đang PENDING trên máy được giữ nguyên.
     * Dùng Source.SERVER nên khi offline sẽ thất bại và KHÔNG xóa gì ở máy.
     */
    public void pullFromCloud(String userId, Callback callback) {
        budgetsOf(userId)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {

                    Set<String> idTrenCloud = new HashSet<>();

                    for (QueryDocumentSnapshot doc : snapshot) {

                        idTrenCloud.add(doc.getId());

                        String danhMuc = doc.getString("danhMuc");
                        Long gioiHan = doc.getLong("soTienGioiHan");
                        Long thang = doc.getLong("thang");
                        Long nam = doc.getLong("nam");

                        if (danhMuc == null || gioiHan == null || gioiHan <= 0
                                || thang == null || nam == null) {
                            continue;
                        }

                        Timestamp tao = doc.getTimestamp("ngayTao");

                        dao.upsertFromCloud(userId,
                                new Budget(doc.getId(), danhMuc, gioiHan, 0,
                                        thang.intValue(), nam.intValue()),
                                tao == null ? System.currentTimeMillis()
                                        : tao.toDate().getTime());
                    }

                    dao.deleteSyncedNotIn(userId, idTrenCloud);
                    callback.onDone(true, null);
                })
                .addOnFailureListener(e -> callback.onDone(false, e.getMessage()));
    }
}