package com.ziv.demo.r5.utils;

import androidx.lifecycle.MutableLiveData;

public class GlobalLiveData {
    public final MutableLiveData<String> mQrCodeStr = new MutableLiveData<>();


    private GlobalLiveData() {
    }

    private static class SingleHolder {
        public static GlobalLiveData INSTANCE = new GlobalLiveData();
    }

    public static GlobalLiveData getInstance() {
        return SingleHolder.INSTANCE;
    }
}
