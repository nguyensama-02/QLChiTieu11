package com.example.qlchitieu.transaction;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.R;
import com.example.qlchitieu.adapter.TransactionAdapter;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Transaction;
import com.example.qlchitieu.util.MonthRange;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionHistoryActivity extends AppCompatActivity {

    /** Bộ lọc nhận từ màn Thống kê (đều tùy chọn). */
    public static final String EXTRA_DANH_MUC = "extra_danh_muc";
    public static final String EXTRA_THANG = "extra_thang"; // 1..12
    public static final String EXTRA_NAM = "extra_nam";
    public static final String EXTRA_LOAI = "extra_loai";   // vd: "Chi tiêu"
    public static final String EXTRA_MA_VI = "extra_ma_vi";
    public static final String EXTRA_TEN_VI = "extra_ten_vi";

    // Lọc theo khoảng ngày bất kỳ (vd: 30 ngày qua). Nếu có thì dùng thay cho tháng/năm.
    public static final String EXTRA_TU_NGAY = "extra_tu_ngay";     // mili-giây
    public static final String EXTRA_DEN_NGAY = "extra_den_ngay";   // mili-giây, không bao gồm
    public static final String EXTRA_NHAN_KY = "extra_nhan_ky";     // vd: "30 ngày qua"

    /** Tên danh mục mà SQL gom cho giao dịch không có danh mục. */
    private static final String DANH_MUC_MAC_DINH = "Chi tiêu khác";
    private static final String DANH_MUC_THU_MAC_DINH = "Thu nhập khác";

    // null / -1 nghĩa là không lọc
    private String locDanhMuc = null;
    private String locMaVi = null;
    private int locThang = -1;
    private int locNam = -1;
    private long locTuNgay = 0;
    private long locDenNgay = Long.MAX_VALUE;
    private boolean locTheoKhoang = false;
    private String nhanKy = null;

    EditText edtTimGiaoDich;
    TextView tvQuayLaiLichSu, tvSoLuongGiaoDich, tvPhuDeLichSu;
    MaterialButtonToggleGroup toggleLocGiaoDich;

    RecyclerView rvLichSuGiaoDich;
    LinearLayout layoutLichSuTrong;

    ArrayList<Transaction> tatCaGiaoDich;
    ArrayList<Transaction> danhSachHienThi;

    TransactionAdapter transactionAdapter;

    FirebaseAuth firebaseAuth;
    TransactionRepository transactionRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_history);

        edtTimGiaoDich = findViewById(R.id.edtTimGiaoDich);
        tvQuayLaiLichSu = findViewById(R.id.tvQuayLaiLichSu);
        tvSoLuongGiaoDich = findViewById(R.id.tvSoLuongGiaoDich);
        tvPhuDeLichSu = findViewById(R.id.tvPhuDeLichSu);

        toggleLocGiaoDich = findViewById(R.id.toggleLocGiaoDich);

        rvLichSuGiaoDich = findViewById(R.id.rvLichSuGiaoDich);
        layoutLichSuTrong = findViewById(R.id.layoutLichSuTrong);

        firebaseAuth = FirebaseAuth.getInstance();
        transactionRepository = new TransactionRepository(this);

        tatCaGiaoDich = new ArrayList<>();
        danhSachHienThi = new ArrayList<>();

        transactionAdapter = new TransactionAdapter(danhSachHienThi);

        rvLichSuGiaoDich.setLayoutManager(
                new LinearLayoutManager(this)
        );

        rvLichSuGiaoDich.setAdapter(transactionAdapter);

        tvQuayLaiLichSu.setOnClickListener(v -> finish());

        docBoLocTuIntent();

        toggleLocGiaoDich.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (isChecked) {
                        locGiaoDich();
                    }
                }
        );

        edtTimGiaoDich.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                locGiaoDich();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

    }

    private void docBoLocTuIntent() {

        locDanhMuc = getIntent().getStringExtra(EXTRA_DANH_MUC);
        locMaVi = getIntent().getStringExtra(EXTRA_MA_VI);
        String tenVi = getIntent().getStringExtra(EXTRA_TEN_VI);
        locThang = getIntent().getIntExtra(EXTRA_THANG, -1);
        locNam = getIntent().getIntExtra(EXTRA_NAM, -1);

        String loai = getIntent().getStringExtra(EXTRA_LOAI);

        long tuNgayKhoang = getIntent().getLongExtra(EXTRA_TU_NGAY, -1);
        long denNgayKhoang = getIntent().getLongExtra(EXTRA_DEN_NGAY, -1);
        nhanKy = getIntent().getStringExtra(EXTRA_NHAN_KY);

        if (tuNgayKhoang >= 0 && denNgayKhoang > tuNgayKhoang) {
            locTuNgay = tuNgayKhoang;
            locDenNgay = denNgayKhoang;
            locTheoKhoang = true;
            locThang = -1;
            locNam = -1;
        } else if (locThang >= 1 && locThang <= 12 && locNam > 0) {
            locTuNgay = MonthRange.start(locThang, locNam);
            locDenNgay = MonthRange.end(locThang, locNam);
        } else {
            locThang = -1;
            locNam = -1;
        }

        // Chọn sẵn nút Thu / Chi theo loại được truyền sang.
        // Đặt trước khi gắn listener nên chưa gọi locGiaoDich lúc dữ liệu còn trống.
        if ("Chi tiêu".equals(loai)) {
            toggleLocGiaoDich.check(R.id.btnLocChi);
        } else if ("Thu nhập".equals(loai)) {
            toggleLocGiaoDich.check(R.id.btnLocThu);
        }

        List<String> mota = new ArrayList<>();

        if (locDanhMuc != null) {
            mota.add(locDanhMuc);
        }

        if (locThang != -1) {
            mota.add("Tháng " + locThang + "/" + locNam);
        }

        if (locTheoKhoang) {
            mota.add(nhanKy != null ? nhanKy : "Khoảng ngày đã chọn");
        }

        if (locMaVi != null && tenVi != null) {
            mota.add(tenVi);
        }

        if (!mota.isEmpty()) {
            tvPhuDeLichSu.setText(TextUtils.join(" · ", mota));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        taiLichSuGiaoDich();
    }

    private void taiLichSuGiaoDich() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = nguoiDung.getUid();

        // 1) Hiện ngay từ SQLite
        hienThiTuSQLite(uid);

        // 2) Đồng bộ với Firestore ở nền, xong thì hiện lại.
        // Mất mạng thì bỏ qua, vẫn dùng dữ liệu đang có trên máy.
        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            hienThiTuSQLite(uid);
        });
    }

    private void hienThiTuSQLite(String uid) {

        // SQLite đã sắp xếp sẵn: giao dịch mới nhất lên đầu
        tatCaGiaoDich.clear();
        tatCaGiaoDich.addAll(transactionRepository.getTransactions(uid));

        locGiaoDich();
    }

    private void locGiaoDich() {

        String tuKhoa = edtTimGiaoDich.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        int nutDuocChon = toggleLocGiaoDich.getCheckedButtonId();

        danhSachHienThi.clear();

        for (Transaction giaoDich : tatCaGiaoDich) {

            String loai = giaoDich.getLoaiGiaoDich();
            String danhMuc = giaoDich.getDanhMuc();
            String ghiChu = giaoDich.getGhiChu();

            if (loai == null) {
                loai = "";
            }

            if (danhMuc == null) {
                danhMuc = "";
            }

            if (ghiChu == null) {
                ghiChu = "";
            }

            boolean dungLoai = true;

            if (nutDuocChon == R.id.btnLocThu) {
                dungLoai = loai.equals("Thu nhập");
            } else if (nutDuocChon == R.id.btnLocChi) {
                dungLoai = loai.equals("Chi tiêu");
            } else if (nutDuocChon == R.id.btnLocChuyen) {
                dungLoai = loai.equals("Chuyển tiền");
            }

            boolean dungTuKhoa =
                    danhMuc.toLowerCase(Locale.ROOT).contains(tuKhoa)
                            || ghiChu.toLowerCase(Locale.ROOT).contains(tuKhoa);

            boolean dungDanhMuc = true;

            if (locDanhMuc != null) {
                if (DANH_MUC_MAC_DINH.equals(locDanhMuc)
                        || DANH_MUC_THU_MAC_DINH.equals(locDanhMuc)) {
                    // SQL gom giao dịch không có danh mục vào "Chi tiêu khác" / "Thu nhập khác"
                    dungDanhMuc = danhMuc.trim().isEmpty()
                            || danhMuc.equals(locDanhMuc);
                } else {
                    dungDanhMuc = danhMuc.equals(locDanhMuc);
                }
            }

            boolean dungVi = locMaVi == null
                    || locMaVi.equals(giaoDich.getMaViNguon());

            boolean dungThang = true;

            if (locThang != -1 || locTheoKhoang) {
                Date ngay = giaoDich.getNgayGiaoDich();
                long mili = ngay == null ? -1 : ngay.getTime();
                dungThang = mili >= locTuNgay && mili < locDenNgay;
            }

            if (dungLoai && dungTuKhoa && dungDanhMuc && dungThang && dungVi) {
                danhSachHienThi.add(giaoDich);
            }
        }

        transactionAdapter.notifyDataSetChanged();

        tvSoLuongGiaoDich.setText(
                danhSachHienThi.size() + " giao dịch"
        );

        if (danhSachHienThi.isEmpty()) {
            layoutLichSuTrong.setVisibility(View.VISIBLE);
            rvLichSuGiaoDich.setVisibility(View.GONE);
        } else {
            layoutLichSuTrong.setVisibility(View.GONE);
            rvLichSuGiaoDich.setVisibility(View.VISIBLE);
        }
    }
}