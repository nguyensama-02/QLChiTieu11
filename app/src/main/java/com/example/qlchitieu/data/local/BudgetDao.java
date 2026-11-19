package com.example.qlchitieu.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.qlchitieu.model.Budget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chỉ làm việc với SQLite cho bảng budgets. Không biết gì về Firestore.
 * "Đã chi" không lưu trong bảng mà tính bằng SUM từ transactions mỗi lần đọc.
 */
public class BudgetDao {

    private static final String LOAI_CHI_TIEU = "Chi tiêu";

    private static final String WHERE_MOT_DONG =
            PhinovaDbHelper.C_USER_ID + "=? AND " + PhinovaDbHelper.B_ID + "=?";

    private final PhinovaDbHelper helper;

    public BudgetDao(Context context) {
        helper = PhinovaDbHelper.getInstance(context);
    }

    /** Dòng ngân sách có tồn tại (chưa xóa) không? */
    private boolean tonTai(SQLiteDatabase db, String userId, String maNganSach) {
        try (Cursor c = db.query(
                PhinovaDbHelper.T_BUDGETS,
                new String[]{PhinovaDbHelper.B_ID},
                WHERE_MOT_DONG + " AND " + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{userId, maNganSach},
                null, null, null)) {
            return c.moveToFirst();
        }
    }

    /**
     * Thêm ngân sách mới (PENDING). Mỗi danh mục chỉ có một ngân sách trong một tháng
     * (mã ngân sách đã chứa năm_tháng_danhmục). Nếu dòng cũ đang bị xóa mềm thì dùng lại dòng đó.
     */
    public void insert(String userId, Budget b, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            Integer daXoa = null; // null = chưa có dòng nào

            try (Cursor c = db.query(
                    PhinovaDbHelper.T_BUDGETS,
                    new String[]{PhinovaDbHelper.C_IS_DELETED},
                    WHERE_MOT_DONG,
                    new String[]{userId, b.getMaNganSach()},
                    null, null, null)) {

                if (c.moveToFirst()) {
                    daXoa = c.getInt(0);
                }
            }

            if (daXoa != null && daXoa == 0) {
                throw new IllegalStateException("Danh mục này đã có ngân sách trong tháng");
            }

            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.B_DANH_MUC, b.getDanhMuc());
            v.put(PhinovaDbHelper.B_GIOI_HAN, b.getSoTienGioiHan());
            v.put(PhinovaDbHelper.B_THANG, b.getThang());
            v.put(PhinovaDbHelper.B_NAM, b.getNam());
            v.put(PhinovaDbHelper.B_NGAY_TAO, now);
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            v.put(PhinovaDbHelper.C_UPDATED_AT, now);
            v.put(PhinovaDbHelper.C_IS_DELETED, 0);

