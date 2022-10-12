package com.ziv.demo.r5;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ziv.demo.r5.bean.ReceiveBaseBean;
import com.ziv.demo.r5.bean.ReceiveBodyBaseBean;
import com.ziv.demo.r5.bean.ReceiveParkInfoBean;
import com.ziv.demo.r5.bean.SendBaseBean;
import com.ziv.demo.r5.bean.SendDeviceNameBean;
import com.ziv.demo.r5.bean.SendGateOpenBean;
import com.ziv.demo.r5.bean.SendHeartBeatBean;
import com.ziv.demo.r5.bean.SendParkInfoBean;
import com.ziv.demo.r5.ble.BluetoothServer;
import com.ziv.demo.r5.ble.OnBleConnectListener;
import com.ziv.demo.r5.utils.GlobalLiveData;
import com.ziv.demo.r5.utils.TypeConversion;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, EasyPermissions.PermissionCallbacks {
    private static final String TAG = "MainActivity";

    private static final int MSG_SHOW_IN_TEST_VIEW = 0x00;
    private static final int MSG_CONNECT_SUCCESS = 0x01;
    private static final int MSG_CONNECT_WAIT_DATA_SUCCESS = 0x02;
    private static final int MSG_DISCONNECT_SUCCESS = 0x03;
    private static final int MSG_GET_PARK_INFO = 0x04;
    private static final int MSG_GATE_OPEN_SUCCESS = 0x05;
    private static final int MSG_GATE_OPEN_FAILED = 0x06;
    private static final int MSG_SCANNING_START = 0x07;
    private static final int MSG_SCANNING_STOP = 0x08;

    private static final int MSG_SEND_HEART_BEAT = 0x11;
    private static final int MSG_RECEIVE_HEART_BEAT = 0x12;
    private static final int MSG_SCANNING_TIME_OUT = 0x13;

    private static final int DELAY_SCANNING_TIME_OUT = 15000;
    private static final int DELAY_SEND_HEART_BEAT_TIME = 5000;
    private static final int DELAY_RECEIVE_HEART_BEAT_TIME = DELAY_SEND_HEART_BEAT_TIME * 3;

    private static final String STATE_OPERATE_OK = "ok";

    private static final int REQUEST_PERMISSION = 10000;
    private static String[] perms;
    private static final String[] perms_location = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
    private static final String[] perms_bluetooth = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN};
    private static final String[] perms_bluetooth_api31 = {Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN};

    private TextView mScanResultView;
    private TextView mConnectResultBtn;
    private TextView mConnectResultView;
    private TextView mConnectResultHintView;
    private TextView mScanQrCodeBtn;

    private TextView mParkLicenseView;
    private TextView mParkTimeView;
    private TextView mParkMoneyView;
    private View mParkMoreInfoBtn;
    private TextView mParkInfoBtn;

    private TextView mGateOpenStateView;
    private TextView mGateOpenBtn;

    private View mMaskGetInfo;
    private View mMaskGateOpen;
    private View mScanning;
    private TextView mScanningTipsView;

    private TextView mTestShowMsgView;

    private HandlerThread mHandlerThread;
    private Handler mWorkHandler;
    private UiHandler mUiHandler;

    private ActivityResultLauncher<Intent> mActivityResultLauncher;
    private final Gson mGson = new Gson();
    private ReceiveParkInfoBean mReceiveParkInfoBean;
    private boolean mSendDeviceNameState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTestShowMsgView = findViewById(R.id.test_show_msg);
        mTestShowMsgView.setVisibility(View.GONE);

        // initView
        mScanResultView = findViewById(R.id.result_device_sn);
        mConnectResultHintView = findViewById(R.id.device_state_hint);
        mConnectResultView = findViewById(R.id.result_device_connect_state);
        mConnectResultBtn = findViewById(R.id.btn_stop_connect);
        mConnectResultBtn.setOnClickListener(this);
        mScanQrCodeBtn = findViewById(R.id.btn_scan_qr_code);
        mScanQrCodeBtn.setOnClickListener(this);

        mParkLicenseView = findViewById(R.id.result_park_license);
        mParkTimeView = findViewById(R.id.result_park_time);
        mParkMoneyView = findViewById(R.id.result_park_money);
        mParkMoreInfoBtn = findViewById(R.id.btn_park_more_info);
        mParkMoreInfoBtn.setOnClickListener(this);
        mParkInfoBtn = findViewById(R.id.btn_get_park_info);
        mParkInfoBtn.setOnClickListener(this);

        mGateOpenStateView = findViewById(R.id.result_gate_open);
        mGateOpenBtn = findViewById(R.id.btn_gate_open);
        mGateOpenBtn.setOnClickListener(this);

        mMaskGetInfo = findViewById(R.id.mask_get_info);
        mMaskGateOpen = findViewById(R.id.mask_gate_open);

        mScanning = findViewById(R.id.scanning);
        mScanningTipsView = findViewById(R.id.scanning_tips);

        mUiHandler = new UiHandler(this);
        mHandlerThread = new HandlerThread("thread-work-handler");
        mHandlerThread.start();
        mWorkHandler = new WorkHandler(this, mHandlerThread.getLooper());

        // 检查是否支持BLE蓝牙
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, getResources().getText(R.string.failed_not_support_ble), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intent = result.getData();
                    // Handle the Intent
                }
            }
        });

        checkBluetoothOpen();

        BluetoothServer.getInstance().init(this, onBleConnectListener);
        // bind data
        GlobalLiveData.getInstance().mQrCodeStr.observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                Log.d(TAG, "QrCode:" + s);
                BluetoothServer.TARGET_BLE_MAC = s;

                startBleScanBtn();
            }
        });

