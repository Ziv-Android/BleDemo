package com.ziv.demo.r5.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * 4.0蓝牙连接监听
 */
public interface OnBleConnectListener {
    void connectState(String state);
    void onConnectSuccess(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice);  //连接成功
    void onConnectFailure(BluetoothGatt bluetoothGatt, BluetoothDevice bluetoothDevice, int status);  //连接失败
    void onConnecting(BluetoothGatt bluetoothGatt); //正在连接
    void onDisConnecting(BluetoothGatt bluetoothGatt); //正在断开
    void onDisConnectSuccess(BluetoothDevice bluetoothDevice); // 断开连接

    void onServiceDiscoverySucceed(BluetoothDevice bluetoothDevice);  //发现服务成功
    void onServiceDiscoveryFailed(BluetoothGatt bluetoothGatt, int state);  //发现服务失败
    void onReceiveMessage(String msg);      //收到消息
    void onReceiveError(String errorMsg);  //接收数据出错
    void onWriteSuccess(BluetoothGatt gatt, byte[] msg);        //写入成功
    void onWriteFailure(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);        //写入失败
}
