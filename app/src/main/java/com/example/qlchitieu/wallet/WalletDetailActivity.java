package com.example.qlchitieu.wallet;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.WalletRepository;
import com.example.qlchitieu.model.Wallet;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.Locale;

public class WalletDetailActivity extends AppCompatActivity {

    TextView tvQuayLaiChiTietVi;
    TextView tvSoDuChiTietVi, tvTenViTieuDe;
    TextView tvTenViChiTiet, tvLoaiViChiTiet, tvTrangThaiVi;

    MaterialButton btnSuaVi, btnDieuChinhSoDu;
    MaterialButton btnLichSuDieuChinh, btnQuanLyVi;

    FirebaseAuth firebaseAuth;
    WalletRepository walletRepository;

    String maVi;
    String uid;
    String trangThaiVi = "HOAT_DONG";

    boolean dangXuLy = false;
    boolean daTaiVi = false;

    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_detail);

        tvQuayLaiChiTietVi = findViewById(R.id.tvQuayLaiChiTietVi);
        tvSoDuChiTietVi = findViewById(R.id.tvSoDuChiTietVi);
        tvTenViTieuDe = findViewById(R.id.tvTenViTieuDe);
        tvTenViChiTiet = findViewById(R.id.tvTenViChiTiet);
        tvLoaiViChiTiet = findViewById(R.id.tvLoaiViChiTiet);
        tvTrangThaiVi = findViewById(R.id.tvTrangThaiVi);

        btnSuaVi = findViewById(R.id.btnSuaVi);
        btnDieuChinhSoDu = findViewById(R.id.btnDieuChinhSoDu);
        btnLichSuDieuChinh = findViewById(R.id.btnLichSuDieuChinh);
        btnQuanLyVi = findViewById(R.id.btnQuanLyVi);

        firebaseAuth = FirebaseAuth.getInstance();
        walletRepository = new WalletRepository(this);

        maVi = getIntent().getStringExtra("maVi");

        tvQuayLaiChiTietVi.setOnClickListener(v -> finish());

        btnSuaVi.setOnClickListener(v -> {
            if (!"HOAT_DONG".equals(trangThaiVi)) {
                return;
            }

            Intent intent = new Intent(
                    WalletDetailActivity.this,
                    EditWalletActivity.class
            );
            intent.putExtra("maVi", maVi);
            startActivity(intent);
        });

        btnDieuChinhSoDu.setOnClickListener(v -> {
            if (!"HOAT_DONG".equals(trangThaiVi)) {
                return;
            }

            Intent intent = new Intent(
                    WalletDetailActivity.this,
                    AdjustBalanceActivity.class
            );
            intent.putExtra("maVi", maVi);
            startActivity(intent);
        });

        btnLichSuDieuChinh.setOnClickListener(v -> {
            Intent intent = new Intent(
                    WalletDetailActivity.this,
                    BalanceHistoryActivity.class
            );
            intent.putExtra("maVi", maVi);
            startActivity(intent);
        });

        btnQuanLyVi.setOnClickListener(v -> hienThiQuanLyVi());

        btnSuaVi.setEnabled(false);
        btnDieuChinhSoDu.setEnabled(false);
        btnQuanLyVi.setEnabled(false);

        if (maVi == null || maVi.trim().isEmpty()) {
            thongBao("Không tìm thấy mã ví");
            finish();
            return;
        }

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            thongBao("Vui lòng đăng nhập lại");
            finish();
            return;
        }

        uid = nguoiDung.getUid();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (uid != null && maVi != null && !maVi.trim().isEmpty()) {
            taiChiTietVi();
        }
    }

    private void taiChiTietVi() {

        // 1) Hiện ngay từ SQLite
        hienThiChiTiet();

        // 2) Tạm thời: kéo ví từ Firestore về (các màn hình giao dịch vẫn ghi
        // thẳng lên Firestore), rồi hiện lại và mới cho phép thao tác.
        walletRepository.pullFromCloud(uid, (thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            hienThiChiTiet();

            if (daTaiVi) {
                capNhatNut();
            } else if (thanhCong) {
                thongBao("Ví không tồn tại");
                finish();
            } else {
                thongBao("Không thể tải chi tiết ví: " + loi);
            }
        });
    }

    /** Đọc ví từ SQLite và hiển thị thông tin lên màn hình. */
    private void hienThiChiTiet() {

        Wallet vi = walletRepository.getWallet(uid, maVi);

        if (vi == null) {
            return;
        }

        String loaiVi = vi.getLoaiVi() == null ? "Khác" : vi.getLoaiVi();

        tvSoDuChiTietVi.setText(
                dinhDangTien.format(vi.getSoDu()) + " ₫"
        );

        tvTenViTieuDe.setText(vi.getTenVi());
        tvTenViChiTiet.setText(vi.getTenVi());
        tvLoaiViChiTiet.setText(loaiVi);

        if ("LUU_TRU".equals(vi.getTrangThai())) {
            trangThaiVi = "LUU_TRU";

            tvTrangThaiVi.setText("Đã lưu trữ");
            tvTrangThaiVi.setTextColor(
                    Color.parseColor("#64748B")
            );

        } else {
            trangThaiVi = "HOAT_DONG";

            tvTrangThaiVi.setText("Đang sử dụng");
            tvTrangThaiVi.setTextColor(
                    Color.parseColor("#059669")
            );
        }

        daTaiVi = true;
    }

    /** Bật/tắt các nút theo trạng thái ví. */
    private void capNhatNut() {
        boolean hoatDong = "HOAT_DONG".equals(trangThaiVi);

        btnSuaVi.setEnabled(hoatDong);
        btnDieuChinhSoDu.setEnabled(hoatDong);
        btnQuanLyVi.setEnabled(!dangXuLy);
    }

    private void hienThiQuanLyVi() {
        if (dangXuLy || !daTaiVi) {
            return;
        }

        boolean daLuuTru = "LUU_TRU".equals(trangThaiVi);

        String[] luaChon = {
                daLuuTru ? "Khôi phục ví" : "Lưu trữ ví",
                "Xóa ví vĩnh viễn"
        };

        new AlertDialog.Builder(this)
                .setTitle("Quản lý ví")
                .setItems(luaChon, (dialog, which) -> {
                    if (which == 0) {
                        xacNhanDoiTrangThaiVi();
                    } else {
                        kiemTraVaXoaVi();
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void xacNhanDoiTrangThaiVi() {
        boolean daLuuTru = "LUU_TRU".equals(trangThaiVi);

        String tieuDe = daLuuTru
                ? "Khôi phục ví"
                : "Lưu trữ ví";

        String noiDung = daLuuTru
                ? "Bạn muốn tiếp tục sử dụng ví này?"
                : "Bạn muốn ngừng sử dụng ví này?\n\n"
                  + "Ví và lịch sử vẫn được giữ lại.\n"
                  + "Chỉ có thể lưu trữ ví khi số dư bằng 0 ₫.";

        new AlertDialog.Builder(this)
                .setTitle(tieuDe)
                .setMessage(noiDung)
                .setNegativeButton("Hủy", null)
                .setPositiveButton(
                        daLuuTru ? "Khôi phục" : "Lưu trữ",
                        (dialog, which) -> doiTrangThaiVi(daLuuTru)
                )
                .show();
    }

    private void doiTrangThaiVi(boolean daLuuTru) {
        if (dangXuLy) {
            return;
        }

        batDauXuLy();

        walletRepository.setArchived(uid, maVi, daLuuTru, (thanhCong, loi) -> {

            if (thanhCong) {
                thongBao(daLuuTru
                        ? "Khôi phục ví thành công"
                        : "Lưu trữ ví thành công");
            } else {
                thongBao("Không thể thay đổi trạng thái: " + loi);
            }

            ketThucXuLy();
            hienThiChiTiet();
            capNhatNut();
        });
    }

    private void kiemTraVaXoaVi() {
        if (dangXuLy) {
            return;
        }

        batDauXuLy();

        walletRepository.checkCanDelete(uid, maVi,
                new WalletRepository.DeleteCheckCallback() {

                    @Override
                    public void onOk() {
                        hienThiXacNhanXoa();
                    }

                    @Override
                    public void onRefused(String lyDo) {
                        new AlertDialog.Builder(WalletDetailActivity.this)
                                .setTitle("Không thể xóa ví")
                                .setMessage(lyDo)
                                .setPositiveButton("Đã hiểu", null)
                                .show();

                        ketThucXuLy();
                    }

                    @Override
                    public void onError(String loi) {
                        thongBao("Không thể kiểm tra ví: " + loi);
                        ketThucXuLy();
                    }
                });
    }

    private void hienThiXacNhanXoa() {
        String tenVi = tvTenViChiTiet.getText().toString();

        new AlertDialog.Builder(this)
                .setTitle("Xóa ví vĩnh viễn")
                .setMessage(
                        "Bạn có chắc muốn xóa ví \""
                                + tenVi + "\"?\n\n"
                                + "Hành động này không thể hoàn tác."
                )
                .setNegativeButton("Hủy",
                        (dialog, which) -> ketThucXuLy())
                .setPositiveButton("Xóa ví",
                        (dialog, which) -> thucHienXoaVi())
                .setOnCancelListener(dialog -> ketThucXuLy())
                .show();
    }

    private void thucHienXoaVi() {
        walletRepository.deleteWallet(uid, maVi, (thanhCong, loi) -> {

            if (thanhCong) {
                thongBao("Xóa ví thành công");
                ketThucXuLy();
                finish();
            } else {
                thongBao("Xóa ví thất bại: " + loi);
                ketThucXuLy();
            }
        });
    }

    private void batDauXuLy() {
        dangXuLy = true;
        btnQuanLyVi.setEnabled(false);
    }

    private void ketThucXuLy() {
        dangXuLy = false;
        btnQuanLyVi.setEnabled(true);
    }

    private void thongBao(String noiDung) {
        Toast.makeText(this,
                noiDung,
                Toast.LENGTH_LONG).show();
    }
}