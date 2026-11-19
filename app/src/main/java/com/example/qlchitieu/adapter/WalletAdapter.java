package com.example.qlchitieu.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qlchitieu.R;
import com.example.qlchitieu.model.Wallet;
import com.example.qlchitieu.wallet.WalletDetailActivity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.WalletViewHolder> {

    private ArrayList<Wallet> danhSachVi;

    public WalletAdapter(ArrayList<Wallet> danhSachVi) {
        this.danhSachVi = danhSachVi;
    }

    @NonNull
    @Override
    public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wallet, parent, false);

        return new WalletViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {

        Wallet vi = danhSachVi.get(position);
        holder.itemView.setOnClickListener(v -> {

            Intent intent = new Intent(
                    v.getContext(),
                    WalletDetailActivity.class
            );

            intent.putExtra("maVi", vi.getMaVi());

            v.getContext().startActivity(intent);
        });

        holder.tvTenViItem.setText(vi.getTenVi());
        holder.tvLoaiViItem.setText(vi.getLoaiVi());

        NumberFormat dinhDang = NumberFormat.getInstance(
                new Locale("vi", "VN")
        );

        holder.tvSoDuViItem.setText(
                dinhDang.format(vi.getSoDu()) + " ₫"
        );

        if (vi.getLoaiVi().equals("Tiền mặt")) {
            holder.tvBieuTuongVi.setText("₫");
        } else if (vi.getLoaiVi().equals("Ngân hàng")) {
            holder.tvBieuTuongVi.setText("▣");
        } else {
            holder.tvBieuTuongVi.setText("◈");
        }
    }

    @Override
    public int getItemCount() {
        return danhSachVi.size();
    }

    public static class WalletViewHolder extends RecyclerView.ViewHolder {

        TextView tvTenViItem, tvLoaiViItem, tvSoDuViItem, tvBieuTuongVi;

        public WalletViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTenViItem = itemView.findViewById(R.id.tvTenViItem);
            tvLoaiViItem = itemView.findViewById(R.id.tvLoaiViItem);
            tvSoDuViItem = itemView.findViewById(R.id.tvSoDuViItem);
            tvBieuTuongVi = itemView.findViewById(R.id.tvBieuTuongVi);
        }
    }
}
