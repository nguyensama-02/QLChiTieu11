package com.example.qlchitieu.dashboard;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.example.qlchitieu.profile.ProfileActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.R;
import com.example.qlchitieu.adapter.TransactionAdapter;
import com.example.qlchitieu.auth.LoginActivity;
import com.example.qlchitieu.budget.BudgetActivity;
import com.example.qlchitieu.data.repository.BudgetRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.data.repository.TransactionRepository;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.model.Budget;
import com.example.qlchitieu.model.Transaction;
import com.example.qlchitieu.statistics.StatisticsActivity;
import com.example.qlchitieu.transaction.AddTransactionActivity;
import com.example.qlchitieu.transaction.TransactionHistoryActivity;
import com.example.qlchitieu.util.MonthRange;
import com.example.qlchitieu.wallet.WalletActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.graphics.Bitmap;
import android.widget.ImageView;
import com.example.qlchitieu.data.repository.UserProfileRepository;
import com.example.qlchitieu.profile.AvatarStore;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    TextView tvTenNguoiDung, tvTongSoDu, tvThuNhap, tvChiTieu;
    View navViTien, navGiaoDich;

    RecyclerView rvGiaoDichGanDay;
    LinearLayout layoutGiaoDichTrong;

    LinearLayout btnNganSach;
    LinearLayout layoutCanhBaoNganSach;

    TextView tvTieuDeCanhBaoNganSach;
    TextView tvNoiDungCanhBaoNganSach;
    TextView tvXemNganSach;

    ArrayList<Transaction> danhSachGiaoDich;
    TransactionAdapter transactionAdapter;

    FirebaseAuth firebaseAuth;

    UserProfileRepository userProfileRepository;
    ImageView btnHoSoHeader;

    WalletRepository walletRepository;
    TransactionRepository transactionRepository;
    BudgetRepository budgetRepository;

    int lanDongBo = 0;

    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            moDangNhap();
            return;
        }

        setContentView(R.layout.activity_main);

        userProfileRepository = new UserProfileRepository(this);

        walletRepository = new WalletRepository(this);
        transactionRepository = new TransactionRepository(this);
        budgetRepository = new BudgetRepository(this);

        tvTenNguoiDung = findViewById(R.id.tvTenNguoiDung);
        btnHoSoHeader = findViewById(R.id.btnHoSoHeader);
        tvTongSoDu = findViewById(R.id.tvTongSoDu);

        findViewById(R.id.cardTongSoDu).setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    WalletActivity.class
            );
            startActivity(intent);
        });

        tvThuNhap = findViewById(R.id.tvThuNhap);
        tvChiTieu = findViewById(R.id.tvChiTieu);

        navViTien = findViewById(R.id.navViTien);
        navGiaoDich = findViewById(R.id.navGiaoDich);

        rvGiaoDichGanDay =
                findViewById(R.id.rvGiaoDichGanDay);

        layoutGiaoDichTrong =
                findViewById(R.id.layoutGiaoDichTrong);

        btnNganSach = findViewById(R.id.btnNganSach);

        layoutCanhBaoNganSach =
                findViewById(R.id.layoutCanhBaoNganSach);

        tvTieuDeCanhBaoNganSach =
                findViewById(R.id.tvTieuDeCanhBaoNganSach);

        tvNoiDungCanhBaoNganSach =
                findViewById(R.id.tvNoiDungCanhBaoNganSach);

        tvXemNganSach =
                findViewById(R.id.tvXemNganSach);

        TextView tvXemTatCa = findViewById(R.id.tvXemTatCa);
        View navThongKe = findViewById(R.id.navThongKe);

        danhSachGiaoDich = new ArrayList<>();

        transactionAdapter =
                new TransactionAdapter(danhSachGiaoDich);

        rvGiaoDichGanDay.setLayoutManager(
                new LinearLayoutManager(this)
        );

        rvGiaoDichGanDay.setNestedScrollingEnabled(false);
        rvGiaoDichGanDay.setAdapter(transactionAdapter);

        tvTongSoDu.setText("0 ₫");
        tvThuNhap.setText("0 ₫");
        tvChiTieu.setText("0 ₫");

        layoutCanhBaoNganSach.setVisibility(View.GONE);

        hienHoSo(nguoiDung.getUid());

        tvXemTatCa.setOnClickListener(v -> {
            startActivity(new Intent(
                    this,
                    TransactionHistoryActivity.class
            ));
        });

        btnNganSach.setOnClickListener(v -> moNganSach());

        tvXemNganSach.setOnClickListener(v -> moNganSach());

        layoutCanhBaoNganSach.setOnClickListener(
                v -> moNganSach()
        );

        navThongKe.setOnClickListener(v -> {
            startActivity(new Intent(
                    this,
                    StatisticsActivity.class
            ));
        });

        navViTien.setOnClickListener(v -> {
            startActivity(new Intent(
                    this,
                    WalletActivity.class
            ));
        });

        navGiaoDich.setOnClickListener(v -> {
            startActivity(new Intent(
                    this,
                    AddTransactionActivity.class
            ));
        });
        View.OnClickListener moCaNhan = v ->
                startActivity(new Intent(this, ProfileActivity.class));

        findViewById(R.id.navCaNhan).setOnClickListener(moCaNhan);
        findViewById(R.id.btnHoSoHeader).setOnClickListener(moCaNhan);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (userProfileRepository == null) {
            return;
        }

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            return;
        }

        String uid = nguoiDung.getUid();

        // 1) Hiện ngay từ SQLite
        taiDuLieuTuSQLite(uid);
        hienHoSo(uid);

        // 2) Tạm thời: kéo dữ liệu từ Firestore về rồi hiện lại.
        // Mất mạng thì bỏ qua, vẫn dùng dữ liệu đang có trên máy.
        int lanHienTai = ++lanDongBo;

        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed() || lanHienTai != lanDongBo) {
                return;
            }

            taiDuLieuTuSQLite(uid);
            hienHoSo(uid);
        });
    }

    private void taiDuLieuTuSQLite(String uid) {
        taiTongSoDu(uid);
        taiGiaoDichGanDay(uid);
        taiNganSach(uid);
    }

    private void moNganSach() {
        Intent intent = new Intent(
                MainActivity.this,
                BudgetActivity.class
        );
        startActivity(intent);
    }

    /** Hiện họ tên đã lưu trên máy và ảnh đại diện (nếu có) ở đầu trang. */
    private void hienHoSo(String uid) {

        String hoTen = userProfileRepository.getHoTen(uid);

        if (hoTen != null) {
            tvTenNguoiDung.setText(hoTen);
        }

        Bitmap anh = AvatarStore.tai(this, uid);

        if (anh != null) {
            btnHoSoHeader.setPadding(0, 0, 0, 0);
            btnHoSoHeader.setScaleType(ImageView.ScaleType.CENTER_CROP);
            btnHoSoHeader.setClipToOutline(true);
            btnHoSoHeader.setImageBitmap(anh);
        } else {
            int le = (int) (11 * getResources().getDisplayMetrics().density);
            btnHoSoHeader.setPadding(le, le, le, le);
            btnHoSoHeader.setScaleType(ImageView.ScaleType.FIT_CENTER);
            btnHoSoHeader.setImageResource(R.drawable.ic_user);
        }
    }

    private void taiTongSoDu(String uid) {

        long tongSoDu = walletRepository.getTotalBalance(uid);

        tvTongSoDu.setText(
                dinhDangTien.format(tongSoDu) + " ₫"
        );
    }

    private void taiGiaoDichGanDay(String uid) {

        Calendar homNay = Calendar.getInstance();

        int thang = homNay.get(Calendar.MONTH) + 1;
        int nam = homNay.get(Calendar.YEAR);

        long[] thuChi = transactionRepository.getIncomeExpense(
                uid,
                MonthRange.start(thang, nam),
                MonthRange.end(thang, nam)
        );

        List<Transaction> ganDay =
                transactionRepository.getRecent(uid, 5);

        danhSachGiaoDich.clear();
        danhSachGiaoDich.addAll(ganDay);

        transactionAdapter.notifyDataSetChanged();

        tvThuNhap.setText(
                dinhDangTien.format(thuChi[0]) + " ₫"
        );

        tvChiTieu.setText(
                dinhDangTien.format(thuChi[1]) + " ₫"
        );

        if (danhSachGiaoDich.isEmpty()) {

            layoutGiaoDichTrong.setVisibility(View.VISIBLE);
            rvGiaoDichGanDay.setVisibility(View.GONE);

        } else {

            layoutGiaoDichTrong.setVisibility(View.GONE);
            rvGiaoDichGanDay.setVisibility(View.VISIBLE);
        }
    }

    /** Đọc ngân sách tháng này một lần, dùng cho cả cảnh báo lẫn thẻ tổng quan. */
    private void taiNganSach(String uid) {

        Calendar homNay = Calendar.getInstance();

        int thang = homNay.get(Calendar.MONTH) + 1;
        int nam = homNay.get(Calendar.YEAR);

        List<Budget> danhSach = budgetRepository.getBudgets(uid, thang, nam);

        hienThiCanhBaoNganSach(danhSach);
        hienThiTongQuanNganSach(danhSach, thang, nam);
    }

    private void hienThiCanhBaoNganSach(List<Budget> danhSach) {

        ArrayList<String> canhBaoDo = new ArrayList<>();
        ArrayList<String> canhBaoCam = new ArrayList<>();

        try {
            for (Budget nganSach : danhSach) {

                String danhMuc = nganSach.getDanhMuc();
                long gioiHan = nganSach.getSoTienGioiHan();
                long daChi = nganSach.getSoTienDaChi();

                double phanTram = (double) daChi * 100 / gioiHan;

                if (phanTram >= 100) {

                    if (daChi > gioiHan) {

                        long vuot = Math.subtractExact(daChi, gioiHan);

                        canhBaoDo.add(
                                "• " + danhMuc
                                        + ": Đã vượt "
                                        + dinhDangTien.format(vuot)
                                        + " ₫ ("
                                        + dinhDangPhanTram(phanTram)
                                        + ")."
                        );

                    } else {

                        canhBaoDo.add(
                                "• " + danhMuc
                                        + ": Đã đạt 100% ngân sách."
                        );
                    }

                } else if (phanTram >= 80) {

                    canhBaoCam.add(
                            "• " + danhMuc
                                    + ": Đã sử dụng "
                                    + dinhDangPhanTram(phanTram)
                                    + " ngân sách."
                    );
                }
            }

            hienThiCanhBao(canhBaoDo, canhBaoCam);

        } catch (ArithmeticException e) {

            layoutCanhBaoNganSach.setVisibility(View.GONE);

            thongBao("Dữ liệu chi tiêu vượt giới hạn tính toán");
        }
    }

    private String dinhDangPhanTram(double phanTram) {

        return String.format(
                new Locale("vi", "VN"),
                "%.1f%%",
                phanTram
        );
    }

    private void hienThiCanhBao(
            ArrayList<String> canhBaoDo,
            ArrayList<String> canhBaoCam) {

        if (canhBaoDo.isEmpty() && canhBaoCam.isEmpty()) {

            layoutCanhBaoNganSach.setVisibility(View.GONE);
            return;
        }

        boolean coCanhBaoDo = !canhBaoDo.isEmpty();

        StringBuilder noiDung = new StringBuilder();

        for (String canhBao : canhBaoDo) {
            if (noiDung.length() > 0) {
                noiDung.append("\n");
            }
            noiDung.append(canhBao);
        }

        for (String canhBao : canhBaoCam) {
            if (noiDung.length() > 0) {
                noiDung.append("\n");
            }
            noiDung.append(canhBao);
        }

        tvNoiDungCanhBaoNganSach.setText(
                noiDung.toString()
        );

        if (coCanhBaoDo) {

            tvTieuDeCanhBaoNganSach.setText(
                    "⚠ Ngân sách đã đạt hoặc vượt giới hạn"
            );

            tvTieuDeCanhBaoNganSach.setTextColor(
                    Color.parseColor("#DC2626")
            );

        } else {

            tvTieuDeCanhBaoNganSach.setText(
                    "⚠ Ngân sách sắp đạt giới hạn"
            );

            tvTieuDeCanhBaoNganSach.setTextColor(
                    Color.parseColor("#D97706")
            );
        }

        layoutCanhBaoNganSach.setVisibility(View.VISIBLE);
    }

    private void moDangNhap() {

        Intent intent = new Intent(
                this,
                LoginActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private void thongBao(String noiDung) {
        Toast.makeText(
                this,
                noiDung,
                Toast.LENGTH_LONG
        ).show();
    }

    private void hienThiTongQuanNganSach(List<Budget> danhSach, int thang, int nam) {

        TextView tvThang = findViewById(R.id.tvThangTongNganSach);
        TextView tvTien = findViewById(R.id.tvTienNganSach);
        TextView tvPhanTram = findViewById(R.id.tvPhanTramNganSach);
        TextView tvConLai = findViewById(R.id.tvConLaiNganSach);

        ProgressBar progress =
                findViewById(R.id.progressTongNganSach);

        tvThang.setText(
                "Ngân sách tháng " + thang + "/" + nam
        );

        try {

            long tongNganSach = 0;
            long tongDaChi = 0;

            for (Budget nganSach : danhSach) {

                tongNganSach = Math.addExact(
                        tongNganSach, nganSach.getSoTienGioiHan()
                );

                tongDaChi = Math.addExact(
                        tongDaChi, nganSach.getSoTienDaChi()
                );
            }

            if (tongNganSach == 0) {

                tvTien.setText("Chưa có ngân sách tháng này");
                tvPhanTram.setText("0%");
                tvConLai.setText(
                        "Nhấn để tạo ngân sách đầu tiên"
                );

                progress.setProgress(0);
                return;
            }

            double phanTram =
                    (double) tongDaChi * 100 / tongNganSach;

            int tienDo = (int) Math.min(
                    100,
                    Math.max(0, phanTram)
            );

            tvTien.setText(
                    "Đã chi "
                            + dinhDangTien.format(tongDaChi)
                            + " / "
                            + dinhDangTien.format(tongNganSach)
                            + " ₫"
            );

            tvPhanTram.setText(
                    String.format(
                            Locale.forLanguageTag("vi-VN"),
                            "%.0f%%",
                            phanTram
                    )
            );

            progress.setProgress(tienDo);

            long conLai = Math.subtractExact(
                    tongNganSach, tongDaChi
            );

            if (conLai >= 0) {

                tvConLai.setText(
                        "Còn lại: "
                                + dinhDangTien.format(conLai)
                                + " ₫"
                );

            } else {

                tvConLai.setText(
                        "Đã vượt tổng ngân sách "
                                + dinhDangTien.format(
                                Math.negateExact(conLai)
                        )
                                + " ₫"
                );
            }

        } catch (ArithmeticException e) {

            tvTien.setText(
                    "Số tiền vượt giới hạn tính toán"
            );
            tvPhanTram.setText("--");
            tvConLai.setText("Không thể tính ngân sách");
            progress.setProgress(0);
        }
    }

}