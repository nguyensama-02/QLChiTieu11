package com.example.qlchitieu.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.qlchitieu.model.BalanceAdjustment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Chỉ làm việc với SQLite cho bảng balance_adjustments (lịch sử điều chỉnh số dư).
 */
public class BalanceAdjustmentDao {

    private final PhinovaDbHelper helper;

    public BalanceAdjustmentDao(Context context) {
        helper = PhinovaDbHelper.getInstance(context);
    }

    /** Lịch sử điều chỉnh của một ví, mới nhất lên đầu. */
    public List<BalanceAdjustment> getByWallet(String userId, String maVi) {
        List<BalanceAdjustment> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_ADJUSTMENTS,
                null,
                PhinovaDbHelper.C_USER_ID + "=? AND "
                        + PhinovaDbHelper.A_VI + "=? AND "
                        + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{userId, maVi},
                null, null,
                PhinovaDbHelper.A_NGAY + " DESC")) {

            while (c.moveToNext()) {
                long ngay = c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.A_NGAY));

                list.add(new BalanceAdjustment(
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.A_ID)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.A_VI)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.A_LY_DO)),
                        c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.A_SO_DU_CU)),
                        c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.A_SO_DU_MOI)),
                        c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.A_CHENH_LECH)),
                        ngay == 0 ? null : new Date(ngay)));
            }
        }
        return list;
    }

    /**
     * Ghi một bản ghi kéo từ cloud về. Nếu bản ghi này đang PENDING trên máy
     * (chưa đẩy lên) thì bỏ qua để không ghi đè.
     */
    public void upsertFromCloud(String userId, BalanceAdjustment a) {
        SQLiteDatabase db = helper.getWritableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_ADJUSTMENTS,
                new String[]{PhinovaDbHelper.C_SYNC_STATUS},
                PhinovaDbHelper.A_ID + "=?", new String[]{a.getMaDieuChinh()},
                null, null, null)) {

            if (c.moveToFirst() && PhinovaDbHelper.SYNC_PENDING.equals(c.getString(0))) {
                return;
            }
        }

        Date ngay = a.getNgayDieuChinh();

        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.A_ID, a.getMaDieuChinh());
        v.put(PhinovaDbHelper.C_USER_ID, userId);
        v.put(PhinovaDbHelper.A_VI, a.getMaVi());
        v.put(PhinovaDbHelper.A_LY_DO, a.getLyDo());
        v.put(PhinovaDbHelper.A_SO_DU_CU, a.getSoDuCu());
        v.put(PhinovaDbHelper.A_SO_DU_MOI, a.getSoDuMoi());
        v.put(PhinovaDbHelper.A_CHENH_LECH, a.getChenhLech());
        v.put(PhinovaDbHelper.A_NGAY, ngay == null ? 0 : ngay.getTime());
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);
        v.put(PhinovaDbHelper.C_UPDATED_AT, System.currentTimeMillis());
        v.put(PhinovaDbHelper.C_IS_DELETED, 0);

        db.insertWithOnConflict(PhinovaDbHelper.T_ADJUSTMENTS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Điều chỉnh số dư: cập nhật số dư ví VÀ ghi lịch sử trong CÙNG MỘT transaction SQLite.
     * Hoặc cả hai cùng thành công, hoặc cả hai cùng bị hủy (không bao giờ đổi số dư mà thiếu lịch sử).
     * Ném IllegalStateException (kèm thông báo tiếng Việt) nếu không hợp lệ.
     */
    public BalanceAdjustment adjustBalance(String userId, String maVi, long soDuMoi,
                                           String lyDo, String maDieuChinh, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            long soDuCu;

            try (Cursor c = db.query(
                    PhinovaDbHelper.T_WALLETS,
                    new String[]{PhinovaDbHelper.W_SO_DU},
                    PhinovaDbHelper.W_ID + "=? AND " + PhinovaDbHelper.C_USER_ID + "=? AND "
                            + PhinovaDbHelper.C_IS_DELETED + "=0",
                    new String[]{maVi, userId},
                    null, null, null)) {

                if (!c.moveToFirst()) {
                    throw new IllegalStateException("Ví không tồn tại");
                }
                soDuCu = c.getLong(0);
            }

            long chenhLech = Math.subtractExact(soDuMoi, soDuCu);

            if (chenhLech == 0) {
                throw new IllegalStateException("Số dư mới không thay đổi");
            }

            // 1) Cập nhật số dư ví
            ContentValues vi = new ContentValues();
            vi.put(PhinovaDbHelper.W_SO_DU, soDuMoi);
            vi.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            vi.put(PhinovaDbHelper.C_UPDATED_AT, now);
            db.update(PhinovaDbHelper.T_WALLETS, vi,
                    PhinovaDbHelper.W_ID + "=?", new String[]{maVi});

            // 2) Ghi lịch sử điều chỉnh
            ContentValues ls = new ContentValues();
            ls.put(PhinovaDbHelper.A_ID, maDieuChinh);
            ls.put(PhinovaDbHelper.C_USER_ID, userId);
            ls.put(PhinovaDbHelper.A_VI, maVi);
            ls.put(PhinovaDbHelper.A_LY_DO, lyDo);
            ls.put(PhinovaDbHelper.A_SO_DU_CU, soDuCu);
            ls.put(PhinovaDbHelper.A_SO_DU_MOI, soDuMoi);
            ls.put(PhinovaDbHelper.A_CHENH_LECH, chenhLech);
            ls.put(PhinovaDbHelper.A_NGAY, now);
            ls.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            ls.put(PhinovaDbHelper.C_UPDATED_AT, now);
            ls.put(PhinovaDbHelper.C_IS_DELETED, 0);
            db.insertOrThrow(PhinovaDbHelper.T_ADJUSTMENTS, null, ls);

            db.setTransactionSuccessful();

            return new BalanceAdjustment(maDieuChinh, maVi, lyDo,
                    soDuCu, soDuMoi, chenhLech, new Date(now));

        } finally {
            db.endTransaction();
        }
    }

    /** Đánh dấu ví và bản ghi điều chỉnh đã đẩy lên cloud thành công. */
    public void markSynced(String maVi, String maDieuChinh) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);

            db.update(PhinovaDbHelper.T_WALLETS, v,
                    PhinovaDbHelper.W_ID + "=?", new String[]{maVi});
            db.update(PhinovaDbHelper.T_ADJUSTMENTS, v,
                    PhinovaDbHelper.A_ID + "=?", new String[]{maDieuChinh});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Ví này đã có lịch sử điều chỉnh số dư chưa? */
    public boolean existsForWallet(String userId, String maVi) {
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_ADJUSTMENTS,
                new String[]{PhinovaDbHelper.A_ID},
                PhinovaDbHelper.C_USER_ID + "=? AND " + PhinovaDbHelper.A_VI + "=? AND "
                        + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{userId, maVi},
                null, null, null, "1")) {

            return c.moveToFirst();
        }
    }
}