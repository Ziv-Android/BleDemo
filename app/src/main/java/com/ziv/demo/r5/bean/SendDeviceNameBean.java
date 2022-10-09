package com.ziv.demo.r5.bean;

import com.google.gson.annotations.SerializedName;

public class SendDeviceNameBean {
    @SerializedName("name")
    String name;

    public SendDeviceNameBean(String name) {
        this.name = name;
    }
}
