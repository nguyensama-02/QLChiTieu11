package com.example.qlchitieu.model;

import java.util.Date;

public class BalanceAdjustment {

    private String maDieuChinh;
    private String maVi;
    private String lyDo;

    private long soDuCu;
    private long soDuMoi;
    private long chenhLech;

    private Date ngayDieuChinh;

    public BalanceAdjustment() {
    }

    public BalanceAdjustment(String maDieuChinh, String maVi,
                             String lyDo, long soDuCu,
                             long soDuMoi, long chenhLech,
                             Date ngayDieuChinh) {

        this.maDieuChinh = maDieuChinh;
        this.maVi = maVi;
        this.lyDo = lyDo;
        this.soDuCu = soDuCu;
        this.soDuMoi = soDuMoi;
        this.chenhLech = chenhLech;
        this.ngayDieuChinh = ngayDieuChinh;
    }

    public String getMaDieuChinh() {
        return maDieuChinh;
    }

    public void setMaDieuChinh(String maDieuChinh) {
        this.maDieuChinh = maDieuChinh;
    }

    public String getMaVi() {
        return maVi;
    }

    public void setMaVi(String maVi) {
        this.maVi = maVi;
    }

    public String getLyDo() {
        return lyDo;
    }

    public void setLyDo(String lyDo) {
        this.lyDo = lyDo;
    }

    public long getSoDuCu() {
        return soDuCu;
    }

    public void setSoDuCu(long soDuCu) {
        this.soDuCu = soDuCu;
    }

    public long getSoDuMoi() {
        return soDuMoi;
    }

    public void setSoDuMoi(long soDuMoi) {
        this.soDuMoi = soDuMoi;
    }

    public long getChenhLech() {
        return chenhLech;
    }

    public void setChenhLech(long chenhLech) {
        this.chenhLech = chenhLech;
    }

    public Date getNgayDieuChinh() {
        return ngayDieuChinh;
    }

    public void setNgayDieuChinh(Date ngayDieuChinh) {
        this.ngayDieuChinh = ngayDieuChinh;
    }
}
