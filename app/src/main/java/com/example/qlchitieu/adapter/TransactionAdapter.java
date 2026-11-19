package com.example.qlchitieu.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.R;
import com.example.qlchitieu.transaction.TransactionDetailActivity;
import com.example.qlchitieu.model.Transaction;
import android.graphics.drawable.GradientDrawable;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private final ArrayList<Transaction> danhSachGiaoDich;

    public TransactionAdapter(ArrayList<Transaction> danhSachGiaoDich) {
        this.danhSachGiaoDich = danhSachGiaoDich;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);

        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull TransactionViewHolder holder, int position) {

        Transaction giaoDich = danhSachGiaoDich.get(position);

        String loai = giaoDich.getLoaiGiaoDich();
        String danhMuc = giaoDich.getDanhMuc();
        String ghiChu = giaoDich.getGhiChu();
        hienThiBieuTuong(
                holder.tvBieuTuongGiaoDich,
                loai,
                danhMuc
        );
        NumberFormat dinhDangTien = NumberFormat.getInstance(
                new Locale("vi", "VN")
        );

        SimpleDateFormat dinhDangNgay = new SimpleDateFormat(
                "dd/MM/yyyy",
                new Locale("vi", "VN")
        );

        if (ghiChu == null || ghiChu.trim().isEmpty()) {
            ghiChu = "Không có ghi chú";
        }

        holder.tvGhiChuGiaoDich.setText(ghiChu);

        if (giaoDich.getNgayGiaoDich() != null) {
            holder.tvNgayGiaoDichItem.setText(
                    dinhDangNgay.format(giaoDich.getNgayGiaoDich())
            );
        } else {
            holder.tvNgayGiaoDichItem.setText("Chưa có ngày");
        }

        String soTien = dinhDangTien.format(giaoDich.getSoTien()) + " ₫";

        if ("Thu nhập".equals(loai)) {

            holder.tvDanhMucGiaoDich.setText(
                    danhMuc == null ? "Thu nhập" : danhMuc
            );

            holder.tvSoTienGiaoDichItem.setText("+" + soTien);
            holder.tvSoTienGiaoDichItem.setTextColor(
                    Color.parseColor("#059669")
            );

        } else if ("Chi tiêu".equals(loai)) {

            holder.tvDanhMucGiaoDich.setText(
                    danhMuc == null ? "Chi tiêu" : danhMuc
            );

            holder.tvSoTienGiaoDichItem.setText("-" + soTien);
            holder.tvSoTienGiaoDichItem.setTextColor(
                    Color.parseColor("#DC2626")
            );

        } else {

            holder.tvDanhMucGiaoDich.setText("Chuyển tiền");

            holder.tvBieuTuongGiaoDich.setText("⇄");

            holder.tvSoTienGiaoDichItem.setText(soTien);
            holder.tvSoTienGiaoDichItem.setTextColor(
                    Color.parseColor("#0891B2")
            );
        }

        holder.itemView.setOnClickListener(v -> {

            Intent intent = new Intent(
                    v.getContext(),
                    TransactionDetailActivity.class
            );

            intent.putExtra("maGiaoDich", giaoDich.getMaGiaoDich());

            v.getContext().startActivity(intent);
        });

    }

    @Override
    public int getItemCount() {
        return danhSachGiaoDich.size();
    }

    public static class TransactionViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvBieuTuongGiaoDich;
        TextView tvDanhMucGiaoDich;
        TextView tvGhiChuGiaoDich;
        TextView tvNgayGiaoDichItem;
        TextView tvSoTienGiaoDichItem;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);

            tvBieuTuongGiaoDich = itemView.findViewById(
                    R.id.tvBieuTuongGiaoDich
            );

            tvDanhMucGiaoDich = itemView.findViewById(
                    R.id.tvDanhMucGiaoDich
            );

            tvGhiChuGiaoDich = itemView.findViewById(
                    R.id.tvGhiChuGiaoDich
            );

            tvNgayGiaoDichItem = itemView.findViewById(
                    R.id.tvNgayGiaoDichItem
            );

            tvSoTienGiaoDichItem = itemView.findViewById(
                    R.id.tvSoTienGiaoDichItem
            );
        }
    }

    private void hienThiBieuTuong(
            TextView tvBieuTuong,
            String loai,
            String danhMuc) {

        String bieuTuong;
        String mauChu;
        String mauNen;

        if ("Thu nhập".equals(loai)) {

            bieuTuong = "↙";
            mauChu = "#059669";
            mauNen = "#DCFCE7";

            if ("Lương".equalsIgnoreCase(danhMuc)) {
                bieuTuong = "₫";
            } else if ("Thưởng".equalsIgnoreCase(danhMuc)) {
                bieuTuong = "★";
            }

        } else if ("Chi tiêu".equals(loai)) {

            bieuTuong = "↗";
            mauChu = "#DC2626";
            mauNen = "#FEE2E2";

            if (danhMuc != null) {

                switch (danhMuc.trim().toLowerCase(
                        Locale.forLanguageTag("vi-VN"))) {

                    case "ăn uống":
                        bieuTuong = "♨";
                        mauChu = "#D97706";
                        mauNen = "#FEF3C7";
                        break;

                    case "mua sắm":
                        bieuTuong = "▣";
                        mauChu = "#DB2777";
                        mauNen = "#FCE7F3";
                        break;

                    case "đi lại":
                        bieuTuong = "➜";
                        mauChu = "#2563EB";
                        mauNen = "#DBEAFE";
                        break;

                    case "hóa đơn":
                        bieuTuong = "▤";
                        mauChu = "#7C3AED";
                        mauNen = "#F3E8FF";
                        break;

                    case "giải trí":
                        bieuTuong = "♫";
                        mauChu = "#9333EA";
                        mauNen = "#F3E8FF";
                        break;

                    case "học tập":
                        bieuTuong = "✎";
                        mauChu = "#2563EB";
                        mauNen = "#DBEAFE";
                        break;

                    case "sức khỏe":
                        bieuTuong = "+";
                        mauChu = "#DC2626";
                        mauNen = "#FEE2E2";
                        break;

                    default:
                        break;
                }
            }

        } else {

            bieuTuong = "⇄";
            mauChu = "#0891B2";
            mauNen = "#E0F2FE";
        }

        GradientDrawable background = new GradientDrawable();

        background.setColor(Color.parseColor(mauNen));
        background.setCornerRadius(
                12 * tvBieuTuong.getResources()
                        .getDisplayMetrics().density
        );

        tvBieuTuong.setBackground(background);
        tvBieuTuong.setText(bieuTuong);
        tvBieuTuong.setTextColor(Color.parseColor(mauChu));
    }

}
