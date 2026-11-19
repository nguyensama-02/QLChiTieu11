package com.example.qlchitieu.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.qlchitieu.model.Transaction;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chỉ làm việc với SQLite cho bảng transactions.
 * Mọi thao tác làm thay đổi tiền (thêm giao dịch...) cập nhật số dư ví
 * trong CÙNG MỘT transaction SQLite với việc ghi giao dịch.
 */
public class TransactionDao {

    private final PhinovaDbHelper helper;

    public TransactionDao(Context context) {
        helper = PhinovaDbHelper.getInstance(context);
    }

    /** Trạng thái tối thiểu của một ví, dùng để kiểm tra trước khi giao dịch. */
    private static class WalletState {
        String trangThai;
        long soDu;
    }

    private WalletState readWallet(SQLiteDatabase db, String userId, String maVi) {
        if (maVi == null) {
            return null;
        }

        try (Cursor c = db.query(
                PhinovaDbHelper.T_WALLETS,
                new String[]{PhinovaDbHelper.W_TRANG_THAI, PhinovaDbHelper.W_SO_DU},
                PhinovaDbHelper.W_ID + "=? AND " + PhinovaDbHelper.C_USER_ID + "=? AND "
                        + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{maVi, userId},
                null, null, null)) {

            if (!c.moveToFirst()) {
                return null;
            }

            WalletState w = new WalletState();
            w.trangThai = c.getString(0);
            w.soDu = c.getLong(1);
            return w;
        }
    }

