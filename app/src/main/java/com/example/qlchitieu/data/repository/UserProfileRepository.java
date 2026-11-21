package com.example.qlchitieu.data.repository;

import android.content.Context;

import com.example.qlchitieu.data.local.UserProfileDao;
import com.example.qlchitieu.data.sync.SyncManager;

/**
 * Hồ sơ người dùng (hiện có họ tên và email).
 *
 * Mọi thứ đọc/ghi qua SQLite (bảng user_profiles) nên dùng được cả khi mất mạng.
 * Việc đưa lên / lấy về Firestore (users/{uid}) do SyncManager đảm nhiệm,
 * lớp này không gọi Firestore.
 */
public class UserProfileRepository {

    private final UserProfileDao dao;
    private final Context appContext;

    public UserProfileRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = new UserProfileDao(appContext);
    }

    /** Họ tên đã lưu trên máy, hoặc null nếu chưa có. */
    public String getHoTen(String uid) {
        return dao.getHoTen(uid);
    }

    /** Hồ sơ của tài khoản vừa đăng ký: lưu SQLite rồi nhờ SyncManager đẩy lên cloud. */
    public void taoHoSo(String uid, String hoTen, String email) {
        dao.create(uid, hoTen.trim(), email, System.currentTimeMillis());
        SyncManager.getInstance(appContext).syncNow();
    }

    /** Đổi họ tên: lưu SQLite ngay, SyncManager sẽ đẩy lên cloud khi có mạng. */
    public void luuHoTen(String uid, String hoTen) {
        dao.updateHoTen(uid, hoTen.trim(), System.currentTimeMillis());
        SyncManager.getInstance(appContext).syncNow();
    }
}