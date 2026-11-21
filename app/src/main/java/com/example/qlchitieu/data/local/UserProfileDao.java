package com.example.qlchitieu.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Chỉ làm việc với SQLite cho bảng user_profiles (hồ sơ người dùng: họ tên, email).
 * Mỗi tài khoản một dòng, khóa chính là user_id (chính là UID của Firebase Auth).
 */
public class UserProfileDao {

    private final PhinovaDbHelper helper;

    public UserProfileDao(Context context) {
        helper = PhinovaDbHelper.getInstance(context);
    }

    /** Họ tên đã lưu trên máy, hoặc null nếu chưa có / để trống. */
    public String getHoTen(String userId) {
        try (Cursor c = helper.getReadableDatabase().query(
                PhinovaDbHelper.T_USER_PROFILES,
                new String[]{PhinovaDbHelper.P_HO_TEN},
                PhinovaDbHelper.C_USER_ID + "=?",
                new String[]{userId},
                null, null, null)) {

            if (!c.moveToFirst()) {
                return null;
            }

            String hoTen = c.getString(0);

            if (hoTen == null || hoTen.trim().isEmpty()) {
                return null;
            }

            return hoTen.trim();
        }
    }

    /** Tạo hồ sơ cho tài khoản mới đăng ký: PENDING, chờ đẩy lên cloud. */
    public void create(String userId, String hoTen, String email, long now) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.C_USER_ID, userId);
        v.put(PhinovaDbHelper.P_HO_TEN, hoTen);
        v.put(PhinovaDbHelper.P_EMAIL, email);
        v.put(PhinovaDbHelper.P_NGAY_TAO, now);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
        v.put(PhinovaDbHelper.C_UPDATED_AT, now);
        v.put(PhinovaDbHelper.C_IS_DELETED, 0);

        helper.getWritableDatabase().insertWithOnConflict(
                PhinovaDbHelper.T_USER_PROFILES, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Đổi họ tên: PENDING, chờ đẩy lên cloud. Chưa có hồ sơ thì tạo mới. */
    public void updateHoTen(String userId, String hoTen, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.P_HO_TEN, hoTen);
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            v.put(PhinovaDbHelper.C_UPDATED_AT, now);

            int soDong = db.update(PhinovaDbHelper.T_USER_PROFILES, v,
                    PhinovaDbHelper.C_USER_ID + "=?", new String[]{userId});

            if (soDong == 0) {
                v.put(PhinovaDbHelper.C_USER_ID, userId);
                v.put(PhinovaDbHelper.P_NGAY_TAO, now);
                v.put(PhinovaDbHelper.C_IS_DELETED, 0);
                db.insertOrThrow(PhinovaDbHelper.T_USER_PROFILES, null, v);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Ghi hồ sơ kéo từ cloud về. Nếu hồ sơ trên máy đang PENDING (vừa sửa, chưa đẩy lên)
     * thì bỏ qua để không ghi đè.
     */
    public void upsertFromCloud(String userId, String hoTen, String email, long ngayTao) {
        SQLiteDatabase db = helper.getWritableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_USER_PROFILES,
                new String[]{PhinovaDbHelper.C_SYNC_STATUS},
                PhinovaDbHelper.C_USER_ID + "=?", new String[]{userId},
                null, null, null)) {

            if (c.moveToFirst() && PhinovaDbHelper.SYNC_PENDING.equals(c.getString(0))) {
                return;
            }
        }

        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.C_USER_ID, userId);
        v.put(PhinovaDbHelper.P_HO_TEN, hoTen);
        v.put(PhinovaDbHelper.P_EMAIL, email);
        v.put(PhinovaDbHelper.P_NGAY_TAO, ngayTao);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);
        v.put(PhinovaDbHelper.C_UPDATED_AT, System.currentTimeMillis());
        v.put(PhinovaDbHelper.C_IS_DELETED, 0);

        db.insertWithOnConflict(PhinovaDbHelper.T_USER_PROFILES, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }
}