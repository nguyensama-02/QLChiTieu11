package com.example.qlchitieu.profile;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.qlchitieu.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.qlchitieu.data.repository.UserProfileRepository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Màn Sửa hồ sơ: đổi tên hiển thị và ảnh đại diện.
 *
 * - Tên chỉ được lưu khi bấm "Lưu thay đổi".
 * - Ảnh có hiệu lực ngay khi chọn xong (hoặc xóa xong), giống đa số app khác.
 *   Ảnh chỉ lưu trên máy này (xem AvatarStore).
 */
public class EditProfileActivity extends AppCompatActivity {

    /** Extra vào: họ tên đang hiển thị. Extra ra (RESULT_OK): họ tên mới. */
    public static final String EXTRA_HO_TEN = "ho_ten";

    private static final String MAU_DO = "#DC2626";
    private static final String MAU_XAM = "#64748B";
    private static final String MAU_XANH = "#059669";

    UserProfileRepository userProfileRepository;

    String uid;
    String tenGoc;

    View flAvatar;
    ImageView ivAvatar;
    TextView tvAvatar, tvTenXemTruoc, tvDoiAnh, tvXoaAnh;
    EditText edtHoTen;
    TextView btnXoaTen, tvGoiYTen, tvDemKyTu, btnLuu;

    boolean dangXuLyAnh = false;

    ActivityResultLauncher<PickVisualMediaRequest> boChonAnh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        FirebaseUser nguoiDung = FirebaseAuth.getInstance().getCurrentUser();

        if (nguoiDung == null) {
            finish();
            return;
        }

        uid = nguoiDung.getUid();
        userProfileRepository = new UserProfileRepository(this);

        flAvatar = findViewById(R.id.flAvatarSua);
        ivAvatar = findViewById(R.id.ivAvatarSua);
        tvAvatar = findViewById(R.id.tvAvatarSua);
        tvTenXemTruoc = findViewById(R.id.tvTenXemTruoc);
        tvDoiAnh = findViewById(R.id.tvDoiAnh);
        tvXoaAnh = findViewById(R.id.tvXoaAnh);
        edtHoTen = findViewById(R.id.edtHoTen);
        btnXoaTen = findViewById(R.id.btnXoaTen);
        tvGoiYTen = findViewById(R.id.tvGoiYTen);
        tvDemKyTu = findViewById(R.id.tvDemKyTu);
        btnLuu = findViewById(R.id.btnLuuHoSo);

        TextView tvEmail = findViewById(R.id.tvEmailChiDoc);
        tvEmail.setText(nguoiDung.getEmail() == null ? "" : nguoiDung.getEmail());

        tenGoc = chuanHoa(layTenBanDau(nguoiDung));

