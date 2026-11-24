package com.example.qlchitieu.wallet;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.BalanceAdjustmentRepository;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Wallet;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.Locale;

public class AdjustBalanceActivity extends AppCompatActivity {

    TextView tvQuayLaiDieuChinh, tvSoDuHienTai;
    TextView tvTenViDieuChinh, tvChenhLechDieuChinh;

    EditText edtSoDuThucTe, edtLyDoDieuChinh;
    MaterialButton btnXacNhanDieuChinh;

    FirebaseAuth firebaseAuth;
    WalletRepository walletRepository;
    BalanceAdjustmentRepository adjustmentRepository;

    String maVi;
    String uid;
    long soDuHienTai = 0;
    boolean daTaiVi = false;

    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjust_balance);
        com.example.qlchitieu.util.InsetsUtil.apply(this);

        tvQuayLaiDieuChinh = findViewById(R.id.tvQuayLaiDieuChinh);
        tvSoDuHienTai = findViewById(R.id.tvSoDuHienTai);
        tvTenViDieuChinh = findViewById(R.id.tvTenViDieuChinh);
        tvChenhLechDieuChinh = findViewById(R.id.tvChenhLechDieuChinh);

        edtSoDuThucTe = findViewById(R.id.edtSoDuThucTe);
        edtLyDoDieuChinh = findViewById(R.id.edtLyDoDieuChinh);
        btnXacNhanDieuChinh = findViewById(R.id.btnXacNhanDieuChinh);

        firebaseAuth = FirebaseAuth.getInstance();
        walletRepository = new WalletRepository(this);
        adjustmentRepository = new BalanceAdjustmentRepository(this);

        maVi = getIntent().getStringExtra("maVi");

        tvQuayLaiDieuChinh.setOnClickListener(v -> finish());

        btnXacNhanDieuChinh.setEnabled(false);

        edtSoDuThucTe.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                tinhChenhLech();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnXacNhanDieuChinh.setOnClickListener(v -> xacNhanDieuChinh());

        if (maVi == null || maVi.trim().isEmpty()) {
            Toast.makeText(this,
                    "Không tìm thấy mã ví",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        uid = nguoiDung.getUid();

        taiThongTinVi();
    }

    private void taiThongTinVi() {

        // 1) Hiện ngay thông tin ví từ SQLite và cho phép điều chỉnh
        // (SQLite là nguồn dữ liệu chính nên không cần chờ mạng)
        hienThiVi();

        if (daTaiVi) {
            btnXacNhanDieuChinh.setEnabled(true);
        }

        // 2) Đồng bộ với Firestore ở nền, xong thì hiện lại.
        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            hienThiVi();

            if (daTaiVi) {
                btnXacNhanDieuChinh.setEnabled(true);
            } else if (thanhCong) {
                Toast.makeText(this,
                        "Ví không tồn tại",
                        Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this,
                        "Không thể tải ví: " + loi,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Đọc ví từ SQLite và hiển thị lên màn hình. */
    private void hienThiVi() {

        Wallet vi = walletRepository.getWallet(uid, maVi);

        if (vi == null) {
            return;
        }

        soDuHienTai = vi.getSoDu();
        daTaiVi = true;

        tvTenViDieuChinh.setText(vi.getTenVi());

        tvSoDuHienTai.setText(
                dinhDangTien.format(soDuHienTai) + " ₫"
        );

        tinhChenhLech();
    }

    private void tinhChenhLech() {

        String soDuNhap = edtSoDuThucTe.getText()
                .toString().trim();

        if (!daTaiVi || soDuNhap.isEmpty()) {
            tvChenhLechDieuChinh.setText("0 ₫");
            tvChenhLechDieuChinh.setTextColor(
                    Color.parseColor("#102A4C")
            );
            return;
        }

        try {
            long soDuMoi = Long.parseLong(soDuNhap);
            long chenhLech = Math.subtractExact(
                    soDuMoi,
                    soDuHienTai
            );

            String dau = chenhLech > 0 ? "+" : "";

            tvChenhLechDieuChinh.setText(
                    dau + dinhDangTien.format(chenhLech) + " ₫"
            );

            if (chenhLech > 0) {
                tvChenhLechDieuChinh.setTextColor(
                        Color.parseColor("#059669")
                );
            } else if (chenhLech < 0) {
                tvChenhLechDieuChinh.setTextColor(
                        Color.parseColor("#DC2626")
                );
            } else {
                tvChenhLechDieuChinh.setTextColor(
                        Color.parseColor("#102A4C")
                );
            }

        } catch (NumberFormatException | ArithmeticException e) {

            tvChenhLechDieuChinh.setText("Số tiền không hợp lệ");
            tvChenhLechDieuChinh.setTextColor(
                    Color.parseColor("#DC2626")
            );
        }
    }

    private void xacNhanDieuChinh() {

        if (!daTaiVi) {
            return;
        }

        String soDuNhap = edtSoDuThucTe.getText()
                .toString().trim();

        String lyDo = edtLyDoDieuChinh.getText()
                .toString().trim();

        if (soDuNhap.isEmpty()) {
            edtSoDuThucTe.setError("Vui lòng nhập số dư thực tế");
            return;
        }

        long soDuMoi;

        try {
            soDuMoi = Long.parseLong(soDuNhap);
        } catch (NumberFormatException e) {
            edtSoDuThucTe.setError("Số tiền không hợp lệ");
            return;
        }

        if (soDuMoi < 0) {
            edtSoDuThucTe.setError("Số dư không được âm");
            return;
        }

        if (lyDo.isEmpty()) {
            edtLyDoDieuChinh.setError("Vui lòng nhập lý do");
            return;
        }

        if (lyDo.length() > 500) {
            edtLyDoDieuChinh.setError("Lý do tối đa 500 ký tự");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận điều chỉnh")
                .setMessage(
                        "Số dư thực tế: "
                                + dinhDangTien.format(soDuMoi)
                                + " ₫\n\n"
                                + "Lý do: " + lyDo
                                + "\n\nSố dư sẽ được kiểm tra lại trước khi lưu."
                )
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (dialog, which) ->
                        luuDieuChinh(soDuMoi, lyDo))
                .show();
    }

    private void luuDieuChinh(long soDuMoi, String lyDo) {

        btnXacNhanDieuChinh.setEnabled(false);

        adjustmentRepository.adjustBalance(uid, maVi, soDuMoi, lyDo,
                (thanhCong, loi) -> {
                    if (thanhCong) {
                        Toast.makeText(this,
                                "Điều chỉnh số dư thành công!",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        btnXacNhanDieuChinh.setEnabled(true);
                        Toast.makeText(this,
                                "Không thể điều chỉnh: " + loi,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}