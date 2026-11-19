package com.example.qlchitieu.budget;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.example.qlchitieu.data.repository.BudgetRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.Locale;

public class AddBudgetActivity extends AppCompatActivity {

    TextView tvQuayLaiThemNganSach, tvThangThemNganSach;
    Spinner spDanhMucNganSach;
    EditText edtSoTienNganSach;
    MaterialButton btnLuuNganSach;

    FirebaseAuth firebaseAuth;
    BudgetRepository budgetRepository;

    int thang, nam;
    boolean dangLuu = false;

    String[] danhMuc = {
            "Ăn uống",
            "Mua sắm",
            "Đi lại",
            "Hóa đơn",
            "Giải trí",
            "Học tập",
            "Sức khỏe",
            "Chi tiêu khác"
    };

    String[] maDanhMuc = {
            "an_uong",
            "mua_sam",
            "di_lai",
            "hoa_don",
            "giai_tri",
            "hoc_tap",
            "suc_khoe",
            "chi_tieu_khac"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_budget);

        tvQuayLaiThemNganSach =
                findViewById(R.id.tvQuayLaiThemNganSach);

        tvThangThemNganSach =
                findViewById(R.id.tvThangThemNganSach);

        spDanhMucNganSach =
                findViewById(R.id.spDanhMucNganSach);

        edtSoTienNganSach =
                findViewById(R.id.edtSoTienNganSach);

        btnLuuNganSach =
                findViewById(R.id.btnLuuNganSach);

        firebaseAuth = FirebaseAuth.getInstance();
        budgetRepository = new BudgetRepository(this);

        Calendar lich = Calendar.getInstance();

        thang = getIntent().getIntExtra(
                "thang", lich.get(Calendar.MONTH) + 1
        );

        nam = getIntent().getIntExtra(
                "nam", lich.get(Calendar.YEAR)
        );

        if (thang < 1 || thang > 12 || nam < 2000 || nam > 2100) {
            thang = lich.get(Calendar.MONTH) + 1;
            nam = lich.get(Calendar.YEAR);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                danhMuc
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spDanhMucNganSach.setAdapter(adapter);

        hienThiThang();

        tvQuayLaiThemNganSach.setOnClickListener(v -> finish());

        tvThangThemNganSach.setOnClickListener(v -> chonThang());

        btnLuuNganSach.setOnClickListener(v -> luuNganSach());
    }

    private void hienThiThang() {

        tvThangThemNganSach.setText(
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
        layout.setPadding(32, 24, 32, 16);

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
                new LinearLayout.LayoutParams(0, 180, 1)
        );

        layout.addView(
                chonNam,
                new LinearLayout.LayoutParams(0, 180, 1)
        );

        new AlertDialog.Builder(this)
                .setTitle("Chọn tháng và năm")
                .setView(layout)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Chọn", (dialog, which) -> {

                    thang = chonThang.getValue();
                    nam = chonNam.getValue();

                    hienThiThang();
                })
                .show();
    }

    private void luuNganSach() {

        if (dangLuu) {
            return;
        }

        int viTri = spDanhMucNganSach.getSelectedItemPosition();

        if (viTri < 0 || viTri >= danhMuc.length) {
            thongBao("Vui lòng chọn danh mục");
            return;
        }

        String soTienNhap = edtSoTienNganSach
                .getText().toString().trim();

        if (soTienNhap.isEmpty()) {
            edtSoTienNganSach.setError(
                    "Vui lòng nhập giới hạn ngân sách"
            );
            return;
        }

        long soTienGioiHan;

        try {
            soTienGioiHan = Long.parseLong(soTienNhap);
        } catch (NumberFormatException e) {
            edtSoTienNganSach.setError(
                    "Số tiền không hợp lệ"
            );
            return;
        }

        if (soTienGioiHan <= 0) {
            edtSoTienNganSach.setError(
                    "Giới hạn phải lớn hơn 0"
            );
            return;
        }

        FirebaseUser nguoiDung = firebaseAuth.getCurrentUser();

        if (nguoiDung == null) {
            thongBao("Vui lòng đăng nhập lại");
            return;
        }

        dangLuu = true;
        btnLuuNganSach.setEnabled(false);

        // Lưu SQLite ngay (chạy được khi offline), đẩy lên cloud ở phía sau.
        budgetRepository.addBudget(
                nguoiDung.getUid(),
                danhMuc[viTri],
                maDanhMuc[viTri],
                soTienGioiHan,
                thang,
                nam,
                (thanhCong, loi) -> {

                    if (thanhCong) {
                        thongBao("Thêm ngân sách thành công!");

                        setResult(RESULT_OK);
                        finish();
                        return;
                    }

                    dangLuu = false;
                    btnLuuNganSach.setEnabled(true);

                    thongBao("Không thể lưu ngân sách: " + loi);
                });
    }

    private void thongBao(String noiDung) {
        Toast.makeText(this, noiDung, Toast.LENGTH_LONG).show();
    }
}
