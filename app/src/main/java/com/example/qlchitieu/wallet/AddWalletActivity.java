package com.example.qlchitieu.wallet;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


public class AddWalletActivity extends AppCompatActivity {

    EditText edtTenVi, edtSoDuBanDau;
    Spinner spLoaiVi;
    MaterialButton btnLuuVi;
    TextView tvQuayLaiThemVi;

    FirebaseAuth firebaseAuth;
    WalletRepository walletRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_wallet);

        edtTenVi = findViewById(R.id.edtTenVi);
        edtSoDuBanDau = findViewById(R.id.edtSoDuBanDau);
        spLoaiVi = findViewById(R.id.spLoaiVi);
        btnLuuVi = findViewById(R.id.btnLuuVi);
        tvQuayLaiThemVi = findViewById(R.id.tvQuayLaiThemVi);

        firebaseAuth = FirebaseAuth.getInstance();
        walletRepository = new WalletRepository(this);

        String[] danhSachLoaiVi = {
                "Tiền mặt",
                "Ngân hàng",
                "Ví điện tử"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhSachLoaiVi
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spLoaiVi.setAdapter(adapter);

        tvQuayLaiThemVi.setOnClickListener(v -> finish());

        btnLuuVi.setOnClickListener(v -> luuVi());
    }

    private void luuVi() {

        String tenVi = edtTenVi.getText().toString().trim();
        String loaiVi = spLoaiVi.getSelectedItem().toString();
        String soDuNhap = edtSoDuBanDau.getText().toString().trim();

        if (tenVi.isEmpty()) {
            edtTenVi.setError("Vui lòng nhập tên ví");
            edtTenVi.requestFocus();
            return;
        }

        if (soDuNhap.isEmpty()) {
            edtSoDuBanDau.setError("Vui lòng nhập số dư ban đầu");
            edtSoDuBanDau.requestFocus();
            return;
        }

        long soDu;

        try {
            soDu = Long.parseLong(soDuNhap);
        } catch (NumberFormatException e) {
            edtSoDuBanDau.setError("Số dư không hợp lệ");
            return;
        }

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnLuuVi.setEnabled(false);

        walletRepository.addWallet(nguoiDung.getUid(), tenVi, loaiVi, soDu,
                (thanhCong, loi) -> {
                    if (thanhCong) {
                        Toast.makeText(this, "Thêm ví thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        btnLuuVi.setEnabled(true);
                        Toast.makeText(this, "Lỗi lưu ví: " + loi, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
