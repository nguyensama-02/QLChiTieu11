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
import com.example.qlchitieu.model.Wallet;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.Locale;

public class EditWalletActivity extends AppCompatActivity {

    EditText edtTenViSua;
    Spinner spLoaiViSua;
    TextView tvSoDuViSua, tvQuayLaiSuaVi;
    MaterialButton btnLuuSuaVi;

    FirebaseAuth firebaseAuth;
    WalletRepository walletRepository;

    String maVi;
    String[] danhSachLoaiVi = {
            "Tiền mặt",
            "Ngân hàng",
            "Ví điện tử"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_wallet);

        edtTenViSua = findViewById(R.id.edtTenViSua);
        spLoaiViSua = findViewById(R.id.spLoaiViSua);
        tvSoDuViSua = findViewById(R.id.tvSoDuViSua);
        tvQuayLaiSuaVi = findViewById(R.id.tvQuayLaiSuaVi);
        btnLuuSuaVi = findViewById(R.id.btnLuuSuaVi);

        firebaseAuth = FirebaseAuth.getInstance();
        walletRepository = new WalletRepository(this);

        maVi = getIntent().getStringExtra("maVi");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhSachLoaiVi
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spLoaiViSua.setAdapter(adapter);

        tvQuayLaiSuaVi.setOnClickListener(v -> finish());

        btnLuuSuaVi.setEnabled(false);
        btnLuuSuaVi.setOnClickListener(v -> luuThayDoi());

        if (maVi == null || maVi.trim().isEmpty()) {
            Toast.makeText(this,
                    "Không tìm thấy mã ví",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        taiThongTinVi();
    }

    /** Trả về UID người dùng hiện tại, hoặc null nếu chưa đăng nhập. */
    private String layUid() {
        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();
        return nguoiDung == null ? null : nguoiDung.getUid();
    }

    private void taiThongTinVi() {

        String uid = layUid();

        if (uid == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Wallet vi = walletRepository.getWallet(uid, maVi);

        if (vi == null) {
            Toast.makeText(this,
                    "Ví không tồn tại",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        edtTenViSua.setText(vi.getTenVi());

        for (int i = 0; i < danhSachLoaiVi.length; i++) {
            if (danhSachLoaiVi[i].equals(vi.getLoaiVi())) {
                spLoaiViSua.setSelection(i);
                break;
            }
        }

        NumberFormat dinhDang = NumberFormat.getInstance(
                new Locale("vi", "VN")
        );

        tvSoDuViSua.setText(dinhDang.format(vi.getSoDu()) + " ₫");

        btnLuuSuaVi.setEnabled(true);
    }

    private void luuThayDoi() {

        String tenViMoi = edtTenViSua.getText()
                .toString().trim();

        String loaiViMoi = spLoaiViSua.getSelectedItem()
                .toString();

        if (tenViMoi.isEmpty()) {
            edtTenViSua.setError("Vui lòng nhập tên ví");
            edtTenViSua.requestFocus();
            return;
        }

        if (tenViMoi.length() > 50) {
            edtTenViSua.setError("Tên ví tối đa 50 ký tự");
            return;
        }

        String uid = layUid();

        if (uid == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnLuuSuaVi.setEnabled(false);

        walletRepository.updateWalletInfo(uid, maVi, tenViMoi, loaiViMoi,
                (thanhCong, loi) -> {
                    if (thanhCong) {
                        Toast.makeText(this,
                                "Cập nhật ví thành công!",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        btnLuuSuaVi.setEnabled(true);
                        Toast.makeText(this,
                                "Không thể cập nhật ví: " + loi,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}