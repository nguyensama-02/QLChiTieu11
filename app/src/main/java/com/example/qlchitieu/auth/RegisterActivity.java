package com.example.qlchitieu.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.qlchitieu.data.repository.UserProfileRepository;
import com.example.qlchitieu.util.EmailRule;
import com.example.qlchitieu.util.NetworkUtil;
import com.example.qlchitieu.util.PasswordRule;

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


        btnDangKy.setOnClickListener(v -> dangKyTaiKhoan());
        // Login luôn nằm ngay dưới Register trong back stack nên chỉ cần đóng màn này
        findViewById(R.id.tvDangNhap).setOnClickListener(v -> finish());
    }

    private void dangKyTaiKhoan() {

        String hoTen = edtHoTen.getText().toString().trim();
        String email = EmailRule.chuanHoa(edtEmail.getText().toString());
        String matKhau = edtMatKhau.getText().toString();
        String xacNhan = edtXacNhanMatKhau.getText().toString();

        if (hoTen.isEmpty()) {
            edtHoTen.setError(getString(R.string.register_name_required));
            edtHoTen.requestFocus();
            return;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError(getString(R.string.register_email_invalid));
            edtEmail.requestFocus();
            return;
        }

        // Chỉ nhận Gmail: ten@gmail.com, phần tên 6-30 ký tự gồm chữ, số, dấu chấm
        if (!EmailRule.laGmailHopLe(email)) {
            edtEmail.setError(getString(R.string.register_email_gmail_only));
            edtEmail.requestFocus();
            return;
        }

        if (!PasswordRule.laHopLe(matKhau)) {
            edtMatKhau.setError(getString(R.string.register_password_weak));
            edtMatKhau.requestFocus();
            return;
        }

        if (!matKhau.equals(xacNhan)) {
            edtXacNhanMatKhau.setError(getString(R.string.register_confirm_mismatch));
            edtXacNhanMatKhau.requestFocus();
            return;
        }

        if (!NetworkUtil.coMang(this)) {
            Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_LONG).show();
            return;
        }

        btnDangKy.setEnabled(false);
        btnDangKy.setText(getString(R.string.register_creating));

        firebaseAuth.createUserWithEmailAndPassword(email, matKhau)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        btnDangKy.setEnabled(true);
                        btnDangKy.setText(getString(R.string.register_button));

                        String loi = getString(AuthErrorMapper.choDangKy(task.getException()));

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
                                btnDangKy.setText(getString(R.string.register_button));

                                hienThongBaoXacMinh(email, guiTask.isSuccessful());
                            });
                });
    }

    private void hienThongBaoXacMinh(String email, boolean daGui) {

        String noiDung = daGui
                ? getString(R.string.register_verify_sent, email)
                : getString(R.string.register_verify_not_sent);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.register_verify_title))
                .setMessage(noiDung)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.register_go_login), (dialog, which) -> finish())
                .show();
    }
}
