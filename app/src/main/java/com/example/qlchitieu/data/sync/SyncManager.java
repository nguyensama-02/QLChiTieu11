package com.example.qlchitieu.data.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.example.qlchitieu.data.local.BudgetDao;
import com.example.qlchitieu.data.local.PhinovaDbHelper;
import com.example.qlchitieu.data.local.SyncDao;
import com.example.qlchitieu.data.local.UserProfileDao;
import com.example.qlchitieu.data.local.WalletDao;
import com.example.qlchitieu.data.repository.BalanceAdjustmentRepository;
import com.example.qlchitieu.data.repository.BudgetRepository;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.model.Wallet;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Đồng bộ SQLite <-> Firestore.
 *
 * Mỗi lần chạy gồm hai pha, theo thứ tự:
 *  1) ĐẨY: mọi dòng sync_status = PENDING (ví, điều chỉnh số dư, giao dịch, nhật ký sửa giao dịch,
 *     ngân sách; kể cả dòng đã xóa mềm) lên Firestore. Thành công thì đánh dấu SYNCED
 *     (hoặc xóa hẳn dòng nếu là dòng đã xóa). Thất bại thì giữ PENDING để lần sau thử lại.
 *  2) KÉO: lấy dữ liệu từ Firestore về SQLite (dòng PENDING trên máy không bị ghi đè).
 *
 * Mọi thao tác đẩy đều "ghi đè theo ID" nên thử lại bao nhiêu lần cũng không tạo bản ghi trùng.
 * SQLite trên máy này là nguồn sự thật; Firestore là bản sao lưu.
 *
 * Tự chạy khi: có mạng trở lại, và khi màn hình chính được mở. Chỉ một lần chạy tại một thời điểm.
 */
public final class SyncManager {

    public interface Listener {
        void onDone(boolean thanhCong, String loi);
    }

    private interface KetQua {
        void onDone(boolean thanhCong, String loi);
    }

    private interface BatDauKeo {
        void chay(KetQua ketQua);
    }

    /**
     * Nơi ghi thời điểm đồng bộ thành công gần nhất, theo uid. Trang Cá nhân đọc giá trị này để
     * hiện "Đã đồng bộ lúc 14:32". Hai hằng số trùng giá trị với ProfileActivity.PREFS_DONG_BO
     * và ProfileActivity.KEY_LAN_DONG_BO_CUOI: nếu đổi ở đây thì phải đổi cả bên đó.
     */
    public static final String PREFS_DONG_BO = "phinova_sync";
    public static final String KEY_LAN_DONG_BO_CUOI = "last_sync_ms_";

    /** Thời gian chờ tối đa cho mỗi lần gọi Firestore. */
    private static final long CHO_TOI_DA_GIAY = 25;

