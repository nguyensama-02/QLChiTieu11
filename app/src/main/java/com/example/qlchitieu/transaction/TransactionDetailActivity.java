package com.example.qlchitieu.transaction;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.model.Transaction;
import com.example.qlchitieu.model.Wallet;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailActivity extends AppCompatActivity {

    TextView tvQuayLaiChiTietGiaoDich;
    TextView tvLoaiGiaoDichChiTiet, tvSoTienChiTietGiaoDich;
    TextView tvDanhMucChiTietGiaoDich, tvLoaiChiTiet;
    TextView tvViNguonChiTiet, tvViDichChiTiet;
    TextView tvNgayChiTietGiaoDich, tvGhiChuChiTietGiaoDich;

    MaterialButton btnSuaGiaoDich, btnXoaGiaoDich;

    FirebaseAuth firebaseAuth;
    TransactionRepository transactionRepository;
    WalletRepository walletRepository;

    String maGiaoDich;

    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    SimpleDateFormat dinhDangNgay =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);

        tvQuayLaiChiTietGiaoDich =
                findViewById(R.id.tvQuayLaiChiTietGiaoDich);

        tvLoaiGiaoDichChiTiet =
                findViewById(R.id.tvLoaiGiaoDichChiTiet);

        tvSoTienChiTietGiaoDich =
                findViewById(R.id.tvSoTienChiTietGiaoDich);

        tvDanhMucChiTietGiaoDich =
                findViewById(R.id.tvDanhMucChiTietGiaoDich);

        tvLoaiChiTiet = findViewById(R.id.tvLoaiChiTiet);
        tvViNguonChiTiet = findViewById(R.id.tvViNguonChiTiet);
        tvViDichChiTiet = findViewById(R.id.tvViDichChiTiet);

        tvNgayChiTietGiaoDich =
                findViewById(R.id.tvNgayChiTietGiaoDich);

        tvGhiChuChiTietGiaoDich =
                findViewById(R.id.tvGhiChuChiTietGiaoDich);

        btnSuaGiaoDich = findViewById(R.id.btnSuaGiaoDich);
        btnXoaGiaoDich = findViewById(R.id.btnXoaGiaoDich);

        firebaseAuth = FirebaseAuth.getInstance();
        transactionRepository = new TransactionRepository(this);
        walletRepository = new WalletRepository(this);

        maGiaoDich = getIntent().getStringExtra("maGiaoDich");

        btnXoaGiaoDich.setOnClickListener(v -> {
            xacNhanXoaGiaoDich();
        });

        btnSuaGiaoDich.setOnClickListener(v -> {

            Intent intent = new Intent(
                    TransactionDetailActivity.this,
                    EditTransactionActivity.class
            );

            intent.putExtra("maGiaoDich", maGiaoDich);

            startActivity(intent);
        });

        tvQuayLaiChiTietGiaoDich.setOnClickListener(v -> finish());

        btnSuaGiaoDich.setEnabled(false);
        btnXoaGiaoDich.setEnabled(false);

        if (maGiaoDich == null || maGiaoDich.trim().isEmpty()) {
            Toast.makeText(this,
                    "Không tìm thấy mã giao dịch",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (maGiaoDich != null && !maGiaoDich.trim().isEmpty()) {
            taiChiTietGiaoDich();
        }
    }

    private void taiChiTietGiaoDich() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = nguoiDung.getUid();

        // 1) Đọc từ SQLite
        Transaction giaoDich =
                transactionRepository.getTransaction(uid, maGiaoDich);

        if (giaoDich != null) {
            hienThiChiTiet(uid, giaoDich);
            return;
        }

        // 2) Chưa có trên máy (ví dụ giao dịch cũ chưa kéo về): đồng bộ với Firestore rồi đọc lại
        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            Transaction daKeo =
                    transactionRepository.getTransaction(uid, maGiaoDich);

            if (daKeo != null) {
                hienThiChiTiet(uid, daKeo);
            } else if (thanhCong) {
                Toast.makeText(this,
                        "Giao dịch không tồn tại",
                        Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this,
                        "Không thể tải giao dịch: " + loi,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void hienThiChiTiet(String uid, Transaction giaoDich) {

        String loai = giaoDich.getLoaiGiaoDich();
        String danhMuc = giaoDich.getDanhMuc();
        String ghiChu = giaoDich.getGhiChu();

        String maViNguon = giaoDich.getMaViNguon();
        String maViDich = giaoDich.getMaViDich();

        long soTien = giaoDich.getSoTien();
        Date ngayGiaoDich = giaoDich.getNgayGiaoDich();

        if (loai == null) {
            loai = "Không xác định";
        }

        if (danhMuc == null || danhMuc.isEmpty()) {
            danhMuc = "Không có danh mục";
        }

        if (ghiChu == null || ghiChu.trim().isEmpty()) {
            ghiChu = "Không có";
        }

        tvLoaiGiaoDichChiTiet.setText(loai.toUpperCase(
                new Locale("vi", "VN")
        ));

        tvLoaiChiTiet.setText("Loại giao dịch: " + loai);

        tvDanhMucChiTietGiaoDich.setText(danhMuc);

        if ("Thu nhập".equals(loai)) {

            tvSoTienChiTietGiaoDich.setText(
                    "+" + dinhDangTien.format(soTien) + " ₫"
            );

            tvSoTienChiTietGiaoDich.setTextColor(
                    Color.parseColor("#059669")
            );

        } else if ("Chi tiêu".equals(loai)) {

            tvSoTienChiTietGiaoDich.setText(
                    "-" + dinhDangTien.format(soTien) + " ₫"
            );

            tvSoTienChiTietGiaoDich.setTextColor(
                    Color.parseColor("#DC2626")
            );

        } else {

            tvSoTienChiTietGiaoDich.setText(
                    dinhDangTien.format(soTien) + " ₫"
            );

            tvSoTienChiTietGiaoDich.setTextColor(
                    Color.parseColor("#0891B2")
            );
        }

        boolean laChuyenTien = "Chuyển tiền".equals(loai);

        hienThiTenVi(uid, maViNguon, tvViNguonChiTiet,
                laChuyenTien ? "Ví chuyển" : "Ví sử dụng");

        if (laChuyenTien) {
            tvViDichChiTiet.setVisibility(View.VISIBLE);
            hienThiTenVi(uid, maViDich, tvViDichChiTiet, "Ví nhận");
        } else {
            tvViDichChiTiet.setVisibility(View.GONE);
        }

        if (ngayGiaoDich != null) {
            tvNgayChiTietGiaoDich.setText(
                    "Ngày giao dịch: " + dinhDangNgay.format(ngayGiaoDich)
            );
        } else {
            tvNgayChiTietGiaoDich.setText("Ngày giao dịch: Không có");
        }

        tvGhiChuChiTietGiaoDich.setText("Ghi chú: " + ghiChu);

        boolean coTheThaoTac =
                "Thu nhập".equals(loai)
                        || "Chi tiêu".equals(loai)
                        || "Chuyển tiền".equals(loai);

        btnXoaGiaoDich.setEnabled(coTheThaoTac);
        btnSuaGiaoDich.setEnabled(coTheThaoTac);
    }

    private void hienThiTenVi(String uid, String maVi, TextView textView, String nhan) {

        if (maVi == null || maVi.isEmpty()) {
            textView.setText(nhan + ": Không xác định");
            return;
        }

        Wallet vi = walletRepository.getWallet(uid, maVi);

        textView.setText(nhan + ": "
                + (vi == null ? "Ví không còn tồn tại" : vi.getTenVi()));
    }

    private void xacNhanXoaGiaoDich() {

        new AlertDialog.Builder(this)
                .setTitle("Xóa giao dịch")
                .setMessage(
                        "Bạn có chắc chắn muốn xóa giao dịch này?\n\n"
                                + "Phinova sẽ hoàn tác số dư của các ví liên quan "
                                + "và lưu lịch sử xóa.\n\n"
                                + "Thao tác này không thể hoàn tác trực tiếp."
                )
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa giao dịch",
                        (dialog, which) -> thucHienXoaGiaoDich())
                .show();
    }

    private void thucHienXoaGiaoDich() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnXoaGiaoDich.setEnabled(false);
        btnSuaGiaoDich.setEnabled(false);

        transactionRepository.deleteTransaction(nguoiDung.getUid(), maGiaoDich,
                (thanhCong, loi) -> {

                    if (thanhCong) {
                        Toast.makeText(this,
                                "Xóa giao dịch thành công!",
                                Toast.LENGTH_SHORT).show();

                        finish();

                    } else {
                        thongBaoXoa("Không thể xóa giao dịch: " + loi);

                        taiChiTietGiaoDich();
                    }
                });
    }

    private void thongBaoXoa(String noiDung) {
        Toast.makeText(this, noiDung, Toast.LENGTH_LONG).show();
    }

}