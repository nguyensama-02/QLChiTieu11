package com.example.qlchitieu.model;

public class Wallet {

    private String maVi;
    private String tenVi;
    private String loaiVi;
    private long soDu;
    private String trangThai;

    public Wallet() {
    }

    public Wallet(String maVi, String tenVi, String loaiVi, long soDu) {
        this.maVi = maVi;
        this.tenVi = tenVi;
        this.loaiVi = loaiVi;
        this.soDu = soDu;
    }

    public String getMaVi() {
        return maVi;
    }

    public void setMaVi(String maVi) {
        this.maVi = maVi;
    }

    public String getTenVi() {
        return tenVi;
    }

    public void setTenVi(String tenVi) {
        this.tenVi = tenVi;
    }

    public String getLoaiVi() {
        return loaiVi;
    }

    public void setLoaiVi(String loaiVi) {
        this.loaiVi = loaiVi;
    }

    public long getSoDu() {
        return soDu;
    }

    public void setSoDu(long soDu) {
        this.soDu = soDu;
    }

    public String getTrangThai() {
        return trangThai == null ? "HOAT_DONG" : trangThai;
    }

    public void setTrangThai(String trangThai) {
        this.trangThai = trangThai;
    }

}
