package com.example.qlchitieu.auth;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.qlchitieu.data.repository.UserProfileRepository;
import com.example.qlchitieu.util.EmailRule;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText edtHoTen, edtEmail, edtMatKhau, edtXacNhanMatKhau;
    MaterialButton btnDangKy;

    FirebaseAuth firebaseAuth;
    UserProfileRepository userProfileRepository;

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
        userProfileRepository = new UserProfileRepository(this);

        caiDatHienAnMatKhau(edtMatKhau);
        caiDatHienAnMatKhau(edtXacNhanMatKhau);

        btnDangKy.setOnClickListener(v -> dangKyTaiKhoan());
        findViewById(R.id.tvDangNhap).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });
    }

    private void dangKyTaiKhoan() {

        String hoTen = edtHoTen.getText().toString().trim();
        String email = EmailRule.chuanHoa(edtEmail.getText().toString());
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

        // Chỉ nhận Gmail: ten@gmail.com, phần tên 6-30 ký tự gồm chữ, số, dấu chấm
        if (!EmailRule.laGmailHopLe(email)) {
            edtEmail.setError("Chỉ nhận địa chỉ Gmail hợp lệ, ví dụ ten.ban@gmail.com");
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

                    FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();
                    String uid = nguoiDung.getUid();

                    // Lưu hồ sơ vào SQLite, SyncManager sẽ đưa lên Firestore (kể cả khi mất mạng lúc này)
                    userProfileRepository.taoHoSo(uid, hoTen, email);

                    // Gửi email xác minh rồi đăng xuất: chỉ vào được app sau khi bấm liên kết trong email
                    firebaseAuth.setLanguageCode("vi");

                    nguoiDung.sendEmailVerification()
                            .addOnCompleteListener(this, guiTask -> {

                                firebaseAuth.signOut();

                                btnDangKy.setEnabled(true);
                                btnDangKy.setText("ĐĂNG KÝ");

                                hienThongBaoXacMinh(email, guiTask.isSuccessful());
                            });
                });
    }

    private void hienThongBaoXacMinh(String email, boolean daGui) {

        String noiDung = daGui
                ? "Đã gửi email xác minh tới " + email + ".\n\n"
                  + "Hãy mở email (kiểm tra cả thư rác), bấm vào liên kết, "
                  + "rồi quay lại đăng nhập."
                : "Tài khoản đã được tạo nhưng chưa gửi được email xác minh.\n\n"
                  + "Hãy đăng nhập, app sẽ cho bạn gửi lại.";

        new AlertDialog.Builder(this)
                .setTitle("Xác minh email")
                .setMessage(noiDung)
                .setCancelable(false)
                .setPositiveButton("Đến đăng nhập", (dialog, which) -> finish())
                .show();
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