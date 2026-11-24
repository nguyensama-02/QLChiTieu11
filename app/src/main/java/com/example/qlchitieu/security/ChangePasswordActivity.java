package com.example.qlchitieu.security;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Đổi mật khẩu: xác nhận lại bằng mật khẩu hiện tại (Firebase yêu cầu đăng nhập gần đây)
 * rồi đặt mật khẩu mới. Cần kết nối mạng.
 */
public class ChangePasswordActivity extends AppCompatActivity {

    private static final String XANH = "#059669";
    private static final String XAM = "#64748B";

    FirebaseAuth firebaseAuth;

    TextInputLayout tilMatKhauCu, tilMatKhauMoi, tilXacNhan;
    TextInputEditText edtMatKhauCu, edtMatKhauMoi, edtXacNhan;
    LinearProgressIndicator progressDoManh;
    TextView tvDoManh, tvQuyTac1, tvQuyTac2, tvQuyTac3;
    MaterialButton btnDoiMatKhau;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        firebaseAuth = FirebaseAuth.getInstance();

        if (firebaseAuth.getCurrentUser() == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        tilMatKhauCu = findViewById(R.id.tilMatKhauCu);
        tilMatKhauMoi = findViewById(R.id.tilMatKhauMoi);
        tilXacNhan = findViewById(R.id.tilXacNhan);
        edtMatKhauCu = findViewById(R.id.edtMatKhauCu);
        edtMatKhauMoi = findViewById(R.id.edtMatKhauMoi);
        edtXacNhan = findViewById(R.id.edtXacNhan);
        progressDoManh = findViewById(R.id.progressDoManh);
        tvDoManh = findViewById(R.id.tvDoManh);
        tvQuyTac1 = findViewById(R.id.tvQuyTac1);
        tvQuyTac2 = findViewById(R.id.tvQuyTac2);
        tvQuyTac3 = findViewById(R.id.tvQuyTac3);
        btnDoiMatKhau = findViewById(R.id.btnDoiMatKhau);

        findViewById(R.id.tvQuayLaiDoiMatKhau).setOnClickListener(v -> finish());

        edtMatKhauMoi.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                tilMatKhauMoi.setError(null);
                capNhatDoManh(s.toString());
            }
        });

        edtMatKhauCu.addTextChangedListener(xoaLoiKhiNhap(tilMatKhauCu));
        edtXacNhan.addTextChangedListener(xoaLoiKhiNhap(tilXacNhan));

        capNhatDoManh("");

        btnDoiMatKhau.setOnClickListener(v -> doiMatKhau());
    }

    private TextWatcher xoaLoiKhiNhap(TextInputLayout o) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                o.setError(null);
            }
        };
    }

    // ------------------------------------------------------------------
    //  Độ mạnh mật khẩu
    // ------------------------------------------------------------------

    private static boolean coChuVaSo(String mk) {
        boolean chu = false;
        boolean so = false;

        for (int i = 0; i < mk.length(); i++) {
            char c = mk.charAt(i);
            if (Character.isLetter(c)) {
                chu = true;
            } else if (Character.isDigit(c)) {
                so = true;
            }
        }
        return chu && so;
    }

    private static boolean coHoaHoacDacBiet(String mk) {
        for (int i = 0; i < mk.length(); i++) {
            char c = mk.charAt(i);
            if (Character.isUpperCase(c) || (!Character.isLetterOrDigit(c) && c != ' ')) {
                return true;
            }
        }
        return false;
    }

    private void capNhatDoManh(String mk) {

        boolean dat1 = mk.length() >= 8;
        boolean dat2 = coChuVaSo(mk);
        boolean dat3 = coHoaHoacDacBiet(mk);

        datQuyTac(tvQuyTac1, "Ít nhất 8 ký tự", dat1);
        datQuyTac(tvQuyTac2, "Có cả chữ và số", dat2);
        datQuyTac(tvQuyTac3, "Có chữ hoa hoặc ký tự đặc biệt", dat3);

        int diem = 0;

        if (!mk.isEmpty()) {
            if (dat1) diem++;
            if (dat2) diem++;
            if (dat3) diem++;
            if (mk.length() >= 12) diem++;
        }

        String nhan;
        String mau;

        switch (diem) {
            case 0:
                nhan = mk.isEmpty() ? "" : "Rất yếu";
                mau = "#DC2626";
                break;
            case 1:
                nhan = "Yếu";
                mau = "#DC2626";
                break;
            case 2:
                nhan = "Trung bình";
                mau = "#D97706";
                break;
            case 3:
                nhan = "Khá";
                mau = "#0891B2";
                break;
            default:
                nhan = "Mạnh";
                mau = XANH;
                break;
        }

        int mauInt = Color.parseColor(mau);

        tvDoManh.setText(nhan);
        tvDoManh.setTextColor(mauInt);

        progressDoManh.setIndicatorColor(mauInt);
        progressDoManh.setProgressCompat(diem * 25, true);
    }

    private void datQuyTac(TextView tv, String noiDung, boolean dat) {
        tv.setText((dat ? "✓  " : "○  ") + noiDung);
        tv.setTextColor(Color.parseColor(dat ? XANH : XAM));
    }

    // ------------------------------------------------------------------
    //  Đổi mật khẩu
    // ------------------------------------------------------------------

    private void doiMatKhau() {

        String cu = String.valueOf(edtMatKhauCu.getText());
        String moi = String.valueOf(edtMatKhauMoi.getText());
        String xacNhan = String.valueOf(edtXacNhan.getText());

        tilMatKhauCu.setError(null);
        tilMatKhauMoi.setError(null);
        tilXacNhan.setError(null);

        if (cu.isEmpty()) {
            tilMatKhauCu.setError("Vui lòng nhập mật khẩu hiện tại");
            return;
        }

        if (moi.length() < 8) {
            tilMatKhauMoi.setError("Mật khẩu mới cần ít nhất 8 ký tự");
            return;
        }

        if (!coChuVaSo(moi)) {
            tilMatKhauMoi.setError("Mật khẩu mới cần có cả chữ và số");
            return;
        }

        if (moi.equals(cu)) {
            tilMatKhauMoi.setError("Mật khẩu mới phải khác mật khẩu hiện tại");
            return;
        }

        if (!moi.equals(xacNhan)) {
            tilXacNhan.setError("Mật khẩu nhập lại không khớp");
            return;
        }

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null || nguoiDung.getEmail() == null) {
            thongBao("Không xác định được tài khoản, hãy đăng nhập lại");
            return;
        }

        datDangXuLy(true);

        // Firebase yêu cầu xác nhận lại danh tính trước khi đổi mật khẩu
        nguoiDung.reauthenticate(EmailAuthProvider.getCredential(nguoiDung.getEmail(), cu))
                .addOnCompleteListener(task -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (!task.isSuccessful()) {
                        datDangXuLy(false);
                        xuLyLoi(task.getException());
                        return;
                    }

                    nguoiDung.updatePassword(moi).addOnCompleteListener(task2 -> {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        datDangXuLy(false);

                        if (task2.isSuccessful()) {
                            thongBao("Đổi mật khẩu thành công");
                            finish();
                        } else {
                            xuLyLoi(task2.getException());
                        }
                    });
                });
    }

    private void xuLyLoi(Exception loi) {

        if (loi instanceof FirebaseAuthInvalidCredentialsException) {
            tilMatKhauCu.setError("Mật khẩu hiện tại không đúng");

        } else if (loi instanceof FirebaseAuthWeakPasswordException) {
            tilMatKhauMoi.setError("Mật khẩu mới quá yếu, hãy chọn mật khẩu mạnh hơn");

        } else if (loi instanceof FirebaseNetworkException) {
            thongBao("Cần kết nối mạng để đổi mật khẩu");

        } else if (loi instanceof FirebaseTooManyRequestsException) {
            thongBao("Bạn thử quá nhiều lần, hãy đợi một lúc rồi thử lại");

        } else if (loi instanceof FirebaseAuthRecentLoginRequiredException) {
            thongBao("Phiên đăng nhập đã cũ, hãy đăng xuất và đăng nhập lại rồi thử lại");

        } else {
            thongBao("Không đổi được mật khẩu, hãy thử lại");
        }
    }

    private void datDangXuLy(boolean dang) {
        btnDoiMatKhau.setEnabled(!dang);
        btnDoiMatKhau.setText(dang ? "Đang cập nhật…" : "Cập nhật mật khẩu");
    }

    private void thongBao(String noiDung) {
        Toast.makeText(this, noiDung, Toast.LENGTH_SHORT).show();
    }
}
