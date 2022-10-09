package com.ziv.demo.r5.bean;

import com.google.gson.annotations.SerializedName;

public class SendBaseBean<T> {
    @SerializedName("cmd")
    int cmd;
    @SerializedName("body")
    T body;

    public SendBaseBean(int cmd, T body){
        this.cmd = cmd;
        this.body = body;
    }
}
