package com.example.qlchitieu.model;

import java.util.Date;

public class Transaction {

    private String maGiaoDich;
    private String loaiGiaoDich;
    private String danhMuc;
    private String ghiChu;
    private String maViNguon;
    private String maViDich;
    private long soTien;
    private Date ngayGiaoDich;

    public Transaction() {
    }

    public Transaction(String maGiaoDich, String loaiGiaoDich,
                       String danhMuc, String ghiChu,
                       String maViNguon, String maViDich,
                       long soTien, Date ngayGiaoDich) {

        this.maGiaoDich = maGiaoDich;
        this.loaiGiaoDich = loaiGiaoDich;
        this.danhMuc = danhMuc;
        this.ghiChu = ghiChu;
        this.maViNguon = maViNguon;
        this.maViDich = maViDich;
        this.soTien = soTien;
        this.ngayGiaoDich = ngayGiaoDich;
    }

    public String getMaGiaoDich() {
        return maGiaoDich;
    }

    public String getLoaiGiaoDich() {
        return loaiGiaoDich;
    }

    public String getDanhMuc() {
        return danhMuc;
    }

    public String getGhiChu() {
        return ghiChu;
    }

    public String getMaViNguon() {
        return maViNguon;
    }

    public String getMaViDich() {
        return maViDich;
    }

    public long getSoTien() {
        return soTien;
    }

    public Date getNgayGiaoDich() {
        return ngayGiaoDich;
    }
}
