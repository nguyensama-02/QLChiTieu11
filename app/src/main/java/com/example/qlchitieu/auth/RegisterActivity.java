package com.example.qlchitieu.auth;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText edtHoTen, edtEmail, edtMatKhau, edtXacNhanMatKhau;
    MaterialButton btnDangKy;

    FirebaseAuth firebaseAuth;
    FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtHoTen = findViewById(R.id.edtHoTen);
        edtEmail = findViewById(R.id.edtEmail);
        edtMatKhau = findViewById(R.id.edtMatKhau);
        edtXacNhanMatKhau = findViewById(R.id.edtXacNhanMatKhau);
        btnDangKy = findViewById(R.id.btnDangKy);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        caiDatHienAnMatKhau(edtMatKhau);
        caiDatHienAnMatKhau(edtXacNhanMatKhau);

        btnDangKy.setOnClickListener(v -> dangKyTaiKhoan());
        findViewById(R.id.tvDangNhap).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });
    }

    private void dangKyTaiKhoan() {

        String hoTen = edtHoTen.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String matKhau = edtMatKhau.getText().toString();
        String xacNhan = edtXacNhanMatKhau.getText().toString();

        if (hoTen.isEmpty()) {
            edtHoTen.setError("Vui lòng nhập họ và tên");
            edtHoTen.requestFocus();
            return;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Email không hợp lệ");
            edtEmail.requestFocus();
            return;
        }

        if (matKhau.length() < 6) {
            edtMatKhau.setError("Mật khẩu phải có ít nhất 6 ký tự");
            edtMatKhau.requestFocus();
            return;
        }

        if (!matKhau.equals(xacNhan)) {
            edtXacNhanMatKhau.setError("Mật khẩu xác nhận không khớp");
            edtXacNhanMatKhau.requestFocus();
            return;
        }

        btnDangKy.setEnabled(false);
        btnDangKy.setText("ĐANG TẠO TÀI KHOẢN...");

        firebaseAuth.createUserWithEmailAndPassword(email, matKhau)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        btnDangKy.setEnabled(true);
                        btnDangKy.setText("ĐĂNG KÝ");

                        String loi = task.getException() != null
                                ? task.getException().getMessage()
                                : "Không thể đăng ký";

                        Toast.makeText(this, loi, Toast.LENGTH_LONG).show();
                        return;
                    }

                    String uid = firebaseAuth.getCurrentUser().getUid();

                    Map<String, Object> nguoiDung = new HashMap<>();
                    nguoiDung.put("uid", uid);
                    nguoiDung.put("hoTen", hoTen);
                    nguoiDung.put("email", email);
                    nguoiDung.put("ngayTao",
                            com.google.firebase.firestore.FieldValue.serverTimestamp());

                    firestore.collection("users")
                            .document(uid)
                            .set(nguoiDung)
                            .addOnSuccessListener(unused -> {

                                Toast.makeText(this,
                                        "Đăng ký tài khoản thành công!",
                                        Toast.LENGTH_LONG).show();

                                btnDangKy.setEnabled(true);
                                btnDangKy.setText("ĐĂNG KÝ");

                            })
                            .addOnFailureListener(e -> {

                                btnDangKy.setEnabled(true);
                                btnDangKy.setText("ĐĂNG KÝ");

                                Toast.makeText(this,
                                        "Đã tạo tài khoản nhưng chưa lưu được hồ sơ: "
                                                + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
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
