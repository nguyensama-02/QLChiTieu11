package com.example.qlchitieu.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Các thao tác dùng chung cho SyncManager, áp dụng cho mọi bảng có cột
 * user_id, sync_status, updated_at, is_deleted.
 */
public class SyncDao {

    private final PhinovaDbHelper helper;

    public SyncDao(Context context) {
        helper = PhinovaDbHelper.getInstance(context);
    }

    /**
     * Các dòng đang PENDING (chưa đẩy lên cloud) của một người dùng, kể cả dòng đã xóa mềm,
     * cũ nhất trước. Mỗi dòng là một map: tên cột -> giá trị (Long, String hoặc null).
     */
    public List<Map<String, Object>> getPending(String table, String userId) {
        List<Map<String, Object>> danhSach = new ArrayList<>();

        try (Cursor c = helper.getReadableDatabase().query(
                table,
                null,
                PhinovaDbHelper.C_USER_ID + "=? AND " + PhinovaDbHelper.C_SYNC_STATUS + "=?",
                new String[]{userId, PhinovaDbHelper.SYNC_PENDING},
                null, null,
                PhinovaDbHelper.C_UPDATED_AT + " ASC")) {

            while (c.moveToNext()) {
                Map<String, Object> dong = new HashMap<>();

                for (int i = 0; i < c.getColumnCount(); i++) {
                    String ten = c.getColumnName(i);

                    switch (c.getType(i)) {
                        case Cursor.FIELD_TYPE_INTEGER:
                            dong.put(ten, c.getLong(i));
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            dong.put(ten, c.getString(i));
                            break;
                        default:
                            dong.put(ten, null);
                    }
                }

                danhSach.add(dong);
            }
        }

        return danhSach;
    }

    /**
     * Đã đẩy lên cloud thành công. Chỉ đánh dấu SYNCED nếu dòng chưa bị sửa tiếp kể từ lúc đẩy
     * (updated_at còn nguyên), để không đánh dấu nhầm một thay đổi mới hơn.
     */
    public void markSynced(String table, String idColumn, String id, long updatedAt) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);

        helper.getWritableDatabase().update(
                table, v,
                idColumn + "=? AND " + PhinovaDbHelper.C_UPDATED_AT + "=?",
                new String[]{id, String.valueOf(updatedAt)});
    }

    /**
     * Cloud đã xóa xong: xóa hẳn dòng ở SQLite. Chỉ xóa nếu dòng vẫn đang ở trạng thái đã xóa
     * và chưa bị đổi kể từ lúc đẩy.
     */
    public void purge(String table, String idColumn, String id, long updatedAt) {
        helper.getWritableDatabase().delete(
                table,
                idColumn + "=? AND " + PhinovaDbHelper.C_IS_DELETED + "=1 AND "
                        + PhinovaDbHelper.C_UPDATED_AT + "=?",
                new String[]{id, String.valueOf(updatedAt)});
    }
}