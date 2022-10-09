package com.ziv.demo.r5.bean;

import com.google.gson.annotations.SerializedName;

public class SendGateOpenBean {
    @SerializedName("ioout")
    int ioout;

    public SendGateOpenBean(int open) {
        ioout = open;
    }
}
