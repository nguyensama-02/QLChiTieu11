package com.example.qlchitieu.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.qlchitieu.model.Wallet;

import java.util.ArrayList;
import java.util.List;

/**
 * Chỉ làm việc với SQLite cho bảng wallets. Không biết gì về Firestore.
 */
public class WalletDao {

    private final PhinovaDbHelper helper;

    public WalletDao(Context context) {
        helper = PhinovaDbHelper.getInstance(context);
    }

    /** Thêm ví mới tạo trên máy: đánh dấu PENDING (chưa lên cloud). */
    public void insert(String userId, Wallet w, long now) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.W_ID, w.getMaVi());
        v.put(PhinovaDbHelper.C_USER_ID, userId);
        v.put(PhinovaDbHelper.W_TEN, w.getTenVi());
        v.put(PhinovaDbHelper.W_LOAI, w.getLoaiVi());
        v.put(PhinovaDbHelper.W_SO_DU, w.getSoDu());
        v.put(PhinovaDbHelper.W_TRANG_THAI, w.getTrangThai());
        v.put(PhinovaDbHelper.W_NGAY_TAO, now);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
        v.put(PhinovaDbHelper.C_UPDATED_AT, now);
        v.put(PhinovaDbHelper.C_IS_DELETED, 0);

        helper.getWritableDatabase().insertOrThrow(PhinovaDbHelper.T_WALLETS, null, v);
    }

    /** Lấy tất cả ví chưa bị xóa của một người dùng (cả hoạt động và lưu trữ). */
    public List<Wallet> getAll(String userId) {
        List<Wallet> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_WALLETS,
                null,
                PhinovaDbHelper.C_USER_ID + "=? AND " + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{userId},
                null, null,
                PhinovaDbHelper.W_NGAY_TAO + " ASC")) {

            while (c.moveToNext()) {
                Wallet w = new Wallet(
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_ID)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_TEN)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_LOAI)),
                        c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.W_SO_DU)));
                w.setTrangThai(c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_TRANG_THAI)));
                list.add(w);
            }
        }
        return list;
    }

    /** Ví đã đẩy lên cloud thành công. */
    public void markSynced(String maVi) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);
        helper.getWritableDatabase().update(
                PhinovaDbHelper.T_WALLETS, v,
                PhinovaDbHelper.W_ID + "=?", new String[]{maVi});
    }

    /**
     * Ghi dữ liệu kéo từ cloud về. Nếu ví này đang có thay đổi chưa đẩy lên (PENDING)
     * thì bỏ qua để không ghi đè mất dữ liệu trên máy.
     */
    public void upsertFromCloud(String userId, Wallet w, long ngayTao, long ngayLuuTru) {
        SQLiteDatabase db = helper.getWritableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_WALLETS,
                new String[]{PhinovaDbHelper.C_SYNC_STATUS},
                PhinovaDbHelper.W_ID + "=?", new String[]{w.getMaVi()},
                null, null, null)) {

            if (c.moveToFirst() && PhinovaDbHelper.SYNC_PENDING.equals(c.getString(0))) {
                return;
            }
        }

        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.W_ID, w.getMaVi());
        v.put(PhinovaDbHelper.C_USER_ID, userId);
        v.put(PhinovaDbHelper.W_TEN, w.getTenVi());
        v.put(PhinovaDbHelper.W_LOAI, w.getLoaiVi());
        v.put(PhinovaDbHelper.W_SO_DU, w.getSoDu());
        v.put(PhinovaDbHelper.W_TRANG_THAI, w.getTrangThai());
        v.put(PhinovaDbHelper.W_NGAY_TAO, ngayTao);
        if (ngayLuuTru > 0) {
            v.put(PhinovaDbHelper.W_NGAY_LUU_TRU, ngayLuuTru);
        }
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);
        v.put(PhinovaDbHelper.C_UPDATED_AT, System.currentTimeMillis());
        v.put(PhinovaDbHelper.C_IS_DELETED, 0);

        db.insertWithOnConflict(PhinovaDbHelper.T_WALLETS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Lấy một ví theo ID (null nếu không có hoặc đã bị xóa). */
    public Wallet getById(String userId, String maVi) {
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_WALLETS,
                null,
                PhinovaDbHelper.W_ID + "=? AND " + PhinovaDbHelper.C_USER_ID + "=? AND "
                        + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{maVi, userId},
                null, null, null)) {

            if (!c.moveToFirst()) {
                return null;
            }

            Wallet w = new Wallet(
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_ID)),
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_TEN)),
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_LOAI)),
                    c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.W_SO_DU)));
            w.setTrangThai(c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.W_TRANG_THAI)));
            return w;
        }
    }

    /** Sửa tên và loại ví. Trả về số dòng được cập nhật (0 = không tìm thấy ví). */
    public int updateInfo(String userId, String maVi, String tenVi, String loaiVi, long now) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.W_TEN, tenVi);
        v.put(PhinovaDbHelper.W_LOAI, loaiVi);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
        v.put(PhinovaDbHelper.C_UPDATED_AT, now);

        return helper.getWritableDatabase().update(
                PhinovaDbHelper.T_WALLETS, v,
                PhinovaDbHelper.W_ID + "=? AND " + PhinovaDbHelper.C_USER_ID + "=? AND "
                        + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{maVi, userId});
    }

    /**
     * Lưu trữ / khôi phục ví trong một transaction SQLite.
     * @param dangLuuTru trạng thái hiện tại mà màn hình đang thấy (true = đang lưu trữ).
     *                   Nếu trạng thái thật khác thì báo lỗi để người dùng tải lại.
     */
    public void toggleArchive(String userId, String maVi, boolean dangLuuTru, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            String trangThai;
            long soDu;

            try (Cursor c = db.query(
                    PhinovaDbHelper.T_WALLETS,
                    new String[]{PhinovaDbHelper.W_TRANG_THAI, PhinovaDbHelper.W_SO_DU},
                    PhinovaDbHelper.W_ID + "=? AND " + PhinovaDbHelper.C_USER_ID + "=? AND "
                            + PhinovaDbHelper.C_IS_DELETED + "=0",
                    new String[]{maVi, userId},
                    null, null, null)) {

                if (!c.moveToFirst()) {
                    throw new IllegalStateException("Ví không tồn tại");
                }
                trangThai = c.getString(0);
                soDu = c.getLong(1);
            }

            if ("LUU_TRU".equals(trangThai) != dangLuuTru) {
                throw new IllegalStateException("Trạng thái ví đã thay đổi, hãy tải lại");
            }

            ContentValues v = new ContentValues();

            if (dangLuuTru) {
                v.put(PhinovaDbHelper.W_TRANG_THAI, "HOAT_DONG");
                v.putNull(PhinovaDbHelper.W_NGAY_LUU_TRU);
            } else {
                if (soDu != 0) {
                    throw new IllegalStateException("Cần đưa số dư về 0 ₫ trước khi lưu trữ");
                }
                v.put(PhinovaDbHelper.W_TRANG_THAI, "LUU_TRU");
                v.put(PhinovaDbHelper.W_NGAY_LUU_TRU, now);
            }

            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            v.put(PhinovaDbHelper.C_UPDATED_AT, now);

            db.update(PhinovaDbHelper.T_WALLETS, v,
                    PhinovaDbHelper.W_ID + "=?", new String[]{maVi});

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    /** Xóa mềm: ẩn ví khỏi app, chờ đẩy việc xóa lên cloud. Trả về số dòng bị ảnh hưởng. */
    public int softDelete(String userId, String maVi, long now) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.C_IS_DELETED, 1);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
        v.put(PhinovaDbHelper.C_UPDATED_AT, now);

        return helper.getWritableDatabase().update(
                PhinovaDbHelper.T_WALLETS, v,
                PhinovaDbHelper.W_ID + "=? AND " + PhinovaDbHelper.C_USER_ID + "=?",
                new String[]{maVi, userId});
    }

    /** Xóa hẳn dòng khỏi SQLite (sau khi cloud đã xóa xong). */
    public void purge(String maVi) {
        helper.getWritableDatabase().delete(
                PhinovaDbHelper.T_WALLETS,
                PhinovaDbHelper.W_ID + "=?", new String[]{maVi});
    }

    /** Tổng số dư các ví đang hoạt động (không tính ví lưu trữ và ví đã xóa). */
    public long sumActiveBalance(String userId) {
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(" + PhinovaDbHelper.W_SO_DU + "), 0) FROM "
                        + PhinovaDbHelper.T_WALLETS
                        + " WHERE " + PhinovaDbHelper.C_USER_ID + "=? AND "
                        + PhinovaDbHelper.C_IS_DELETED + "=0 AND "
                        + PhinovaDbHelper.W_TRANG_THAI + "<>?",
                new String[]{userId, "LUU_TRU"})) {

            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }
}