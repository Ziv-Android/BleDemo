package com.ziv.demo.r5.bean;

import com.google.gson.annotations.SerializedName;

public class ReceiveBodyBean {
    @SerializedName("plate")
    public String plate;
    @SerializedName("time")
    public String time;
    @SerializedName("money")
    public String money;

    @Override
    public String toString() {
        return "ReceiveParkInfoBean{" +
                "plate='" + plate + '\'' +
                ", time='" + time + '\'' +
                ", money='" + money + '\'' +
                '}';
    }
}
