package com.example.qlchitieu.profile;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.qlchitieu.R;
import com.example.qlchitieu.auth.LoginActivity;
import com.example.qlchitieu.data.local.PhinovaDbHelper;
import com.example.qlchitieu.data.local.SyncDao;
import com.example.qlchitieu.data.repository.UserProfileRepository;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Transaction;
import com.example.qlchitieu.model.Wallet;
import com.example.qlchitieu.security.AppLock;
import com.example.qlchitieu.security.SecurityActivity;
import com.example.qlchitieu.util.CsvExporter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trang Cá nhân: hồ sơ, trạng thái đồng bộ, các tùy chỉnh và đăng xuất.
 * Thẻ hồ sơ mở màn Sửa hồ sơ (tên + ảnh đại diện). Các mục chưa làm tạm hiện "Sắp có".
 */
public class ProfileActivity extends AppCompatActivity {

    /**
     * Thời điểm (mili giây) đồng bộ thành công gần nhất, theo uid.
     * Hiện chưa có nơi nào ghi giá trị này: khi chưa có thì trang chỉ hiện "Đã đồng bộ".
     */
    public static final String PREFS_DONG_BO = "phinova_sync";
    public static final String KEY_LAN_DONG_BO_CUOI = "last_sync_ms_";

    private static final String MAU_XANH = "#059669";
    private static final String MAU_CAM = "#D97706";