    private void setBalance(SQLiteDatabase db, String maVi, long soDuMoi, long now) {
        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.W_SO_DU, soDuMoi);
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
        v.put(PhinovaDbHelper.C_UPDATED_AT, now);
        db.update(PhinovaDbHelper.T_WALLETS, v,
                PhinovaDbHelper.W_ID + "=?", new String[]{maVi});
    }

    /**
     * Thêm giao dịch mới và cập nhật số dư ví liên quan trong một transaction SQLite.
     * Ném IllegalStateException (thông báo tiếng Việt) nếu không hợp lệ.
     */
    public void insertAndApply(String userId, Transaction t, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            String loai = t.getLoaiGiaoDich();
            long soTien = t.getSoTien();

            WalletState nguon = readWallet(db, userId, t.getMaViNguon());

            if (nguon == null) {
                throw new IllegalStateException("Ví nguồn không tồn tại");
            }
            if ("LUU_TRU".equals(nguon.trangThai)) {
                throw new IllegalStateException("Ví nguồn đã lưu trữ, không thể giao dịch");
            }
            if (!"HOAT_DONG".equals(nguon.trangThai)) {
                throw new IllegalStateException("Trạng thái ví nguồn không hợp lệ");
            }

            try {
                if ("Thu nhập".equals(loai)) {

                    setBalance(db, t.getMaViNguon(),
                            Math.addExact(nguon.soDu, soTien), now);

                } else if ("Chi tiêu".equals(loai)) {

                    if (nguon.soDu < soTien) {
                        throw new IllegalStateException("Số dư ví không đủ");
                    }
                    setBalance(db, t.getMaViNguon(), nguon.soDu - soTien, now);

                } else {

                    WalletState dich = readWallet(db, userId, t.getMaViDich());

                    if (dich == null) {
                        throw new IllegalStateException("Ví nhận không tồn tại");
                    }
                    if ("LUU_TRU".equals(dich.trangThai)) {
                        throw new IllegalStateException("Ví nhận đã lưu trữ, không thể chuyển tiền");
                    }
                    if (!"HOAT_DONG".equals(dich.trangThai)) {
                        throw new IllegalStateException("Trạng thái ví nhận không hợp lệ");
                    }
                    if (nguon.soDu < soTien) {
                        throw new IllegalStateException("Số dư ví nguồn không đủ");
                    }

                    setBalance(db, t.getMaViNguon(), nguon.soDu - soTien, now);
                    setBalance(db, t.getMaViDich(), Math.addExact(dich.soDu, soTien), now);
                }
            } catch (ArithmeticException e) {
                throw new IllegalStateException("Số dư vượt quá giới hạn cho phép");
            }

            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.TX_ID, t.getMaGiaoDich());
            v.put(PhinovaDbHelper.C_USER_ID, userId);
            v.put(PhinovaDbHelper.TX_LOAI, loai);
            v.put(PhinovaDbHelper.TX_DANH_MUC, t.getDanhMuc());
            v.put(PhinovaDbHelper.TX_GHI_CHU, t.getGhiChu());
            v.put(PhinovaDbHelper.TX_VI_NGUON, t.getMaViNguon());
            if (t.getMaViDich() == null) {
                v.putNull(PhinovaDbHelper.TX_VI_DICH);
            } else {
                v.put(PhinovaDbHelper.TX_VI_DICH, t.getMaViDich());
            }
            v.put(PhinovaDbHelper.TX_SO_TIEN, soTien);
            v.put(PhinovaDbHelper.TX_NGAY, t.getNgayGiaoDich().getTime());
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            v.put(PhinovaDbHelper.C_UPDATED_AT, now);
            v.put(PhinovaDbHelper.C_IS_DELETED, 0);
            db.insertOrThrow(PhinovaDbHelper.T_TRANSACTIONS, null, v);

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    /** Tất cả giao dịch chưa xóa của một người dùng, mới nhất lên đầu. */
    public List<Transaction> getAll(String userId) {
        List<Transaction> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_TRANSACTIONS,
                null,
                PhinovaDbHelper.C_USER_ID + "=? AND " + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{userId},
                null, null,
                PhinovaDbHelper.TX_NGAY + " DESC, " + PhinovaDbHelper.C_UPDATED_AT + " DESC")) {

            while (c.moveToNext()) {
                list.add(new Transaction(
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_ID)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_LOAI)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_DANH_MUC)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_GHI_CHU)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_VI_NGUON)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_VI_DICH)),
                        c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_SO_TIEN)),
                        new Date(c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_NGAY)))));
            }
        }
        return list;
    }

    /** Giao dịch đã đẩy lên cloud thành công; ví liên quan cũng đã khớp với cloud. */
    public void markSynced(String maGiaoDich, String maViNguon, String maViDich) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);

            db.update(PhinovaDbHelper.T_TRANSACTIONS, v,
                    PhinovaDbHelper.TX_ID + "=?", new String[]{maGiaoDich});

            if (maViNguon != null) {
                db.update(PhinovaDbHelper.T_WALLETS, v,
                        PhinovaDbHelper.W_ID + "=?", new String[]{maViNguon});
            }
            if (maViDich != null) {
                db.update(PhinovaDbHelper.T_WALLETS, v,
                        PhinovaDbHelper.W_ID + "=?", new String[]{maViDich});
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Ghi một giao dịch kéo từ cloud về. Nếu giao dịch này đang PENDING trên máy
     * thì bỏ qua để không ghi đè.
     */
    public void upsertFromCloud(String userId, Transaction t) {
        SQLiteDatabase db = helper.getWritableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_TRANSACTIONS,
                new String[]{PhinovaDbHelper.C_SYNC_STATUS},
                PhinovaDbHelper.TX_ID + "=?", new String[]{t.getMaGiaoDich()},
                null, null, null)) {

            if (c.moveToFirst() && PhinovaDbHelper.SYNC_PENDING.equals(c.getString(0))) {
                return;
            }
        }

        ContentValues v = new ContentValues();
        v.put(PhinovaDbHelper.TX_ID, t.getMaGiaoDich());
        v.put(PhinovaDbHelper.C_USER_ID, userId);
        v.put(PhinovaDbHelper.TX_LOAI, t.getLoaiGiaoDich());
        v.put(PhinovaDbHelper.TX_DANH_MUC, t.getDanhMuc());
        v.put(PhinovaDbHelper.TX_GHI_CHU, t.getGhiChu());
        v.put(PhinovaDbHelper.TX_VI_NGUON, t.getMaViNguon());
        if (t.getMaViDich() == null) {
            v.putNull(PhinovaDbHelper.TX_VI_DICH);
        } else {
            v.put(PhinovaDbHelper.TX_VI_DICH, t.getMaViDich());
        }
        v.put(PhinovaDbHelper.TX_SO_TIEN, t.getSoTien());
        v.put(PhinovaDbHelper.TX_NGAY, t.getNgayGiaoDich().getTime());
        v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);
        v.put(PhinovaDbHelper.C_UPDATED_AT, System.currentTimeMillis());
        v.put(PhinovaDbHelper.C_IS_DELETED, 0);

        db.insertWithOnConflict(PhinovaDbHelper.T_TRANSACTIONS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Xóa khỏi SQLite những giao dịch đã đồng bộ (SYNCED) mà không còn trên cloud
     * (ví dụ đã bị xóa từ màn hình chưa chuyển sang SQLite). Giao dịch PENDING được giữ lại.
     */
    public void deleteSyncedNotIn(String userId, Set<String> idTrenCloud) {
        SQLiteDatabase db = helper.getWritableDatabase();
        List<String> canXoa = new ArrayList<>();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_TRANSACTIONS,
                new String[]{PhinovaDbHelper.TX_ID},
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
            db.delete(PhinovaDbHelper.T_TRANSACTIONS,
                    PhinovaDbHelper.TX_ID + "=?", new String[]{id});
        }
    }

    // ------------------------------------------------------------------
    //  Đọc một giao dịch, sửa giao dịch, xóa giao dịch
    // ------------------------------------------------------------------

    private static String chuoi(String s) {
        return s == null ? "" : s;
    }

    private Transaction docGiaoDich(SQLiteDatabase db, String userId, String maGiaoDich) {
        try (Cursor c = db.query(
                PhinovaDbHelper.T_TRANSACTIONS,
                null,
                PhinovaDbHelper.TX_ID + "=? AND " + PhinovaDbHelper.C_USER_ID + "=? AND "
                        + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{maGiaoDich, userId},
                null, null, null)) {

            if (!c.moveToFirst()) {
                return null;
            }

            return new Transaction(
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_ID)),
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_LOAI)),
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_DANH_MUC)),
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_GHI_CHU)),
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_VI_NGUON)),
                    c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_VI_DICH)),
                    c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_SO_TIEN)),
                    new Date(c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_NGAY))));
        }
    }

    /** Lấy một giao dịch theo ID (null nếu không có hoặc đã xóa). */
    public Transaction getById(String userId, String maGiaoDich) {
        return docGiaoDich(helper.getReadableDatabase(), userId, maGiaoDich);
    }

    /**
     * Sửa số tiền / danh mục / ghi chú / ngày của giao dịch, tính lại số dư các ví liên quan
     * và ghi nhật ký chỉnh sửa, tất cả trong một transaction SQLite.
     *
     * @param cu giá trị cũ mà màn hình đang dựa vào; nếu giao dịch thật đã khác thì báo lỗi.
     */
    public void updateAndRecalculate(String userId, Transaction cu, long soTienMoi,
                                     String danhMucMoi, String ghiChuMoi, Date ngayMoi,
                                     String maChinhSua, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            Transaction hienTai = docGiaoDich(db, userId, cu.getMaGiaoDich());

            if (hienTai == null) {
                throw new IllegalStateException("Giao dịch không còn tồn tại");
            }

            if (!Objects.equals(hienTai.getLoaiGiaoDich(), cu.getLoaiGiaoDich())
                    || hienTai.getSoTien() != cu.getSoTien()
                    || !Objects.equals(hienTai.getMaViNguon(), cu.getMaViNguon())
                    || !Objects.equals(hienTai.getMaViDich(), cu.getMaViDich())
                    || !chuoi(hienTai.getDanhMuc()).equals(chuoi(cu.getDanhMuc()))
                    || !chuoi(hienTai.getGhiChu()).equals(chuoi(cu.getGhiChu()))
                    || hienTai.getNgayGiaoDich().getTime() != cu.getNgayGiaoDich().getTime()) {

                throw new IllegalStateException(
                        "Giao dịch đã được thay đổi. Vui lòng mở lại để cập nhật dữ liệu");
            }

            String loai = cu.getLoaiGiaoDich();

            WalletState nguon = readWallet(db, userId, cu.getMaViNguon());

            if (nguon == null) {
                throw new IllegalStateException("Ví liên quan không tồn tại");
            }
            if (!"HOAT_DONG".equals(nguon.trangThai)) {
                throw new IllegalStateException("Ví đã lưu trữ, không thể chỉnh sửa");
            }

            WalletState dich = null;

            if ("Chuyển tiền".equals(loai)) {
                dich = readWallet(db, userId, cu.getMaViDich());

                if (dich == null) {
                    throw new IllegalStateException("Ví nhận không tồn tại");
                }
                if (!"HOAT_DONG".equals(dich.trangThai)) {
                    throw new IllegalStateException("Ví nhận đã lưu trữ");
                }
            }

            try {
                long chenhLech = Math.subtractExact(soTienMoi, cu.getSoTien());

                if ("Thu nhập".equals(loai)) {

                    long soDuMoi = Math.addExact(nguon.soDu, chenhLech);

                    if (soDuMoi < 0) {
                        throw new IllegalStateException("Không đủ số dư để giảm khoản thu nhập");
                    }
                    setBalance(db, cu.getMaViNguon(), soDuMoi, now);

                } else if ("Chi tiêu".equals(loai)) {

                    long soDuMoi = Math.subtractExact(nguon.soDu, chenhLech);

                    if (soDuMoi < 0) {
                        throw new IllegalStateException("Không đủ số dư để tăng khoản chi tiêu");
                    }
                    setBalance(db, cu.getMaViNguon(), soDuMoi, now);

                } else {

                    long soDuNguonMoi = Math.subtractExact(nguon.soDu, chenhLech);
                    long soDuDichMoi = Math.addExact(dich.soDu, chenhLech);

                    if (soDuNguonMoi < 0 || soDuDichMoi < 0) {
                        throw new IllegalStateException("Số dư một trong hai ví không đủ");
                    }
                    setBalance(db, cu.getMaViNguon(), soDuNguonMoi, now);
                    setBalance(db, cu.getMaViDich(), soDuDichMoi, now);
                }
            } catch (ArithmeticException e) {
                throw new IllegalStateException("Số dư vượt quá giới hạn cho phép");
            }

            // Cập nhật giao dịch
            ContentValues gd = new ContentValues();
            gd.put(PhinovaDbHelper.TX_SO_TIEN, soTienMoi);
            gd.put(PhinovaDbHelper.TX_DANH_MUC, danhMucMoi);
            gd.put(PhinovaDbHelper.TX_GHI_CHU, ghiChuMoi);
            gd.put(PhinovaDbHelper.TX_NGAY, ngayMoi.getTime());
            gd.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            gd.put(PhinovaDbHelper.C_UPDATED_AT, now);
            db.update(PhinovaDbHelper.T_TRANSACTIONS, gd,
                    PhinovaDbHelper.TX_ID + "=?", new String[]{cu.getMaGiaoDich()});

            // Ghi nhật ký chỉnh sửa
            ContentValues log = new ContentValues();
            log.put(PhinovaDbHelper.E_ID, maChinhSua);
            log.put(PhinovaDbHelper.C_USER_ID, userId);
            log.put(PhinovaDbHelper.E_MA_GD, cu.getMaGiaoDich());
            log.put(PhinovaDbHelper.E_LOAI, loai);
            log.put(PhinovaDbHelper.E_VI_NGUON, cu.getMaViNguon());
            log.put(PhinovaDbHelper.E_VI_DICH, cu.getMaViDich());
            log.put(PhinovaDbHelper.E_TIEN_CU, cu.getSoTien());
            log.put(PhinovaDbHelper.E_TIEN_MOI, soTienMoi);
            log.put(PhinovaDbHelper.E_DM_CU, cu.getDanhMuc());
            log.put(PhinovaDbHelper.E_DM_MOI, danhMucMoi);
            log.put(PhinovaDbHelper.E_GC_CU, cu.getGhiChu());
            log.put(PhinovaDbHelper.E_GC_MOI, ghiChuMoi);
            log.put(PhinovaDbHelper.E_NGAY_CU, cu.getNgayGiaoDich().getTime());
            log.put(PhinovaDbHelper.E_NGAY_MOI, ngayMoi.getTime());
            log.put(PhinovaDbHelper.E_NGAY_SUA, now);
            log.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            log.put(PhinovaDbHelper.C_UPDATED_AT, now);
            log.put(PhinovaDbHelper.C_IS_DELETED, 0);
            db.insertOrThrow(PhinovaDbHelper.T_TX_EDITS, null, log);

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    /** Lần sửa đã đẩy lên cloud thành công: nhật ký, giao dịch và các ví liên quan đều khớp. */
    public void markEditSynced(String maChinhSua, String maGiaoDich,
                               String maViNguon, String maViDich) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);

            db.update(PhinovaDbHelper.T_TX_EDITS, v,
                    PhinovaDbHelper.E_ID + "=?", new String[]{maChinhSua});
            db.update(PhinovaDbHelper.T_TRANSACTIONS, v,
                    PhinovaDbHelper.TX_ID + "=?", new String[]{maGiaoDich});

            if (maViNguon != null) {
                db.update(PhinovaDbHelper.T_WALLETS, v,
                        PhinovaDbHelper.W_ID + "=?", new String[]{maViNguon});
            }
            if (maViDich != null) {
                db.update(PhinovaDbHelper.T_WALLETS, v,
                        PhinovaDbHelper.W_ID + "=?", new String[]{maViDich});
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Xóa giao dịch: hoàn tác số dư các ví liên quan và xóa mềm giao dịch
     * trong một transaction SQLite. Trả về giao dịch vừa xóa (để ghi nhật ký lên cloud).
     */
    public Transaction deleteAndRevert(String userId, String maGiaoDich, long now) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            Transaction gd = docGiaoDich(db, userId, maGiaoDich);

            if (gd == null) {
                throw new IllegalStateException("Giao dịch không tồn tại");
            }

            String loai = gd.getLoaiGiaoDich();
            String maNguon = gd.getMaViNguon();
            String maDich = gd.getMaViDich();
            long soTien = gd.getSoTien();

            if (maNguon == null || maNguon.isEmpty()) {
                throw new IllegalStateException("Giao dịch thiếu thông tin ví");
            }

            boolean laChuyen = "Chuyển tiền".equals(loai);

            if (laChuyen && (maDich == null || maDich.isEmpty() || maNguon.equals(maDich))) {
                throw new IllegalStateException("Thông tin ví nhận không hợp lệ");
            }

            if (!"Thu nhập".equals(loai) && !"Chi tiêu".equals(loai) && !laChuyen) {
                throw new IllegalStateException("Loại giao dịch không hỗ trợ xóa");
            }

            if (soTien <= 0) {
                throw new IllegalStateException("Số tiền giao dịch không hợp lệ");
            }

            WalletState nguon = readWallet(db, userId, maNguon);

            if (nguon == null) {
                throw new IllegalStateException("Ví nguồn không tồn tại");
            }
            if (!"HOAT_DONG".equals(nguon.trangThai)) {
                throw new IllegalStateException("Ví nguồn đã ngừng hoạt động");
            }

            WalletState dich = null;

            if (laChuyen) {
                dich = readWallet(db, userId, maDich);

                if (dich == null) {
                    throw new IllegalStateException("Ví nhận không tồn tại");
                }
                if (!"HOAT_DONG".equals(dich.trangThai)) {
                    throw new IllegalStateException("Ví nhận đã ngừng hoạt động");
                }
            }

            try {
                if ("Thu nhập".equals(loai)) {

                    long soDuMoi = Math.subtractExact(nguon.soDu, soTien);

                    if (soDuMoi < 0) {
                        throw new IllegalStateException("Số dư không đủ để hoàn tác thu nhập");
                    }
                    setBalance(db, maNguon, soDuMoi, now);

                } else if ("Chi tiêu".equals(loai)) {

                    setBalance(db, maNguon, Math.addExact(nguon.soDu, soTien), now);

                } else {

                    long soDuNguonMoi = Math.addExact(nguon.soDu, soTien);
                    long soDuDichMoi = Math.subtractExact(dich.soDu, soTien);

                    if (soDuDichMoi < 0) {
                        throw new IllegalStateException("Ví nhận không đủ số dư để hoàn tác");
                    }
                    setBalance(db, maNguon, soDuNguonMoi, now);
                    setBalance(db, maDich, soDuDichMoi, now);
                }
            } catch (ArithmeticException e) {
                throw new IllegalStateException("Số dư vượt quá giới hạn cho phép");
            }

            // Xóa mềm: ẩn khỏi app, chờ cloud xóa xong rồi mới xóa hẳn
            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.C_IS_DELETED, 1);
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_PENDING);
            v.put(PhinovaDbHelper.C_UPDATED_AT, now);
            db.update(PhinovaDbHelper.T_TRANSACTIONS, v,
                    PhinovaDbHelper.TX_ID + "=?", new String[]{maGiaoDich});

            db.setTransactionSuccessful();
            return gd;

        } finally {
            db.endTransaction();
        }
    }

    /** Cloud đã xóa xong: xóa hẳn dòng giao dịch ở SQLite, các ví liên quan khớp với cloud. */
    public void purgeDeleted(String maGiaoDich, String maViNguon, String maViDich) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();

        try {
            db.delete(PhinovaDbHelper.T_TRANSACTIONS,
                    PhinovaDbHelper.TX_ID + "=?", new String[]{maGiaoDich});

            ContentValues v = new ContentValues();
            v.put(PhinovaDbHelper.C_SYNC_STATUS, PhinovaDbHelper.SYNC_SYNCED);

            if (maViNguon != null) {
                db.update(PhinovaDbHelper.T_WALLETS, v,
                        PhinovaDbHelper.W_ID + "=?", new String[]{maViNguon});
            }
            if (maViDich != null) {
                db.update(PhinovaDbHelper.T_WALLETS, v,
                        PhinovaDbHelper.W_ID + "=?", new String[]{maViDich});
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ------------------------------------------------------------------
    //  Truy vấn tổng hợp (Dashboard, Thống kê): tính bằng SQL, không tải hết giao dịch
    // ------------------------------------------------------------------

    /**
     * Tổng thu và tổng chi trong khoảng [tuNgay, denNgay) (epoch ms).
     * @return mảng {tổng thu nhập, tổng chi tiêu}; chuyển tiền không được tính.
     */
    public long[] sumIncomeExpense(String userId, long tuNgay, long denNgay) {
        long thu = 0;
        long chi = 0;

        SQLiteDatabase db = helper.getReadableDatabase();

        String sql = "SELECT " + PhinovaDbHelper.TX_LOAI + ", SUM(" + PhinovaDbHelper.TX_SO_TIEN + ")"
                + " FROM " + PhinovaDbHelper.T_TRANSACTIONS
                + " WHERE " + PhinovaDbHelper.C_USER_ID + "=?"
                + " AND " + PhinovaDbHelper.C_IS_DELETED + "=0"
                + " AND " + PhinovaDbHelper.TX_SO_TIEN + ">0"
                + " AND " + PhinovaDbHelper.TX_NGAY + ">=?"
                + " AND " + PhinovaDbHelper.TX_NGAY + "<?"
                + " GROUP BY " + PhinovaDbHelper.TX_LOAI;

        try (Cursor c = db.rawQuery(sql,
                new String[]{userId, String.valueOf(tuNgay), String.valueOf(denNgay)})) {

            while (c.moveToNext()) {
                String loai = c.getString(0);

                if ("Thu nhập".equals(loai)) {
                    thu = c.getLong(1);
                } else if ("Chi tiêu".equals(loai)) {
                    chi = c.getLong(1);
                }
            }
        }
        return new long[]{thu, chi};
    }

    /**
     * Tổng chi tiêu theo danh mục trong khoảng [tuNgay, denNgay).
     * Giao dịch không có danh mục được gộp vào "Chi tiêu khác".
     */
    public HashMap<String, Long> sumExpenseByCategory(String userId, long tuNgay, long denNgay) {
        HashMap<String, Long> ketQua = new HashMap<>();

        SQLiteDatabase db = helper.getReadableDatabase();

        String sql = "SELECT " + PhinovaDbHelper.TX_DANH_MUC + ", SUM(" + PhinovaDbHelper.TX_SO_TIEN + ")"
                + " FROM " + PhinovaDbHelper.T_TRANSACTIONS
                + " WHERE " + PhinovaDbHelper.C_USER_ID + "=?"
                + " AND " + PhinovaDbHelper.C_IS_DELETED + "=0"
                + " AND " + PhinovaDbHelper.TX_LOAI + "=?"
                + " AND " + PhinovaDbHelper.TX_SO_TIEN + ">0"
                + " AND " + PhinovaDbHelper.TX_NGAY + ">=?"
                + " AND " + PhinovaDbHelper.TX_NGAY + "<?"
                + " GROUP BY " + PhinovaDbHelper.TX_DANH_MUC;

        try (Cursor c = db.rawQuery(sql,
                new String[]{userId, "Chi tiêu",
                        String.valueOf(tuNgay), String.valueOf(denNgay)})) {

            while (c.moveToNext()) {
                String danhMuc = c.getString(0);

                if (danhMuc == null || danhMuc.trim().isEmpty()) {
                    danhMuc = "Chi tiêu khác";
                }

                long cu = ketQua.containsKey(danhMuc) ? ketQua.get(danhMuc) : 0L;
                ketQua.put(danhMuc, cu + c.getLong(1));
            }
        }
        return ketQua;
    }

    /** Một số giao dịch mới nhất (dùng cho "Giao dịch gần đây"). */
    public List<Transaction> getRecent(String userId, int soLuong) {
        List<Transaction> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.query(
                PhinovaDbHelper.T_TRANSACTIONS,
                null,
                PhinovaDbHelper.C_USER_ID + "=? AND " + PhinovaDbHelper.C_IS_DELETED + "=0",
                new String[]{userId},
                null, null,
                PhinovaDbHelper.TX_NGAY + " DESC, " + PhinovaDbHelper.C_UPDATED_AT + " DESC",
                String.valueOf(soLuong))) {

            while (c.moveToNext()) {
                list.add(new Transaction(
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_ID)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_LOAI)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_DANH_MUC)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_GHI_CHU)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_VI_NGUON)),
                        c.getString(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_VI_DICH)),
                        c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_SO_TIEN)),
                        new Date(c.getLong(c.getColumnIndexOrThrow(PhinovaDbHelper.TX_NGAY)))));
            }
        }
        return list;
    }
}