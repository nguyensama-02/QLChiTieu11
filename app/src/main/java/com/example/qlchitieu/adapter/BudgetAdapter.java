package com.example.qlchitieu.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.R;
import com.example.qlchitieu.model.Budget;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class BudgetAdapter
        extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    private final ArrayList<Budget> danhSachNganSach;

    private final NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    public BudgetAdapter(ArrayList<Budget> danhSachNganSach) {
        this.danhSachNganSach = danhSachNganSach;
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_budget, parent, false);

        return new BudgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull BudgetViewHolder holder,
            int position) {

        Budget nganSach = danhSachNganSach.get(position);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBudgetClick(nganSach);
            }
        });

        String danhMuc = nganSach.getDanhMuc();

        if (danhMuc == null || danhMuc.isEmpty()) {
            danhMuc = "Danh mục khác";
        }

        holder.tvDanhMucItemNganSach.setText(danhMuc);

        long gioiHan = nganSach.getSoTienGioiHan();
        long daChi = nganSach.getSoTienDaChi();

        holder.tvDaChiItemNganSach.setText(
                "Đã chi: " + dinhDangTien.format(daChi) + " ₫"
        );

        holder.tvGioiHanItemNganSach.setText(
                "Giới hạn: " + dinhDangTien.format(gioiHan) + " ₫"
        );

        double phanTram = nganSach.getPhanTramSuDung();

        if (!Double.isFinite(phanTram) || phanTram < 0) {
            phanTram = 0;
        }

        holder.tvPhanTramItemNganSach.setText(
                String.format(
                        new Locale("vi", "VN"),
                        "%.1f%%",
                        phanTram
                )
        );

        int tienDo = (int) Math.min(100, phanTram);

        holder.progressNganSach.setProgress(tienDo);

        int mauTrangThai;
        String trangThai;

        if (phanTram < 80) {

            mauTrangThai = Color.parseColor("#059669");
            trangThai = "Đang trong ngân sách";

        } else if (phanTram < 100) {

            mauTrangThai = Color.parseColor("#D97706");
            trangThai = "Sắp đạt giới hạn ngân sách";

        } else if (phanTram == 100) {

            mauTrangThai = Color.parseColor("#DC2626");
            trangThai = "Đã đạt giới hạn ngân sách";

        } else {

            mauTrangThai = Color.parseColor("#DC2626");

            long vuotNganSach = daChi - gioiHan;

            trangThai = "Đã vượt ngân sách "
                    + dinhDangTien.format(vuotNganSach) + " ₫";
        }

        holder.tvTrangThaiItemNganSach.setText(trangThai);

        holder.tvTrangThaiItemNganSach.setTextColor(
                mauTrangThai
        );

        holder.tvPhanTramItemNganSach.setTextColor(
                mauTrangThai
        );

        holder.progressNganSach.setProgressTintList(
                ColorStateList.valueOf(mauTrangThai)
        );
    }

    @Override
    public int getItemCount() {
        return danhSachNganSach.size();
    }

    public static class BudgetViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvDanhMucItemNganSach;
        TextView tvPhanTramItemNganSach;
        TextView tvDaChiItemNganSach;
        TextView tvGioiHanItemNganSach;
        TextView tvTrangThaiItemNganSach;

        ProgressBar progressNganSach;

        public BudgetViewHolder(@NonNull View itemView) {
            super(itemView);

            tvDanhMucItemNganSach = itemView.findViewById(
                    R.id.tvDanhMucItemNganSach
            );

            tvPhanTramItemNganSach = itemView.findViewById(
                    R.id.tvPhanTramItemNganSach
            );

            tvDaChiItemNganSach = itemView.findViewById(
                    R.id.tvDaChiItemNganSach
            );

            tvGioiHanItemNganSach = itemView.findViewById(
                    R.id.tvGioiHanItemNganSach
            );

            tvTrangThaiItemNganSach = itemView.findViewById(
                    R.id.tvTrangThaiItemNganSach
            );

            progressNganSach = itemView.findViewById(
                    R.id.progressNganSach
            );
        }
    }

    public interface OnBudgetClickListener {
        void onBudgetClick(Budget nganSach);
    }

    private OnBudgetClickListener listener;

    public void setOnBudgetClickListener(
            OnBudgetClickListener listener) {
        this.listener = listener;
    }

}
