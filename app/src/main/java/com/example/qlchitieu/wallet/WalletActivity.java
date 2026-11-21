package com.example.qlchitieu.wallet;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.example.qlchitieu.model.Wallet;
import com.example.qlchitieu.adapter.WalletAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class WalletActivity extends AppCompatActivity {

    TextView tvQuayLai, tvTongTienVi, tvSoLuongVi;

    MaterialButton btnThemVi, btnViHoatDong, btnViLuuTru;

    RecyclerView rvDanhSachVi;
    LinearLayout layoutViTrong;

    ArrayList<Wallet> danhSachVi;
    ArrayList<Wallet> tatCaVi;

    WalletAdapter walletAdapter;

    FirebaseAuth firebaseAuth;
    WalletRepository walletRepository;

    boolean dangXemViLuuTru = false;

    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        tvQuayLai = findViewById(R.id.tvQuayLai);
        tvTongTienVi = findViewById(R.id.tvTongTienVi);
        tvSoLuongVi = findViewById(R.id.tvSoLuongVi);

        btnThemVi = findViewById(R.id.btnThemVi);
        btnViHoatDong = findViewById(R.id.btnViHoatDong);
        btnViLuuTru = findViewById(R.id.btnViLuuTru);

        rvDanhSachVi = findViewById(R.id.rvDanhSachVi);
        layoutViTrong = findViewById(R.id.layoutViTrong);

        firebaseAuth = FirebaseAuth.getInstance();
        walletRepository = new WalletRepository(this);

        danhSachVi = new ArrayList<>();
        tatCaVi = new ArrayList<>();

        walletAdapter = new WalletAdapter(danhSachVi);

        rvDanhSachVi.setLayoutManager(
                new LinearLayoutManager(this)
        );

        rvDanhSachVi.setAdapter(walletAdapter);

        tvQuayLai.setOnClickListener(v -> finish());

        btnThemVi.setOnClickListener(v -> {
            Intent intent = new Intent(
                    WalletActivity.this,
                    AddWalletActivity.class
            );
            startActivity(intent);
        });

        btnViHoatDong.setOnClickListener(v -> {
            dangXemViLuuTru = false;

            capNhatGiaoDienTab();
            locDanhSachVi();
        });

        btnViLuuTru.setOnClickListener(v -> {
            dangXemViLuuTru = true;

            capNhatGiaoDienTab();
            locDanhSachVi();
        });

        capNhatGiaoDienTab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        taiDanhSachVi();
    }

    private void taiDanhSachVi() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = nguoiDung.getUid();

        // 1) Hiện ngay từ SQLite
        tatCaVi.clear();
        tatCaVi.addAll(walletRepository.getWallets(uid));
        locDanhSachVi();

        // 2) Đồng bộ với Firestore ở nền, xong thì hiện lại.
        // Dù đồng bộ thành công hay không (ví dụ mất mạng), luôn hiện lại dữ liệu trên máy.
        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            tatCaVi.clear();
            tatCaVi.addAll(walletRepository.getWallets(uid));
            locDanhSachVi();
        });
    }

    private void locDanhSachVi() {

        danhSachVi.clear();

        long tongSoDu = 0;

        for (Wallet vi : tatCaVi) {

            boolean daLuuTru =
                    "LUU_TRU".equals(vi.getTrangThai());

            if (daLuuTru == dangXemViLuuTru) {

                danhSachVi.add(vi);

                if (!daLuuTru) {
                    tongSoDu += vi.getSoDu();
                }
            }
        }

        walletAdapter.notifyDataSetChanged();

        if (dangXemViLuuTru) {

            tvTongTienVi.setText("Đã lưu trữ");

            tvSoLuongVi.setText(
                    danhSachVi.size() + " ví đã lưu trữ"
            );

        } else {

            tvTongTienVi.setText(
                    dinhDangTien.format(tongSoDu) + " ₫"
            );

            tvSoLuongVi.setText(
                    danhSachVi.size() + " ví đang sử dụng"
            );
        }

        if (danhSachVi.isEmpty()) {

            layoutViTrong.setVisibility(View.VISIBLE);
            rvDanhSachVi.setVisibility(View.GONE);

        } else {

            layoutViTrong.setVisibility(View.GONE);
            rvDanhSachVi.setVisibility(View.VISIBLE);
        }
    }

    private void capNhatGiaoDienTab() {

        int mauXanh = Color.parseColor("#2563EB");
        int mauTrang = Color.WHITE;
        int mauNen = Color.parseColor("#E8EDF5");
        int mauChu = Color.parseColor("#64748B");

        btnViHoatDong.setBackgroundTintList(
                ColorStateList.valueOf(
                        dangXemViLuuTru ? mauNen : mauXanh
                )
        );

        btnViHoatDong.setTextColor(
                dangXemViLuuTru ? mauChu : mauTrang
        );

        btnViLuuTru.setBackgroundTintList(
                ColorStateList.valueOf(
                        dangXemViLuuTru ? mauXanh : mauNen
                )
        );

        btnViLuuTru.setTextColor(
                dangXemViLuuTru ? mauTrang : mauChu
        );
    }
}