        // Bộ chọn ảnh của hệ thống: không cần xin quyền đọc ảnh
        boChonAnh = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::xuLyAnhDaChon
        );

        flAvatar.setOnClickListener(v -> chonAnh());
        tvDoiAnh.setOnClickListener(v -> chonAnh());
        tvXoaAnh.setOnClickListener(v -> hoiXoaAnh());

        edtHoTen.setText(tenGoc);
        edtHoTen.setSelection(edtHoTen.getText().length());

        edtHoTen.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                capNhatGiaoDien();
            }
        });

        edtHoTen.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (hopLe()) {
                    luuHoSo();
                }
                return true;
            }
            return false;
        });

        btnXoaTen.setOnClickListener(v -> {
            edtHoTen.setText("");
            edtHoTen.requestFocus();
        });

        btnLuu.setOnClickListener(v -> luuHoSo());

        findViewById(R.id.tvQuayLaiSua).setOnClickListener(v -> thoat());
        findViewById(R.id.tvHuySua).setOnClickListener(v -> thoat());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                thoat();
            }
        });

        hienAvatar();
        capNhatGiaoDien();
    }

    // ------------------------------------------------------------------
    //  Tên
    // ------------------------------------------------------------------

    private String layTenBanDau(FirebaseUser nguoiDung) {

        String tuMan = getIntent().getStringExtra(EXTRA_HO_TEN);

        if (tuMan != null && !tuMan.trim().isEmpty()) {
            return tuMan;
        }

        String daLuu = userProfileRepository.getHoTen(uid);

        if (daLuu != null && !daLuu.trim().isEmpty()) {
            return daLuu;
        }

        String ten = nguoiDung.getDisplayName();

        if (ten != null && !ten.trim().isEmpty()) {
            return ten;
        }

        String email = nguoiDung.getEmail();

        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }

        return "";
    }

    private String chuanHoa(String ten) {
        return ten == null ? "" : ten.trim().replaceAll("\\s+", " ");
    }

    private String tenDangNhap() {
        return chuanHoa(edtHoTen.getText().toString());
    }

    private boolean hopLe() {
        String v = tenDangNhap();
        return v.length() >= 2 && !v.equals(tenGoc);
    }

    private boolean daSuaTen() {
        return !tenDangNhap().equals(tenGoc);
    }

    /** Cập nhật bộ đếm, gợi ý, tên xem trước, chữ cái avatar và trạng thái nút Lưu. */
    private void capNhatGiaoDien() {

        String tho = edtHoTen.getText().toString();
        String v = chuanHoa(tho);

        tvDemKyTu.setText(tho.length() + "/50");
        btnXoaTen.setVisibility(tho.isEmpty() ? View.INVISIBLE : View.VISIBLE);

        tvTenXemTruoc.setText(v.isEmpty() ? "Tên của bạn" : v);
        tvAvatar.setText(chuCaiDau(v));

        if (tho.isEmpty()) {
            datGoiY("Nhập tên của bạn.", MAU_DO);
        } else if (v.length() < 2) {
            datGoiY("Tên cần ít nhất 2 ký tự.", MAU_DO);
        } else if (v.equals(tenGoc)) {
            datGoiY("Bạn chưa thay đổi tên.", MAU_XAM);
        } else {
            datGoiY("Sẵn sàng lưu.", MAU_XANH);
        }

        boolean choLuu = hopLe();
        btnLuu.setEnabled(choLuu);
        btnLuu.setAlpha(choLuu ? 1f : 0.45f);
    }

    private void datGoiY(String noiDung, String mau) {
        tvGoiYTen.setText(noiDung);
        tvGoiYTen.setTextColor(Color.parseColor(mau));
    }

    /** Chữ cái đầu của từ cuối (tên riêng), ví dụ "Nguyễn Văn An" -> "A". */
    private String chuCaiDau(String hoTen) {

        if (hoTen.isEmpty()) {
            return "?";
        }

        String[] tu = hoTen.split(" ");
        String tuCuoi = tu[tu.length - 1];

        if (tuCuoi.isEmpty()) {
            return "?";
        }

        return tuCuoi.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private void luuHoSo() {

        if (!hopLe()) {
            return;
        }

        String tenMoi = tenDangNhap();

        // Lưu vào SQLite trước, không chờ mạng.
        // SyncManager sẽ đưa lên Firestore khi có mạng.
        userProfileRepository.luuHoTen(uid, tenMoi);

        setResult(RESULT_OK, new Intent().putExtra(EXTRA_HO_TEN, tenMoi));

        Toast.makeText(this, "Đã lưu hồ sơ", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void thoat() {

        if (!daSuaTen()) {
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Bỏ thay đổi?")
                .setMessage("Tên bạn vừa sửa chưa được lưu.")
                .setNegativeButton("Ở lại", null)
                .setPositiveButton("Bỏ thay đổi", (dialog, which) -> finish())
                .show();
    }

    // ------------------------------------------------------------------
    //  Ảnh đại diện
    // ------------------------------------------------------------------

    private void hienAvatar() {

        Bitmap anh = AvatarStore.tai(this, uid);

        if (anh != null) {
            ivAvatar.setImageBitmap(anh);
            ivAvatar.setVisibility(View.VISIBLE);
            tvAvatar.setVisibility(View.INVISIBLE);
            tvXoaAnh.setVisibility(View.VISIBLE);
        } else {
            ivAvatar.setImageDrawable(null);
            ivAvatar.setVisibility(View.GONE);
            tvAvatar.setVisibility(View.VISIBLE);
            tvXoaAnh.setVisibility(View.GONE);
        }
    }

    private void chonAnh() {

        if (dangXuLyAnh) {
            return;
        }

        boChonAnh.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void xuLyAnhDaChon(Uri uri) {

        // Người dùng thoát bộ chọn mà không chọn ảnh nào
        if (uri == null) {
            return;
        }

        dangXuLyAnh = true;
        flAvatar.setAlpha(0.5f);

        AvatarStore.luuBatDongBo(this, uid, uri, thanhCong -> {

            dangXuLyAnh = false;

            if (isFinishing() || isDestroyed()) {
                return;
            }

            flAvatar.setAlpha(1f);

            if (thanhCong) {
                hienAvatar();
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_HO_TEN, tenGoc));
                Toast.makeText(this, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Không đọc được ảnh này, hãy thử ảnh khác",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void hoiXoaAnh() {

        new AlertDialog.Builder(this)
                .setTitle("Xóa ảnh đại diện?")
                .setMessage("Avatar sẽ quay về chữ cái đầu của tên.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa ảnh", (dialog, which) -> {
                    AvatarStore.xoa(this, uid);
                    hienAvatar();
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_HO_TEN, tenGoc));
                })
                .show();
    }
}