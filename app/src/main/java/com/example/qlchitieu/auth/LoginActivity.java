
package com.example.qlchitieu.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.dashboard.MainActivity;
import com.example.qlchitieu.R;
import com.example.qlchitieu.util.EmailRule;
import com.example.qlchitieu.util.NetworkUtil;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    EditText edtEmailDangNhap, edtMatKhauDangNhap;
    MaterialButton btnDangNhap;
    TextView tvChuyenDangKy, tvQuenMatKhau;

    FirebaseAuth firebaseAuth;

    // Đang kiểm tra / đang hỏi xác minh: tránh mở chồng nhiều hộp thoại khi onStart chạy lại
    private boolean dangXuLyXacMinh = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtEmailDangNhap = findViewById(R.id.edtEmailDangNhap);
        edtMatKhauDangNhap = findViewById(R.id.edtMatKhauDangNhap);
        btnDangNhap = findViewById(R.id.btnDangNhap);
        tvChuyenDangKy = findViewById(R.id.tvChuyenDangKy);
        tvQuenMatKhau = findViewById(R.id.tvQuenMatKhau);

        firebaseAuth = FirebaseAuth.getInstance();


        btnDangNhap.setOnClickListener(v -> dangNhap());

        tvChuyenDangKy.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        tvQuenMatKhau.setOnClickListener(v -> quenMatKhau());
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser nguoiDung = FirebaseAuth.getInstance().getCurrentUser();

        if (nguoiDung == null || dangXuLyXacMinh) {
            return;
        }

        if (nguoiDung.isEmailVerified()) {
            vaoManHinhChinh();
            return;
        }

        // Phiên cũ chưa xác minh: tải lại thông tin (người dùng có thể vừa bấm liên kết)
        dangXuLyXacMinh = true;

        nguoiDung.reload().addOnCompleteListener(this, task -> {

            FirebaseUser moi = firebaseAuth.getCurrentUser();

            if (!task.isSuccessful() || moi == null) {
                dangXuLyXacMinh = false;
                Toast.makeText(this,
                        getString(R.string.login_verify_check_failed),
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (moi.isEmailVerified()) {
                dangXuLyXacMinh = false;
                vaoManHinhChinh();
                return;
            }

            hienThiChuaXacMinh(moi);
        });
    }

    private void vaoManHinhChinh() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    /** Tài khoản chưa xác minh email: không cho vào, cho gửi lại email, rồi đăng xuất. */
    private void hienThiChuaXacMinh(FirebaseUser nguoiDung) {

        dangXuLyXacMinh = true;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.login_unverified_title))
                .setMessage(getString(R.string.login_unverified_message, nguoiDung.getEmail()))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.login_resend), (dialog, which) -> {

                    firebaseAuth.setLanguageCode("vi");

                    nguoiDung.sendEmailVerification()
                            .addOnCompleteListener(this, task -> {

                                Toast.makeText(this,
                                        task.isSuccessful()
                                                ? getString(R.string.login_resend_ok)
                                                : getString(R.string.login_resend_fail),
                                        Toast.LENGTH_LONG).show();

                                firebaseAuth.signOut();
                                dangXuLyXacMinh = false;
                            });
                })
                .setNegativeButton(getString(R.string.login_close), (dialog, which) -> {
                    firebaseAuth.signOut();
                    dangXuLyXacMinh = false;
                })
                .show();
    }


    private void dangNhap() {
        String email = EmailRule.chuanHoa(edtEmailDangNhap.getText().toString());
        String matKhau = edtMatKhauDangNhap.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmailDangNhap.setError(getString(R.string.login_email_invalid));
            edtEmailDangNhap.requestFocus();
            return;
        }

        if (matKhau.isEmpty()) {
            edtMatKhauDangNhap.setError(getString(R.string.login_password_required));
            edtMatKhauDangNhap.requestFocus();
            return;
        }

        if (!NetworkUtil.coMang(this)) {
            Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_LONG).show();
            return;
        }

        btnDangNhap.setEnabled(false);
        btnDangNhap.setText(getString(R.string.login_in_progress));

        firebaseAuth.signInWithEmailAndPassword(email, matKhau)
                .addOnCompleteListener(this, task -> {

                    btnDangNhap.setEnabled(true);
                    btnDangNhap.setText(getString(R.string.login_button));

                    if (task.isSuccessful()) {

                        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

                        // Chưa bấm liên kết trong email xác minh thì chưa cho vào
                        if (nguoiDung != null && !nguoiDung.isEmailVerified()) {
                            hienThiChuaXacMinh(nguoiDung);
                            return;
                        }

                        Toast.makeText(this,
                                getString(R.string.login_success),
                                Toast.LENGTH_SHORT).show();

                        vaoManHinhChinh();
                    } else {
                        Toast.makeText(this,
                                getString(AuthErrorMapper.choDangNhap(task.getException())),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void quenMatKhau() {
        String email = EmailRule.chuanHoa(edtEmailDangNhap.getText().toString());

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmailDangNhap.setError(getString(R.string.reset_enter_email));
            edtEmailDangNhap.requestFocus();
            return;
        }

        if (!NetworkUtil.coMang(this)) {
            Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_LONG).show();
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                getString(R.string.reset_sent),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                getString(AuthErrorMapper.choQuenMatKhau(task.getException())),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
