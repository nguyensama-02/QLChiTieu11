package com.example.qlchitieu.transaction;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.model.Wallet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddTransactionActivity extends AppCompatActivity {

    MaterialButtonToggleGroup toggleLoaiGiaoDich;

    LinearLayout layoutViDich, layoutDanhMuc;

    TextView tvQuayLaiGiaoDich, tvNhanViNguon;
    TextView tvNgayGiaoDich;

    Spinner spViNguon, spViDich, spDanhMuc;

    EditText edtSoTienGiaoDich, edtGhiChu;
    MaterialButton btnLuuGiaoDich;

    ArrayList<Wallet> danhSachVi;
    ArrayList<String> danhSachTenVi;

    ArrayAdapter<String> adapterVi;

    Calendar ngayDuocChon;

    FirebaseAuth firebaseAuth;
    WalletRepository walletRepository;
    TransactionRepository transactionRepository;

    boolean dangTaiVi = false;
    boolean dangLuuGiaoDich = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        spViNguon = findViewById(R.id.spViNguon);
        spViDich = findViewById(R.id.spViDich);
        spDanhMuc = findViewById(R.id.spDanhMuc);

        tvNgayGiaoDich = findViewById(R.id.tvNgayGiaoDich);
        tvQuayLaiGiaoDich = findViewById(R.id.tvQuayLaiGiaoDich);
        tvNhanViNguon = findViewById(R.id.tvNhanViNguon);

        edtSoTienGiaoDich = findViewById(R.id.edtSoTienGiaoDich);
        edtGhiChu = findViewById(R.id.edtGhiChu);

        btnLuuGiaoDich = findViewById(R.id.btnLuuGiaoDich);
        toggleLoaiGiaoDich = findViewById(R.id.toggleLoaiGiaoDich);

        layoutViDich = findViewById(R.id.layoutViDich);
        layoutDanhMuc = findViewById(R.id.layoutDanhMuc);

        firebaseAuth = FirebaseAuth.getInstance();
        walletRepository = new WalletRepository(this);
        transactionRepository = new TransactionRepository(this);

        danhSachVi = new ArrayList<>();
        danhSachTenVi = new ArrayList<>();

        adapterVi = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhSachTenVi
        );

        adapterVi.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spViNguon.setAdapter(adapterVi);
        spViDich.setAdapter(adapterVi);

        ngayDuocChon = Calendar.getInstance();

        hienThiNgay();
        taiDanhMuc("Chi tiêu");

        tvQuayLaiGiaoDich.setOnClickListener(v -> finish());

        tvNgayGiaoDich.setOnClickListener(v -> chonNgay());

        btnLuuGiaoDich.setOnClickListener(v -> luuGiaoDich());

        toggleLoaiGiaoDich.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {

                    if (!isChecked) {
                        return;
                    }

                    if (checkedId == R.id.btnChuyenTien) {

                        layoutViDich.setVisibility(View.VISIBLE);
                        layoutDanhMuc.setVisibility(View.GONE);

                        tvNhanViNguon.setText("Ví chuyển tiền");

                    } else {

                        layoutViDich.setVisibility(View.GONE);
                        layoutDanhMuc.setVisibility(View.VISIBLE);

                        tvNhanViNguon.setText("Ví sử dụng");

                        if (checkedId == R.id.btnThuNhap) {
                            taiDanhMuc("Thu nhập");
                        } else {
                            taiDanhMuc("Chi tiêu");
                        }
                    }

                    capNhatNutLuu();
                }
        );

        btnLuuGiaoDich.setEnabled(false);
        capNhatNutLuu();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!dangLuuGiaoDich) {
            taiDanhSachVi();
        }
    }

    private void capNhatNutLuu() {

        if (btnLuuGiaoDich == null) {
            return;
        }

        boolean coVi = danhSachVi != null
                && !danhSachVi.isEmpty();

        btnLuuGiaoDich.setEnabled(
                coVi && !dangTaiVi && !dangLuuGiaoDich
        );
    }

    private void taiDanhSachVi() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = nguoiDung.getUid();

        // 1) Hiện ngay từ SQLite. SQLite là nguồn dữ liệu chính nên cho lưu luôn,
        // không cần chờ mạng.
        dangTaiVi = false;
        napDanhSachViTuSQLite(uid);
        capNhatNutLuu();

        // 2) Đồng bộ với Firestore ở nền (ví mới từ thiết bị khác...), xong thì nạp lại danh sách ví.
        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            napDanhSachViTuSQLite(uid);
            capNhatNutLuu();

            if (danhSachVi.isEmpty()) {

                Toast.makeText(this,
                        "Không có ví đang sử dụng. "
                                + "Hãy tạo hoặc khôi phục ví trước!",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Nạp các ví đang sử dụng từ SQLite vào hai ô chọn ví, giữ lại ví người dùng đang chọn. */
    private void napDanhSachViTuSQLite(String uid) {

        String maViNguonDangChon = layMaViDangChon(spViNguon);
        String maViDichDangChon = layMaViDangChon(spViDich);

        danhSachVi.clear();
        danhSachTenVi.clear();

        for (Wallet vi : walletRepository.getWallets(uid)) {

            if (!"HOAT_DONG".equals(vi.getTrangThai())) {
                continue;
            }

            danhSachVi.add(vi);
            danhSachTenVi.add(vi.getTenVi());
        }

        adapterVi.notifyDataSetChanged();

        chonLaiVi(spViNguon, maViNguonDangChon);
        chonLaiVi(spViDich, maViDichDangChon);
    }

    private String layMaViDangChon(Spinner spinner) {
        int viTri = spinner.getSelectedItemPosition();

        if (viTri < 0 || viTri >= danhSachVi.size()) {
            return null;
        }

        return danhSachVi.get(viTri).getMaVi();
    }

    private void chonLaiVi(Spinner spinner, String maVi) {
        if (maVi == null) {
            return;
        }

        for (int i = 0; i < danhSachVi.size(); i++) {
            if (maVi.equals(danhSachVi.get(i).getMaVi())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void taiDanhMuc(String loaiGiaoDich) {

        String[] danhMuc;

        if (loaiGiaoDich.equals("Thu nhập")) {

            danhMuc = new String[]{
                    "Tiền lương",
                    "Tiền thưởng",
                    "Kinh doanh",
                    "Quà tặng",
                    "Thu nhập khác"
            };

        } else {

            danhMuc = new String[]{
                    "Ăn uống",
                    "Mua sắm",
                    "Đi lại",
                    "Hóa đơn",
                    "Giải trí",
                    "Học tập",
                    "Sức khỏe",
                    "Chi tiêu khác"
            };
        }

        ArrayAdapter<String> adapterDanhMuc = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhMuc
        );

        adapterDanhMuc.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spDanhMuc.setAdapter(adapterDanhMuc);
    }

    private void hienThiNgay() {

        int ngay = ngayDuocChon.get(Calendar.DAY_OF_MONTH);
        int thang = ngayDuocChon.get(Calendar.MONTH) + 1;
        int nam = ngayDuocChon.get(Calendar.YEAR);

        String ngayHienThi = String.format(
                Locale.forLanguageTag("vi-VN"),
                "%02d/%02d/%04d",
                ngay, thang, nam
        );

        tvNgayGiaoDich.setText(ngayHienThi);
    }

    private void chonNgay() {

        int ngay = ngayDuocChon.get(Calendar.DAY_OF_MONTH);
        int thang = ngayDuocChon.get(Calendar.MONTH);
        int nam = ngayDuocChon.get(Calendar.YEAR);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, namChon, thangChon, ngayChon) -> {

                    ngayDuocChon.set(
                            namChon,
                            thangChon,
                            ngayChon
                    );

                    hienThiNgay();
                },
                nam,
                thang,
                ngay
        );

        datePickerDialog.show();
    }

    private void luuGiaoDich() {

        if (dangTaiVi || dangLuuGiaoDich) {
            return;
        }

        String soTienNhap = edtSoTienGiaoDich
                .getText().toString().trim();

        String ghiChu = edtGhiChu
                .getText().toString().trim();

        if (soTienNhap.isEmpty()) {
            edtSoTienGiaoDich.setError("Vui lòng nhập số tiền");
            edtSoTienGiaoDich.requestFocus();
            return;
        }

        long soTien;

        try {
            soTien = Long.parseLong(soTienNhap);
        } catch (NumberFormatException e) {
            edtSoTienGiaoDich.setError("Số tiền không hợp lệ");
            return;
        }

        if (soTien <= 0) {
            edtSoTienGiaoDich.setError("Số tiền phải lớn hơn 0");
            return;
        }

        if (danhSachVi.isEmpty()) {
            Toast.makeText(this,
                    "Không có ví đang sử dụng",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int viTriNguon = spViNguon.getSelectedItemPosition();

        if (viTriNguon < 0 || viTriNguon >= danhSachVi.size()) {
            Toast.makeText(this,
                    "Vui lòng chọn ví",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Wallet viNguon = danhSachVi.get(viTriNguon);

        int nutDuocChon =
                toggleLoaiGiaoDich.getCheckedButtonId();

        String loaiGiaoDich;

        if (nutDuocChon == R.id.btnThuNhap) {
            loaiGiaoDich = "Thu nhập";
        } else if (nutDuocChon == R.id.btnChuyenTien) {
            loaiGiaoDich = "Chuyển tiền";
        } else {
            loaiGiaoDich = "Chi tiêu";
        }

        Wallet viDich = null;
        String danhMuc = "";

        if (loaiGiaoDich.equals("Chuyển tiền")) {

            if (danhSachVi.size() < 2) {
                Toast.makeText(this,
                        "Cần ít nhất 2 ví đang sử dụng để chuyển tiền",
                        Toast.LENGTH_LONG).show();
                return;
            }

            int viTriDich = spViDich.getSelectedItemPosition();

            if (viTriDich < 0
                    || viTriDich >= danhSachVi.size()) {

                Toast.makeText(this,
                        "Vui lòng chọn ví nhận",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            viDich = danhSachVi.get(viTriDich);

            if (viNguon.getMaVi().equals(viDich.getMaVi())) {
                Toast.makeText(this,
                        "Ví chuyển và ví nhận phải khác nhau",
                        Toast.LENGTH_SHORT).show();
                return;
            }

        } else {

            if (spDanhMuc.getSelectedItem() == null) {
                Toast.makeText(this,
                        "Vui lòng chọn danh mục",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            danhMuc = spDanhMuc.getSelectedItem().toString();
        }

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        dangLuuGiaoDich = true;
        capNhatNutLuu();

        transactionRepository.addTransaction(
                nguoiDung.getUid(),
                loaiGiaoDich,
                danhMuc,
                ghiChu,
                viNguon.getMaVi(),
                viDich == null ? null : viDich.getMaVi(),
                soTien,
                new Date(ngayDuocChon.getTimeInMillis()),
                (thanhCong, loi) -> {

                    dangLuuGiaoDich = false;

                    if (thanhCong) {
                        Toast.makeText(this,
                                "Lưu giao dịch thành công!",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        capNhatNutLuu();

                        Toast.makeText(this,
                                "Không thể lưu giao dịch: " + loi,
                                Toast.LENGTH_LONG).show();

                        taiDanhSachVi();
                    }
                });
    }
}