//        jsonParse();
    }

    private boolean checkBluetoothOpen() {
        perms = createCheckPermission();
        // check bluetooth
        if (!checkPermission() || !EasyPermissions.hasPermissions(this, perms)) {
            // request permission
            EasyPermissions.requestPermissions(this, getResources().getText(R.string.request_ble_perms).toString(), REQUEST_PERMISSION, perms);
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // 询问打开蓝牙
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (mActivityResultLauncher != null) {
                mActivityResultLauncher.launch(enableBtIntent);
            }
            return false;
        } else {
            return true;
        }
    }

    private void jsonParse() {
        ReceiveBaseBean<ReceiveBodyBaseBean> bean_1 = new ReceiveBaseBean<>();
        bean_1.state = "ok";
        String s_1 = mGson.toJson(bean_1);
        Log.d(TAG, "toJson-1: " + s_1);

        ReceiveBaseBean<ReceiveBodyBaseBean> o_1 = mGson.fromJson(s_1, new TypeToken<ReceiveBaseBean<ReceiveBodyBaseBean>>() {
        }.getType());

        Log.d(TAG, "fromJson-1: " + o_1.cmd + ", state: " + o_1.state);

        mTestShowMsgView.setText(s_1);

        ReceiveBaseBean<ReceiveParkInfoBean> bean_2 = new ReceiveBaseBean<>();
        bean_2.state = "ok";
        bean_2.body = new ReceiveParkInfoBean();
        bean_2.body.plate = "测A1223456";
        bean_2.body.time = "2099-10-10 10:10:10";
        bean_2.body.money = "99999.0元";
        String s_2 = mGson.toJson(bean_2);
        Log.d(TAG, "toJson-2: " + s_2);

        ReceiveBaseBean<ReceiveParkInfoBean> o_2 = mGson.fromJson(s_2, new TypeToken<ReceiveBaseBean<ReceiveParkInfoBean>>() {
        }.getType());

        Log.d(TAG, "fromJson-2: " + o_2.cmd + ", state: " + o_2.state + o_2.body);

        mTestShowMsgView.setText(s_2);

        byte[] bytes = "VZ".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            Log.d(TAG, "bytes: " + i + " -> " + bytes[i]);
        }

        byte[] dataLength = TypeConversion.int2Bytes(2550);
        for (int i = 0; i < dataLength.length; i++) {
            Log.d(TAG, "bytes: " + i + " -> " + dataLength[i]);
        }
    }

    private String[] createCheckPermission() {
        ArrayList<String> permsList = new ArrayList<>();
        permsList.addAll(Arrays.asList(perms_location));
        permsList.addAll(Arrays.asList(perms_bluetooth));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permsList.addAll(Arrays.asList(perms_bluetooth_api31));
        }
        return permsList.toArray(new String[0]);
    }

    private boolean checkPermission() {
        for (String permission : perms) {
            int checkSelfPermission = ActivityCompat.checkSelfPermission(this, permission);
            Log.d(TAG, "checkSelfPermission: " + permission + " -> " + checkSelfPermission);
            if (checkSelfPermission != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private final OnBleConnectListener onBleConnectListener = new OnBleConnectListener() {
        @Override
        public void connectState(String state) {
            Log.d(TAG, "connectState: " + state);
        }

        @Override
        public void onConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice) {
//            mConnectCurrentMacAddress = bluetoothDevice.getAddress();
            sendUiHandlerMsg(MSG_CONNECT_SUCCESS, null);
        }

        @Override
        public void onConnectFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status) {

        }

        @Override
        public void onConnecting(BluetoothGatt bluetoothGatt) {

        }

        @Override
        public void onDisConnecting(BluetoothGatt bluetoothGatt) {

        }

        @Override
        public void onDisConnectSuccess(BluetoothDevice bluetoothDevice) {
            sendUiHandlerMsg(MSG_DISCONNECT_SUCCESS, null);
        }

        @Override
        public void onServiceDiscoverySucceed(BluetoothDevice bluetoothDevice) {

        }

        @Override
        public void onServiceDiscoveryFailed(BluetoothGatt bluetoothGatt, int state) {

        }

        @Override
        public void onReceiveMessage(String msg) {
            Log.d(TAG, "onReceiveMessage: " + msg);
            sendUiHandlerMsg(MSG_SHOW_IN_TEST_VIEW, msg);
            processReceive(msg);
        }

        @Override
        public void onReceiveError(String errorMsg) {

        }

        @Override
        public void onWriteSuccess(BluetoothGatt gatt, byte[] msg) {

        }

        @Override
        public void onWriteFailure(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        }
    };

    private void processReceive(String msg) {
        try {
            TypeToken<ReceiveBaseBean<ReceiveBodyBaseBean>> typeToken = new TypeToken<ReceiveBaseBean<ReceiveBodyBaseBean>>() {
            };
            ReceiveBaseBean<ReceiveParkInfoBean> receiveBean = mGson.fromJson(msg, typeToken.getType());

            // 解析json/text,根据类型决定是开始发送心跳还是数据解析显示
            switch (receiveBean.cmd) {
                case 1:
                    Log.d(TAG, "Receive 开闸 -> cmd: 1, state: " + receiveBean.state);
                    if (STATE_OPERATE_OK.equals(receiveBean.state)) {
                        sendUiHandlerMsg(MSG_GATE_OPEN_SUCCESS, null);
                    } else {
                        sendUiHandlerMsg(MSG_GATE_OPEN_FAILED, null);
                    }
                    break;
                case 2:
                    Log.d(TAG, "Receive 获取数据 -> cmd: 2, state: " + receiveBean.state);
                    if (STATE_OPERATE_OK.equals(receiveBean.state)) {
                        mReceiveParkInfoBean = receiveBean.body;
                        sendUiHandlerMsg(MSG_GET_PARK_INFO, null);
                    }
                    break;
                case 3:
                    Log.d(TAG, "Receive 设备名称 -> cmd: 3, state: " + receiveBean.state);
                    break;
                case 4:
                    Log.d(TAG, "Receive 心跳 -> cmd: 4, state: " + receiveBean.state);
                    sendUiHandlerMsg(MSG_CONNECT_WAIT_DATA_SUCCESS, null);
                    sendWorkHandlerMsg(MSG_RECEIVE_HEART_BEAT, null, DELAY_RECEIVE_HEART_BEAT_TIME);
                    sendDeviceName();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.d(TAG, "msg is not json: " + msg);
        }
    }

    private void sendHeartBeat() {
        SendHeartBeatBean heartBeatBean = new SendHeartBeatBean();
        SendBaseBean<SendHeartBeatBean> msgHeartBeat = new SendBaseBean<>(4, heartBeatBean);
        BluetoothServer.getInstance().sendBleMessage(mGson.toJson(msgHeartBeat));
    }

    private void sendDeviceName() {
        if (!mSendDeviceNameState) {
            // 借用心跳数据发送设备信息，但设备名称信息只需要发送一次
            SendDeviceNameBean deviceNameBean = new SendDeviceNameBean(BluetoothServer.getInstance().getBleName());
            SendBaseBean<SendDeviceNameBean> msg = new SendBaseBean<>(3, deviceNameBean);
            BluetoothServer.getInstance().sendBleMessage(mGson.toJson(msg));
            mSendDeviceNameState = true;
        }

        // 触发心跳消息
        sendWorkHandlerMsg(MSG_SEND_HEART_BEAT, null, DELAY_SEND_HEART_BEAT_TIME);
    }

    private void sendGetParkInfo() {
        if (mMaskGetInfo.getVisibility() != View.VISIBLE) {
            SendParkInfoBean parkInfoBean = new SendParkInfoBean();
            SendBaseBean<SendParkInfoBean> msgPark = new SendBaseBean<>(2, parkInfoBean);
            BluetoothServer.getInstance().sendBleMessage(mGson.toJson(msgPark));

            sendWorkHandlerMsg(MSG_SEND_HEART_BEAT, null, DELAY_SEND_HEART_BEAT_TIME);
        }
    }

    private void sendOpenGate() {
        if (mMaskGateOpen.getVisibility() != View.VISIBLE) {
            SendGateOpenBean gateOpenBean = new SendGateOpenBean(0);
            SendBaseBean<SendGateOpenBean> msgGate = new SendBaseBean<>(1, gateOpenBean);
            BluetoothServer.getInstance().sendBleMessage(mGson.toJson(msgGate));

            sendWorkHandlerMsg(MSG_SEND_HEART_BEAT, null, DELAY_SEND_HEART_BEAT_TIME);
        }
    }

    private void startBleScanBtn() {
        stopBleConnectBtn();

        sendUiHandlerMsg(MSG_SCANNING_START, null);
        sendWorkHandlerMsg(MSG_SCANNING_TIME_OUT, null, DELAY_SCANNING_TIME_OUT);
        BluetoothServer.getInstance().startBleScan();
    }

    private void stopBleConnectBtn() {
        mSendDeviceNameState = false;
        clearAllWorkHandlerMsg();

        BluetoothServer.getInstance().stopBleConnect();
        sendUiHandlerMsg(MSG_DISCONNECT_SUCCESS, null);
    }

    private void sendUiHandlerMsg(int what, Object obj) {
        if (mUiHandler == null) {
            return;
        }
        sendHandlerMsg(mUiHandler, what, obj, 0);
    }

    private void sendWorkHandlerMsg(int what, Object obj, int delayTime) {
        if (mWorkHandler == null) {
            return;
        }
        sendHandlerMsg(mWorkHandler, what, obj, delayTime);
    }

    private void sendHandlerMsg(Handler handler, int what, Object obj, int delayTime) {
        if (handler == null) {
            return;
        }
        clearHandlerMsg(handler, what);
        Message message = handler.obtainMessage(what);
        message.obj = obj;
        handler.sendMessageDelayed(message, delayTime);
    }

    private void clearAllUiHandlerMsg() {
        if (mUiHandler == null) {
            return;
        }
        mUiHandler.removeCallbacksAndMessages(null);
    }

    private void clearAllWorkHandlerMsg() {
        if (mWorkHandler == null) {
            return;
        }
        mWorkHandler.removeCallbacksAndMessages(null);
    }

    private void clearHandlerMsg(Handler handler, int what) {
        if (handler == null) {
            return;
        }
        if (handler.hasMessages(what)) {
            handler.removeMessages(what);
        }
    }

    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        checkConnecting();
    }

    private void checkConnecting() {
        //Todo 检查蓝牙连接状态
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        clearAllWorkHandlerMsg();
        mWorkHandler = null;
        clearAllUiHandlerMsg();
        mUiHandler = null;
        BluetoothServer.getInstance().stopBleScan();
        BluetoothServer.getInstance().stopBleConnect();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        if (checkBluetoothOpen()) {
            switch (view.getId()) {
                case R.id.btn_scan_qr_code:
                    startActivity(new Intent(this, ScanQrCodeActivity.class));
//                    BluetoothServer.TARGET_BLE_MAC = BluetoothServer.TEXT_BLE_MAC;
//                    startBleScanBtn();
                    break;
                case R.id.btn_stop_connect:
                    stopBleConnectBtn();
                    break;
                case R.id.btn_get_park_info:
                    sendGetParkInfo();
                    break;
                case R.id.btn_park_more_info:
                    startActivity(new Intent(this, MoreActivity.class));
                    break;
                case R.id.btn_gate_open:
                    sendOpenGate();
                    break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "permission granted:" + perms);
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        Log.d(TAG, "permission denied:" + perms);
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this).build().show();
        }
    }

    private void updateUi(Message msg) {
        switch (msg.what) {
            case MSG_CONNECT_SUCCESS:
                mScanningTipsView.setText(getResources().getText(R.string.connected_wait_data_message));
                break;
            case MSG_CONNECT_WAIT_DATA_SUCCESS:
                mScanResultView.setText(BluetoothServer.TARGET_BLE_MAC);

                mConnectResultBtn.setVisibility(View.VISIBLE);
                mConnectResultBtn.setText(getResources().getString(R.string.device_stop_connect));
                mConnectResultView.setVisibility(View.VISIBLE);
                mConnectResultView.setText(getResources().getText(R.string.device_connected));
                mConnectResultView.setTextColor(getResources().getColor(R.color.color_ok));
                mConnectResultHintView.setVisibility(View.GONE);

                mMaskGetInfo.setVisibility(View.GONE);
                mMaskGateOpen.setVisibility(View.GONE);
                mParkInfoBtn.setTextColor(getResources().getColor(R.color.blue));
                mGateOpenBtn.setTextColor(getResources().getColor(R.color.blue));

                sendUiHandlerMsg(MSG_SCANNING_STOP, null);
                break;
            case MSG_SCANNING_START:
                mScanning.setVisibility(View.VISIBLE);
                break;
            case MSG_SCANNING_STOP:
                if (mUiHandler.hasMessages(MSG_SCANNING_TIME_OUT)) {
                    mUiHandler.removeMessages(MSG_SCANNING_TIME_OUT);
                }
                if (mWorkHandler.hasMessages(MSG_SCANNING_TIME_OUT)) {
                    mWorkHandler.removeMessages(MSG_SCANNING_TIME_OUT);
                }
                mScanning.setVisibility(View.GONE);
                break;
            case MSG_SCANNING_TIME_OUT:
                mScanning.setVisibility(View.GONE);
                showToast(String.format("%s, %s", getResources().getString(R.string.connect_failed), getResources().getString(R.string.failed_connect_time_out)));
                break;
            case MSG_DISCONNECT_SUCCESS:
                mScanResultView.setText(null);

                mConnectResultView.setText(getResources().getText(R.string.device_disconnect));
                mConnectResultView.setVisibility(View.GONE);
                mConnectResultBtn.setVisibility(View.GONE);
                mConnectResultHintView.setVisibility(View.VISIBLE);

                mMaskGetInfo.setVisibility(View.VISIBLE);
                mMaskGateOpen.setVisibility(View.VISIBLE);
                mParkLicenseView.setText(null);
                mParkTimeView.setText(null);
                mParkMoneyView.setText(null);
                mParkInfoBtn.setTextColor(getResources().getColor(R.color.blue_11));
                mGateOpenStateView.setText(null);
                mGateOpenBtn.setTextColor(getResources().getColor(R.color.blue_11));

                mScanning.setVisibility(View.GONE);
                mScanningTipsView.setText(getResources().getText(R.string.active_scan_message));
                break;
            case MSG_GET_PARK_INFO:
                if (mReceiveParkInfoBean == null) {
                    return;
                }
                mParkLicenseView.setText(mReceiveParkInfoBean.plate);
                mParkTimeView.setText(mReceiveParkInfoBean.time);
                mParkMoneyView.setText(mReceiveParkInfoBean.money);
                break;
            case MSG_GATE_OPEN_SUCCESS:
                mGateOpenStateView.setText(getResources().getText(R.string.gate_open_success));
                mGateOpenStateView.setTextColor(getResources().getColor(R.color.color_ok));
                break;
            case MSG_GATE_OPEN_FAILED:
                mGateOpenStateView.setText(getResources().getText(R.string.gate_open_fail));
                mGateOpenStateView.setTextColor(getResources().getColor(R.color.color_failed));
                break;
            case MSG_SHOW_IN_TEST_VIEW:
                String value = (String) msg.obj;
                mTestShowMsgView.setText(value);
                break;
            default:
                break;
        }
    }

    private static class UiHandler extends Handler {
        private final WeakReference<MainActivity> activity;

        public UiHandler(MainActivity activity) {
            super();
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            MainActivity mainActivity = activity.get();
            if (mainActivity == null) {
                return;
            }
            mainActivity.updateUi(msg);
        }
    }

    private static class WorkHandler extends Handler {
        private final WeakReference<MainActivity> activity;

        public WorkHandler(MainActivity activity, Looper looper) {
            super(looper);
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            MainActivity mainActivity = activity.get();
            if (mainActivity == null) {
                return;
            }
            switch (msg.what) {
                case MSG_SEND_HEART_BEAT:
                    mainActivity.sendHeartBeat();
                    mainActivity.sendWorkHandlerMsg(MSG_SEND_HEART_BEAT, null, DELAY_SEND_HEART_BEAT_TIME);
                    Log.d(TAG, "send heart beat");
                    break;
                case MSG_RECEIVE_HEART_BEAT:
                    mainActivity.stopBleConnectBtn();
                    Log.d(TAG, "receive heart beat time out");
                    break;
                case MSG_SCANNING_STOP:
                    BluetoothServer.getInstance().stopBleScan();
                    mainActivity.clearAllUiHandlerMsg();
                    Log.d(TAG, "scan stop");
                    break;
                case MSG_SCANNING_TIME_OUT:
                    BluetoothServer.getInstance().stopBleScan();
                    BluetoothServer.getInstance().stopBleConnect();
                    mainActivity.sendUiHandlerMsg(MSG_SCANNING_TIME_OUT, null);
                    Log.d(TAG, "scan timeout");
                    break;
                default:
                    break;
            }
        }
    }
}