    /** Mở hộp thoại hệ thống để người dùng chọn nơi lưu file CSV (không cần xin quyền). */
    private final ActivityResultLauncher<String> chonNoiLuuFile =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("text/csv"),
                    uri -> {
                        if (uri != null) {
                            xuatRaFile(uri);
                        }
                    });

    FirebaseAuth firebaseAuth;
    UserProfileRepository userProfileRepository;
    SyncDao syncDao;

    ImageView ivAvatar;
    TextView tvChuCaiAvatar, tvHoTen, tvEmail;
    TextView tvChamDongBo, tvTrangThaiDongBo, tvPhienBan, btnDangXuat;
    SwitchCompat switchNhacNho, switchKhoaUngDung;

    /** Đang chạy đồng bộ thủ công hoặc đồng bộ trước khi đăng xuất. */
    boolean dangDongBo = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        firebaseAuth = FirebaseAuth.getInstance();
        userProfileRepository = new UserProfileRepository(this);

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            veManHinhDangNhap();
            return;
        }

        syncDao = new SyncDao(this);

        ivAvatar = findViewById(R.id.ivAvatarCaNhan);
        tvChuCaiAvatar = findViewById(R.id.tvChuCaiAvatar);
        tvHoTen = findViewById(R.id.tvHoTenCaNhan);
        tvEmail = findViewById(R.id.tvEmailCaNhan);
        tvChamDongBo = findViewById(R.id.tvChamDongBo);
        tvTrangThaiDongBo = findViewById(R.id.tvTrangThaiDongBo);
        tvPhienBan = findViewById(R.id.tvPhienBan);
        btnDangXuat = findViewById(R.id.btnDangXuat);
        switchNhacNho = findViewById(R.id.switchNhacNho);
        switchKhoaUngDung = findViewById(R.id.switchKhoaUngDung);

        // Các chức năng chưa làm
        View.OnClickListener sapCo = v -> thongBao("Sắp có");

        // Thẻ hồ sơ và nút Sửa cùng mở màn Sửa hồ sơ
        View.OnClickListener moSuaHoSo = v -> moManSuaHoSo();

        findViewById(R.id.cardHoSo).setOnClickListener(moSuaHoSo);
        findViewById(R.id.btnSuaHoSo).setOnClickListener(moSuaHoSo);
        findViewById(R.id.rowDanhMuc).setOnClickListener(sapCo);
        findViewById(R.id.rowGiaoDien).setOnClickListener(sapCo);
        findViewById(R.id.rowBaoMat).setOnClickListener(v -> moManBaoMat());
        findViewById(R.id.rowXuatDuLieu).setOnClickListener(v -> batDauXuatDuLieu());
        findViewById(R.id.rowGopY).setOnClickListener(v -> moEmailGopY());
        findViewById(R.id.tvXoaTaiKhoan).setOnClickListener(sapCo);

        // Công tắc chưa làm: bấm vào thì trả về trạng thái tắt
        View.OnClickListener congTacSapCo = v -> {
            ((SwitchCompat) v).setChecked(false);
            thongBao("Sắp có");
        };

        switchNhacNho.setOnClickListener(congTacSapCo);

        // Công tắc Khóa ứng dụng chỉ hiển thị trạng thái; bật/tắt (cần xác thực) ở màn Bảo mật
        switchKhoaUngDung.setOnClickListener(v -> {
            ((SwitchCompat) v).setChecked(AppLock.isEnabled(this, nguoiDung.getUid()));
            moManBaoMat();
        });
        findViewById(R.id.rowKhoaUngDung).setOnClickListener(v -> moManBaoMat());

        findViewById(R.id.tvDongBoNgay).setOnClickListener(v -> dongBoNgay());
        btnDangXuat.setOnClickListener(v -> hoiXacNhanDangXuat());

        tvEmail.setText(nguoiDung.getEmail() == null ? "" : nguoiDung.getEmail());
        tvPhienBan.setText("Phinova " + layPhienBan());

        taiHoTen(nguoiDung);
    }

    @Override
    protected void onResume() {
        super.onResume();

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null || syncDao == null) {
            return;
        }

        // Tên có thể vừa được sửa ở màn khác
        String daLuu = userProfileRepository.getHoTen(nguoiDung.getUid());

        if (daLuu != null) {
            hienHoTen(daLuu);
        }

        hienAvatar(nguoiDung.getUid());
        capNhatTrangThaiDongBo(nguoiDung.getUid());

        // Khóa ứng dụng có thể vừa được bật/tắt ở màn Bảo mật
        switchKhoaUngDung.setChecked(AppLock.isEnabled(this, nguoiDung.getUid()));
    }

    // ------------------------------------------------------------------
    //  Hồ sơ
    // ------------------------------------------------------------------

    private void taiHoTen(FirebaseUser nguoiDung) {

        String uid = nguoiDung.getUid();

        // 1) Hiện ngay: họ tên lưu trong SQLite, không có thì lấy từ Auth / email
        String daLuu = userProfileRepository.getHoTen(uid);

        hienHoTen(daLuu != null ? daLuu : hoTenMacDinh(nguoiDung));

        // 2) Đồng bộ với Firestore ở nền (máy mới, hoặc tên đổi từ máy khác), xong thì hiện lại
        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            String tenMoi = userProfileRepository.getHoTen(uid);

            if (tenMoi != null) {
                hienHoTen(tenMoi);
            }
        });
    }

    private String hoTenMacDinh(FirebaseUser nguoiDung) {

        String ten = nguoiDung.getDisplayName();

        if (ten != null && !ten.trim().isEmpty()) {
            return ten.trim();
        }

        String email = nguoiDung.getEmail();

        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }

        return "Người dùng";
    }

    private void hienHoTen(String hoTen) {

        tvHoTen.setText(hoTen);

        // Chữ cái đầu của từ cuối (tên riêng), ví dụ "Nguyễn Văn An" -> "A"
        String[] tu = hoTen.trim().split("\\s+");
        String tuCuoi = tu.length == 0 ? "" : tu[tu.length - 1];

        if (tuCuoi.isEmpty()) {
            tvChuCaiAvatar.setText("?");
        } else {
            tvChuCaiAvatar.setText(tuCuoi.substring(0, 1).toUpperCase(Locale.getDefault()));
        }
    }

    private void moManSuaHoSo() {

        Intent intent = new Intent(this, EditProfileActivity.class);
        intent.putExtra(EditProfileActivity.EXTRA_HO_TEN, tvHoTen.getText().toString());
        startActivity(intent);
    }

    private void moManBaoMat() {
        startActivity(new Intent(this, SecurityActivity.class));
    }

    /** Hiện ảnh đại diện đã lưu trên máy, chưa có thì hiện chữ cái đầu của tên. */
    private void hienAvatar(String uid) {

        Bitmap anh = AvatarStore.tai(this, uid);

        if (anh != null) {
            ivAvatar.setImageBitmap(anh);
            ivAvatar.setVisibility(View.VISIBLE);
            tvChuCaiAvatar.setVisibility(View.INVISIBLE);
        } else {
            ivAvatar.setImageDrawable(null);
            ivAvatar.setVisibility(View.GONE);
            tvChuCaiAvatar.setVisibility(View.VISIBLE);
        }
    }

    private String layPhienBan() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "" : "phiên bản " + info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    //  Đồng bộ
    // ------------------------------------------------------------------

    /** Số dòng đang chờ đẩy lên cloud của người dùng, cộng dồn các bảng. */
    private int demThayDoiChoDongBo(String uid) {

        String[] cacBang = {
                PhinovaDbHelper.T_WALLETS,
                PhinovaDbHelper.T_TRANSACTIONS,
                PhinovaDbHelper.T_ADJUSTMENTS,
                PhinovaDbHelper.T_BUDGETS,
                PhinovaDbHelper.T_TX_EDITS
        };

        int tong = 0;

        for (String bang : cacBang) {
            tong += syncDao.getPending(bang, uid).size();
        }

        return tong;
    }

    private void capNhatTrangThaiDongBo(String uid) {

        if (dangDongBo) {
            datTrangThai("Đang đồng bộ…", MAU_CAM);
            return;
        }

        int cho = demThayDoiChoDongBo(uid);

        if (cho > 0) {
            datTrangThai(cho + " thay đổi chờ đồng bộ", MAU_CAM);
            return;
        }

        long lanCuoi = getSharedPreferences(PREFS_DONG_BO, Context.MODE_PRIVATE)
                .getLong(KEY_LAN_DONG_BO_CUOI + uid, 0L);

        datTrangThai("Đã đồng bộ" + hienGio(lanCuoi), MAU_XANH);
    }

    /** " lúc 14:32" nếu là hôm nay, " lúc 14:32 20/11" nếu khác ngày, "" nếu chưa biết. */
    private String hienGio(long mili) {

        if (mili <= 0) {
            return "";
        }

        Locale vn = new Locale("vi", "VN");
        String gio = new SimpleDateFormat("HH:mm", vn).format(new Date(mili));

        if (DateUtils.isToday(mili)) {
            return " lúc " + gio;
        }

        return " lúc " + gio + " " + new SimpleDateFormat("dd/MM", vn).format(new Date(mili));
    }

    private void datTrangThai(String noiDung, String mau) {
        int mauInt = Color.parseColor(mau);
        tvTrangThaiDongBo.setText(noiDung);
        tvTrangThaiDongBo.setTextColor(mauInt);
        tvChamDongBo.setTextColor(mauInt);
    }

    private void dongBoNgay() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null || dangDongBo) {
            return;
        }

        String uid = nguoiDung.getUid();

        dangDongBo = true;
        capNhatTrangThaiDongBo(uid);

        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            dangDongBo = false;

            if (isFinishing() || isDestroyed()) {
                return;
            }

            capNhatTrangThaiDongBo(uid);

            if (thanhCong) {
                thongBao("Đã đồng bộ");
            } else {
                thongBao("Chưa đồng bộ được, dữ liệu vẫn an toàn trên máy");
            }
        });
    }

    // ------------------------------------------------------------------
    //  Đăng xuất
    // ------------------------------------------------------------------

    private void hoiXacNhanDangXuat() {

        if (dangDongBo) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn sẽ cần đăng nhập lại để dùng Phinova.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất", (dialog, which) -> batDauDangXuat())
                .show();
    }

    private void batDauDangXuat() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            veManHinhDangNhap();
            return;
        }

        String uid = nguoiDung.getUid();

        if (demThayDoiChoDongBo(uid) == 0) {
            thucHienDangXuat();
            return;
        }

        // Còn thay đổi chưa lên cloud: đồng bộ trước rồi mới đăng xuất
        dangDongBo = true;
        btnDangXuat.setEnabled(false);
        btnDangXuat.setText("Đang đồng bộ trước khi đăng xuất…");
        capNhatTrangThaiDongBo(uid);

        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            dangDongBo = false;

            if (isFinishing() || isDestroyed()) {
                return;
            }

            btnDangXuat.setEnabled(true);
            btnDangXuat.setText("Đăng xuất");
            capNhatTrangThaiDongBo(uid);

            int conLai = demThayDoiChoDongBo(uid);

            if (conLai == 0) {
                thucHienDangXuat();
                return;
            }

            // Mất mạng hoặc lỗi: cho người dùng quyết định. Dữ liệu vẫn nằm trên máy
            // và sẽ được đẩy lên khi họ đăng nhập lại đúng tài khoản này.
            new AlertDialog.Builder(this)
                    .setTitle("Còn " + conLai + " thay đổi chưa đồng bộ")
                    .setMessage("Có thể do mất mạng. Dữ liệu vẫn được giữ trên máy và sẽ "
                            + "đồng bộ khi bạn đăng nhập lại. Vẫn đăng xuất?")
                    .setNegativeButton("Ở lại", null)
                    .setPositiveButton("Vẫn đăng xuất", (dialog, which) -> thucHienDangXuat())
                    .show();
        });
    }

    private void thucHienDangXuat() {
        firebaseAuth.signOut();
        veManHinhDangNhap();
    }

    private void veManHinhDangNhap() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ------------------------------------------------------------------
    //  Xuất dữ liệu ra file CSV
    // ------------------------------------------------------------------

    private void batDauXuatDuLieu() {

        if (firebaseAuth.getCurrentUser() == null) {
            thongBao("Vui lòng đăng nhập lại");
            return;
        }

        String tenFile = "Phinova_giao_dich_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date())
                + ".csv";

        try {
            chonNoiLuuFile.launch(tenFile);
        } catch (Exception e) {
            thongBao("Máy không hỗ trợ chọn nơi lưu file");
        }
    }

    /** Đọc giao dịch từ SQLite và ghi vào file người dùng đã chọn (chạy ở luồng nền). */
    private void xuatRaFile(Uri uri) {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            thongBao("Vui lòng đăng nhập lại");
            return;
        }

        String uid = nguoiDung.getUid();
        Context appContext = getApplicationContext();

        thongBao("Đang xuất dữ liệu...");

        new Thread(() -> {
            String loi = null;
            int soGiaoDich = 0;

            try {
                List<Transaction> giaoDich =
                        new TransactionRepository(appContext).getTransactions(uid);

                Map<String, String> tenVi = new HashMap<>();

                for (Wallet vi : new WalletRepository(appContext).getWallets(uid)) {
                    tenVi.put(vi.getMaVi(), vi.getTenVi());
                }

                try (OutputStream out = appContext.getContentResolver().openOutputStream(uri)) {

                    if (out == null) {
                        throw new IOException("Không mở được file để ghi");
                    }

                    CsvExporter.xuatGiaoDich(out, giaoDich, tenVi);
                }

                soGiaoDich = giaoDich.size();

            } catch (Exception e) {
                loi = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }

            String loiCuoi = loi;
            int soLuong = soGiaoDich;

            runOnUiThread(() -> {
                if (loiCuoi != null) {
                    thongBao("Không thể xuất dữ liệu: " + loiCuoi);
                } else if (soLuong == 0) {
                    thongBao("Đã tạo file, nhưng chưa có giao dịch nào để xuất");
                } else {
                    thongBao("Đã xuất " + soLuong + " giao dịch");
                }
            });
        }).start();
    }

    /** Địa chỉ email nhận góp ý và báo lỗi. */
    private static final String EMAIL_GOP_Y = "nguyensama02@gmail.com";

    /**
     * Mở ứng dụng email của máy với sẵn người nhận, tiêu đề và thông tin thiết bị
     * (phiên bản app, Android, tên máy) để dễ tìm lỗi. Người dùng đọc và sửa trước khi gửi.
     */
    private void moEmailGopY() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        String taiKhoan = nguoiDung != null && nguoiDung.getEmail() != null
                ? nguoiDung.getEmail()
                : "Không rõ";

        String noiDung = "\n\n\n"
                + "----------\n"
                + "Thông tin kèm theo (bạn có thể xóa trước khi gửi)\n"
                + "Ứng dụng: Phinova " + layPhienBan() + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Thiết bị: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Tài khoản: " + taiKhoan + "\n";

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{EMAIL_GOP_Y});
        intent.putExtra(Intent.EXTRA_SUBJECT, "[Phinova] Góp ý / báo lỗi");
        intent.putExtra(Intent.EXTRA_TEXT, noiDung);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            thongBao("Máy chưa có ứng dụng email. Bạn có thể gửi góp ý tới " + EMAIL_GOP_Y);
        }
    }

    private void thongBao(String noiDung) {
        Toast.makeText(this, noiDung, Toast.LENGTH_SHORT).show();
    }
}
