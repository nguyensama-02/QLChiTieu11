package com.example.qlchitieu.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.model.BalanceAdjustment;
import com.example.qlchitieu.R;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class BalanceAdjustmentAdapter extends RecyclerView.Adapter<BalanceAdjustmentAdapter.AdjustmentViewHolder> {

    private final ArrayList<BalanceAdjustment> danhSachDieuChinh;

    private final NumberFormat dinhDangTien =
            NumberFormat.getInstance(new Locale("vi", "VN"));

    private final SimpleDateFormat dinhDangNgay =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));

    public BalanceAdjustmentAdapter(ArrayList<BalanceAdjustment> danhSachDieuChinh) {
        this.danhSachDieuChinh = danhSachDieuChinh;
    }

    @NonNull
    @Override
    public AdjustmentViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_balance_adjustment, parent, false);

        return new AdjustmentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull AdjustmentViewHolder holder, int position) {

        BalanceAdjustment dieuChinh = danhSachDieuChinh.get(position);

        String lyDo = dieuChinh.getLyDo();

        if (lyDo == null || lyDo.trim().isEmpty()) {
            lyDo = "Điều chỉnh số dư";
        }

        holder.tvLyDoItem.setText(lyDo);

        long chenhLech = dieuChinh.getChenhLech();

        String dau = chenhLech > 0 ? "+" : "";

        holder.tvChenhLechItem.setText(
                dau + dinhDangTien.format(chenhLech) + " ₫"
        );

        int mauChu;
        int mauNen;
        int icon;

        if (chenhLech > 0) {
            mauChu = Color.parseColor("#059669");
            mauNen = Color.parseColor("#DCFCE7");
            icon = R.drawable.ic_arrow_up;
        } else if (chenhLech < 0) {
            mauChu = Color.parseColor("#DC2626");
            mauNen = Color.parseColor("#FEE2E2");
            icon = R.drawable.ic_arrow_down;
        } else {
            mauChu = Color.parseColor("#102A4C");
            mauNen = Color.parseColor("#E8EDF5");
            icon = R.drawable.ic_equal;
        }

        holder.tvChenhLechItem.setTextColor(mauChu);
        holder.ivHuongDieuChinh.setImageResource(icon);
        holder.ivHuongDieuChinh.setImageTintList(
                android.content.res.ColorStateList.valueOf(mauChu)
        );
        holder.ivHuongDieuChinh.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(mauNen)
        );

        holder.tvSoDuCuItem.setText(
                dinhDangTien.format(dieuChinh.getSoDuCu()) + " ₫"
        );

        holder.tvSoDuMoiItem.setText(
                dinhDangTien.format(dieuChinh.getSoDuMoi()) + " ₫"
        );

        if (dieuChinh.getNgayDieuChinh() != null) {

            holder.tvNgayDieuChinhItem.setText(
                    dinhDangNgay.format(dieuChinh.getNgayDieuChinh())
            );

        } else {
            holder.tvNgayDieuChinhItem.setText("Chưa có thời gian");
        }
    }

    @Override
    public int getItemCount() {
        return danhSachDieuChinh.size();
    }

    public static class AdjustmentViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvLyDoItem;
        TextView tvChenhLechItem;
        TextView tvNgayDieuChinhItem;
        TextView tvSoDuCuItem;
        TextView tvSoDuMoiItem;
        ImageView ivHuongDieuChinh;

        public AdjustmentViewHolder(@NonNull View itemView) {
            super(itemView);

            tvLyDoItem = itemView.findViewById(R.id.tvLyDoItem);
            tvChenhLechItem = itemView.findViewById(R.id.tvChenhLechItem);
            tvNgayDieuChinhItem = itemView.findViewById(R.id.tvNgayDieuChinhItem);
            tvSoDuCuItem = itemView.findViewById(R.id.tvSoDuCuItem);
            tvSoDuMoiItem = itemView.findViewById(R.id.tvSoDuMoiItem);
            ivHuongDieuChinh = itemView.findViewById(R.id.ivHuongDieuChinh);
        }
    }
}
