package com.keybox.plugins.wallet;

import org.apache.cordova.CallbackContext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;


public class UsbBroadcastReceiver extends BroadcastReceiver {

    private final String TAG = UsbBroadcastReceiver.class.getSimpleName();

    public static final String USB_PERMISSION ="com.keybox.plugins.wallet.USB_PERMISSION";

    private CallbackContext callbackContext;

    private Activity activity;

    public static class UsbInfo {
        public static UsbInterface intf = null;
        public static UsbEndpoint read_endpoint = null;
        public static UsbEndpoint write_endpoint = null;
        public static UsbDeviceConnection connection = null;
    }

    public UsbBroadcastReceiver(CallbackContext callbackContext, Activity activity) {
        this.callbackContext = callbackContext;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (USB_PERMISSION.equals(action)) {

            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                if(Wallet.usbDevice != null){
                    UsbInfo.connection = Wallet.usbManager.openDevice(Wallet.usbDevice);
                    UsbInfo.intf = Wallet.usbDevice.getInterface(0);
                    UsbInfo.read_endpoint = UsbInfo.intf.getEndpoint(0);
                    UsbInfo.write_endpoint = UsbInfo.intf.getEndpoint(1);
                    UsbInfo.connection.claimInterface(UsbInfo.intf,true);
                }
                Log.e(TAG, "Permission accepted!");
                callbackContext.success("Permission accepted!");
            }
            else {
                Log.e(TAG, "Permission denied!");
                callbackContext.error("Permission denied!");
            }

            activity.unregisterReceiver(this);
        }
    }
}
