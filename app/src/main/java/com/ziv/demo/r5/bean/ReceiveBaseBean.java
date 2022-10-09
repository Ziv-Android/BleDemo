package com.ziv.demo.r5.bean;

import com.google.gson.annotations.SerializedName;

public class ReceiveBaseBean<T> {
    @SerializedName("cmd")
    public int cmd;
    @SerializedName("state")
    public String state;
    @SerializedName("body")
    public T body;
}
