package com.example.qlchitieu.statistics;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.qlchitieu.R;
import com.example.qlchitieu.auth.LoginActivity;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import androidx.appcompat.app.AppCompatActivity;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {

    TextView tvQuayLaiThongKe, tvChonThang;
    TextView tvTongThuThongKe, tvTongChiThongKe;
    TextView tvChenhLechThongKe;
    TextView tvCotThu, tvCotChi;
    TextView tvDanhMucTrong;

    LinearLayout layoutThongKeDanhMuc;

    FirebaseAuth firebaseAuth;
    TransactionRepository transactionRepository;

    int lanDongBo = 0;

    Calendar thangDuocChon;
    PieChart pieChartDanhMuc;
    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        pieChartDanhMuc = findViewById(R.id.pieChartDanhMuc);
        tvQuayLaiThongKe = findViewById(R.id.tvQuayLaiThongKe);
        tvChonThang = findViewById(R.id.tvChonThang);

        tvTongThuThongKe = findViewById(R.id.tvTongThuThongKe);
        tvTongChiThongKe = findViewById(R.id.tvTongChiThongKe);
        tvChenhLechThongKe = findViewById(R.id.tvChenhLechThongKe);

        tvCotThu = findViewById(R.id.tvCotThu);
        tvCotChi = findViewById(R.id.tvCotChi);

        tvDanhMucTrong = findViewById(R.id.tvDanhMucTrong);
        layoutThongKeDanhMuc = findViewById(R.id.layoutThongKeDanhMuc);

        firebaseAuth = FirebaseAuth.getInstance();
        transactionRepository = new TransactionRepository(this);

        thangDuocChon = Calendar.getInstance();

        tvQuayLaiThongKe.setOnClickListener(v -> finish());

        tvChonThang.setOnClickListener(v -> chonThang());

        hienThiThang();
        taiDuLieuThongKe();
    }

    private void hienThiThang() {

        int thang = thangDuocChon.get(Calendar.MONTH) + 1;
        int nam = thangDuocChon.get(Calendar.YEAR);

        tvChonThang.setText(
                "Tháng " + thang + "/" + nam + "  ▾"
        );
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

        spChonThang.setSelection(
                thangDuocChon.get(Calendar.MONTH)
        );

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

                    hienThiThang();
                    taiDuLieuThongKe();
                })
                .show();
    }

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

        Calendar batDau = (Calendar) thangDuocChon.clone();
        batDau.set(Calendar.DAY_OF_MONTH, 1);
        batDau.set(Calendar.HOUR_OF_DAY, 0);
        batDau.set(Calendar.MINUTE, 0);
        batDau.set(Calendar.SECOND, 0);
        batDau.set(Calendar.MILLISECOND, 0);

        Calendar ketThuc = (Calendar) batDau.clone();
        ketThuc.add(Calendar.MONTH, 1);

        long tuNgay = batDau.getTimeInMillis();
        long denNgay = ketThuc.getTimeInMillis();

        // Tổng hợp bằng SQL (SUM / GROUP BY), không tải từng giao dịch
        long[] thuChi = transactionRepository.getIncomeExpense(uid, tuNgay, denNgay);

        HashMap<String, Long> chiTheoDanhMuc =
                transactionRepository.getExpenseByCategory(uid, tuNgay, denNgay);

        long tongThu = thuChi[0];
        long tongChi = thuChi[1];

        tvTongThuThongKe.setText(
                dinhDangTien.format(tongThu) + " ₫"
        );

        tvTongChiThongKe.setText(
                dinhDangTien.format(tongChi) + " ₫"
        );

        long chenhLech = tongThu - tongChi;

        if (chenhLech > 0) {

            tvChenhLechThongKe.setText(
                    "+" + dinhDangTien.format(chenhLech) + " ₫"
            );

            tvChenhLechThongKe.setTextColor(
                    Color.parseColor("#059669")
            );

        } else if (chenhLech < 0) {

            tvChenhLechThongKe.setText(
                    dinhDangTien.format(chenhLech) + " ₫"
            );

            tvChenhLechThongKe.setTextColor(
                    Color.parseColor("#DC2626")
            );

        } else {

            tvChenhLechThongKe.setText("0 ₫");

            tvChenhLechThongKe.setTextColor(
                    Color.parseColor("#102A4C")
            );
        }

        capNhatBieuDo(tongThu, tongChi);

        hienThiBieuDoTron(chiTheoDanhMuc);

        hienThiChiTheoDanhMuc(chiTheoDanhMuc);
    }

    private void capNhatBieuDo(long tongThu, long tongChi) {

        long giaTriLonNhat = Math.max(tongThu, tongChi);

        int chieuCaoThu = 0;
        int chieuCaoChi = 0;

        if (giaTriLonNhat > 0) {

            chieuCaoThu = (int) (180.0 * tongThu / giaTriLonNhat);
            chieuCaoChi = (int) (180.0 * tongChi / giaTriLonNhat);

            if (tongThu > 0) {
                chieuCaoThu = Math.max(6, chieuCaoThu);
            }

            if (tongChi > 0) {
                chieuCaoChi = Math.max(6, chieuCaoChi);
            }
        }

        LinearLayout.LayoutParams paramsThu =
                (LinearLayout.LayoutParams) tvCotThu.getLayoutParams();

        paramsThu.height = dp(chieuCaoThu);
        tvCotThu.setLayoutParams(paramsThu);

        LinearLayout.LayoutParams paramsChi =
                (LinearLayout.LayoutParams) tvCotChi.getLayoutParams();

        paramsChi.height = dp(chieuCaoChi);
        tvCotChi.setLayoutParams(paramsChi);
    }

    private void hienThiChiTheoDanhMuc(
            HashMap<String, Long> chiTheoDanhMuc) {

        layoutThongKeDanhMuc.removeAllViews();

        if (chiTheoDanhMuc.isEmpty()) {

            TextView tvTrong = new TextView(this);
            tvTrong.setText("Chưa có dữ liệu chi tiêu");
            tvTrong.setTextColor(Color.parseColor("#94A3B8"));
            tvTrong.setTextSize(14);
            tvTrong.setGravity(android.view.Gravity.CENTER);
            tvTrong.setPadding(0, dp(24), 0, dp(24));

            layoutThongKeDanhMuc.addView(tvTrong);
            return;
        }

        ArrayList<Map.Entry<String, Long>> danhSach =
                new ArrayList<>(chiTheoDanhMuc.entrySet());

        Collections.sort(danhSach,
                (a, b) -> Long.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Long> item : danhSach) {

            LinearLayout dong = new LinearLayout(this);
            dong.setOrientation(LinearLayout.HORIZONTAL);
            dong.setPadding(0, dp(12), 0, dp(12));

            TextView tvTenDanhMuc = new TextView(this);
            tvTenDanhMuc.setText(item.getKey());
            tvTenDanhMuc.setTextSize(15);
            tvTenDanhMuc.setTextColor(Color.parseColor("#102A4C"));

            LinearLayout.LayoutParams paramsTen =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    );

            dong.addView(tvTenDanhMuc, paramsTen);

            TextView tvTien = new TextView(this);
            tvTien.setText(
                    dinhDangTien.format(item.getValue()) + " ₫"
            );
            tvTien.setTextSize(15);
            tvTien.setTextColor(Color.parseColor("#DC2626"));

            dong.addView(tvTien);

            layoutThongKeDanhMuc.addView(dong);
        }
    }

    private int dp(int giaTri) {
        return (int) (
                giaTri * getResources().getDisplayMetrics().density
        );
    }

    private void hienThiBieuDoTron(HashMap<String, Long> chiTheoDanhMuc) {

        ArrayList<PieEntry> duLieu = new ArrayList<>();

        long tongChi = 0;

        for (Long soTien : chiTheoDanhMuc.values()) {
            tongChi += soTien;
        }

        if (tongChi == 0) {
            pieChartDanhMuc.clear();
            pieChartDanhMuc.setNoDataText("Chưa có dữ liệu chi tiêu");
            pieChartDanhMuc.invalidate();
            return;
        }

        for (Map.Entry<String, Long> item : chiTheoDanhMuc.entrySet()) {
            duLieu.add(new PieEntry(
                    item.getValue().floatValue(),
                    item.getKey()
            ));
        }

        PieDataSet dataSet = new PieDataSet(duLieu, "");

        ArrayList<Integer> mauSac = new ArrayList<>();

        mauSac.add(Color.parseColor("#0891B2"));
        mauSac.add(Color.parseColor("#10B981"));
        mauSac.add(Color.parseColor("#F59E0B"));
        mauSac.add(Color.parseColor("#EF4444"));
        mauSac.add(Color.parseColor("#8B5CF6"));
        mauSac.add(Color.parseColor("#EC4899"));

        dataSet.setColors(mauSac);
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChartDanhMuc));

        pieChartDanhMuc.setData(data);

        pieChartDanhMuc.setUsePercentValues(true);
        pieChartDanhMuc.setDrawHoleEnabled(true);
        pieChartDanhMuc.setHoleRadius(58f);
        pieChartDanhMuc.setTransparentCircleRadius(62f);

        pieChartDanhMuc.setDrawCenterText(true);
        pieChartDanhMuc.setCenterText("CHI TIÊU");
        pieChartDanhMuc.setCenterTextSize(15f);
        pieChartDanhMuc.setCenterTextColor(Color.parseColor("#102A4C"));

        pieChartDanhMuc.getDescription().setEnabled(false);
        pieChartDanhMuc.getLegend().setEnabled(true);
        pieChartDanhMuc.setDrawEntryLabels(false);

        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);

        pieChartDanhMuc.setExtraOffsets(8f, 8f, 8f, 8f);
        pieChartDanhMuc.getLegend().setTextSize(12f);

        pieChartDanhMuc.animateY(900);
        pieChartDanhMuc.invalidate();
    }

}