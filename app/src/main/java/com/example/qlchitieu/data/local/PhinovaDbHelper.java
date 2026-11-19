package com.example.qlchitieu.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Tạo và nâng cấp database SQLite của Phinova.
 * Quy ước chung cho mọi bảng:
 *  - Khóa chính là TEXT (dùng chính ID mà Firestore sinh ra) để SQLite và Firestore khớp nhau.
 *  - user_id: tách dữ liệu theo tài khoản (giống users/{uid} trên Firestore).
 *  - Ngày giờ lưu INTEGER = epoch mili-giây (Date.getTime()).
 *  - sync_status: PENDING (chưa lên cloud) / SYNCED (đã lên cloud).
 *  - is_deleted: xóa mềm (1 = đã xóa) để còn đồng bộ việc xóa lên cloud.
 */
public class PhinovaDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "phinova.db";
    private static final int DB_VERSION = 3;

    // ----- Tên bảng -----
    public static final String T_WALLETS = "wallets";
    public static final String T_TRANSACTIONS = "transactions";
    public static final String T_BUDGETS = "budgets";
    public static final String T_ADJUSTMENTS = "balance_adjustments";
    public static final String T_TX_EDITS = "transaction_edits";

    // ----- Cột dùng chung -----
    public static final String C_USER_ID = "user_id";
    public static final String C_SYNC_STATUS = "sync_status";
    public static final String C_UPDATED_AT = "updated_at";
    public static final String C_IS_DELETED = "is_deleted";

    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNC_SYNCED = "SYNCED";

    // ----- wallets -----
    public static final String W_ID = "ma_vi";
    public static final String W_TEN = "ten_vi";
    public static final String W_LOAI = "loai_vi";
    public static final String W_SO_DU = "so_du";
    public static final String W_TRANG_THAI = "trang_thai";
    public static final String W_NGAY_TAO = "ngay_tao";
    public static final String W_NGAY_LUU_TRU = "ngay_luu_tru";

    // ----- transactions -----
    public static final String TX_ID = "ma_giao_dich";
    public static final String TX_LOAI = "loai_giao_dich";
    public static final String TX_DANH_MUC = "danh_muc";
    public static final String TX_GHI_CHU = "ghi_chu";
    public static final String TX_VI_NGUON = "ma_vi_nguon";
    public static final String TX_VI_DICH = "ma_vi_dich";
    public static final String TX_SO_TIEN = "so_tien";
    public static final String TX_NGAY = "ngay_giao_dich";

    // ----- budgets (không lưu so_tien_da_chi: tính bằng SUM từ transactions) -----
    public static final String B_ID = "ma_ngan_sach";
    public static final String B_DANH_MUC = "danh_muc";
    public static final String B_GIOI_HAN = "so_tien_gioi_han";
    public static final String B_THANG = "thang";
    public static final String B_NAM = "nam";
    public static final String B_NGAY_TAO = "ngay_tao";

    // ----- balance_adjustments -----
    public static final String A_ID = "ma_dieu_chinh";
    public static final String A_VI = "ma_vi";
    public static final String A_LY_DO = "ly_do";
    public static final String A_SO_DU_CU = "so_du_cu";
    public static final String A_SO_DU_MOI = "so_du_moi";
    public static final String A_CHENH_LECH = "chenh_lech";
    public static final String A_NGAY = "ngay_dieu_chinh";

    // ----- transaction_edits (nhật ký chỉnh sửa giao dịch) -----
    public static final String E_ID = "ma_chinh_sua";
    public static final String E_MA_GD = "ma_giao_dich";
    public static final String E_LOAI = "loai_giao_dich";
    public static final String E_VI_NGUON = "ma_vi_nguon";
    public static final String E_VI_DICH = "ma_vi_dich";
    public static final String E_TIEN_CU = "so_tien_cu";
    public static final String E_TIEN_MOI = "so_tien_moi";
    public static final String E_DM_CU = "danh_muc_cu";
    public static final String E_DM_MOI = "danh_muc_moi";
    public static final String E_GC_CU = "ghi_chu_cu";
    public static final String E_GC_MOI = "ghi_chu_moi";
    public static final String E_NGAY_CU = "ngay_giao_dich_cu";
    public static final String E_NGAY_MOI = "ngay_giao_dich_moi";
    public static final String E_NGAY_SUA = "ngay_chinh_sua";

    // Cột đồng bộ, chèn vào cuối mỗi bảng
    private static final String SYNC_COLUMNS =
            C_SYNC_STATUS + " TEXT NOT NULL DEFAULT 'PENDING', " +
                    C_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                    C_IS_DELETED + " INTEGER NOT NULL DEFAULT 0";

    private static PhinovaDbHelper instance;

    /** Cả app dùng chung một kết nối database. */
    public static synchronized PhinovaDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new PhinovaDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private PhinovaDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_WALLETS + " (" +
                W_ID + " TEXT PRIMARY KEY, " +
                C_USER_ID + " TEXT NOT NULL, " +
                W_TEN + " TEXT NOT NULL, " +
                W_LOAI + " TEXT, " +
                W_SO_DU + " INTEGER NOT NULL DEFAULT 0, " +
                W_TRANG_THAI + " TEXT NOT NULL DEFAULT 'HOAT_DONG', " +
                W_NGAY_TAO + " INTEGER, " +
                W_NGAY_LUU_TRU + " INTEGER, " +
                SYNC_COLUMNS + ")");

        db.execSQL("CREATE TABLE " + T_TRANSACTIONS + " (" +
                TX_ID + " TEXT PRIMARY KEY, " +
                C_USER_ID + " TEXT NOT NULL, " +
                TX_LOAI + " TEXT NOT NULL, " +
                TX_DANH_MUC + " TEXT, " +
                TX_GHI_CHU + " TEXT, " +
                TX_VI_NGUON + " TEXT, " +
                TX_VI_DICH + " TEXT, " +
                TX_SO_TIEN + " INTEGER NOT NULL, " +
                TX_NGAY + " INTEGER NOT NULL, " +
                SYNC_COLUMNS + ")");

        taoBangNganSach(db);

        db.execSQL("CREATE TABLE " + T_ADJUSTMENTS + " (" +
                A_ID + " TEXT PRIMARY KEY, " +
                C_USER_ID + " TEXT NOT NULL, " +
                A_VI + " TEXT NOT NULL, " +
                A_LY_DO + " TEXT, " +
                A_SO_DU_CU + " INTEGER NOT NULL, " +
                A_SO_DU_MOI + " INTEGER NOT NULL, " +
                A_CHENH_LECH + " INTEGER NOT NULL, " +
                A_NGAY + " INTEGER NOT NULL, " +
                SYNC_COLUMNS + ")");

        taoBangChinhSua(db);

        // Index cho các truy vấn thường dùng
        db.execSQL("CREATE INDEX idx_wallets_user ON " + T_WALLETS + "(" + C_USER_ID + ")");
        db.execSQL("CREATE INDEX idx_tx_user_date ON " + T_TRANSACTIONS +
                "(" + C_USER_ID + ", " + TX_NGAY + ")");
        db.execSQL("CREATE INDEX idx_adj_wallet ON " + T_ADJUSTMENTS +
                "(" + A_VI + ", " + A_NGAY + ")");
    }

    /**
     * Ngân sách theo tháng. Khóa chính gồm user_id + mã ngân sách vì mã (2026_11_an_uong)
     * không chứa user, hai tài khoản trên cùng một máy sẽ bị trùng nếu chỉ dùng mã làm khóa.
     */
    private void taoBangNganSach(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_BUDGETS + " (" +
                B_ID + " TEXT NOT NULL, " +
                C_USER_ID + " TEXT NOT NULL, " +
                B_DANH_MUC + " TEXT NOT NULL, " +
                B_GIOI_HAN + " INTEGER NOT NULL, " +
                B_THANG + " INTEGER NOT NULL, " +
                B_NAM + " INTEGER NOT NULL, " +
                B_NGAY_TAO + " INTEGER, " +
                SYNC_COLUMNS + ", " +
                "PRIMARY KEY (" + C_USER_ID + ", " + B_ID + "))");

        db.execSQL("CREATE INDEX idx_budget_user_month ON " + T_BUDGETS +
                "(" + C_USER_ID + ", " + B_NAM + ", " + B_THANG + ")");
    }

    /** Nhật ký mỗi lần chỉnh sửa giao dịch (được đẩy lên Firestore cùng lúc với việc sửa). */
    private void taoBangChinhSua(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_TX_EDITS + " (" +
                E_ID + " TEXT PRIMARY KEY, " +
                C_USER_ID + " TEXT NOT NULL, " +
                E_MA_GD + " TEXT NOT NULL, " +
                E_LOAI + " TEXT, " +
                E_VI_NGUON + " TEXT, " +
                E_VI_DICH + " TEXT, " +
                E_TIEN_CU + " INTEGER NOT NULL, " +
                E_TIEN_MOI + " INTEGER NOT NULL, " +
                E_DM_CU + " TEXT, " +
                E_DM_MOI + " TEXT, " +
                E_GC_CU + " TEXT, " +
                E_GC_MOI + " TEXT, " +
                E_NGAY_CU + " INTEGER NOT NULL, " +
                E_NGAY_MOI + " INTEGER NOT NULL, " +
                E_NGAY_SUA + " INTEGER NOT NULL, " +
                SYNC_COLUMNS + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nâng cấp từng bước, giữ nguyên dữ liệu đang có.
        if (oldVersion < 2) {
            taoBangChinhSua(db);
        }
        if (oldVersion < 3) {
            // Bảng budgets ở v2 chưa có code nào ghi vào, nên tạo lại với khóa chính mới
            // không làm mất dữ liệu.
            db.execSQL("DROP TABLE IF EXISTS " + T_BUDGETS);
            taoBangNganSach(db);
        }
    }
}