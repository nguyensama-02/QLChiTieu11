
package com.example.qlchitieu.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.dashboard.MainActivity;
import com.example.qlchitieu.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    EditText edtEmailDangNhap, edtMatKhauDangNhap;
    MaterialButton btnDangNhap;
    TextView tvChuyenDangKy, tvQuenMatKhau;

    FirebaseAuth firebaseAuth;

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

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
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
                        Toast.makeText(this,
                                "Đăng nhập thành công!",
                                Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
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
