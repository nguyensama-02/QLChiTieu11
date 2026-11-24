package com.example.qlchitieu.security;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.qlchitieu.R;
import com.example.qlchitieu.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Màn hình khóa: phủ kín mọi màn hình khác cho tới khi xác thực thành công.
 * Luôn có lối thoát: nút Đăng xuất, và tự tắt khóa nếu thiết bị không còn khóa màn hình
 * (tránh bị khóa vĩnh viễn ngoài app).
 */
public class LockActivity extends AppCompatActivity {

    private TextView tvThongBao;

    private BiometricPrompt prompt;
    private BiometricPrompt.PromptInfo thongTinXacThuc;

    /** Hộp xác thực đang mở: tránh mở chồng khi màn hình được start lại. */
    private boolean dangXacThuc = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser nguoiDung = FirebaseAuth.getInstance().getCurrentUser();

        if (nguoiDung == null) {
            finish();
            return;
        }

        // Thiết bị đã gỡ khóa màn hình: không còn gì để xác thực, tắt khóa để không bị kẹt
        if (!AppLock.thietBiHoTro(this)) {
            AppLock.setEnabled(this, nguoiDung.getUid(), false);
            AppLock.daMoKhoa(nguoiDung.getUid());

            Toast.makeText(this,
                    "Đã tắt khóa ứng dụng vì thiết bị không còn khóa màn hình",
                    Toast.LENGTH_LONG).show();

            finish();
            return;
        }

        setContentView(R.layout.activity_lock);

        tvThongBao = findViewById(R.id.tvThongBaoKhoa);

        findViewById(R.id.btnMoKhoa).setOnClickListener(v -> hienXacThuc());
        findViewById(R.id.tvDangXuatKhoa).setOnClickListener(v -> hoiDangXuat());

        // Nút Back không được để lộ nội dung phía sau: đưa app về nền
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        prompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {

                        dangXacThuc = false;

                        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();

                        if (u != null) {
                            AppLock.daMoKhoa(u.getUid());
                        }

                        finish();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {

                        dangXacThuc = false;

                        tvThongBao.setText("Chưa mở khóa được. Chạm “Mở khóa” để thử lại.");
                        tvThongBao.setVisibility(View.VISIBLE);
                    }
                });

        thongTinXacThuc = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Mở khóa Phinova")
                .setSubtitle("Dùng vân tay, khuôn mặt hoặc khóa màn hình")
                .setAllowedAuthenticators(AppLock.KIEU_XAC_THUC)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Tự hiện hộp xác thực mỗi lần màn hình khóa xuất hiện
        if (prompt != null && !dangXacThuc) {
            hienXacThuc();
        }
    }

    private void hienXacThuc() {

        if (prompt == null || dangXacThuc) {
            return;
        }

        dangXacThuc = true;
        tvThongBao.setVisibility(View.GONE);

        prompt.authenticate(thongTinXacThuc);
    }

    private void hoiDangXuat() {

        new AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn sẽ cần đăng nhập lại bằng email và mật khẩu. "
                        + "Dữ liệu trên máy được giữ nguyên.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất", (dialog, which) -> {

                    FirebaseAuth.getInstance().signOut();

                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLock.setManKhoaDangMo(false);
    }
}
