package com.example.qlchitieu.budget;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.model.Budget;
import com.example.qlchitieu.adapter.BudgetAdapter;
import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.BudgetRepository;
import com.example.qlchitieu.data.sync.SyncManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.widget.EditText;
import android.text.InputType;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetActivity extends AppCompatActivity {

    TextView tvQuayLaiNganSach, tvThangNganSach;
    TextView tvTongNganSach, tvDaChiNganSach;

    LinearLayout layoutNganSachTrong;
    RecyclerView rvDanhSachNganSach;

    MaterialButton btnThemNganSach;

    ArrayList<Budget> danhSachNganSach;
    BudgetAdapter budgetAdapter;

    FirebaseAuth firebaseAuth;
    BudgetRepository budgetRepository;

    int thang, nam;
    int lanTaiDuLieu = 0;

    NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        tvQuayLaiNganSach =
                findViewById(R.id.tvQuayLaiNganSach);

        tvThangNganSach =
                findViewById(R.id.tvThangNganSach);

        tvTongNganSach =
                findViewById(R.id.tvTongNganSach);

        tvDaChiNganSach =
                findViewById(R.id.tvDaChiNganSach);

        layoutNganSachTrong =
                findViewById(R.id.layoutNganSachTrong);

        rvDanhSachNganSach =
                findViewById(R.id.rvDanhSachNganSach);

        btnThemNganSach =
                findViewById(R.id.btnThemNganSach);

        firebaseAuth = FirebaseAuth.getInstance();
        budgetRepository = new BudgetRepository(this);

        Calendar lich = Calendar.getInstance();

        thang = lich.get(Calendar.MONTH) + 1;
        nam = lich.get(Calendar.YEAR);

        danhSachNganSach = new ArrayList<>();

        budgetAdapter = new BudgetAdapter(danhSachNganSach);

        rvDanhSachNganSach.setLayoutManager(
                new LinearLayoutManager(this)
        );

        rvDanhSachNganSach.setAdapter(budgetAdapter);
        budgetAdapter.setOnBudgetClickListener(
                nganSach -> hienThiQuanLyNganSach(nganSach)
        );
        hienThiThang();

        tvQuayLaiNganSach.setOnClickListener(v -> finish());

        tvThangNganSach.setOnClickListener(v -> chonThang());

        btnThemNganSach.setOnClickListener(v -> {
            Intent intent = new Intent(
                    BudgetActivity.this,
                    AddBudgetActivity.class
            );

            intent.putExtra("thang", thang);
            intent.putExtra("nam", nam);

            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        taiDuLieuNganSach();
    }

    private void hienThiThang() {

        tvThangNganSach.setText(
                String.format(
                        Locale.forLanguageTag("vi-VN"),
                        "Tháng %02d / %04d  ▾",
                        thang,
                        nam
                )
        );
    }

    private void chonThang() {

        LinearLayout layout = new LinearLayout(this);

        layout.setOrientation(LinearLayout.HORIZONTAL);

        int padding = (int) (16 * getResources()
                .getDisplayMetrics().density);

        layout.setPadding(padding, padding, padding, padding);

        NumberPicker chonThang = new NumberPicker(this);
        chonThang.setMinValue(1);
        chonThang.setMaxValue(12);
        chonThang.setValue(thang);
        chonThang.setWrapSelectorWheel(true);

        NumberPicker chonNam = new NumberPicker(this);
        chonNam.setMinValue(2000);
        chonNam.setMaxValue(2100);
        chonNam.setValue(nam);
        chonNam.setWrapSelectorWheel(false);

        layout.addView(
                chonThang,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        layout.addView(
                chonNam,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        new AlertDialog.Builder(this)
                .setTitle("Chọn tháng và năm")
                .setView(layout)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Chọn", (dialog, which) -> {

                    thang = chonThang.getValue();
                    nam = chonNam.getValue();

                    hienThiThang();
                    taiDuLieuNganSach();
                })
                .show();
    }

    private void taiDuLieuNganSach() {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            thongBao("Vui lòng đăng nhập lại");
            return;
        }

        String uid = nguoiDung.getUid();

        // 1) Hiện ngay từ SQLite
        hienThiTuSQLite(uid);

        // 2) Đồng bộ với Firestore ở nền, xong thì hiện lại.
        // Mất mạng thì bỏ qua, vẫn dùng dữ liệu đang có trên máy.
        int lanTaiHienTai = ++lanTaiDuLieu;

        SyncManager.getInstance(this).syncNow((thanhCong, loi) -> {

            if (isFinishing() || isDestroyed()
                    || lanTaiHienTai != lanTaiDuLieu) {
                return;
            }

            hienThiTuSQLite(uid);
        });
    }

    private void hienThiTuSQLite(String uid) {

        // Ngân sách của tháng đang chọn, kèm số đã chi (tính bằng SUM trong SQL)
        List<Budget> danhSachMoi = budgetRepository.getBudgets(uid, thang, nam);

        long tongNganSach = 0;
        long tongDaChi = 0;

        try {
            for (Budget nganSach : danhSachMoi) {
                tongNganSach = Math.addExact(
                        tongNganSach, nganSach.getSoTienGioiHan()
                );

                tongDaChi = Math.addExact(
                        tongDaChi, nganSach.getSoTienDaChi()
                );
            }
        } catch (ArithmeticException e) {

            thongBao(
                    "Tổng số tiền quá lớn, "
                            + "không thể tính ngân sách"
            );
            return;
        }

        danhSachNganSach.clear();
        danhSachNganSach.addAll(danhSachMoi);

        budgetAdapter.notifyDataSetChanged();

        tvTongNganSach.setText(
                dinhDangTien.format(tongNganSach) + " ₫"
        );

        tvDaChiNganSach.setText(
                dinhDangTien.format(tongDaChi) + " ₫"
        );

        if (danhSachNganSach.isEmpty()) {

            layoutNganSachTrong.setVisibility(View.VISIBLE);
            rvDanhSachNganSach.setVisibility(View.GONE);

        } else {

            layoutNganSachTrong.setVisibility(View.GONE);
            rvDanhSachNganSach.setVisibility(View.VISIBLE);
        }
    }

    private void thongBao(String noiDung) {
        Toast.makeText(
                this,
                noiDung,
                Toast.LENGTH_LONG
        ).show();
    }

    private void hienThiQuanLyNganSach(Budget nganSach) {

        String[] luaChon = {
                "Chỉnh sửa hạn mức",
                "Xóa ngân sách"
        };

        new AlertDialog.Builder(this)
                .setTitle("Quản lý ngân sách " + nganSach.getDanhMuc())
                .setItems(luaChon, (dialog, which) -> {

                    if (which == 0) {
                        hienThiSuaNganSach(nganSach);
                    } else {
                        xacNhanXoaNganSach(nganSach);
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void hienThiSuaNganSach(Budget nganSach) {

        EditText edtGioiHan = new EditText(this);

        edtGioiHan.setInputType(InputType.TYPE_CLASS_NUMBER);
        edtGioiHan.setSingleLine(true);

        edtGioiHan.setText(
                String.valueOf(nganSach.getSoTienGioiHan())
        );

        int padding = (int) (24 * getResources()
                .getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, 12, padding, 0);
        layout.addView(edtGioiHan);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa ngân sách")
                .setMessage("Danh mục: " + nganSach.getDanhMuc())
                .setView(layout)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null)
                .create();

        dialog.setOnShowListener(v -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {

                        String giaTri = edtGioiHan.getText()
                                .toString().trim();

                        long gioiHanMoi;

                        try {
                            gioiHanMoi = Long.parseLong(giaTri);
                        } catch (NumberFormatException e) {
                            edtGioiHan.setError("Số tiền không hợp lệ");
                            return;
                        }

                        if (gioiHanMoi <= 0) {
                            edtGioiHan.setError(
                                    "Giới hạn phải lớn hơn 0"
                            );
                            return;
                        }

                        if (gioiHanMoi == nganSach.getSoTienGioiHan()) {
                            edtGioiHan.setError(
                                    "Giới hạn chưa thay đổi"
                            );
                            return;
                        }

                        FirebaseUser nguoiDung =
                                firebaseAuth.getCurrentUser();

                        if (nguoiDung == null) {
                            thongBao("Vui lòng đăng nhập lại");
                            return;
                        }

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setEnabled(false);

                        budgetRepository.updateLimit(
                                nguoiDung.getUid(),
                                nganSach,
                                gioiHanMoi,
                                (thanhCong, loi) -> {

                                    if (thanhCong) {
                                        dialog.dismiss();
                                        thongBao("Cập nhật ngân sách thành công");
                                        taiDuLieuNganSach();
                                        return;
                                    }

                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                            .setEnabled(true);

                                    thongBao("Không thể cập nhật: " + loi);
                                });
                    });
        });

        dialog.show();
    }

    private void xacNhanXoaNganSach(Budget nganSach) {

        new AlertDialog.Builder(this)
                .setTitle("Xóa ngân sách")
                .setMessage(
                        "Bạn muốn xóa ngân sách "
                                + nganSach.getDanhMuc()
                                + " tháng " + nganSach.getThang()
                                + "/" + nganSach.getNam()
                                + "?\n\n"
                                + "Các giao dịch Chi tiêu sẽ được giữ nguyên."
                )
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) ->
                        xoaNganSach(nganSach))
                .show();
    }

    private void xoaNganSach(Budget nganSach) {

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            thongBao("Vui lòng đăng nhập lại");
            return;
        }

        budgetRepository.deleteBudget(
                nguoiDung.getUid(),
                nganSach,
                (thanhCong, loi) -> {

                    if (thanhCong) {
                        thongBao("Đã xóa ngân sách");
                        taiDuLieuNganSach();
                    } else {
                        thongBao("Không thể xóa: " + loi);
                    }
                });
    }

}