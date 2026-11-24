
package com.example.qlchitieu.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.dashboard.MainActivity;
import com.example.qlchitieu.R;
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

        caiDatHienAnMatKhau(edtMatKhauDangNhap);

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
                        "Không kiểm tra được trạng thái xác minh. Hãy kiểm tra kết nối rồi thử lại",
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
                .setTitle("Email chưa được xác minh")
                .setMessage("Hãy mở email gửi tới " + nguoiDung.getEmail()
                        + " (kiểm tra cả thư rác), bấm vào liên kết xác minh "
                        + "rồi đăng nhập lại.")
                .setCancelable(false)
                .setPositiveButton("Gửi lại email", (dialog, which) -> {

                    firebaseAuth.setLanguageCode("vi");

                    nguoiDung.sendEmailVerification()
                            .addOnCompleteListener(this, task -> {

                                Toast.makeText(this,
                                        task.isSuccessful()
                                                ? "Đã gửi lại email xác minh"
                                                : "Chưa gửi được, vui lòng thử lại sau ít phút",
                                        Toast.LENGTH_LONG).show();

                                firebaseAuth.signOut();
                                dangXuLyXacMinh = false;
                            });
                })
                .setNegativeButton("Đóng", (dialog, which) -> {
                    firebaseAuth.signOut();
                    dangXuLyXacMinh = false;
                })
                .show();
    }


    private void dangNhap() {
        String email = edtEmailDangNhap.getText().toString().trim();
        String matKhau = edtMatKhauDangNhap.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmailDangNhap.setError("Email không hợp lệ");
            edtEmailDangNhap.requestFocus();
            return;
        }

        if (matKhau.isEmpty()) {
            edtMatKhauDangNhap.setError("Vui lòng nhập mật khẩu");
            edtMatKhauDangNhap.requestFocus();
            return;
        }

        btnDangNhap.setEnabled(false);
        btnDangNhap.setText("ĐANG ĐĂNG NHẬP...");

        firebaseAuth.signInWithEmailAndPassword(email, matKhau)
                .addOnCompleteListener(this, task -> {

                    btnDangNhap.setEnabled(true);
                    btnDangNhap.setText("ĐĂNG NHẬP");

                    if (task.isSuccessful()) {

                        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

                        // Chưa bấm liên kết trong email xác minh thì chưa cho vào
                        if (nguoiDung != null && !nguoiDung.isEmailVerified()) {
                            hienThiChuaXacMinh(nguoiDung);
                            return;
                        }

                        Toast.makeText(this,
                                "Đăng nhập thành công!",
                                Toast.LENGTH_SHORT).show();

                        vaoManHinhChinh();
                    } else {
                        Toast.makeText(this,
                                "Email hoặc mật khẩu không đúng",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void quenMatKhau() {
        String email = edtEmailDangNhap.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmailDangNhap.setError("Nhập email để khôi phục mật khẩu");
            edtEmailDangNhap.requestFocus();
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Nếu email có tài khoản, hãy kiểm tra hộp thư để đặt lại mật khẩu",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Không thể gửi yêu cầu. Vui lòng thử lại sau",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void caiDatHienAnMatKhau(EditText editText) {
        final boolean[] hienMatKhau = {false};

        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {

                android.graphics.drawable.Drawable icon =
                        editText.getCompoundDrawablesRelative()[2];

                if (icon != null) {
                    int viTriIcon = editText.getWidth()
                            - editText.getPaddingEnd()
                            - icon.getBounds().width();

                    if (event.getX() >= viTriIcon) {
                        hienMatKhau[0] = !hienMatKhau[0];

                        if (hienMatKhau[0]) {
                            editText.setInputType(
                                    InputType.TYPE_CLASS_TEXT |
                                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            );
                        } else {
                            editText.setInputType(
                                    InputType.TYPE_CLASS_TEXT |
                                            InputType.TYPE_TEXT_VARIATION_PASSWORD
                            );
                        }

                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                R.drawable.ic_lock,
                                0,
                                hienMatKhau[0]
                                        ? R.drawable.ic_eye
                                        : R.drawable.ic_eye_off,
                                0
                        );

                        editText.setSelection(editText.length());
                        return true;
                    }
                }
            }
            return false;
        });
    }
}
