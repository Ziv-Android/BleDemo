package com.ziv.demo.r5.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.ziv.demo.r5.utils.TypeConversion;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class BluetoothServer {
    private static final String TAG = "BluetoothServer";
    // 34:B4:72:48:98:0A  A0:76:4E:6C:C9:DA
    public static final String TEXT_BLE_MAC = "A0:76:4E:6C:C9:DA";
    public static String TARGET_BLE_MAC = "20:00:00:00:00:00";
//    private static final String TARGET_BLE_MAC = "A0:76:4E:79:AF:8A";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothGattCharacteristic mReadCharacteristic;

    private final UUID UUID_SERVICE = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb");
    private final UUID UUID_WRITE_CHARA = UUID.fromString("0000c302-0000-1000-8000-00805f9b34fb");
    private final UUID UUID_NOTIFY_CHARA = UUID.fromString("0000c305-0000-1000-8000-00805f9b34fb");
    private final UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private OnBleConnectListener onBleConnectListener;
    private StringBuffer mCacheMsg = new StringBuffer();
    private static final int HEADER_LENGTH = 3;

    private WeakReference<Activity> activityWeakReference;

    public void init(Activity context, OnBleConnectListener listener) {
        Context applicationContext = context.getApplicationContext();
        final BluetoothManager bluetoothManager =
                (BluetoothManager) applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter = bluetoothManager.getAdapter();
        onBleConnectListener = listener;
        activityWeakReference = new WeakReference<>(context);
    }

    public boolean checkBluetoothPermission(Context context) {
        int checkSelfPermission = -1;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            checkSelfPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN);
        } else {
            checkSelfPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH);
        }
        if (checkSelfPermission == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "BLUETOOTH permission not allow");
            return false;
        }
        Log.d(TAG, "CheckBluetoothPermission:" + checkSelfPermission);
        return true;
    }

    @SuppressLint("MissingPermission")
    public void startBleScan() {
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isDiscovering()) {
            BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                Log.d(TAG, "startLeScan");
                bluetoothLeScanner.startScan(mScanCallback);
                if (onBleConnectListener != null) {
                    onBleConnectListener.connectState("scan-start");
                }
            } else {
                Log.d(TAG, "BluetoothLeScanner is null.");
            }
        } else {
            Log.d(TAG, "mBluetoothAdapter is null or isDiscovering.");
        }
    }

    @SuppressLint("MissingPermission")
    public void stopBleScan() {
        if (mBluetoothAdapter != null) {
            BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                Log.d(TAG, "stopLeScan");
                bluetoothLeScanner.stopScan(mScanCallback);
                if (onBleConnectListener != null) {
                    onBleConnectListener.connectState("scan-stop");
                }
            } else {
                Log.d(TAG, "BluetoothLeScanner is null.");
            }
        } else {
            Log.d(TAG, "mBluetoothAdapter is null.");
        }
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice bluetoothDevice = result.getDevice();
            // 34:b4:72:48:98:0a
            String address = bluetoothDevice.getAddress();
            Log.d(TAG, address);
            if (TARGET_BLE_MAC.equals(address)) {
                mBluetoothDevice = bluetoothDevice;
                stopBleScan();
                startBleConnect();
                if (onBleConnectListener != null) {
                    onBleConnectListener.connectState("scan-connect");
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void startBleConnect() {
        if (activityWeakReference == null) {
            return;
        }
        if (mBluetoothDevice == null) {
            return;
        }
        stopBleConnect();
        Log.d(TAG, "startConnect:" + mBluetoothDevice.getAddress());
        BluetoothGatt bluetoothGatt;
        Context context = activityWeakReference.get();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = mBluetoothDevice.connectGatt(context, false, mBluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = mBluetoothDevice.connectGatt(context, false, mBluetoothGattCallback);
        }
//        bluetoothGatt.connect();
        if (onBleConnectListener != null) {
            onBleConnectListener.connectState("connect-start");
        }
    }

    @SuppressLint("MissingPermission")
    public void stopBleConnect() {
        if (mBluetoothGatt == null) {
            return;
        }
        Log.d(TAG, "disconnect:" + mBluetoothGatt.getDevice().getAddress());
        bleGattDisconnect();
        bleGattClose();
        if (onBleConnectListener != null) {
            onBleConnectListener.connectState("connect-stop");
        }
    }

    @SuppressLint("MissingPermission")
    private void bleGattDisconnect() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
    }

    @SuppressLint("MissingPermission")
    private void bleGattClose() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // ??????????????????-????????????/????????????
            super.onConnectionStateChange(gatt, status, newState);
            processConnectionState(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // ??????GATT????????????
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                registerCharacteristic();
                if (onBleConnectListener != null) {
                    onBleConnectListener.onServiceDiscoverySucceed(gatt.getDevice());
                }
            } else {
                if (onBleConnectListener != null) {
                    onBleConnectListener.onServiceDiscoveryFailed(gatt, status);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
//            String msg = TypeConversion.bytes2HexString(characteristic.getValue(), characteristic.getValue().length);
            String msg = new String(characteristic.getValue());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //????????????
                Log.w(TAG, "???????????????" + msg);
                if (onBleConnectListener != null) {
                    onBleConnectListener.onWriteSuccess(gatt, characteristic.getValue());
                }
            } else {
                if (status == BluetoothGatt.GATT_FAILURE) {
                    //????????????
                    Log.e(TAG, "???????????????" + msg);
                } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                    //????????????
                    Log.e(TAG, "???????????????");
                }
                if (onBleConnectListener != null) {
                    onBleConnectListener.onWriteFailure(gatt, characteristic, status);
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            // ????????????
            String msg = TypeConversion.bytes2HexString(characteristic.getValue(), characteristic.getValue().length);
            Log.w(TAG, "onCharacteristicRead -> ??? status: " + status + ",msg: " + msg);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            // ????????????
            byte[] value = characteristic.getValue();
            if (value == null) {
                Log.w(TAG, "onCharacteristicChanged -> ???????????????null???????????????");
                return;
            }
            byte start1 = (byte) 86;
            byte start2 = (byte) 90;
            byte b1 = value[0];
            byte b2 = value[1];
            Log.w(TAG, "onCharacteristicChanged -> ?????????????????????:" + (start1 == b1) + ", " + (start2 == b2));
            if (start1 == b1 && start2 == b2) {
                byte[] valueRemoveHead = new byte[value.length - HEADER_LENGTH];
                System.arraycopy(value, HEADER_LENGTH, valueRemoveHead, 0, valueRemoveHead.length);
                receiveMessage(valueRemoveHead);
            } else {
                receiveMessage(value);
            }
        }

        private void receiveMessage(byte[] value) {
            String msg = TypeConversion.bytes2HexString(value, value.length);
            Log.w(TAG, "onCharacteristicChanged -> ????????????str(hex):" + msg);
            String s = new String(value);
            Log.w(TAG, "onCharacteristicChanged -> ????????????str:" + s);
            try {
                int length = value.length;
                if (length >= 2) {
                    byte end1 = (byte) 10;
                    byte end2 = (byte) 13;
                    byte b1 = value[length - 1];
                    byte b2 = value[length - 2];
                    Log.w(TAG, "onCharacteristicChanged -> ?????????????????????:" + (b1 == end1) + ", " + (b2 == end2));

                    mCacheMsg.append(new String(value, "GBK"));
                    if (b1 == end1 && b2 == end2) {
                        if (onBleConnectListener != null) {
                            onBleConnectListener.onReceiveMessage(mCacheMsg.toString());
                        }
                        mCacheMsg = new StringBuffer();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "receiveMessage Exception:" + e);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "??????RSSI????????????RSSI??????" + rssi + ",status" + status);
                if (onBleConnectListener != null) {
//                    onBleConnectListener.onReadRssi(gatt, rssi, status);  //???????????????????????????????????????
                }
            } else if (status == BluetoothGatt.GATT_FAILURE) {
                Log.w(TAG, "??????RSSI????????????status???" + status);
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void processConnectionState(BluetoothGatt gatt, int status, int newState) {
        switch (newState) {
            case BluetoothGatt.STATE_CONNECTED:
                Log.w(TAG, "????????????");
                mBluetoothGatt = gatt;
                if (onBleConnectListener != null) {
                    onBleConnectListener.onConnectSuccess(gatt, gatt.getDevice());
                }
                // ??????????????????????????????
                mBluetoothGatt.discoverServices();
                break;
            case BluetoothGatt.STATE_DISCONNECTED:
                Log.w(TAG, "????????????");
                if (onBleConnectListener != null) {
                    onBleConnectListener.onConnectFailure(gatt, gatt.getDevice(), newState);
                }
                stopBleConnect();
                break;
            case BluetoothGatt.STATE_CONNECTING:
                Log.d(TAG, "????????????...");
                if (onBleConnectListener != null) {
                    onBleConnectListener.onConnecting(gatt);
                }
                break;
            case BluetoothGatt.STATE_DISCONNECTING:
                Log.d(TAG, "????????????...");
                if (onBleConnectListener != null) {
                    onBleConnectListener.onDisConnecting(gatt);
                }
                break;
        }

        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                Log.w(TAG, "BluetoothGatt.GATT_SUCCESS");
                if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    stopBleConnect();
                    if (onBleConnectListener != null) {
                        onBleConnectListener.onDisConnectSuccess(mBluetoothDevice);
                    }
                }
                break;
            case BluetoothGatt.GATT_READ_NOT_PERMITTED:
                Log.w(TAG, "BluetoothGatt.GATT_READ_NOT_PERMITTED");
                break;
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                Log.w(TAG, "BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION");
                break;
            case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                Log.w(TAG, "BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED");
                break;
            case BluetoothGatt.GATT_INVALID_OFFSET:
                Log.w(TAG, "BluetoothGatt.GATT_INVALID_OFFSET");
                break;
            case 8:
                Log.w(TAG, "??????????????????????????????????????????????????????8???");
                if (onBleConnectListener != null) {
                    onBleConnectListener.onDisConnectSuccess(gatt.getDevice());
                }
                stopBleConnect();
                break;
            case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                Log.w(TAG, "BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION");
                break;
            case 34:
                Log.w(TAG, "????????????:(34)");
                stopBleConnect();
                break;
            case 62:
                Log.w(TAG, "??????????????????:(62)");
                break;
            case 133:
                Log.w(TAG, "????????????:(133)");
                stopBleConnect();
                break;
            case BluetoothGatt.GATT_CONNECTION_CONGESTED:
                Log.w(TAG, "BluetoothGatt.GATT_CONNECTION_CONGESTED");
                break;
            case BluetoothGatt.GATT_FAILURE:
                Log.w(TAG, "BluetoothGatt.GATT_FAILURE");
                break;
        }

    }

    private UUID read_UUID_chara;
    private UUID read_UUID_service;
    private UUID write_UUID_chara;
    private UUID write_UUID_service;
    private UUID notify_UUID_chara;
    private UUID notify_UUID_service;
    private UUID indicate_UUID_chara;
    private UUID indicate_UUID_service;

    private void initServiceAndChara(BluetoothGatt gatt) {
        if (mBluetoothGatt == null) {
            return;
        }
        List<BluetoothGattService> bluetoothGattServices = mBluetoothGatt.getServices();
        for (BluetoothGattService bluetoothGattService : bluetoothGattServices) {
            List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                int charaProp = characteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    read_UUID_chara = characteristic.getUuid();
                    read_UUID_service = bluetoothGattService.getUuid();
                    Log.e(TAG, "read_chara=" + read_UUID_chara + "----read_service=" + read_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    write_UUID_chara = characteristic.getUuid();
                    write_UUID_service = bluetoothGattService.getUuid();
                    Log.e(TAG, "write_chara=" + write_UUID_chara + "----write_service=" + write_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                    write_UUID_chara = characteristic.getUuid();
                    write_UUID_service = bluetoothGattService.getUuid();
                    Log.e(TAG, "write_chara=" + write_UUID_chara + "----write_service=" + write_UUID_service);

                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    notify_UUID_chara = characteristic.getUuid();
                    notify_UUID_service = bluetoothGattService.getUuid();
                    Log.e(TAG, "notify_chara=" + notify_UUID_chara + "----notify_service=" + notify_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    indicate_UUID_chara = characteristic.getUuid();
                    indicate_UUID_service = bluetoothGattService.getUuid();
                    Log.e(TAG, "indicate_chara=" + indicate_UUID_chara + "----indicate_service=" + indicate_UUID_service);
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void registerCharacteristic() {
        BluetoothGattService service = mBluetoothGatt.getService(UUID_SERVICE);
        Log.d(TAG, "start time Notification: Service -> " + service.getUuid());
        mWriteCharacteristic = service.getCharacteristic(UUID_WRITE_CHARA);
        mReadCharacteristic = service.getCharacteristic(UUID_NOTIFY_CHARA);
        // mNotifyCharacteristic = mBluetoothGatt.getService(UUID_SERVICE).getCharacteristic(UUID_NOTIFY_CHARA);

        //????????????????????????????????????????????????
        if (mBluetoothGatt.setCharacteristicNotification(mReadCharacteristic, true)) {
            BluetoothGattDescriptor defaultDescriptor = mReadCharacteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
            if (defaultDescriptor != null) {
                defaultDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(defaultDescriptor);
            }
        }
//        List<BluetoothGattDescriptor> descriptors = mWriteCharacteristic.getDescriptors();
//        for (BluetoothGattDescriptor descriptor : descriptors) {
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            mBluetoothGatt.writeDescriptor(descriptor);
//        }
//        Log.d(TAG, "end time write UUID: " + mWriteCharacteristic.getUuid());
//        Log.d(TAG, "end time notify UUID: " + mReadCharacteristic.getUuid());
    }

    @SuppressLint("MissingPermission")
    public boolean sendBleMessage(String msg) {
        if (mWriteCharacteristic != null) {
            mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            byte[] bytesVZ = "VZ".getBytes(StandardCharsets.UTF_8);
            byte[] bytesData = msg.getBytes(StandardCharsets.UTF_8);
//            byte[] dataLength = TypeConversion.int2Bytes(bytesData.length);
            byte[] bytesMsg = new byte[bytesData.length + HEADER_LENGTH];
            System.arraycopy(bytesVZ, 0, bytesMsg, 0, 2);
//            System.arraycopy(dataLength, 0, bytesMsg, 2, 4);
            bytesMsg[2] = (byte) bytesData.length;
            System.arraycopy(bytesData, 0, bytesMsg, HEADER_LENGTH, bytesData.length);
            mWriteCharacteristic.setValue(bytesMsg);
            if (mBluetoothGatt != null) {
                Log.d(TAG, "sendBleMessage: " + TypeConversion.bytes2HexString(mWriteCharacteristic.getValue()));
                return mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
            }
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    public String getBleName() {
        if (mBluetoothAdapter == null) {
            return null;
        }
        return mBluetoothAdapter.getName();
    }

    @SuppressLint("MissingPermission")
    public String getDeviceName() {
        if (mBluetoothDevice == null) {
            return null;
        }
        return mBluetoothDevice.getName();
    }

    @SuppressLint("MissingPermission")
    public String getDeviceAddress() {
        if (mBluetoothDevice == null) {
            return null;
        }
        return mBluetoothDevice.getAddress();
    }

    private BluetoothServer() {
    }

    private static class SingleHolder {
        public static BluetoothServer INSTANCE = new BluetoothServer();
    }

    public static BluetoothServer getInstance() {
        return BluetoothServer.SingleHolder.INSTANCE;
    }
}
