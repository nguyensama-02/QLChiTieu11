package com.example.qlchitieu.statistics;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.auth.LoginActivity;
import com.example.qlchitieu.data.repository.BudgetRepository;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Budget;
import com.example.qlchitieu.model.Transaction;
import com.example.qlchitieu.model.Wallet;
import com.example.qlchitieu.transaction.TransactionDetailActivity;
import com.example.qlchitieu.transaction.TransactionHistoryActivity;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {

    private static final int SO_THANG_XU_HUONG = 6;

    // Kỳ thống kê đang xem
    private static final int KY_THANG = 0;
    private static final int KY_30_NGAY = 1;

    private static final int SO_NGAY_KY_NGAN = 30;
    private static final int SO_NGAY_MOT_TUAN = 7;

    private static final String DANH_MUC_MAC_DINH = "Chi tiêu khác";
    private static final String DANH_MUC_THU_MAC_DINH = "Thu nhập khác";

    private static final String LOAI_CHI = "Chi tiêu";
    private static final String LOAI_THU = "Thu nhập";

    private static final int XANH = Color.parseColor("#059669");
    private static final int DO = Color.parseColor("#DC2626");
    private static final int XAM = Color.parseColor("#64748B");
    private static final int CAM = Color.parseColor("#D97706");

    // Số hàng gợi ý ngân sách tối đa hiện trong thẻ
    private static final int SO_GOI_Y_TOI_DA = 5;

    // Màu theo thứ tự danh mục từ chi nhiều đến ít
    private static final int[] BANG_MAU = {
            Color.parseColor("#0891B2"),
            Color.parseColor("#3B82F6"),
            Color.parseColor("#D97706"),
            Color.parseColor("#DC2626"),
            Color.parseColor("#8B5CF6"),
            Color.parseColor("#10B981"),
            Color.parseColor("#EC4899"),
            Color.parseColor("#14B8A6")
    };
    private static final int MAU_KHAC = Color.parseColor("#94A3B8");

    TextView tvQuayLaiThongKe, tvChonThang, tvThangTruoc, tvThangSau;
    TextView tvTongThuThongKe, tvTongChiThongKe;
    TextView tvChenhLechThongKe, tvTyLeTietKiem;
    TextView tvLoaiDanhMuc, tvTieuDeDanhMuc;
    HorizontalScrollView scrollChipVi;

    // Thẻ "Nhịp chi tiêu" và "Khoản chi lớn nhất"
    TextView tvTrungBinhNgay, tvNgayChiMax, tvDuDoan, tvGhiChuDuDoan;
    LinearLayout layoutDuDoan, cardKhoanChiLon, layoutKhoanChiLon;
    LineChart lineChartNgay;
    LinearLayout layoutChipVi;
    TextView tvSoSanhThu, tvSoSanhChi;
    TextView tvDanhMucTrong;

    LinearLayout layoutThongKeDanhMuc;
    LinearLayout cardGoiYNganSach, layoutGoiYNganSach;

    LinearLayout layoutChonKy;
    TextView tvNhanConLai, tvTieuDeXuHuong, tvPhuDeXuHuong, tvPhuDeNhipChiTieu;
    int kyDangChon = KY_THANG;

    BarChart barChartXuHuong;
    PieChart pieChartDanhMuc;

    FirebaseAuth firebaseAuth;
    TransactionRepository transactionRepository;
    WalletRepository walletRepository;
    BudgetRepository budgetRepository;

    int lanDongBo = 0;

    Calendar thangDuocChon;

    // Bộ lọc đang áp dụng cho cả màn hình
    String maViDangChon = null;               // null = tất cả ví
    String tenViDangChon = "Tất cả ví";
    boolean dangXemChi = true;                // công tắc Chi / Thu ở thẻ danh mục
    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        tvQuayLaiThongKe = findViewById(R.id.tvQuayLaiThongKe);
        tvChonThang = findViewById(R.id.tvChonThang);
        tvThangTruoc = findViewById(R.id.tvThangTruoc);
        tvThangSau = findViewById(R.id.tvThangSau);

        tvTongThuThongKe = findViewById(R.id.tvTongThuThongKe);
        tvTongChiThongKe = findViewById(R.id.tvTongChiThongKe);
        tvChenhLechThongKe = findViewById(R.id.tvChenhLechThongKe);
        tvTyLeTietKiem = findViewById(R.id.tvTyLeTietKiem);

        tvTrungBinhNgay = findViewById(R.id.tvTrungBinhNgay);
        tvNgayChiMax = findViewById(R.id.tvNgayChiMax);
        tvDuDoan = findViewById(R.id.tvDuDoan);
        tvGhiChuDuDoan = findViewById(R.id.tvGhiChuDuDoan);
        layoutDuDoan = findViewById(R.id.layoutDuDoan);
        lineChartNgay = findViewById(R.id.lineChartNgay);
        cardKhoanChiLon = findViewById(R.id.cardKhoanChiLon);
        layoutKhoanChiLon = findViewById(R.id.layoutKhoanChiLon);

        scrollChipVi = findViewById(R.id.scrollChipVi);
        layoutChipVi = findViewById(R.id.layoutChipVi);
        tvLoaiDanhMuc = findViewById(R.id.tvLoaiDanhMuc);
        tvTieuDeDanhMuc = findViewById(R.id.tvTieuDeDanhMuc);

        tvSoSanhThu = findViewById(R.id.tvSoSanhThu);
        tvSoSanhChi = findViewById(R.id.tvSoSanhChi);

        barChartXuHuong = findViewById(R.id.barChartXuHuong);
        pieChartDanhMuc = findViewById(R.id.pieChartDanhMuc);

        tvDanhMucTrong = findViewById(R.id.tvDanhMucTrong);
        layoutThongKeDanhMuc = findViewById(R.id.layoutThongKeDanhMuc);
        cardGoiYNganSach = findViewById(R.id.cardGoiYNganSach);

        layoutChonKy = findViewById(R.id.layoutChonKy);
        tvNhanConLai = findViewById(R.id.tvNhanConLai);
        tvTieuDeXuHuong = findViewById(R.id.tvTieuDeXuHuong);
        tvPhuDeXuHuong = findViewById(R.id.tvPhuDeXuHuong);
        tvPhuDeNhipChiTieu = findViewById(R.id.tvPhuDeNhipChiTieu);
        layoutGoiYNganSach = findViewById(R.id.layoutGoiYNganSach);

        firebaseAuth = FirebaseAuth.getInstance();
        transactionRepository = new TransactionRepository(this);
        walletRepository = new WalletRepository(this);
        budgetRepository = new BudgetRepository(this);

        thangDuocChon = Calendar.getInstance();
        thangDuocChon.set(Calendar.DAY_OF_MONTH, 1);

        tvQuayLaiThongKe.setOnClickListener(v -> finish());
        tvChonThang.setOnClickListener(v -> {
            if (kyDangChon == KY_THANG) {
                chonThang();
            }
        });
        tvThangTruoc.setOnClickListener(v -> chuyenThang(-1));
        tvThangSau.setOnClickListener(v -> chuyenThang(1));
        tvLoaiDanhMuc.setOnClickListener(v -> chonLoai());

        khoiTaoBieuDoCot();
        khoiTaoBieuDoDuong();
        khoiTaoBieuDoTron();

        dungChipKy();
        hienThiKy();
        capNhatNhanLoc();
        capNhatNhanKy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tải ở onResume để quay về từ Lịch sử (sau khi sửa/xóa giao dịch) vẫn thấy số mới
        taiDuLieuThongKe();
    }

    // ------------------------------------------------------------------
    //  Chọn tháng
    // ------------------------------------------------------------------

    /** Cập nhật khối chọn thời gian ở đầu màn hình theo kỳ đang xem. */
    private void hienThiKy() {

        boolean laThang = (kyDangChon == KY_THANG);

        tvThangTruoc.setVisibility(laThang ? View.VISIBLE : View.GONE);
        tvThangSau.setVisibility(laThang ? View.VISIBLE : View.GONE);
        tvChonThang.setClickable(laThang);
        tvChonThang.setFocusable(laThang);

        if (laThang) {

            int thang = thangDuocChon.get(Calendar.MONTH) + 1;
            int nam = thangDuocChon.get(Calendar.YEAR);

            tvChonThang.setText("Tháng " + thang + "/" + nam + "  ▾");

            // Không cho đi tiếp quá tháng hiện tại (chưa có dữ liệu)
            tvThangSau.setAlpha(laThangMoiNhat() ? 0.3f : 1f);

        } else {

            Ky ky = tinhKy();
            SimpleDateFormat dd = new SimpleDateFormat("dd/MM", Locale.getDefault());

            tvChonThang.setText("30 ngày qua · " + dd.format(new Date(ky.tuNgay))
                    + " – " + dd.format(new Date(ky.denNgay - 1)));
        }
    }

    // ------------------------------------------------------------------
    //  Chọn kỳ: Tháng / 30 ngày
    // ------------------------------------------------------------------

    private static class Ky {
        long tuNgay, denNgay;        // [tuNgay, denNgay)
        long tuTruoc, denTruoc;      // kỳ liền trước, cùng độ dài
        String nhanTruoc;            // chữ trong chip "so với ..."
        int soNgay;                  // số ngày của kỳ
        int ngayDaQua;               // số ngày đã trôi qua của kỳ
        boolean laThang;
        Calendar batDauThang;        // chỉ có khi là kỳ tháng
    }

    private void khongGio(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    /** Đổi kỳ đang chọn thành khoảng thời gian cụ thể. Mọi thẻ đều tính từ đây. */
    private Ky tinhKy() {

        Ky ky = new Ky();

        if (kyDangChon == KY_THANG) {

            Calendar batDau = (Calendar) thangDuocChon.clone();
            batDau.set(Calendar.DAY_OF_MONTH, 1);
            khongGio(batDau);

            Calendar ketThuc = (Calendar) batDau.clone();
            ketThuc.add(Calendar.MONTH, 1);

            Calendar truoc = (Calendar) batDau.clone();
            truoc.add(Calendar.MONTH, -1);

            ky.laThang = true;
            ky.batDauThang = batDau;
            ky.tuNgay = batDau.getTimeInMillis();
            ky.denNgay = ketThuc.getTimeInMillis();
            ky.tuTruoc = truoc.getTimeInMillis();
            ky.denTruoc = ky.tuNgay;
            ky.nhanTruoc = "T" + (truoc.get(Calendar.MONTH) + 1);
            ky.soNgay = batDau.getActualMaximum(Calendar.DAY_OF_MONTH);
            ky.ngayDaQua = demNgayDaQua(batDau, ky.soNgay);

            return ky;
        }

        // 30 ngày qua: kết thúc hết hôm nay, tính cả hôm nay
        Calendar den = Calendar.getInstance();
        khongGio(den);
        den.add(Calendar.DAY_OF_MONTH, 1);

        Calendar tu = (Calendar) den.clone();
        tu.add(Calendar.DAY_OF_MONTH, -SO_NGAY_KY_NGAN);

        Calendar tuTruoc = (Calendar) tu.clone();
        tuTruoc.add(Calendar.DAY_OF_MONTH, -SO_NGAY_KY_NGAN);

        ky.laThang = false;
        ky.tuNgay = tu.getTimeInMillis();
        ky.denNgay = den.getTimeInMillis();
        ky.tuTruoc = tuTruoc.getTimeInMillis();
        ky.denTruoc = ky.tuNgay;
        ky.nhanTruoc = SO_NGAY_KY_NGAN + " ngày trước";
        ky.soNgay = SO_NGAY_KY_NGAN;
        ky.ngayDaQua = SO_NGAY_KY_NGAN;

        return ky;
    }

    private void dungChipKy() {

        layoutChonKy.removeAllViews();

        GradientDrawable nen = new GradientDrawable();
        nen.setShape(GradientDrawable.RECTANGLE);
        nen.setCornerRadius(dp(22));
        nen.setColor(Color.parseColor("#33FFFFFF"));

        layoutChonKy.setBackground(nen);
        layoutChonKy.setPadding(dp(3), dp(3), dp(3), dp(3));

        layoutChonKy.addView(taoNutKy("Tháng", KY_THANG));
        layoutChonKy.addView(taoNutKy("30 ngày", KY_30_NGAY));
    }

    private TextView taoNutKy(String nhan, int ky) {

        boolean dangChon = (ky == kyDangChon);

        TextView nut = new TextView(this);
        nut.setText(nhan);
        nut.setTextSize(14);
        nut.setGravity(Gravity.CENTER);
        nut.setSingleLine(true);
        nut.setPadding(dp(18), 0, dp(18), 0);
        nut.setClickable(true);
        nut.setFocusable(true);

        GradientDrawable nenNut = new GradientDrawable();
        nenNut.setShape(GradientDrawable.RECTANGLE);
        nenNut.setCornerRadius(dp(19));

        if (dangChon) {
            nenNut.setColor(Color.WHITE);
            nut.setTextColor(Color.parseColor("#102A4C"));
            nut.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            nenNut.setColor(Color.TRANSPARENT);
            nut.setTextColor(Color.WHITE);
        }

        nut.setBackground(nenNut);
        nut.setOnClickListener(v -> chonKy(ky));

        nut.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));

        return nut;
    }

    private void chonKy(int ky) {

        if (ky == kyDangChon) {
            return;
        }

        kyDangChon = ky;

        dungChipKy();
        hienThiKy();
        capNhatNhanKy();

        // Cùng dữ liệu trong SQLite, chỉ đổi khoảng thời gian nên vẽ lại ngay
        veLai();
    }

    /** Đổi các tiêu đề đang ghi sẵn "tháng" cho đúng kỳ. */
    private void capNhatNhanKy() {

        boolean laThang = (kyDangChon == KY_THANG);

        tvNhanConLai.setText(laThang
                ? "Còn lại trong tháng"
                : "Còn lại trong 30 ngày");

        tvTieuDeXuHuong.setText(laThang
                ? "Xu hướng 6 tháng"
                : "Theo tuần trong 30 ngày qua");

        tvPhuDeXuHuong.setText(laThang
                ? "Thu nhập và chi tiêu, đơn vị triệu đồng"
                : "Mỗi cột 7 ngày tính lùi từ hôm nay, cột đầu có thể ngắn hơn. Đơn vị triệu đồng");

        tvPhuDeNhipChiTieu.setText(laThang
                ? "Chi tiêu theo từng ngày trong tháng"
                : "Chi tiêu theo từng ngày trong 30 ngày qua");
    }

    private boolean laThangMoiNhat() {

        Calendar bayGio = Calendar.getInstance();

        int dangXem = thangDuocChon.get(Calendar.YEAR) * 12
                + thangDuocChon.get(Calendar.MONTH);
        int hienTai = bayGio.get(Calendar.YEAR) * 12
                + bayGio.get(Calendar.MONTH);

        return dangXem >= hienTai;
    }

    /** Lùi (âm) hoặc tiến (dương) số tháng; dùng cho mũi tên và bấm cột biểu đồ. */
    private void chuyenThang(int soThang) {

        if (soThang == 0 || kyDangChon != KY_THANG) {
            return;
        }

        if (soThang > 0 && laThangMoiNhat()) {
            return;
        }

        thangDuocChon.set(Calendar.DAY_OF_MONTH, 1);
        thangDuocChon.add(Calendar.MONTH, soThang);

        hienThiKy();
        taiDuLieuThongKe();
    }

    private void chonThang() {

        View giaoDien = getLayoutInflater().inflate(
                R.layout.dialog_chon_thang,
                null
        );

        Spinner spChonThang = giaoDien.findViewById(R.id.spChonThang);
        Spinner spChonNam = giaoDien.findViewById(R.id.spChonNam);

        ArrayList<String> danhSachThang = new ArrayList<>();

        for (int i = 1; i <= 12; i++) {
            danhSachThang.add("Tháng " + i);
        }

        ArrayAdapter<String> adapterThang = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhSachThang
        );

        adapterThang.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spChonThang.setAdapter(adapterThang);

        int namHienTai = Calendar.getInstance().get(Calendar.YEAR);
        int namBatDau = Math.min(2020, thangDuocChon.get(Calendar.YEAR));
        int namKetThuc = Math.max(namHienTai + 5,
                thangDuocChon.get(Calendar.YEAR));

        ArrayList<String> danhSachNam = new ArrayList<>();

        for (int i = namBatDau; i <= namKetThuc; i++) {
            danhSachNam.add(String.valueOf(i));
        }

        ArrayAdapter<String> adapterNam = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhSachNam
        );

        adapterNam.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spChonNam.setAdapter(adapterNam);

        spChonThang.setSelection(thangDuocChon.get(Calendar.MONTH));

        int viTriNam = danhSachNam.indexOf(
                String.valueOf(thangDuocChon.get(Calendar.YEAR))
        );

        if (viTriNam >= 0) {
            spChonNam.setSelection(viTriNam);
        }

        new AlertDialog.Builder(this)
                .setTitle("Chọn thời gian thống kê")
                .setView(giaoDien)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xem thống kê", (dialog, which) -> {

                    int thang = spChonThang.getSelectedItemPosition();

                    int nam = Integer.parseInt(
                            spChonNam.getSelectedItem().toString()
                    );

                    thangDuocChon.set(Calendar.DAY_OF_MONTH, 1);
                    thangDuocChon.set(Calendar.YEAR, nam);
                    thangDuocChon.set(Calendar.MONTH, thang);

                    hienThiKy();
                    taiDuLieuThongKe();
                })
                .show();
    }

    // ------------------------------------------------------------------
    //  Bộ lọc: ví và công tắc Chi / Thu
    // ------------------------------------------------------------------

    /**
     * Dựng hàng chip ví: "Tất cả ví", từng ví đang hoạt động, và một chip "Lưu trữ"
     * gom các ví đã lưu trữ. Chỉ có một ví thì không cần lọc nên ẩn cả hàng.
     */
    private void dungChipVi(String uid) {

        List<Wallet> danhSachVi = walletRepository.getWallets(uid);

        if (danhSachVi.size() <= 1) {
            scrollChipVi.setVisibility(View.GONE);
            return;
        }

        scrollChipVi.setVisibility(View.VISIBLE);
        layoutChipVi.removeAllViews();

        layoutChipVi.addView(taoChipVi("Tất cả ví", maViDangChon == null,
                v -> chonViMoi(null, "Tất cả ví")));

        List<Wallet> luuTru = new ArrayList<>();
        boolean dangChonLuuTru = false;

        for (Wallet vi : danhSachVi) {

            boolean dangChon = vi.getMaVi().equals(maViDangChon);

            if ("LUU_TRU".equals(vi.getTrangThai())) {
                luuTru.add(vi);
                dangChonLuuTru = dangChonLuuTru || dangChon;
                continue;
            }

            layoutChipVi.addView(taoChipVi(vi.getTenVi(), dangChon,
                    v -> chonViMoi(vi.getMaVi(), vi.getTenVi())));
        }

        if (!luuTru.isEmpty()) {

            String nhan = dangChonLuuTru ? tenViDangChon : "Lưu trữ";

            layoutChipVi.addView(taoChipVi(nhan + "  ▾", dangChonLuuTru,
                    v -> chonViLuuTru(luuTru)));
        }
    }

    private TextView taoChipVi(String nhan, boolean dangChon,
                               View.OnClickListener khiBam) {

        TextView chip = new TextView(this);
        chip.setText(nhan);
        chip.setTextSize(14);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(16), 0, dp(16), 0);
        chip.setClickable(true);
        chip.setFocusable(true);

        GradientDrawable nen = new GradientDrawable();
        nen.setShape(GradientDrawable.RECTANGLE);
        nen.setCornerRadius(dp(20));

        if (dangChon) {
            nen.setColor(Color.parseColor("#102A4C"));
            chip.setTextColor(Color.WHITE);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            nen.setColor(Color.WHITE);
            nen.setStroke(dp(1), Color.parseColor("#E2E8F0"));
            chip.setTextColor(Color.parseColor("#102A4C"));
        }

        chip.setBackground(nen);
        chip.setOnClickListener(khiBam);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        params.setMarginEnd(dp(8));
        chip.setLayoutParams(params);

        return chip;
    }

    private void chonViMoi(String maVi, String tenVi) {

        maViDangChon = maVi;
        tenViDangChon = tenVi;

        veLai();
    }

    private void chonViLuuTru(List<Wallet> luuTru) {

        String[] ten = new String[luuTru.size()];
        int viTriChon = -1;

        for (int i = 0; i < luuTru.size(); i++) {
            ten[i] = luuTru.get(i).getTenVi();

            if (luuTru.get(i).getMaVi().equals(maViDangChon)) {
                viTriChon = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Ví đã lưu trữ")
                .setSingleChoiceItems(ten, viTriChon, (dialog, which) -> {

                    dialog.dismiss();
                    chonViMoi(luuTru.get(which).getMaVi(),
                            luuTru.get(which).getTenVi());
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void chonLoai() {

        PopupMenu menu = new PopupMenu(this, tvLoaiDanhMuc);

        menu.getMenu().add(LOAI_CHI);
        menu.getMenu().add(LOAI_THU);

        menu.setOnMenuItemClickListener(item -> {

            dangXemChi = LOAI_CHI.contentEquals(item.getTitle());

            capNhatNhanLoc();
            veLai();
            return true;
        });

        menu.show();
    }

    /** Cập nhật chữ trên các chip và tiêu đề theo bộ lọc đang chọn. */
    private void capNhatNhanLoc() {

        tvLoaiDanhMuc.setText((dangXemChi ? "Chi" : "Thu") + "  ▾");

        tvTieuDeDanhMuc.setText(dangXemChi
                ? "Chi tiêu theo danh mục"
                : "Thu nhập theo danh mục");

        tvDanhMucTrong.setText(dangXemChi
                ? "Chưa có dữ liệu chi tiêu"
                : "Chưa có dữ liệu thu nhập");

        pieChartDanhMuc.setNoDataText(dangXemChi
                ? "Chưa có dữ liệu chi tiêu"
                : "Chưa có dữ liệu thu nhập");
    }

    /** Vẽ lại từ SQLite ngay, không cần đồng bộ lại (đổi bộ lọc không làm đổi dữ liệu). */
    private void veLai() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung != null) {
            hienThiThongKeTuSQLite(nguoiDung.getUid());
        }
    }

    // ------------------------------------------------------------------
    //  Tải dữ liệu
    // ------------------------------------------------------------------

    private void taiDuLieuThongKe() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập lại",
                    Toast.LENGTH_SHORT).show();

            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String uid = nguoiDung.getUid();

        // Ví đang lọc đã bị xóa (ở máy khác chẳng hạn) thì quay về tất cả ví
        if (maViDangChon != null
                && walletRepository.getWallet(uid, maViDangChon) == null) {
            maViDangChon = null;
            tenViDangChon = "Tất cả ví";
            capNhatNhanLoc();
        }

        // 1) Hiện ngay từ SQLite
        hienThiThongKeTuSQLite(uid);

        // 2) Đồng bộ với Firestore ở nền, xong thì hiện lại.
        // Mất mạng thì bỏ qua, vẫn dùng dữ liệu đang có trên máy.
        int lanHienTai = ++lanDongBo;

        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed() || lanHienTai != lanDongBo) {
                return;
            }

            hienThiThongKeTuSQLite(uid);
        });
    }

    private void hienThiThongKeTuSQLite(String uid) {

        dungChipVi(uid);

        Ky ky = tinhKy();

        long tuNgay = ky.tuNgay;
        long denNgay = ky.denNgay;

        // Tổng hợp bằng SQL (SUM / GROUP BY), không tải từng giao dịch
        long[] thuChi = transactionRepository.getIncomeExpense(
                uid, tuNgay, denNgay, maViDangChon);

        // Thẻ danh mục xem chi hay thu tùy công tắc
        HashMap<String, Long> theoDanhMuc = dangXemChi
                ? transactionRepository.getExpenseByCategory(
                uid, tuNgay, denNgay, maViDangChon)
                : transactionRepository.getIncomeByCategory(
                uid, tuNgay, denNgay, maViDangChon);

        long tongThu = thuChi[0];
        long tongChi = thuChi[1];

        tvTongThuThongKe.setText(dinhDangTien.format(tongThu) + " ₫");
        tvTongChiThongKe.setText(dinhDangTien.format(tongChi) + " ₫");

        hienThiConLai(tongThu, tongChi);

        // So với kỳ liền trước cùng độ dài (tháng trước, hoặc 30 ngày trước đó)
        long[] kyTruoc = transactionRepository.getIncomeExpense(
                uid, ky.tuTruoc, ky.denTruoc, maViDangChon);

        HashMap<String, Long> danhMucKyTruoc = dangXemChi
                ? transactionRepository.getExpenseByCategory(
                uid, ky.tuTruoc, ky.denTruoc, maViDangChon)
                : transactionRepository.getIncomeByCategory(
                uid, ky.tuTruoc, ky.denTruoc, maViDangChon);

        hienThiSoSanh(tvSoSanhThu, tongThu, kyTruoc[0], true, ky.nhanTruoc);
        hienThiSoSanh(tvSoSanhChi, tongChi, kyTruoc[1], false, ky.nhanTruoc);

        // Biểu đồ cột: tháng thì 6 tháng, 30 ngày thì theo tuần
        if (ky.laThang) {

            List<long[]> xuHuong = transactionRepository.getMonthlyIncomeExpense(
                    uid, thangDuocChon, SO_THANG_XU_HUONG, maViDangChon);

            List<String> nhanCot = new ArrayList<>();

            for (int i = 0; i < xuHuong.size(); i++) {

                Calendar thang = (Calendar) thangDuocChon.clone();
                thang.set(Calendar.DAY_OF_MONTH, 1);
                thang.add(Calendar.MONTH, -(xuHuong.size() - 1 - i));
                nhanCot.add("T" + (thang.get(Calendar.MONTH) + 1));
            }

            hienThiBieuDoCot(xuHuong, nhanCot);

        } else {
            hienThiBieuDoCotTheoTuan(uid, ky);
        }

        // Nhịp chi tiêu theo ngày + các khoản chi lớn nhất (luôn là chi tiêu)
        long[] chiTheoNgay = ky.laThang
                ? transactionRepository.getDailyExpense(
                uid, tuNgay, denNgay, maViDangChon, ky.soNgay)
                : transactionRepository.getDailyExpenseFrom(
                uid, tuNgay, denNgay, maViDangChon, ky.soNgay);

        hienThiNhipChiTieu(chiTheoNgay, ky, tongThu, tongChi);

        hienThiKhoanChiLon(transactionRepository.getLargestExpenses(
                uid, tuNgay, denNgay, maViDangChon, 3));

        // Ngân sách theo tháng và tính trên tất cả ví,
        // nên chỉ hiện ở kỳ tháng, xem Chi, và đang chọn "Tất cả ví"
        Map<String, Budget> nganSach = null;

        if (ky.laThang && dangXemChi && maViDangChon == null) {

            nganSach = new HashMap<>();

            for (Budget b : budgetRepository.getBudgets(uid,
                    ky.batDauThang.get(Calendar.MONTH) + 1,
                    ky.batDauThang.get(Calendar.YEAR))) {
                nganSach.put(b.getDanhMuc(), b);
            }
        }

        hienThiGoiYNganSach(nganSach, ky.batDauThang, ky.soNgay);

        hienThiChiTheoDanhMuc(theoDanhMuc, dangXemChi ? tongChi : tongThu,
                danhMucKyTruoc, ky.nhanTruoc, nganSach);
    }

    /**
     * Cột theo tuần cho kỳ 30 ngày: cắt thành các khối 7 ngày tính lùi từ hôm nay,
     * nên tuần gần nhất luôn đủ 7 ngày; khối cũ nhất là phần dư (30 ngày = 4 tuần + 2 ngày).
     */
    private void hienThiBieuDoCotTheoTuan(String uid, Ky ky) {

        int soKhoi = (int) Math.ceil(ky.soNgay / (double) SO_NGAY_MOT_TUAN);

        List<long[]> cot = new ArrayList<>();
        List<String> nhanCot = new ArrayList<>();

        SimpleDateFormat dd = new SimpleDateFormat("dd/MM", Locale.getDefault());

        Calendar den = Calendar.getInstance();
        den.setTimeInMillis(ky.denNgay);

        for (int i = 0; i < soKhoi; i++) {

            Calendar phai = (Calendar) den.clone();
            phai.add(Calendar.DAY_OF_MONTH, -(soKhoi - 1 - i) * SO_NGAY_MOT_TUAN);

            Calendar trai = (Calendar) phai.clone();
            trai.add(Calendar.DAY_OF_MONTH, -SO_NGAY_MOT_TUAN);

            long tu = Math.max(trai.getTimeInMillis(), ky.tuNgay);

            cot.add(transactionRepository.getIncomeExpense(
                    uid, tu, phai.getTimeInMillis(), maViDangChon));

            nhanCot.add(dd.format(new Date(tu)));
        }

        hienThiBieuDoCot(cot, nhanCot);
    }

    private void hienThiConLai(long tongThu, long tongChi) {

        long chenhLech = tongThu - tongChi;

        hienThiTyLeTietKiem(tongThu, tongChi);

        if (chenhLech > 0) {
            tvChenhLechThongKe.setText("+" + dinhDangTien.format(chenhLech) + " ₫");
            tvChenhLechThongKe.setTextColor(XANH);
        } else if (chenhLech < 0) {
            tvChenhLechThongKe.setText(dinhDangTien.format(chenhLech) + " ₫");
            tvChenhLechThongKe.setTextColor(DO);
        } else {
            tvChenhLechThongKe.setText("0 ₫");
            tvChenhLechThongKe.setTextColor(Color.parseColor("#102A4C"));
        }
    }

    private void hienThiTyLeTietKiem(long tongThu, long tongChi) {

        if (tongThu <= 0) {
            tvTyLeTietKiem.setVisibility(View.GONE);
            return;
        }

        tvTyLeTietKiem.setVisibility(View.VISIBLE);

        if (tongChi <= tongThu) {

            long phanTram = Math.round((tongThu - tongChi) * 100.0 / tongThu);

            tvTyLeTietKiem.setText("Bạn giữ lại " + phanTram + "% thu nhập");
            tvTyLeTietKiem.setTextColor(XANH);

        } else {

            long phanTram = Math.round((tongChi - tongThu) * 100.0 / tongThu);

            tvTyLeTietKiem.setText("Chi vượt thu nhập " + phanTram + "%");
            tvTyLeTietKiem.setTextColor(DO);
        }
    }

    // ------------------------------------------------------------------
    //  Chip so với tháng trước
    // ------------------------------------------------------------------

    /**
     * Thu tăng là tốt (xanh), chi tăng là xấu (đỏ).
     * Tháng trước bằng 0 thì không tính được phần trăm nên hiện chip trung tính.
     */
    private void hienThiSoSanh(TextView chip, long hienTai, long truoc,
                               boolean laThuNhap, String nhanKyTruoc) {

        String nhan = nhanKyTruoc;

        if (truoc <= 0) {

            if (hienTai <= 0) {
                chip.setVisibility(View.GONE);
                return;
            }

            datChip(chip, "Chưa có dữ liệu " + nhan,
                    XAM, Color.parseColor("#F1F5F9"));
            return;
        }

        long phanTram = Math.round(Math.abs(hienTai - truoc) * 100.0 / truoc);

        if (hienTai == truoc || phanTram == 0) {
            datChip(chip, "Không đổi so với " + nhan,
                    XAM, Color.parseColor("#F1F5F9"));
            return;
        }

        boolean tang = hienTai > truoc;
        boolean tot = (tang == laThuNhap);

        datChip(chip,
                (tang ? "↑ " : "↓ ") + phanTram + "% so với " + nhan,
                tot ? XANH : DO,
                tot ? Color.parseColor("#DCFCE7") : Color.parseColor("#FEE2E2"));
    }

    private void datChip(TextView chip, String noiDung, int mauChu, int mauNen) {

        GradientDrawable nen = new GradientDrawable();
        nen.setShape(GradientDrawable.RECTANGLE);
        nen.setCornerRadius(dp(12));
        nen.setColor(mauNen);

        chip.setBackground(nen);
        chip.setTextColor(mauChu);
        chip.setText(noiDung);
        chip.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    //  Biểu đồ cột 6 tháng
    // ------------------------------------------------------------------

    private void khoiTaoBieuDoCot() {

        // Cho chạm để chọn cột nhưng không kéo, không phóng to
        barChartXuHuong.setTouchEnabled(true);
        barChartXuHuong.setDragEnabled(false);
        barChartXuHuong.setScaleEnabled(false);
        barChartXuHuong.setPinchZoom(false);
        barChartXuHuong.setDoubleTapToZoomEnabled(false);

        // Bấm vào một cột thì chuyển cả màn sang tháng đó
        barChartXuHuong.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {

                // Kỳ 30 ngày: cột là tuần, không chuyển tháng
                if (kyDangChon != KY_THANG) {
                    barChartXuHuong.highlightValues(null);
                    return;
                }

                // Mỗi nhóm cột rộng 1 đơn vị nên phần nguyên của x là vị trí tháng
                int viTri = (int) Math.floor(e.getX());
                viTri = Math.max(0, Math.min(SO_THANG_XU_HUONG - 1, viTri));

                barChartXuHuong.highlightValues(null);

                chuyenThang(viTri - (SO_THANG_XU_HUONG - 1));
            }

            @Override
            public void onNothingSelected() {
            }
        });
    }

    private void hienThiBieuDoCot(List<long[]> xuHuong, List<String> nhanCot) {

        int soCot = xuHuong.size();

        ArrayList<BarEntry> cotThu = new ArrayList<>();
        ArrayList<BarEntry> cotChi = new ArrayList<>();
        List<String> nhanThang = nhanCot;

        ArrayList<Integer> mauThu = new ArrayList<>();
        ArrayList<Integer> mauChi = new ArrayList<>();

        int thuNhat = Color.parseColor("#80059669");   // các tháng cũ: nhạt
        int chiNhat = Color.parseColor("#80DC2626");

        for (int i = 0; i < soCot; i++) {

            long[] thuChiThang = xuHuong.get(i);

            // Đơn vị: triệu đồng
            cotThu.add(new BarEntry(i, thuChiThang[0] / 1_000_000f));
            cotChi.add(new BarEntry(i, thuChiThang[1] / 1_000_000f));

            boolean laThangDangXem = (i == soCot - 1);

            // Tháng đang xem đậm hơn các tháng còn lại
            mauThu.add(laThangDangXem ? XANH : thuNhat);
            mauChi.add(laThangDangXem ? DO : chiNhat);
        }

        BarDataSet dsThu = new BarDataSet(cotThu, "Thu nhập");
        dsThu.setColors(mauThu);
        dsThu.setDrawValues(false);

        BarDataSet dsChi = new BarDataSet(cotChi, "Chi tiêu");
        dsChi.setColors(mauChi);
        dsChi.setDrawValues(false);

        float rongCot = 0.35f;
        float khoangCach = 0.05f;
        float khoangNhom = 1f - 2 * (rongCot + khoangCach);

        BarData data = new BarData(dsThu, dsChi);
        data.setBarWidth(rongCot);

        barChartXuHuong.setData(data);
        barChartXuHuong.groupBars(0f, khoangNhom, khoangCach);

        barChartXuHuong.getDescription().setEnabled(false);
        barChartXuHuong.setDrawGridBackground(false);
        barChartXuHuong.setExtraBottomOffset(4f);

        XAxis truc = barChartXuHuong.getXAxis();
        truc.setPosition(XAxis.XAxisPosition.BOTTOM);
        truc.setValueFormatter(new IndexAxisValueFormatter(nhanThang));
        truc.setGranularity(1f);
        truc.setCenterAxisLabels(true);
        truc.setAxisMinimum(0f);
        truc.setAxisMaximum(soCot);
        truc.setLabelCount(soCot);
        truc.setDrawGridLines(false);
        truc.setTextColor(Color.parseColor("#94A3B8"));
        truc.setTextSize(12f);
        truc.setAxisLineColor(Color.parseColor("#E2E8F0"));

        YAxis trai = barChartXuHuong.getAxisLeft();
        trai.setAxisMinimum(0f);
        trai.setDrawAxisLine(false);
        trai.setGridColor(Color.parseColor("#EEF2F7"));
        trai.setTextColor(Color.parseColor("#94A3B8"));
        trai.setTextSize(11f);
        trai.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value == Math.floor(value)) {
                    return String.valueOf((int) value);
                }
                return String.format(Locale.US, "%.1f", value);
            }
        });

        barChartXuHuong.getAxisRight().setEnabled(false);

        // Chú giải dùng màu đậm cho dễ nhìn
        Legend chuGiai = barChartXuHuong.getLegend();
        chuGiai.setCustom(new LegendEntry[]{
                new LegendEntry("Thu nhập", Legend.LegendForm.SQUARE,
                        10f, 10f, null, XANH),
                new LegendEntry("Chi tiêu", Legend.LegendForm.SQUARE,
                        10f, 10f, null, DO)
        });
        chuGiai.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        chuGiai.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        chuGiai.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        chuGiai.setDrawInside(false);
        chuGiai.setTextColor(Color.parseColor("#64748B"));
        chuGiai.setTextSize(12f);

        barChartXuHuong.animateY(600);
        barChartXuHuong.invalidate();
    }

    // ------------------------------------------------------------------
    //  Nhịp chi tiêu: trung bình, ngày chi nhiều nhất, dự đoán, biểu đồ đường
    // ------------------------------------------------------------------

    private void khoiTaoBieuDoDuong() {

        lineChartNgay.setNoDataText("Chưa có dữ liệu chi tiêu");
        lineChartNgay.getDescription().setEnabled(false);
        lineChartNgay.getLegend().setEnabled(false);
        lineChartNgay.setTouchEnabled(false);
        lineChartNgay.setDrawGridBackground(false);
        lineChartNgay.setExtraBottomOffset(4f);
        lineChartNgay.getAxisRight().setEnabled(false);

        XAxis truc = lineChartNgay.getXAxis();
        truc.setPosition(XAxis.XAxisPosition.BOTTOM);
        truc.setGranularity(1f);
        truc.setDrawGridLines(false);
        truc.setTextColor(Color.parseColor("#94A3B8"));
        truc.setTextSize(11f);
        truc.setAxisLineColor(Color.parseColor("#E2E8F0"));
        truc.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        YAxis trai = lineChartNgay.getAxisLeft();
        trai.setAxisMinimum(0f);
        trai.setDrawAxisLine(false);
        trai.setGridColor(Color.parseColor("#EEF2F7"));
        trai.setTextColor(Color.parseColor("#94A3B8"));
        trai.setTextSize(11f);
        trai.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return dinhDangGon(value);
            }
        });
    }

    /** 1.200.000 -> "1,2tr", 350.000 -> "350k", 0 -> "0". Dùng cho trục biểu đồ. */
    private String dinhDangGon(float giaTri) {

        if (giaTri >= 1_000_000f) {
            String so = String.format(Locale.US, "%.1f", giaTri / 1_000_000f);
            if (so.endsWith(".0")) {
                so = so.substring(0, so.length() - 2);
            }
            return so.replace('.', ',') + "tr";
        }

        if (giaTri >= 1000f) {
            return ((int) (giaTri / 1000f)) + "k";
        }

        return "0";
    }

    private void hienThiNhipChiTieu(long[] chiTheoNgay, Ky ky,
                                    long tongThu, long tongChi) {

        int soNgay = chiTheoNgay.length;

        // Số ngày đã trôi qua của kỳ đang xem
        int ngayDaQua = ky.ngayDaQua;
        boolean laThangHienTai = ky.laThang && kiemTraThangHienTai(ky.batDauThang);

        if (tongChi <= 0 || ngayDaQua == 0) {

            tvTrungBinhNgay.setText("0 ₫");
            tvNgayChiMax.setText("—");
            layoutDuDoan.setVisibility(View.GONE);
            tvGhiChuDuDoan.setVisibility(View.GONE);

            lineChartNgay.clear();
            lineChartNgay.invalidate();
            return;
        }

        // Trung bình mỗi ngày
        tvTrungBinhNgay.setText(
                dinhDangTien.format(Math.round((double) tongChi / ngayDaQua)) + " ₫");

        // Ngày chi nhiều nhất (nhiều ngày bằng nhau thì lấy ngày đầu)
        int ngayMax = 0;

        for (int i = 1; i < soNgay; i++) {
            if (chiTheoNgay[i] > chiTheoNgay[ngayMax]) {
                ngayMax = i;
            }
        }

        tvNgayChiMax.setText(nhanNgay(ky, ngayMax) + " · "
                + dinhDangTien.format(chiTheoNgay[ngayMax]) + " ₫");

        // Dự đoán cả tháng: chỉ cho tháng đang diễn ra, và cần ít nhất 3 ngày dữ liệu.
        // Kỳ 30 ngày không có "cuối tháng" để dự đoán nên ẩn.
        boolean duDoanDuocKhong = coTheDuDoan(laThangHienTai, ngayDaQua, soNgay);

        if (duDoanDuocKhong) {

            long duDoan = duDoanCaThang(tongChi, ngayDaQua, soNgay);

            tvDuDoan.setText("~" + dinhDangTien.format(duDoan) + " ₫");

            // Dự đoán chi vượt thu nhập tháng thì cảnh báo màu đỏ
            tvDuDoan.setTextColor(tongThu > 0 && duDoan > tongThu
                    ? DO : Color.parseColor("#102A4C"));

            layoutDuDoan.setVisibility(View.VISIBLE);
            tvGhiChuDuDoan.setVisibility(View.VISIBLE);

        } else {
            layoutDuDoan.setVisibility(View.GONE);
            tvGhiChuDuDoan.setVisibility(View.GONE);
        }

        veBieuDoDuong(chiTheoNgay, ngayDaQua, ngayMax, ky);
    }

    /** Tên ngày thứ viTri (từ 0) của kỳ: "Ngày 5" ở kỳ tháng, "26/10" ở kỳ 30 ngày. */
    private String nhanNgay(Ky ky, int viTri) {

        if (ky.laThang) {
            return "Ngày " + (viTri + 1);
        }

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ky.tuNgay);
        c.add(Calendar.DAY_OF_MONTH, viTri);

        return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(c.getTime());
    }

    private void veBieuDoDuong(long[] chiTheoNgay, int ngayDaQua, int ngayMax, Ky ky) {

        ArrayList<Entry> duongChi = new ArrayList<>();

        // Chỉ vẽ đến hôm nay, các ngày chưa tới để trống
        for (int i = 0; i < ngayDaQua; i++) {
            duongChi.add(new Entry(i + 1, chiTheoNgay[i]));
        }

        LineDataSet dsChi = new LineDataSet(duongChi, "Chi tiêu");
        dsChi.setColor(DO);
        dsChi.setLineWidth(2.5f);
        dsChi.setDrawCircles(false);
        dsChi.setDrawValues(false);
        dsChi.setMode(LineDataSet.Mode.LINEAR);
        dsChi.setDrawFilled(true);
        dsChi.setFillColor(DO);
        dsChi.setFillAlpha(30);

        // Chấm đánh dấu ngày chi nhiều nhất
        ArrayList<Entry> diemMax = new ArrayList<>();
        diemMax.add(new Entry(ngayMax + 1, chiTheoNgay[ngayMax]));

        LineDataSet dsMax = new LineDataSet(diemMax, "Nhiều nhất");
        dsMax.setDrawCircles(true);
        dsMax.setCircleColor(DO);
        dsMax.setCircleRadius(5f);
        dsMax.setDrawCircleHole(true);
        dsMax.setCircleHoleRadius(2.5f);
        dsMax.setCircleHoleColor(Color.WHITE);
        dsMax.setDrawValues(false);
        dsMax.setLineWidth(0f);
        dsMax.setHighlightEnabled(false);

        lineChartNgay.setData(new LineData(dsChi, dsMax));

        XAxis truc = lineChartNgay.getXAxis();

        // Kỳ tháng: nhãn là số ngày. Kỳ 30 ngày: nhãn là ngày/tháng thật
        final boolean laThang = ky.laThang;
        final long tuNgayKy = ky.tuNgay;

        truc.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {

                if (laThang) {
                    return String.valueOf((int) value);
                }

                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(tuNgayKy);
                c.add(Calendar.DAY_OF_MONTH, (int) value - 1);

                return String.format(Locale.US, "%02d/%02d",
                        c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1);
            }
        });

        truc.setAxisMinimum(1f);
        truc.setAxisMaximum(chiTheoNgay.length);
        truc.setLabelCount(6, false);

        lineChartNgay.animateX(500);
        lineChartNgay.invalidate();
    }

    // ------------------------------------------------------------------
    //  Dự đoán cả tháng (dùng chung cho thẻ Nhịp chi tiêu và ngân sách)
    // ------------------------------------------------------------------

    private boolean kiemTraThangHienTai(Calendar batDauThang) {

        Calendar bayGio = Calendar.getInstance();

        return batDauThang.get(Calendar.YEAR) == bayGio.get(Calendar.YEAR)
                && batDauThang.get(Calendar.MONTH) == bayGio.get(Calendar.MONTH);
    }

    /** Tháng cũ: cả tháng. Tháng này: đến hôm nay. Tháng sau: 0. */
    private int demNgayDaQua(Calendar batDauThang, int soNgay) {

        Calendar bayGio = Calendar.getInstance();

        int dangXem = batDauThang.get(Calendar.YEAR) * 12
                + batDauThang.get(Calendar.MONTH);
        int hienTai = bayGio.get(Calendar.YEAR) * 12 + bayGio.get(Calendar.MONTH);

        if (dangXem < hienTai) {
            return soNgay;
        }

        if (dangXem == hienTai) {
            return Math.min(soNgay, bayGio.get(Calendar.DAY_OF_MONTH));
        }

        return 0;
    }

    /** Chỉ dự đoán cho tháng đang diễn ra và cần ít nhất 3 ngày dữ liệu. */
    private boolean coTheDuDoan(boolean laThangHienTai, int ngayDaQua, int soNgay) {
        return laThangHienTai && ngayDaQua >= 3 && ngayDaQua < soNgay;
    }

    /** Giữ nguyên tốc độ chi trung bình mỗi ngày đến hết tháng. */
    private long duDoanCaThang(long daChi, int ngayDaQua, int soNgay) {
        return Math.round((double) daChi * soNgay / ngayDaQua);
    }

    // ------------------------------------------------------------------
    //  Gợi ý ngân sách
    // ------------------------------------------------------------------

    private static class GoiY {
        final String noiDung;
        final boolean daVuot;
        final long thuTu;   // nhỏ hơn thì xếp trước trong cùng nhóm

        GoiY(String noiDung, boolean daVuot, long thuTu) {
            this.noiDung = noiDung;
            this.daVuot = daVuot;
            this.thuTu = thuTu;
        }
    }

    /**
     * Hai quy tắc:
     * 1) Chi vượt hạn mức: "Mua sắm đã vượt ngân sách 120.000 ₫".
     * 2) Chưa vượt nhưng giữ tốc độ này (số dự đoán giai đoạn 3) thì sẽ vượt trước cuối tháng:
     *    "Với tốc độ này, Ăn uống sẽ vượt ngân sách khoảng ngày 24".
     * Đã vượt xếp trước (vượt nhiều nhất lên đầu), rồi đến sắp vượt (ngày sớm nhất lên đầu).
     */
    private void hienThiGoiYNganSach(Map<String, Budget> nganSach,
                                     Calendar batDauThang, int soNgay) {

        layoutGoiYNganSach.removeAllViews();

        if (nganSach == null || nganSach.isEmpty()) {
            cardGoiYNganSach.setVisibility(View.GONE);
            return;
        }

        int ngayDaQua = demNgayDaQua(batDauThang, soNgay);
        boolean duDoanDuoc = coTheDuDoan(
                kiemTraThangHienTai(batDauThang), ngayDaQua, soNgay);

        List<GoiY> danhSach = new ArrayList<>();

        for (Budget b : nganSach.values()) {

            long gioiHan = b.getSoTienGioiHan();
            long daChi = b.getSoTienDaChi();

            if (daChi > gioiHan) {

                long vuot = daChi - gioiHan;

                danhSach.add(new GoiY(
                        b.getDanhMuc() + " đã vượt ngân sách "
                                + dinhDangTien.format(vuot) + " ₫",
                        true, -vuot));

            } else if (duDoanDuoc && daChi > 0
                    && duDoanCaThang(daChi, ngayDaQua, soNgay) > gioiHan) {

                // Ngày chi cộng dồn vượt hạn mức, giới hạn trong tháng
                int ngayVuot = (int) Math.floor(
                        (double) gioiHan * ngayDaQua / daChi) + 1;
                ngayVuot = Math.min(ngayVuot, soNgay);

                danhSach.add(new GoiY(
                        "Với tốc độ này, " + b.getDanhMuc()
                                + " sẽ vượt ngân sách khoảng ngày " + ngayVuot,
                        false, ngayVuot));
            }
        }

        if (danhSach.isEmpty()) {
            cardGoiYNganSach.setVisibility(View.GONE);
            return;
        }

        Collections.sort(danhSach, (a, b) -> {
            if (a.daVuot != b.daVuot) {
                return a.daVuot ? -1 : 1;
            }
            return Long.compare(a.thuTu, b.thuTu);
        });

        cardGoiYNganSach.setVisibility(View.VISIBLE);

        int soHang = Math.min(SO_GOI_Y_TOI_DA, danhSach.size());

        for (int i = 0; i < soHang; i++) {
            layoutGoiYNganSach.addView(taoHangGoiY(danhSach.get(i)));
        }
    }

    private View taoHangGoiY(GoiY goiY) {

        LinearLayout hang = new LinearLayout(this);
        hang.setOrientation(LinearLayout.HORIZONTAL);
        hang.setGravity(Gravity.TOP);
        hang.setPadding(0, dp(6), 0, dp(6));

        int mau = goiY.daVuot ? DO : CAM;

        View cham = new View(this);
        GradientDrawable hinhCham = new GradientDrawable();
        hinhCham.setShape(GradientDrawable.OVAL);
        hinhCham.setColor(mau);
        cham.setBackground(hinhCham);

        LinearLayout.LayoutParams paramsCham =
                new LinearLayout.LayoutParams(dp(8), dp(8));
        paramsCham.setMargins(0, dp(6), dp(10), 0);
        hang.addView(cham, paramsCham);

        TextView tv = new TextView(this);
        tv.setText(goiY.noiDung);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor("#102A4C"));
        hang.addView(tv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        return hang;
    }

    // ------------------------------------------------------------------
    //  Khoản chi lớn nhất
    // ------------------------------------------------------------------

    private void hienThiKhoanChiLon(List<Transaction> danhSach) {

        layoutKhoanChiLon.removeAllViews();

        if (danhSach == null || danhSach.isEmpty()) {
            cardKhoanChiLon.setVisibility(View.GONE);
            return;
        }

        cardKhoanChiLon.setVisibility(View.VISIBLE);

        SimpleDateFormat dinhDangNgay =
                new SimpleDateFormat("dd/MM", Locale.getDefault());

        for (int i = 0; i < danhSach.size(); i++) {

            if (i > 0) {
                layoutKhoanChiLon.addView(taoDuongKe());
            }

            Transaction giaoDich = danhSach.get(i);

            LinearLayout dong = new LinearLayout(this);
            dong.setOrientation(LinearLayout.HORIZONTAL);
            dong.setGravity(Gravity.CENTER_VERTICAL);
            dong.setPadding(0, dp(12), 0, dp(12));

            TypedValue o = new TypedValue();
            getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, o, true);
            dong.setBackgroundResource(o.resourceId);
            dong.setClickable(true);
            dong.setFocusable(true);

            // Cột trái: danh mục + ngày và ghi chú
            LinearLayout cotTrai = new LinearLayout(this);
            cotTrai.setOrientation(LinearLayout.VERTICAL);

            String danhMuc = giaoDich.getDanhMuc();

            if (danhMuc == null || danhMuc.trim().isEmpty()) {
                danhMuc = DANH_MUC_MAC_DINH;
            }

            TextView tvDanhMuc = new TextView(this);
            tvDanhMuc.setText(danhMuc);
            tvDanhMuc.setTextSize(15);
            tvDanhMuc.setTextColor(Color.parseColor("#102A4C"));
            cotTrai.addView(tvDanhMuc);

            String phu = giaoDich.getNgayGiaoDich() == null
                    ? ""
                    : dinhDangNgay.format(giaoDich.getNgayGiaoDich());

            String ghiChu = giaoDich.getGhiChu();

            if (ghiChu != null && !ghiChu.trim().isEmpty()) {
                phu = phu.isEmpty() ? ghiChu.trim() : phu + " · " + ghiChu.trim();
            }

            if (!phu.isEmpty()) {
                TextView tvPhu = new TextView(this);
                tvPhu.setText(phu);
                tvPhu.setTextSize(12);
                tvPhu.setTextColor(Color.parseColor("#94A3B8"));
                tvPhu.setSingleLine(true);
                tvPhu.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tvPhu.setPadding(0, dp(2), 0, 0);
                cotTrai.addView(tvPhu);
            }

            dong.addView(cotTrai, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvTien = new TextView(this);
            tvTien.setText(dinhDangTien.format(giaoDich.getSoTien()) + " ₫");
            tvTien.setTextSize(15);
            tvTien.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTien.setTextColor(DO);

            LinearLayout.LayoutParams paramsTien = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            paramsTien.setMarginStart(dp(12));
            dong.addView(tvTien, paramsTien);

            String maGiaoDich = giaoDich.getMaGiaoDich();

            dong.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionDetailActivity.class);
                intent.putExtra("maGiaoDich", maGiaoDich);
                startActivity(intent);
            });

            layoutKhoanChiLon.addView(dong);
        }
    }

    // ------------------------------------------------------------------
    //  Biểu đồ tròn + danh sách danh mục
    // ------------------------------------------------------------------

    private void khoiTaoBieuDoTron() {

        pieChartDanhMuc.setNoDataText("Chưa có dữ liệu chi tiêu");
        pieChartDanhMuc.getDescription().setEnabled(false);
        pieChartDanhMuc.getLegend().setEnabled(false); // danh sách bên dưới thay chú giải
        pieChartDanhMuc.setDrawEntryLabels(false);
        pieChartDanhMuc.setUsePercentValues(true);
        pieChartDanhMuc.setRotationEnabled(false);
        pieChartDanhMuc.setExtraOffsets(8f, 8f, 8f, 8f);

        pieChartDanhMuc.setDrawHoleEnabled(true);
        pieChartDanhMuc.setHoleRadius(62f);
        pieChartDanhMuc.setTransparentCircleRadius(0f);
        pieChartDanhMuc.setHoleColor(Color.TRANSPARENT);

        pieChartDanhMuc.setDrawCenterText(true);
        pieChartDanhMuc.setCenterTextColor(Color.parseColor("#102A4C"));

        // Bấm vào một miếng bánh cũng mở lịch sử của danh mục đó
        pieChartDanhMuc.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {

                if (e instanceof PieEntry) {
                    String danhMuc = ((PieEntry) e).getLabel();
                    pieChartDanhMuc.highlightValues(null);
                    moLichSuTheoDanhMuc(danhMuc);
                }
            }

            @Override
            public void onNothingSelected() {
            }
        });
    }

    private int mauDanhMuc(String ten, int viTri) {

        if (DANH_MUC_MAC_DINH.equals(ten) || DANH_MUC_THU_MAC_DINH.equals(ten)) {
            return MAU_KHAC;
        }

        return BANG_MAU[viTri % BANG_MAU.length];
    }

    private void hienThiChiTheoDanhMuc(HashMap<String, Long> theoDanhMuc,
                                       long tongLoai,
                                       HashMap<String, Long> danhMucThangTruoc,
                                       String nhanKyTruoc,
                                       Map<String, Budget> nganSach) {

        layoutThongKeDanhMuc.removeAllViews();

        if (theoDanhMuc.isEmpty() || tongLoai <= 0) {

            pieChartDanhMuc.clear();
            pieChartDanhMuc.setVisibility(View.GONE);
            tvDanhMucTrong.setVisibility(View.VISIBLE);
            return;
        }

        pieChartDanhMuc.setVisibility(View.VISIBLE);
        tvDanhMucTrong.setVisibility(View.GONE);

        ArrayList<Map.Entry<String, Long>> danhSach =
                new ArrayList<>(theoDanhMuc.entrySet());

        Collections.sort(danhSach,
                (a, b) -> Long.compare(b.getValue(), a.getValue()));

        // Màu theo thứ tự đã sắp xếp: biểu đồ và danh sách luôn khớp màu
        ArrayList<Integer> mauSac = new ArrayList<>();
        ArrayList<PieEntry> duLieu = new ArrayList<>();

        for (int i = 0; i < danhSach.size(); i++) {

            Map.Entry<String, Long> item = danhSach.get(i);

            mauSac.add(mauDanhMuc(item.getKey(), i));
            duLieu.add(new PieEntry(item.getValue().floatValue(), item.getKey()));
        }

        veBieuDoTron(duLieu, mauSac, tongLoai);

        for (int i = 0; i < danhSach.size(); i++) {

            if (i > 0) {
                layoutThongKeDanhMuc.addView(taoDuongKe());
            }

            Map.Entry<String, Long> item = danhSach.get(i);

            long phanTram = Math.round(item.getValue() * 100.0 / tongLoai);

            layoutThongKeDanhMuc.addView(
                    taoDongDanhMuc(item.getKey(), item.getValue(),
                            phanTram, mauSac.get(i),
                            danhMucThangTruoc, nhanKyTruoc,
                            nganSach == null ? null : nganSach.get(item.getKey())));
        }
    }

    private void veBieuDoTron(ArrayList<PieEntry> duLieu,
                              ArrayList<Integer> mauSac, long tongLoai) {

        PieDataSet dataSet = new PieDataSet(duLieu, "");
        dataSet.setColors(mauSac);
        dataSet.setSliceSpace(2f);
        dataSet.setDrawValues(false); // phần trăm đã có trong danh sách bên dưới

        pieChartDanhMuc.setData(new PieData(dataSet));

        pieChartDanhMuc.setCenterText(
                (dangXemChi ? "Tổng chi\n" : "Tổng thu\n")
                        + dinhDangTien.format(tongLoai) + " ₫");
        pieChartDanhMuc.setCenterTextSize(14f);

        pieChartDanhMuc.animateY(700);
        pieChartDanhMuc.invalidate();
    }

    private View taoDongDanhMuc(String ten, long soTien, long phanTram, int mau,
                                HashMap<String, Long> danhMucThangTruoc,
                                String nhanKyTruoc, Budget nganSach) {

        LinearLayout dong = new LinearLayout(this);
        dong.setOrientation(LinearLayout.HORIZONTAL);
        dong.setGravity(Gravity.CENTER_VERTICAL);
        dong.setPadding(0, dp(14), 0, nganSach == null ? dp(14) : dp(8));

        // Khung ngoài chứa dòng và thanh ngân sách; hiệu ứng chạm đặt ở đây để phủ cả hai
        LinearLayout khung = new LinearLayout(this);
        khung.setOrientation(LinearLayout.VERTICAL);

        TypedValue o = new TypedValue();
        getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, o, true);
        khung.setBackgroundResource(o.resourceId);
        khung.setClickable(true);
        khung.setFocusable(true);

        khung.addView(dong);

        if (nganSach != null) {
            khung.addView(taoKhoiNganSach(nganSach));
        }

        View cham = new View(this);
        GradientDrawable hinhCham = new GradientDrawable();
        hinhCham.setShape(GradientDrawable.RECTANGLE);
        hinhCham.setCornerRadius(dp(4));
        hinhCham.setColor(mau);
        cham.setBackground(hinhCham);

        LinearLayout.LayoutParams paramsCham =
                new LinearLayout.LayoutParams(dp(12), dp(12));
        paramsCham.setMarginEnd(dp(12));
        dong.addView(cham, paramsCham);

        // Cột tên: tên danh mục + dòng nhỏ so với tháng trước
        LinearLayout cotTen = new LinearLayout(this);
        cotTen.setOrientation(LinearLayout.VERTICAL);

        TextView tvTen = new TextView(this);
        tvTen.setText(ten);
        tvTen.setTextSize(15);
        tvTen.setTextColor(Color.parseColor("#102A4C"));
        cotTen.addView(tvTen);

        TextView tvThayDoi = taoDongThayDoi(
                soTien, ten, danhMucThangTruoc, nhanKyTruoc);

        if (tvThayDoi != null) {
            cotTen.addView(tvThayDoi);
        }

        dong.addView(cotTen, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvTien = new TextView(this);
        tvTien.setText(dinhDangTien.format(soTien) + " ₫");
        tvTien.setTextSize(14);
        tvTien.setTextColor(Color.parseColor("#64748B"));
        dong.addView(tvTien);

        TextView tvPhanTram = new TextView(this);
        tvPhanTram.setText(phanTram + "%");
        tvPhanTram.setTextSize(13);
        tvPhanTram.setTextColor(Color.parseColor("#94A3B8"));
        tvPhanTram.setGravity(Gravity.END);

        LinearLayout.LayoutParams paramsPt =
                new LinearLayout.LayoutParams(dp(44),
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsPt.setMarginStart(dp(8));
        dong.addView(tvPhanTram, paramsPt);

        TextView tvMuiTen = new TextView(this);
        tvMuiTen.setText("›");
        tvMuiTen.setTextSize(20);
        tvMuiTen.setTextColor(Color.parseColor("#94A3B8"));

        LinearLayout.LayoutParams paramsMt =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsMt.setMarginStart(dp(6));
        dong.addView(tvMuiTen, paramsMt);

        khung.setOnClickListener(v -> moLichSuTheoDanhMuc(ten));

        return khung;
    }

    /**
     * Thanh tiến độ ngân sách và dòng "Còn X ₫" dưới dòng danh mục.
     * Dưới 80% xanh, từ 80% đến hết hạn mức cam, vượt thì đỏ.
     */
    private View taoKhoiNganSach(Budget nganSach) {

        long gioiHan = nganSach.getSoTienGioiHan();
        long daChi = nganSach.getSoTienDaChi();

        boolean vuot = daChi > gioiHan;

        int phanTram = (int) Math.min(100, Math.round(daChi * 100.0 / gioiHan));

        int mauThanh = vuot ? DO : (phanTram >= 80 ? CAM : XANH);

        LinearLayout khoi = new LinearLayout(this);
        khoi.setOrientation(LinearLayout.VERTICAL);
        // Thụt vào bằng chấm màu (12dp) + khoảng cách (12dp) để thẳng với tên danh mục
        khoi.setPadding(dp(24), 0, 0, dp(14));

        // Thanh: nền xám, phần đã dùng tô màu theo trọng số
        LinearLayout thanh = new LinearLayout(this);
        thanh.setOrientation(LinearLayout.HORIZONTAL);
        thanh.setWeightSum(100);

        GradientDrawable nenThanh = new GradientDrawable();
        nenThanh.setShape(GradientDrawable.RECTANGLE);
        nenThanh.setCornerRadius(dp(3));
        nenThanh.setColor(Color.parseColor("#E2E8F0"));
        thanh.setBackground(nenThanh);

        View daDung = new View(this);
        GradientDrawable nenDaDung = new GradientDrawable();
        nenDaDung.setShape(GradientDrawable.RECTANGLE);
        nenDaDung.setCornerRadius(dp(3));
        nenDaDung.setColor(mauThanh);
        daDung.setBackground(nenDaDung);

        thanh.addView(daDung, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, phanTram));

        khoi.addView(thanh, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));

        TextView tvConLai = new TextView(this);
        tvConLai.setText((vuot
                ? "Vượt " + dinhDangTien.format(daChi - gioiHan)
                : "Còn " + dinhDangTien.format(gioiHan - daChi))
                + " ₫ · ngân sách " + dinhDangTien.format(gioiHan) + " ₫");
        tvConLai.setTextSize(12);
        tvConLai.setTextColor(vuot ? DO : XAM);
        tvConLai.setPadding(0, dp(6), 0, 0);
        khoi.addView(tvConLai);

        return khoi;
    }

    /**
     * Dòng nhỏ "↑ 20% so với T10" dưới tên danh mục. Chi tăng là đỏ, giảm là xanh.
     * Tháng trước không có chi tiêu nào thì không hiện gì (không có gì để so).
     */
    private TextView taoDongThayDoi(long hienTai, String ten,
                                    HashMap<String, Long> danhMucThangTruoc,
                                    String nhanKyTruoc) {

        if (danhMucThangTruoc == null || danhMucThangTruoc.isEmpty()) {
            return null;
        }

        Long giaTriTruoc = danhMucThangTruoc.get(ten);
        long truoc = giaTriTruoc == null ? 0 : giaTriTruoc;

        String noiDung;
        int mau;

        if (truoc <= 0) {
            noiDung = "Mới phát sinh";
            mau = XAM;
        } else {
            long phanTram = Math.round(Math.abs(hienTai - truoc) * 100.0 / truoc);

            if (phanTram == 0) {
                noiDung = "Không đổi so với " + nhanKyTruoc;
                mau = XAM;
            } else if (hienTai > truoc) {
                noiDung = "↑ " + phanTram + "% so với " + nhanKyTruoc;
                mau = dangXemChi ? DO : XANH;   // chi tăng xấu, thu tăng tốt
            } else {
                noiDung = "↓ " + phanTram + "% so với " + nhanKyTruoc;
                mau = dangXemChi ? XANH : DO;
            }
        }

        TextView tv = new TextView(this);
        tv.setText(noiDung);
        tv.setTextSize(12);
        tv.setTextColor(mau);
        tv.setPadding(0, dp(2), 0, 0);
        return tv;
    }

    private View taoDuongKe() {

        View duong = new View(this);
        duong.setBackgroundColor(Color.parseColor("#E2E8F0"));
        duong.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return duong;
    }

    // ------------------------------------------------------------------
    //  Mở lịch sử giao dịch đã lọc
    // ------------------------------------------------------------------

    private void moLichSuTheoDanhMuc(String danhMuc) {

        Intent intent = new Intent(this, TransactionHistoryActivity.class);

        intent.putExtra(TransactionHistoryActivity.EXTRA_DANH_MUC, danhMuc);
        if (kyDangChon == KY_THANG) {

            intent.putExtra(TransactionHistoryActivity.EXTRA_THANG,
                    thangDuocChon.get(Calendar.MONTH) + 1);
            intent.putExtra(TransactionHistoryActivity.EXTRA_NAM,
                    thangDuocChon.get(Calendar.YEAR));

        } else {

            Ky ky = tinhKy();

            intent.putExtra(TransactionHistoryActivity.EXTRA_TU_NGAY, ky.tuNgay);
            intent.putExtra(TransactionHistoryActivity.EXTRA_DEN_NGAY, ky.denNgay);
            intent.putExtra(TransactionHistoryActivity.EXTRA_NHAN_KY,
                    SO_NGAY_KY_NGAN + " ngày qua");
        }
        intent.putExtra(TransactionHistoryActivity.EXTRA_LOAI,
                dangXemChi ? LOAI_CHI : LOAI_THU);

        if (maViDangChon != null) {
            intent.putExtra(TransactionHistoryActivity.EXTRA_MA_VI, maViDangChon);
            intent.putExtra(TransactionHistoryActivity.EXTRA_TEN_VI, tenViDangChon);
        }

        startActivity(intent);
    }

    private int dp(int giaTri) {
        return (int) (giaTri * getResources().getDisplayMetrics().density);
    }
}