    private static SyncManager instance;

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context.getApplicationContext());
            instance.theoDoiMang();
        }
        return instance;
    }

    private final Context appContext;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final SyncDao syncDao;
    private final WalletDao walletDao;
    private final BudgetDao budgetDao;
    private final UserProfileDao userProfileDao;

    private final AtomicBoolean dangChay = new AtomicBoolean(false);
    private volatile boolean canChayLai = false;
    private final List<Listener> dangCho = new ArrayList<>();

    private SyncManager(Context context) {
        appContext = context;
        syncDao = new SyncDao(context);
        walletDao = new WalletDao(context);
        budgetDao = new BudgetDao(context);
        userProfileDao = new UserProfileDao(context);
    }

    // ------------------------------------------------------------------
    //  Điểm vào
    // ------------------------------------------------------------------

    public void syncNow() {
        syncNow(null);
    }

    /**
     * Chạy đồng bộ ngay. Nếu đang có một lần chạy thì lần này được gộp vào (chạy thêm một vòng
     * sau khi xong). listener (có thể null) được gọi trên main thread khi xong.
     */
    public void syncNow(Listener listener) {

        FirebaseUser nguoiDung = FirebaseAuth.getInstance().getCurrentUser();

        if (nguoiDung == null) {
            if (listener != null) {
                mainHandler.post(() -> listener.onDone(false, "Chưa đăng nhập"));
            }
            return;
        }

        if (listener != null) {
            synchronized (dangCho) {
                dangCho.add(listener);
            }
        }

        if (!dangChay.compareAndSet(false, true)) {
            canChayLai = true;
            return;
        }

        String uid = nguoiDung.getUid();

        executor.execute(() -> {
            String loi;

            try {
                do {
                    canChayLai = false;
                    loi = chayMotLan(uid);
                } while (canChayLai && loi == null);

            } catch (Exception e) {
                loi = thongDiep(e);
            } finally {
                dangChay.set(false);
            }

            baoKetQua(loi == null, loi);
            if (canChayLai) {
                syncNow(null);
            }
        });
    }

    private void baoKetQua(boolean thanhCong, String loi) {
        List<Listener> danhSach;

        synchronized (dangCho) {
            danhSach = new ArrayList<>(dangCho);
            dangCho.clear();
        }

        if (danhSach.isEmpty()) {
            return;
        }

        mainHandler.post(() -> {
            for (Listener l : danhSach) {
                l.onDone(thanhCong, loi);
            }
        });
    }

    /** Một lần đồng bộ đầy đủ. Trả về null nếu thành công, hoặc thông báo lỗi. */
    private String chayMotLan(String uid) {

        if (!coMang()) {
            return "Không có kết nối mạng";
        }

        try {
            pushTatCa(uid);
        } catch (Exception e) {
            return thongDiep(e);
        }

        String loi = keoTatCa(uid);

        if (loi == null) {
            ghiLanDongBoCuoi(uid);
        }

        return loi;
    }

    // ------------------------------------------------------------------
    //  Theo dõi mạng
    // ------------------------------------------------------------------

    private void theoDoiMang() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) {
                return;
            }

            // onAvailable cũng được gọi ngay khi đăng ký nếu đang có mạng
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    syncNow(null);
                }
            });
        } catch (Exception ignored) {
            // Thiếu quyền ACCESS_NETWORK_STATE hoặc không đăng ký được:
            // vẫn đồng bộ được mỗi khi mở màn hình chính.
        }
    }

    private boolean coMang() {
        ConnectivityManager cm = (ConnectivityManager)
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return true;
        }

        Network mang = cm.getActiveNetwork();

        if (mang == null) {
            return false;
        }

        NetworkCapabilities nl = cm.getNetworkCapabilities(mang);

        return nl != null && nl.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // ------------------------------------------------------------------
    //  Pha 1: ĐẨY các dòng PENDING lên Firestore
    // ------------------------------------------------------------------

    /** Thứ tự quan trọng: ví trước (giao dịch/nhật ký tham chiếu ví), nhật ký sau cùng. */
    private void pushTatCa(String uid) throws Exception {
        pushWallets(uid);
        pushAdjustments(uid);
        pushTransactions(uid);
        pushTransactionEdits(uid);
        pushBudgets(uid);
        pushUserProfile(uid);
    }

    private void pushWallets(String uid) throws Exception {

        for (Map<String, Object> r : syncDao.getPending(PhinovaDbHelper.T_WALLETS, uid)) {

            String id = chuoi(r.get(PhinovaDbHelper.W_ID));
            long capNhat = so(r.get(PhinovaDbHelper.C_UPDATED_AT));

            DocumentReference ref = users(uid, "wallets").document(id);

            if (daXoa(r)) {
                cho(ref.delete());
                syncDao.purge(PhinovaDbHelper.T_WALLETS, PhinovaDbHelper.W_ID, id, capNhat);
                continue;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("tenVi", r.get(PhinovaDbHelper.W_TEN));
            map.put("loaiVi", r.get(PhinovaDbHelper.W_LOAI));
            map.put("soDu", so(r.get(PhinovaDbHelper.W_SO_DU)));
            map.put("trangThai", r.get(PhinovaDbHelper.W_TRANG_THAI));
            map.put("ngayTao", thoiGian(r.get(PhinovaDbHelper.W_NGAY_TAO)));
            map.put("ngayLuuTru", thoiGian(r.get(PhinovaDbHelper.W_NGAY_LUU_TRU)));

            cho(ref.set(map));
            syncDao.markSynced(PhinovaDbHelper.T_WALLETS, PhinovaDbHelper.W_ID, id, capNhat);
        }
    }

    private void pushAdjustments(String uid) throws Exception {

        for (Map<String, Object> r : syncDao.getPending(PhinovaDbHelper.T_ADJUSTMENTS, uid)) {

            String id = chuoi(r.get(PhinovaDbHelper.A_ID));
            long capNhat = so(r.get(PhinovaDbHelper.C_UPDATED_AT));

            Map<String, Object> map = new HashMap<>();
            map.put("maVi", r.get(PhinovaDbHelper.A_VI));
            map.put("soDuCu", so(r.get(PhinovaDbHelper.A_SO_DU_CU)));
            map.put("soDuMoi", so(r.get(PhinovaDbHelper.A_SO_DU_MOI)));
            map.put("chenhLech", so(r.get(PhinovaDbHelper.A_CHENH_LECH)));
            map.put("lyDo", r.get(PhinovaDbHelper.A_LY_DO));
            map.put("ngayDieuChinh", thoiGian(r.get(PhinovaDbHelper.A_NGAY)));

            cho(users(uid, "balance_adjustments").document(id).set(map));
            syncDao.markSynced(PhinovaDbHelper.T_ADJUSTMENTS, PhinovaDbHelper.A_ID, id, capNhat);
        }
    }

    private void pushTransactions(String uid) throws Exception {

        for (Map<String, Object> r : syncDao.getPending(PhinovaDbHelper.T_TRANSACTIONS, uid)) {

            String id = chuoi(r.get(PhinovaDbHelper.TX_ID));
            long capNhat = so(r.get(PhinovaDbHelper.C_UPDATED_AT));

            DocumentReference ref = users(uid, "transactions").document(id);

            if (daXoa(r)) {
                pushXoaGiaoDich(uid, r, ref);
                syncDao.purge(PhinovaDbHelper.T_TRANSACTIONS, PhinovaDbHelper.TX_ID, id, capNhat);
                continue;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("loaiGiaoDich", r.get(PhinovaDbHelper.TX_LOAI));
            map.put("soTien", so(r.get(PhinovaDbHelper.TX_SO_TIEN)));
            map.put("maViNguon", r.get(PhinovaDbHelper.TX_VI_NGUON));
            map.put("maViDich", r.get(PhinovaDbHelper.TX_VI_DICH));
            map.put("danhMuc", r.get(PhinovaDbHelper.TX_DANH_MUC));
            map.put("ghiChu", r.get(PhinovaDbHelper.TX_GHI_CHU));
            map.put("ngayGiaoDich", ngay(r.get(PhinovaDbHelper.TX_NGAY)));
            map.put("ngayCapNhat", thoiGian(capNhat));

            // merge: giữ nguyên các trường khác đã có trên cloud (ví dụ ngayTao)
            cho(ref.set(map, SetOptions.merge()));
            syncDao.markSynced(PhinovaDbHelper.T_TRANSACTIONS, PhinovaDbHelper.TX_ID, id, capNhat);
        }
    }

    /**
     * Giao dịch đã xóa mềm: ghi nhật ký xóa rồi xóa giao dịch trên cloud trong một batch.
     * Nhật ký dùng ID cố định theo mã giao dịch nên thử lại không tạo bản ghi trùng.
     */
    private void pushXoaGiaoDich(String uid, Map<String, Object> r,
                                 DocumentReference giaoDichRef) throws Exception {

        String id = chuoi(r.get(PhinovaDbHelper.TX_ID));
        String maViNguon = chuoi(r.get(PhinovaDbHelper.TX_VI_NGUON));

        Wallet viNguon = maViNguon == null ? null : walletDao.getById(
                chuoi(r.get(PhinovaDbHelper.C_USER_ID)), maViNguon);

        Map<String, Object> lichSu = new HashMap<>();
        lichSu.put("maGiaoDich", id);
        lichSu.put("loaiGiaoDich", r.get(PhinovaDbHelper.TX_LOAI));
        lichSu.put("soTien", so(r.get(PhinovaDbHelper.TX_SO_TIEN)));
        lichSu.put("maViNguon", r.get(PhinovaDbHelper.TX_VI_NGUON));
        lichSu.put("maViDich", r.get(PhinovaDbHelper.TX_VI_DICH));
        lichSu.put("danhMuc", r.get(PhinovaDbHelper.TX_DANH_MUC));
        lichSu.put("ghiChu", r.get(PhinovaDbHelper.TX_GHI_CHU));
        lichSu.put("ngayGiaoDich", ngay(r.get(PhinovaDbHelper.TX_NGAY)));
        lichSu.put("soDuNguonSauXoa", viNguon == null ? 0L : viNguon.getSoDu());
        lichSu.put("ngayXoa", thoiGian(r.get(PhinovaDbHelper.C_UPDATED_AT)));

        WriteBatch batch = firestore.batch();
        batch.set(users(uid, "transaction_deletions").document("xoa_" + id), lichSu);
        batch.delete(giaoDichRef);

        cho(batch.commit());
    }

    private void pushTransactionEdits(String uid) throws Exception {

        for (Map<String, Object> r : syncDao.getPending(PhinovaDbHelper.T_TX_EDITS, uid)) {

            String id = chuoi(r.get(PhinovaDbHelper.E_ID));
            long capNhat = so(r.get(PhinovaDbHelper.C_UPDATED_AT));

            Map<String, Object> map = new HashMap<>();
            map.put("maGiaoDich", r.get(PhinovaDbHelper.E_MA_GD));
            map.put("loaiGiaoDich", r.get(PhinovaDbHelper.E_LOAI));
            map.put("maViNguon", r.get(PhinovaDbHelper.E_VI_NGUON));
            map.put("maViDich", r.get(PhinovaDbHelper.E_VI_DICH));
            map.put("soTienCu", so(r.get(PhinovaDbHelper.E_TIEN_CU)));
            map.put("soTienMoi", so(r.get(PhinovaDbHelper.E_TIEN_MOI)));
            map.put("danhMucCu", r.get(PhinovaDbHelper.E_DM_CU));
            map.put("danhMucMoi", r.get(PhinovaDbHelper.E_DM_MOI));
            map.put("ghiChuCu", r.get(PhinovaDbHelper.E_GC_CU));
            map.put("ghiChuMoi", r.get(PhinovaDbHelper.E_GC_MOI));
            map.put("ngayGiaoDichCu", ngay(r.get(PhinovaDbHelper.E_NGAY_CU)));
            map.put("ngayGiaoDichMoi", ngay(r.get(PhinovaDbHelper.E_NGAY_MOI)));
            map.put("ngayChinhSua", thoiGian(r.get(PhinovaDbHelper.E_NGAY_SUA)));

            cho(users(uid, "transaction_edits").document(id).set(map));
            syncDao.markSynced(PhinovaDbHelper.T_TX_EDITS, PhinovaDbHelper.E_ID, id, capNhat);
        }
    }

    /** Hồ sơ người dùng (họ tên, email) -> document users/{uid}. */
    private void pushUserProfile(String uid) throws Exception {

        for (Map<String, Object> r : syncDao.getPending(PhinovaDbHelper.T_USER_PROFILES, uid)) {

            long capNhat = so(r.get(PhinovaDbHelper.C_UPDATED_AT));

            Map<String, Object> map = new HashMap<>();
            map.put("uid", uid);
            map.put("hoTen", r.get(PhinovaDbHelper.P_HO_TEN));

            String email = chuoi(r.get(PhinovaDbHelper.P_EMAIL));
            if (email != null) {
                map.put("email", email);
            }

            Timestamp ngayTao = thoiGian(r.get(PhinovaDbHelper.P_NGAY_TAO));
            if (ngayTao != null) {
                map.put("ngayTao", ngayTao);
            }

            // merge: giữ nguyên các trường khác đã có trên document users/{uid}
            cho(firestore.collection("users").document(uid).set(map, SetOptions.merge()));
            syncDao.markSynced(PhinovaDbHelper.T_USER_PROFILES,
                    PhinovaDbHelper.C_USER_ID, uid, capNhat);
        }
    }

    private void pushBudgets(String uid) throws Exception {

        for (Map<String, Object> r : syncDao.getPending(PhinovaDbHelper.T_BUDGETS, uid)) {

            String id = chuoi(r.get(PhinovaDbHelper.B_ID));
            long capNhat = so(r.get(PhinovaDbHelper.C_UPDATED_AT));

            DocumentReference ref = users(uid, "budgets").document(id);

            if (daXoa(r)) {
                cho(ref.delete());
                budgetDao.purge(uid, id, capNhat);
                continue;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("danhMuc", r.get(PhinovaDbHelper.B_DANH_MUC));
            map.put("soTienGioiHan", so(r.get(PhinovaDbHelper.B_GIOI_HAN)));
            map.put("thang", so(r.get(PhinovaDbHelper.B_THANG)));
            map.put("nam", so(r.get(PhinovaDbHelper.B_NAM)));
            map.put("ngayCapNhat", thoiGian(capNhat));

            Timestamp ngayTao = thoiGian(r.get(PhinovaDbHelper.B_NGAY_TAO));
            if (ngayTao != null) {
                map.put("ngayTao", ngayTao);
            }

            cho(ref.set(map, SetOptions.merge()));
            budgetDao.markSynced(uid, id, capNhat);
        }
    }

    // ------------------------------------------------------------------
    //  Pha 2: KÉO dữ liệu từ Firestore về SQLite
    // ------------------------------------------------------------------

    /**
     * Dùng lại các hàm pullFromCloud đã có trong các Repository. Chạy lần lượt, gặp lỗi vẫn
     * thử tiếp các phần còn lại. Trả về thông báo lỗi đầu tiên (null nếu tất cả thành công).
     */
    private String keoTatCa(String uid) {

        String loi = null;

        loi = giuLoiDauTien(loi, keoHoSo(uid));

        loi = giuLoiDauTien(loi, keo(kq ->
                new WalletRepository(appContext).pullFromCloud(uid, kq::onDone)));

        loi = giuLoiDauTien(loi, keo(kq ->
                new TransactionRepository(appContext).pullFromCloud(uid, kq::onDone)));

        loi = giuLoiDauTien(loi, keo(kq ->
                new BudgetRepository(appContext).pullFromCloud(uid, kq::onDone)));

        // Lịch sử điều chỉnh số dư kéo theo từng ví
        for (Wallet vi : walletDao.getAll(uid)) {
            String maVi = vi.getMaVi();

            loi = giuLoiDauTien(loi, keo(kq ->
                    new BalanceAdjustmentRepository(appContext)
                            .pullFromCloud(uid, maVi, kq::onDone)));
        }

        return loi;
    }

    /** Kéo hồ sơ người dùng (họ tên, email) từ users/{uid}. Trả về lỗi hoặc null. */
    private String keoHoSo(String uid) {
        try {
            DocumentSnapshot doc = cho(firestore.collection("users").document(uid).get());

            if (doc.exists()) {
                Timestamp ngayTao = doc.getTimestamp("ngayTao");

                userProfileDao.upsertFromCloud(
                        uid,
                        doc.getString("hoTen"),
                        doc.getString("email"),
                        ngayTao == null ? 0 : ngayTao.toDate().getTime());
            }

            return null;

        } catch (Exception e) {
            return thongDiep(e);
        }
    }

    /** Bắt đầu một lần kéo trên main thread rồi chờ kết quả. Trả về lỗi hoặc null. */
    private String keo(BatDauKeo batDau) {

        CountDownLatch cho = new CountDownLatch(1);
        String[] loi = {null};

        mainHandler.post(() -> batDau.chay((thanhCong, thongBao) -> {
            if (!thanhCong) {
                loi[0] = thongBao == null ? "Không thể kéo dữ liệu về" : thongBao;
            }
            cho.countDown();
        }));

        try {
            if (!cho.await(CHO_TOI_DA_GIAY, TimeUnit.SECONDS)) {
                return "Hết thời gian chờ khi kéo dữ liệu";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Đồng bộ bị gián đoạn";
        }

        return loi[0];
    }

    private static String giuLoiDauTien(String hienTai, String moi) {
        return hienTai != null ? hienTai : moi;
    }

    // ------------------------------------------------------------------
    //  Tiện ích
    // ------------------------------------------------------------------

    private CollectionReference users(String uid, String tenBoSuuTap) {
        return firestore.collection("users").document(uid).collection(tenBoSuuTap);
    }

    /** Chờ một tác vụ Firestore xong (chỉ gọi từ luồng nền). Quá hạn thì ném lỗi. */
    private <T> T cho(Task<T> task) throws Exception {
        try {
            return Tasks.await(task, CHO_TOI_DA_GIAY, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable goc = e.getCause();
            throw goc instanceof Exception ? (Exception) goc : e;
        }
    }

    private static boolean daXoa(Map<String, Object> dong) {
        return so(dong.get(PhinovaDbHelper.C_IS_DELETED)) == 1;
    }

    private static String chuoi(Object o) {
        return o instanceof String ? (String) o : null;
    }

    private static long so(Object o) {
        return o instanceof Long ? (Long) o : 0L;
    }

    /** Mili-giây -> Timestamp của Firestore (null nếu không có giá trị). */
    private static Timestamp thoiGian(Object o) {
        long ms = so(o);
        return ms > 0 ? new Timestamp(new Date(ms)) : null;
    }

    private static Date ngay(Object o) {
        return new Date(so(o));
    }

    /** Ghi lại thời điểm đồng bộ thành công (cả đẩy lẫn kéo) để trang Cá nhân hiển thị. */
    private void ghiLanDongBoCuoi(String uid) {
        appContext.getSharedPreferences(PREFS_DONG_BO, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAN_DONG_BO_CUOI + uid, System.currentTimeMillis())
                .apply();
    }

    private static String thongDiep(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}