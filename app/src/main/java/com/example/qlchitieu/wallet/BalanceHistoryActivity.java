package com.example.qlchitieu.wallet;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.R;
import com.example.qlchitieu.adapter.BalanceAdjustmentAdapter;
import com.example.qlchitieu.data.repository.BalanceAdjustmentRepository;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.model.BalanceAdjustment;
import com.example.qlchitieu.model.Wallet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;

public class BalanceHistoryActivity extends AppCompatActivity {

    TextView tvQuayLaiLichSuDieuChinh;
    TextView tvTenViLichSu, tvSoLanDieuChinh;

    LinearLayout layoutLichSuDieuChinhTrong;
    RecyclerView rvLichSuDieuChinh;

    ArrayList<BalanceAdjustment> danhSachDieuChinh;
    BalanceAdjustmentAdapter adapter;

    FirebaseAuth firebaseAuth;
    WalletRepository walletRepository;
    BalanceAdjustmentRepository adjustmentRepository;

    String maVi;
    String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance_history);

        tvQuayLaiLichSuDieuChinh =
                findViewById(R.id.tvQuayLaiLichSuDieuChinh);

        tvTenViLichSu = findViewById(R.id.tvTenViLichSu);
        tvSoLanDieuChinh = findViewById(R.id.tvSoLanDieuChinh);

        layoutLichSuDieuChinhTrong =
                findViewById(R.id.layoutLichSuDieuChinhTrong);

        rvLichSuDieuChinh =
                findViewById(R.id.rvLichSuDieuChinh);

        firebaseAuth = FirebaseAuth.getInstance();
        walletRepository = new WalletRepository(this);
        adjustmentRepository = new BalanceAdjustmentRepository(this);

        maVi = getIntent().getStringExtra("maVi");

        danhSachDieuChinh = new ArrayList<>();

        adapter = new BalanceAdjustmentAdapter(danhSachDieuChinh);

        rvLichSuDieuChinh.setLayoutManager(
                new LinearLayoutManager(this)
        );

        rvLichSuDieuChinh.setAdapter(adapter);

        tvQuayLaiLichSuDieuChinh.setOnClickListener(v -> finish());

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

        taiTenVi();
        taiLichSuDieuChinh();
    }

    private void taiTenVi() {

        Wallet vi = walletRepository.getWallet(uid, maVi);

        if (vi != null) {
            tvTenViLichSu.setText(vi.getTenVi());
        }
    }

    private void taiLichSuDieuChinh() {

        // 1) Hiện ngay từ SQLite
        hienThiLichSu();

        // 2) Tạm thời: kéo từ Firestore về rồi hiện lại.
        // Mất mạng thì bỏ qua, vẫn dùng dữ liệu đang có trên máy.
        adjustmentRepository.pullFromCloud(uid, maVi, (thanhCong, loi) -> {
            if (thanhCong) {
                hienThiLichSu();
            }
        });
    }

    private void hienThiLichSu() {

        danhSachDieuChinh.clear();
        danhSachDieuChinh.addAll(
                adjustmentRepository.getAdjustments(uid, maVi)
        );

        adapter.notifyDataSetChanged();

        tvSoLanDieuChinh.setText(
                danhSachDieuChinh.size() + " lần điều chỉnh"
        );

        if (danhSachDieuChinh.isEmpty()) {

            layoutLichSuDieuChinhTrong.setVisibility(View.VISIBLE);
            rvLichSuDieuChinh.setVisibility(View.GONE);

        } else {

            layoutLichSuDieuChinhTrong.setVisibility(View.GONE);
            rvLichSuDieuChinh.setVisibility(View.VISIBLE);
        }
    }
}