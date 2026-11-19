package com.example.qlchitieu.transaction;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.model.Transaction;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class EditTransactionActivity extends AppCompatActivity {

    TextView tvQuayLaiSuaGiaoDich;
    TextView tvThongTinGiaoDichSua, tvSoTienCuSua;
    TextView tvNhanDanhMucSua, tvNgaySuaGiaoDich;

    EditText edtSoTienSua, edtGhiChuSua;
    Spinner spDanhMucSua;
    MaterialButton btnLuuSuaGiaoDich;

    FirebaseAuth firebaseAuth;
    TransactionRepository transactionRepository;
    String uid;

    String maGiaoDich;
    String loaiGiaoDichCu;
    String maViNguonCu, maViDichCu;
    String danhMucCu, ghiChuCu;

    long soTienCu;
    Date ngayGiaoDichCu;

    Calendar ngayDuocChon = Calendar.getInstance();

    boolean daTaiGiaoDich = false;
    boolean dangLuu = false;

    ArrayList<String> danhSachDanhMuc = new ArrayList<>();

    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    SimpleDateFormat dinhDangNgay =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);

        tvQuayLaiSuaGiaoDich =
                findViewById(R.id.tvQuayLaiSuaGiaoDich);

        tvThongTinGiaoDichSua =
                findViewById(R.id.tvThongTinGiaoDichSua);

        tvSoTienCuSua = findViewById(R.id.tvSoTienCuSua);
        tvNhanDanhMucSua = findViewById(R.id.tvNhanDanhMucSua);
        tvNgaySuaGiaoDich = findViewById(R.id.tvNgaySuaGiaoDich);

        edtSoTienSua = findViewById(R.id.edtSoTienSua);
        edtGhiChuSua = findViewById(R.id.edtGhiChuSua);

        spDanhMucSua = findViewById(R.id.spDanhMucSua);

        btnLuuSuaGiaoDich = findViewById(R.id.btnLuuSuaGiaoDich);

        firebaseAuth = FirebaseAuth.getInstance();
        transactionRepository = new TransactionRepository(this);

        maGiaoDich = getIntent().getStringExtra("maGiaoDich");

        btnLuuSuaGiaoDich.setEnabled(false);

        tvQuayLaiSuaGiaoDich.setOnClickListener(v -> finish());

        tvNgaySuaGiaoDich.setOnClickListener(v -> chonNgay());

        btnLuuSuaGiaoDich.setOnClickListener(v -> luuChinhSua());

        if (maGiaoDich == null || maGiaoDich.trim().isEmpty()) {
            thongBao("Không tìm thấy mã giao dịch");
            finish();
            return;
        }

        taiThongTinGiaoDich();
    }

    private void taiThongTinGiaoDich() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            thongBao("Vui lòng đăng nhập lại");
            finish();
            return;
        }

        uid = nguoiDung.getUid();

        btnLuuSuaGiaoDich.setEnabled(false);

        // 1) Đọc từ SQLite
        Transaction giaoDich =
                transactionRepository.getTransaction(uid, maGiaoDich);

        if (giaoDich != null) {
            hienThiThongTin(giaoDich);
            return;
        }

        // 2) Chưa có trên máy: kéo từ Firestore rồi đọc lại
        transactionRepository.pullFromCloud(uid, (thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            Transaction daKeo =
                    transactionRepository.getTransaction(uid, maGiaoDich);

            if (daKeo != null) {
                hienThiThongTin(daKeo);
            } else if (thanhCong) {
                thongBao("Giao dịch không tồn tại");
                finish();
            } else {
                thongBao("Không thể tải giao dịch: " + loi);
            }
        });
    }

    private void hienThiThongTin(Transaction giaoDich) {

        String loai = giaoDich.getLoaiGiaoDich();
        long soTien = giaoDich.getSoTien();

        String maNguon = giaoDich.getMaViNguon();
        String maDich = giaoDich.getMaViDich();

        if (!"Thu nhập".equals(loai)
                && !"Chi tiêu".equals(loai)
                && !"Chuyển tiền".equals(loai)) {

            thongBao("Loại giao dịch không hỗ trợ chỉnh sửa");
            finish();
            return;
        }

        if (soTien <= 0
                || maNguon == null
                || maNguon.isEmpty()
                || ("Chuyển tiền".equals(loai)
                && (maDich == null
                || maDich.isEmpty()
                || maNguon.equals(maDich)))) {

            thongBao("Dữ liệu giao dịch không hợp lệ");
            finish();
            return;
        }

        loaiGiaoDichCu = loai;
        soTienCu = soTien;

        maViNguonCu = maNguon;
        maViDichCu = maDich;

        danhMucCu = giaoDich.getDanhMuc();
        ghiChuCu = giaoDich.getGhiChu();

        if (danhMucCu == null) {
            danhMucCu = "";
        }

        if (ghiChuCu == null) {
            ghiChuCu = "";
        }

        ngayGiaoDichCu = giaoDich.getNgayGiaoDich();

        if (ngayGiaoDichCu == null) {
            thongBao("Ngày giao dịch không hợp lệ");
            finish();
            return;
        }

        ngayDuocChon.setTime(ngayGiaoDichCu);

        edtSoTienSua.setText(String.valueOf(soTienCu));
        edtGhiChuSua.setText(ghiChuCu);

        tvSoTienCuSua.setText(
                dinhDangTien.format(soTienCu) + " ₫"
        );

        tvThongTinGiaoDichSua.setText(loaiGiaoDichCu);

        tvNgaySuaGiaoDich.setText(
                dinhDangNgay.format(ngayGiaoDichCu)
        );

        hienThiDanhMuc();

        daTaiGiaoDich = true;
        btnLuuSuaGiaoDich.setEnabled(true);
    }

    private void hienThiDanhMuc() {

        danhSachDanhMuc.clear();

        if ("Chuyển tiền".equals(loaiGiaoDichCu)) {

            tvNhanDanhMucSua.setVisibility(View.GONE);
            spDanhMucSua.setVisibility(View.GONE);
            return;
        }

        tvNhanDanhMucSua.setVisibility(View.VISIBLE);
        spDanhMucSua.setVisibility(View.VISIBLE);

        String[] danhMuc;

        if ("Thu nhập".equals(loaiGiaoDichCu)) {
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

        for (String muc : danhMuc) {
            danhSachDanhMuc.add(muc);
        }

        if (!danhMucCu.isEmpty()
                && !danhSachDanhMuc.contains(danhMucCu)) {
            danhSachDanhMuc.add(danhMucCu);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhSachDanhMuc
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spDanhMucSua.setAdapter(adapter);

        int viTri = danhSachDanhMuc.indexOf(danhMucCu);

        if (viTri >= 0) {
            spDanhMucSua.setSelection(viTri);
        }
    }

    private void chonNgay() {

        if (!daTaiGiaoDich || dangLuu) {
            return;
        }

        int ngay = ngayDuocChon.get(Calendar.DAY_OF_MONTH);
        int thang = ngayDuocChon.get(Calendar.MONTH);
        int nam = ngayDuocChon.get(Calendar.YEAR);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, namMoi, thangMoi, ngayMoi) -> {

                    ngayDuocChon.set(
                            namMoi,
                            thangMoi,
                            ngayMoi
                    );

                    tvNgaySuaGiaoDich.setText(
                            dinhDangNgay.format(ngayDuocChon.getTime())
                    );
                },
                nam,
                thang,
                ngay
        );

        dialog.show();
    }

    private void luuChinhSua() {

        if (!daTaiGiaoDich || dangLuu) {
            return;
        }

        String soTienNhap =
                edtSoTienSua.getText().toString().trim();

        String ghiChuMoi =
                edtGhiChuSua.getText().toString().trim();

        if (soTienNhap.isEmpty()) {
            edtSoTienSua.setError("Vui lòng nhập số tiền");
            return;
        }

        long soTienMoi;

        try {
            soTienMoi = Long.parseLong(soTienNhap);
        } catch (NumberFormatException e) {
            edtSoTienSua.setError("Số tiền không hợp lệ");
            return;
        }

        if (soTienMoi <= 0) {
            edtSoTienSua.setError("Số tiền phải lớn hơn 0");
            return;
        }

        String danhMucMoi = "";

        if (!"Chuyển tiền".equals(loaiGiaoDichCu)) {

            if (spDanhMucSua.getSelectedItem() == null) {
                thongBao("Vui lòng chọn danh mục");
                return;
            }

            danhMucMoi = spDanhMucSua.getSelectedItem().toString();
        }

        Date ngayMoi = ngayDuocChon.getTime();

        boolean khongThayDoi =
                soTienMoi == soTienCu
                        && Objects.equals(danhMucMoi, danhMucCu)
                        && Objects.equals(ghiChuMoi, ghiChuCu)
                        && ngayMoi.equals(ngayGiaoDichCu);

        if (khongThayDoi) {
            thongBao("Bạn chưa thay đổi thông tin nào");
            return;
        }

        if (uid == null) {
            thongBao("Vui lòng đăng nhập lại");
            return;
        }

        // Giá trị cũ mà màn hình đang dựa vào, dùng để phát hiện
        // giao dịch đã bị thay đổi ở nơi khác trước khi lưu
        Transaction cu = new Transaction(
                maGiaoDich,
                loaiGiaoDichCu,
                danhMucCu,
                ghiChuCu,
                maViNguonCu,
                maViDichCu,
                soTienCu,
                ngayGiaoDichCu
        );

        dangLuu = true;
        btnLuuSuaGiaoDich.setEnabled(false);

        transactionRepository.editTransaction(
                uid, cu, soTienMoi, danhMucMoi, ghiChuMoi, ngayMoi,
                (thanhCong, loi) -> {

                    dangLuu = false;

                    if (thanhCong) {
                        Toast.makeText(this,
                                "Chỉnh sửa giao dịch thành công!",
                                Toast.LENGTH_SHORT).show();

                        finish();

                    } else {
                        btnLuuSuaGiaoDich.setEnabled(true);

                        thongBao("Không thể chỉnh sửa: " + loi);
                    }
                });
    }

    private void thongBao(String noiDung) {
        Toast.makeText(this, noiDung, Toast.LENGTH_LONG).show();
    }
}