            if (daXoa == null) {
                v.put(PhinovaDbHelper.B_ID, b.getMaNganSach());
                v.put(PhinovaDbHelper.C_USER_ID, userId);
                db.insertOrThrow(PhinovaDbHelper.T_BUDGETS, null, v);
            } else {
                db.update(PhinovaDbHelper.T_BUDGETS, v, WHERE_MOT_DONG,
                        new String[]{userId, b.getMaNganSach()});
            }

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    /**
     * Ngân sách của một tháng, kèm số đã chi (SUM giao dịch Chi tiêu cùng danh mục trong tháng).
     * Chưa sắp xếp: Repository sắp theo tên danh mục.
     */
    public List<Budget> getByMonth(String userId, int thang, int nam, long tuNgay, long denNgay) {
        List<Budget> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();

        String sql =
                "SELECT b." + PhinovaDbHelper.B_ID
                        + ", b." + PhinovaDbHelper.B_DANH_MUC
                        + ", b." + PhinovaDbHelper.B_GIOI_HAN
                        + ", b." + PhinovaDbHelper.B_THANG
                        + ", b." + PhinovaDbHelper.B_NAM
                        + ", COALESCE((SELECT SUM(t." + PhinovaDbHelper.TX_SO_TIEN + ")"
                        + " FROM " + PhinovaDbHelper.T_TRANSACTIONS + " t"
                        + " WHERE t." + PhinovaDbHelper.C_USER_ID + "=b." + PhinovaDbHelper.C_USER_ID
                        + " AND t." + PhinovaDbHelper.C_IS_DELETED + "=0"
                        + " AND t." + PhinovaDbHelper.TX_LOAI + "=?"
                        + " AND t." + PhinovaDbHelper.TX_DANH_MUC + "=b." + PhinovaDbHelper.B_DANH_MUC
                        + " AND t." + PhinovaDbHelper.TX_SO_TIEN + ">0"
                        + " AND t." + PhinovaDbHelper.TX_NGAY + ">=?"
                        + " AND t." + PhinovaDbHelper.TX_NGAY + "<?), 0)"
                        + " FROM " + PhinovaDbHelper.T_BUDGETS + " b"
                        + " WHERE b." + PhinovaDbHelper.C_USER_ID + "=?"
                        + " AND b." + PhinovaDbHelper.C_IS_DELETED + "=0"
                        + " AND b." + PhinovaDbHelper.B_THANG + "=?"
                        + " AND b." + PhinovaDbHelper.B_NAM + "=?"
                        + " AND b." + PhinovaDbHelper.B_GIOI_HAN + ">0";

        String[] args = {
                LOAI_CHI_TIEU,
                String.valueOf(tuNgay),
                String.valueOf(denNgay),
                userId,
                String.valueOf(thang),
                String.valueOf(nam)
        };

        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) {
                list.add(new Budget(
                        c.getString(0),
                        c.getString(1),
                        c.getLong(2),
                        c.getLong(5),
                        c.getInt(3),
                        c.getInt(4)));
            }
        }
        return list;
    }

    /**
     * Đổi giới hạn. Chỉ thành công nếu giới hạn hiện tại vẫn là gioiHanCu (màn hình không bị cũ).
     * Ném IllegalStateException (thông báo tiếng Việt) nếu không hợp lệ.
     */
    public void updateLimit(String userId, String maNganSach,
                            long gioiHanCu, long gioiHanMoi, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.B_GIOI_HAN, gioiHanMoi);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
        v.put(PhinovaDbHelper.C_UPDATED_AT, now);

        int soDong = db.update(
                PhinovaDbHelper.T_BUDGETS, v,
                WHERE_MOT_DONG + " AND " + PhinovaDbHelper.C_IS_DELETED + "=0 AND "
                        + PhinovaDbHelper.B_GIOI_HAN + "=?",
                new String[]{userId, maNganSach, String.valueOf(gioiHanCu)});

        if (soDong == 0) {
            throw new IllegalStateException(tonTai(db, userId, maNganSach)
                    ? "Ngân sách đã thay đổi, vui lòng tải lại"
                    : "Ngân sách không tồn tại");
        }
    }

    /** Xóa mềm (ẩn khỏi app, chờ cloud xóa xong mới xóa hẳn). Kiểm tra giới hạn như updateLimit. */
    public void softDelete(String userId, String maNganSach, long gioiHanCu, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.C_IS_DELETED, 1);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
        v.put(PhinovaDbHelper.C_UPDATED_AT, now);

        int soDong = db.update(
                PhinovaDbHelper.T_BUDGETS, v,
                WHERE_MOT_DONG + " AND " + PhinovaDbHelper.C_IS_DELETED + "=0 AND "
                        + PhinovaDbHelper.B_GIOI_HAN + "=?",
                new String[]{userId, maNganSach, String.valueOf(gioiHanCu)});

        if (soDong == 0) {
            throw new IllegalStateException(tonTai(db, userId, maNganSach)
                    ? "Ngân sách đã thay đổi, vui lòng tải lại"
                    : "Ngân sách đã được xóa");
        }
    }

    /**
     * Đã đẩy lên cloud thành công. Chỉ đánh dấu SYNCED nếu dòng chưa bị sửa tiếp
     * kể từ lúc đẩy (updated_at còn nguyên), tránh đánh dấu nhầm thay đổi mới.
     */
    public void markSynced(String userId, String maNganSach, long updatedAt) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);

        helper.getWritableDatabase().update(
                PhinovaDbHelper.T_BUDGETS, v,
                WHERE_MOT_DONG + " AND " + PhinovaDbHelper.C_UPDATED_AT + "=?",
                new String[]{userId, maNganSach, String.valueOf(updatedAt)});
    }

    /**
     * Cloud đã xóa xong: xóa hẳn dòng. Chỉ xóa nếu dòng vẫn đang ở trạng thái đã xóa
     * và chưa bị đổi (người dùng có thể đã thêm lại đúng ngân sách đó).
     */
    public void purge(String userId, String maNganSach, long updatedAt) {
        helper.getWritableDatabase().delete(
                PhinovaDbHelper.T_BUDGETS,
                WHERE_MOT_DONG + " AND " + PhinovaDbHelper.C_IS_DELETED + "=1 AND "
                        + PhinovaDbHelper.C_UPDATED_AT + "=?",
                new String[]{userId, maNganSach, String.valueOf(updatedAt)});
    }

    /** Ghi dữ liệu kéo từ cloud về. Dòng đang PENDING trên máy thì bỏ qua để không ghi đè. */
    public void upsertFromCloud(String userId, Budget b, long ngayTao) {
        SQLiteDatabase db = helper.getWritableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_BUDGETS,
                new String[]{PhinovaDbHelper.C_SYNC_STATUS},
                WHERE_MOT_DONG,
                new String[]{userId, b.getMaNganSach()},
                null, null, null)) {

            if (c.moveToFirst() && PhinovaDbHelper.SYNC_PENDING.equals(c.getString(0))) {
                return;
            }
        }

        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.B_ID, b.getMaNganSach());
        v.put(PhinovaDbHelper.C_USER_ID, userId);
        v.put(PhinovaDbHelper.B_DANH_MUC, b.getDanhMuc());
        v.put(PhinovaDbHelper.B_GIOI_HAN, b.getSoTienGioiHan());
        v.put(PhinovaDbHelper.B_THANG, b.getThang());
        v.put(PhinovaDbHelper.B_NAM, b.getNam());
        v.put(PhinovaDbHelper.B_NGAY_TAO, ngayTao);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);
        v.put(PhinovaDbHelper.C_UPDATED_AT, System.currentTimeMillis());
        v.put(PhinovaDbHelper.C_IS_DELETED, 0);

        db.insertWithOnConflict(PhinovaDbHelper.T_BUDGETS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Xóa khỏi SQLite các ngân sách đã SYNCED mà không còn trên cloud. PENDING được giữ lại. */
    public void deleteSyncedNotIn(String userId, Set<String> idTrenCloud) {
        SQLiteDatabase db = helper.getWritableDatabase();
        List<String> canXoa = new ArrayList<>();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_BUDGETS,
                new String[]{PhinovaDbHelper.B_ID},
                PhinovaDbHelper.C_USER_ID + "=? AND " + PhinovaDbHelper.C_SYNC_STATUS + "=?",
                new String[]{userId, PhinovaDbHelper.SYNC_SYNCED},
                null, null, null)) {

            while (c.moveToNext()) {
                String id = c.getString(0);
                if (!idTrenCloud.contains(id)) {
                    canXoa.add(id);
                }
            }
        }

        for (String id : canXoa) {
            db.delete(PhinovaDbHelper.T_BUDGETS, WHERE_MOT_DONG, new String[]{userId, id});
        }
    }
}
