package com.example.qlchitieu.security;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.qlchitieu.R;
import com.example.qlchitieu.auth.LoginActivity;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseUserMetadata;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bảo mật tài khoản: mức bảo mật, đổi mật khẩu, xác thực email, khóa ứng dụng
 * và thông tin hoạt động của tài khoản.
 */
public class SecurityActivity extends AppCompatActivity {

    FirebaseAuth firebaseAuth;

    LinearProgressIndicator progressBaoMat;
    TextView tvMucBaoMat, tvMoTaBaoMat;

    View hangDoiMatKhau, hangEmail, hangKhoa, hangThoiGian, hangDangNhapCuoi, hangNgayTao;
    SwitchCompat congTacKhoa;

    /** Đang tự gạt công tắc bằng code: không coi là người dùng bấm. */
    boolean dangDatCongTac = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);

        firebaseAuth = FirebaseAuth.getInstance();

        if (firebaseAuth.getCurrentUser() == null) {
            veManHinhDangNhap();
            return;
        }

        tvMucBaoMat = findViewById(R.id.tvMucBaoMat);
        tvMoTaBaoMat = findViewById(R.id.tvMoTaBaoMat);
        progressBaoMat = findViewById(R.id.progressBaoMat);

        findViewById(R.id.tvQuayLaiBaoMat).setOnClickListener(v -> finish());

        LinearLayout nhomDangNhap = findViewById(R.id.nhomDangNhap);
        LinearLayout nhomUngDung = findViewById(R.id.nhomUngDung);
        LinearLayout nhomHoatDong = findViewById(R.id.nhomHoatDong);

        hangDoiMatKhau = themHang(nhomDangNhap, "🔑", "Đổi mật khẩu",
                "Nên đổi định kỳ để tài khoản an toàn hơn");
        hangEmail = themHang(nhomDangNhap, "✉️", "Xác thực email", "");

        hangKhoa = themHang(nhomUngDung, "🔒", "Khóa ứng dụng",
                "Dùng vân tay, khuôn mặt hoặc khóa màn hình");
        hangThoiGian = themHang(nhomUngDung, "⏱️", "Tự động khóa", "");

        hangDangNhapCuoi = themHang(nhomHoatDong, "🕘", "Đăng nhập gần nhất", "");
        hangNgayTao = themHang(nhomHoatDong, "📅", "Tạo tài khoản", "");

        hangDoiMatKhau.findViewById(R.id.tvMuiTen).setVisibility(View.VISIBLE);
        hangThoiGian.findViewById(R.id.tvMuiTen).setVisibility(View.VISIBLE);

        hangDoiMatKhau.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        hangEmail.setOnClickListener(v -> guiEmailXacThuc());

        congTacKhoa = hangKhoa.findViewById(R.id.swPhai);
        congTacKhoa.setVisibility(View.VISIBLE);
        congTacKhoa.setOnCheckedChangeListener((nut, bat) -> {
            if (!dangDatCongTac) {
                xuLyDoiKhoa(bat);
            }
        });
        hangKhoa.setOnClickListener(v -> congTacKhoa.toggle());

        hangThoiGian.setOnClickListener(v -> chonThoiGianKhoa());
    }

    @Override
    protected void onResume() {
        super.onResume();

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null || hangEmail == null) {
            return;
        }

        capNhat();

        // Người dùng có thể vừa bấm link xác thực trong email: tải lại trạng thái
        nguoiDung.reload().addOnCompleteListener(task -> {
            if (!isFinishing() && !isDestroyed()) {
                capNhat();
            }
        });
    }

    // ------------------------------------------------------------------
    //  Dựng giao diện
    // ------------------------------------------------------------------

    /** Thêm một dòng vào nhóm (có đường kẻ ngăn với dòng trước). */
    private View themHang(LinearLayout nhom, String bieuTuong, String tieuDe, String phuDe) {

        if (nhom.getChildCount() > 0) {
            View duongKe = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMarginStart(dp(72));
            duongKe.setLayoutParams(lp);
            duongKe.setBackgroundColor(Color.parseColor("#E8EDF5"));
            nhom.addView(duongKe);
        }

        View hang = getLayoutInflater().inflate(R.layout.item_security_row, nhom, false);

        ((TextView) hang.findViewById(R.id.tvIcon)).setText(bieuTuong);
        ((TextView) hang.findViewById(R.id.tvTieuDe)).setText(tieuDe);
        ((TextView) hang.findViewById(R.id.tvPhuDe)).setText(phuDe);

        nhom.addView(hang);
        return hang;
    }

    private int dp(int giaTri) {
        return (int) (giaTri * getResources().getDisplayMetrics().density);
    }

    private void datPhuDe(View hang, String noiDung) {
        ((TextView) hang.findViewById(R.id.tvPhuDe)).setText(noiDung);
    }

    private void datChip(View hang, String noiDung, boolean tot) {
        TextView chip = hang.findViewById(R.id.tvPhai);
        chip.setVisibility(View.VISIBLE);
        chip.setText(noiDung);
        chip.setBackgroundResource(tot ? R.drawable.bg_chip_ok : R.drawable.bg_chip_warn);
        chip.setTextColor(Color.parseColor(tot ? "#059669" : "#D97706"));
    }

    private void datCongTac(boolean bat) {
        dangDatCongTac = true;
        congTacKhoa.setChecked(bat);
        dangDatCongTac = false;
    }

    private String dinhDangThoiGian(long mili) {

        if (mili <= 0) {
            return "Không rõ";
        }

        return new SimpleDateFormat("HH:mm, dd/MM/yyyy", new Locale("vi", "VN"))
                .format(new Date(mili));
    }

    /** Vẽ lại toàn bộ trạng thái theo dữ liệu hiện tại. */
    private void capNhat() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            return;
        }

        String uid = nguoiDung.getUid();
        String email = nguoiDung.getEmail() == null ? "" : nguoiDung.getEmail();

        boolean daXacThucEmail = nguoiDung.isEmailVerified();
        boolean khoaDangBat = AppLock.isEnabled(this, uid);

        // Email
        datPhuDe(hangEmail, daXacThucEmail
                ? email
                : "Chạm để gửi email xác thực tới " + email);
        datChip(hangEmail, daXacThucEmail ? "Đã xác thực" : "Chưa xác thực", daXacThucEmail);

        // Khóa ứng dụng
        datCongTac(khoaDangBat);

        hangThoiGian.setEnabled(khoaDangBat);
        hangThoiGian.setAlpha(khoaDangBat ? 1f : 0.45f);

        int chiSo = AppLock.chiSoThoiGian(this, uid);
        datPhuDe(hangThoiGian, khoaDangBat
                ? AppLock.NHAN_THOI_GIAN[chiSo]
                : "Bật khóa ứng dụng để chọn thời gian");

        // Hoạt động tài khoản
        FirebaseUserMetadata meta = nguoiDung.getMetadata();

        datPhuDe(hangDangNhapCuoi, meta == null
                ? "Không rõ" : dinhDangThoiGian(meta.getLastSignInTimestamp()));
        datPhuDe(hangNgayTao, meta == null
                ? "Không rõ" : dinhDangThoiGian(meta.getCreationTimestamp()));

        capNhatMucBaoMat(daXacThucEmail, khoaDangBat);
    }

    private void capNhatMucBaoMat(boolean daXacThucEmail, boolean khoaDangBat) {

        int diem = (daXacThucEmail ? 1 : 0) + (khoaDangBat ? 1 : 0);

        String muc;
        String mau;
        String moTa;

        if (diem == 2) {
            muc = "Rất tốt";
            mau = "#059669";
            moTa = "Tài khoản của bạn đang được bảo vệ đầy đủ.";
        } else if (diem == 1) {
            muc = "Khá tốt";
            mau = "#0891B2";
            moTa = daXacThucEmail
                    ? "Bật khóa ứng dụng để bảo vệ dữ liệu tài chính trên máy."
                    : "Xác thực email để xác nhận bạn là chủ tài khoản.";
        } else {
            muc = "Cần cải thiện";
            mau = "#D97706";
            moTa = "Hãy xác thực email và bật khóa ứng dụng.";
        }

        int mauInt = Color.parseColor(mau);

        tvMucBaoMat.setText("Mức bảo mật: " + muc);
        tvMucBaoMat.setTextColor(mauInt);
        tvMoTaBaoMat.setText(moTa);

        progressBaoMat.setIndicatorColor(mauInt);
        progressBaoMat.setProgressCompat(Math.max(diem * 50, 6), true);
    }

    // ------------------------------------------------------------------
    //  Xác thực email
    // ------------------------------------------------------------------

    private void guiEmailXacThuc() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            return;
        }

        if (nguoiDung.isEmailVerified()) {
            thongBao("Email của bạn đã được xác thực");
            return;
        }

        nguoiDung.sendEmailVerification().addOnCompleteListener(task -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (task.isSuccessful()) {
                thongBao("Đã gửi email xác thực. Hãy kiểm tra cả mục Thư rác.");
            } else if (task.getException() instanceof FirebaseTooManyRequestsException) {
                thongBao("Bạn vừa yêu cầu quá nhiều lần, hãy thử lại sau");
            } else {
                thongBao("Không gửi được email, hãy kiểm tra kết nối mạng");
            }
        });
    }

    // ------------------------------------------------------------------
    //  Khóa ứng dụng
    // ------------------------------------------------------------------

    private void xuLyDoiKhoa(boolean bat) {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            return;
        }

        String uid = nguoiDung.getUid();

        // Trả công tắc về trạng thái cũ, chỉ gạt thật sự sau khi xác thực xong
        datCongTac(!bat);

        if (bat) {

            if (!AppLock.thietBiHoTro(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Chưa thể bật khóa ứng dụng")
                        .setMessage("Thiết bị chưa có vân tay, khuôn mặt hoặc khóa màn hình. "
                                + "Hãy cài đặt khóa màn hình trong Cài đặt của máy rồi thử lại.")
                        .setPositiveButton("Đã hiểu", null)
                        .show();
                return;
            }

            xacThuc("Bật khóa ứng dụng", "Xác thực để xác nhận", () -> {
                AppLock.setEnabled(this, uid, true);
                AppLock.daMoKhoa(uid);
                capNhat();
                thongBao("Đã bật khóa ứng dụng");
            });

        } else {

            xacThuc("Tắt khóa ứng dụng", "Xác thực để xác nhận", () -> {
                AppLock.setEnabled(this, uid, false);
                capNhat();
                thongBao("Đã tắt khóa ứng dụng");
            });
        }
    }

    private void xacThuc(String tieuDe, String phuDe, Runnable khiThanhCong) {

        BiometricPrompt prompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        khiThanhCong.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {

                        boolean nguoiDungHuy =
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                        || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                        || errorCode == BiometricPrompt.ERROR_CANCELED;

                        if (!nguoiDungHuy) {
                            thongBao(String.valueOf(errString));
                        }
                    }
                });

        BiometricPrompt.PromptInfo thongTin = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(tieuDe)
                .setSubtitle(phuDe)
                .setAllowedAuthenticators(AppLock.KIEU_XAC_THUC)
                .build();

        prompt.authenticate(thongTin);
    }

    private void chonThoiGianKhoa() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            return;
        }

        String uid = nguoiDung.getUid();

        new AlertDialog.Builder(this)
                .setTitle("Tự động khóa")
                .setSingleChoiceItems(
                        AppLock.NHAN_THOI_GIAN,
                        AppLock.chiSoThoiGian(this, uid),
                        (dialog, which) -> {
                            AppLock.setTimeout(this, uid, AppLock.MOC_THOI_GIAN[which]);
                            dialog.dismiss();
                            capNhat();
                        })
                .setNegativeButton("Đóng", null)
                .show();
    }

    // ------------------------------------------------------------------

    private void veManHinhDangNhap() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void thongBao(String noiDung) {
        Toast.makeText(this, noiDung, Toast.LENGTH_SHORT).show();
    }
}
