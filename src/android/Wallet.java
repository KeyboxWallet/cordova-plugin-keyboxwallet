package com.keybox.plugins.wallet;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Base64;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

import walletproto.Messages;
import com.magicw.plugins.gts.UsbBroadcastReceiver.UsbInfo;

/**
 * This class echoes a string called from JavaScript.
 */
public class Wallt extends CordovaPlugin {

    private static final String TAG = Wallet.class.getSimpleName();
    private static final String ACTION_REQUEST_PERMISSION = "requestPermission";

    // private static final int MsgTypeLowLimit = 0;
    // private static final int MsgTypeGenericConfirmReply = 1;
    private static final int MsgTypeRequestRejected = 2;
    // private static final int MsgTypeEraseDataRequest = 8;
    // private static final int MsgTypeUpgradeStartRequest = 10;
    // private static final int MsgTypeSendUpgradeFirmware = 11;
    private static final int MsgTypeEccSignRequest = 15;
    private static final int MsgTypeEccSignResult = 16;
    private static final int MsgTypeEccGetPublicKeyRequest = 17;
    private static final int MsgTypeEccGetPublicKeyReply = 18;
    private static final int MsgTypeEccMultiplyRequest = 19;
    private static final int MsgTypeEccMultiplyReply = 20;
    // private static final int MsgTypeHighLimit = 21;

    public static UsbManager usbManager;
    public static UsbDevice usbDevice;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "执行execute!!!!!!!!");
        // String arg_obj = args.getString(0);
        if (ACTION_REQUEST_PERMISSION.equals(action)) {
            JSONObject opts = new JSONObject();
            requestPermission(opts, callbackContext);
            return true;

        } else if ("getPublicKeyFromPath".equals(action)) {
            String arg_obj = args.getString(0);
            getPublicKeyFromPath(arg_obj, callbackContext);
            return true;

        } else if ("signReq".equals(action)) {
            Log.d(TAG, "signReq!!!!!!!!");
            JSONObject arg_obj = args.getJSONObject(0);
            Log.d(TAG, arg_obj.toString());
            signReq(arg_obj, callbackContext);
            return true;

        } else if ("multiplyReq".equals(action)) {
            String arg_obj = args.getString(0);
            multiplyReq(arg_obj, callbackContext);
            return true;

        }

        return false;
    }

    private void composeBufferTransfer(int messageType, String reqMethod, OutputStream os,
            CallbackContext callbackContext) {
        Log.d(TAG, "进入到composeBufferTransfer!!!!!!!!");
        Log.d(TAG, String.valueOf(messageType));
        Log.d(TAG, reqMethod);
        try {
            final byte[] buffer = new byte[1024];

            final ByteArrayOutputStream ostream = (ByteArrayOutputStream) os;
            final int contentSize = ostream.toByteArray().length;
            final CallbackContext lcb = callbackContext;
            final String lReqMethod = reqMethod;

            if (contentSize <= 1015) {
                buffer[0] = 1;
            } else {
                buffer[0] = 2;
            }

            System.arraycopy(htonl(messageType), 0, buffer, 1, 4);
            System.arraycopy(htonl(contentSize), 0, buffer, 5, 4);

            final boolean mulpkg = contentSize > 1015 ? true : false;
            int loop = 1;
            if (mulpkg) {
                System.arraycopy(ostream.toByteArray(), 0, buffer, 9, 1015);
                loop = ((contentSize - 1015) % 1019) > 0 ? (contentSize - 1015) / 1019 + 2
                        : (contentSize - 1015) / 1019 + 1;
            } else {
                System.arraycopy(ostream.toByteArray(), 0, buffer, 9, contentSize);
            }

            final int floop = loop;
            Log.d(TAG, "loop");
            Log.d(TAG, String.valueOf(loop));

            cordova.getThreadPool().execute(new Runnable() {
                byte[] read_buf = new byte[1024];

                public void run() {
                    for (int i = 0; i < floop; i++) {
                        Log.d(TAG, "run方法!!!!!!!!!!!!!!!!!!!");
                        UsbInfo.connection.bulkTransfer(UsbInfo.write_endpoint, buffer, 1024, 2000);
                        if (mulpkg) {
                            buffer[0] = 3;
                            System.arraycopy(htonl(1015 + i * 1019), 0, buffer, 1, 4);

                            if ((contentSize - 1015 - i * 1019) / 1019 > 0)
                                System.arraycopy(ostream.toByteArray(), 1015 + i * 1019, buffer, 5, 1019);
                            else
                                System.arraycopy(ostream.toByteArray(), 1015 + i * 1019, buffer, 5,
                                        (contentSize - 1015 - i * 1019) % 1019);
                        }
                        UsbInfo.connection.bulkTransfer(UsbInfo.read_endpoint, read_buf, 1024, 0);
                    }
                    JSONObject res = new JSONObject();
                    Log.d(TAG, "read_buf");
                    Log.d(TAG, String.valueOf(read_buf[0] != 1));
                    if (read_buf[0] != 1) {
                        // res.put("error", "server issue");
                        lcb.success(res);
                        return;
                    }

                    byte[] intBuf = new byte[4];
                    System.arraycopy(read_buf, 1, intBuf, 0, 4);
                    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! 2!!!!!!
                    int msgType = ByteBuffer.wrap(intBuf).getInt();
                    System.arraycopy(read_buf, 5, intBuf, 0, 4);
                    int msgLen = ByteBuffer.wrap(intBuf).getInt();

                    if (msgLen < 1015) {
                        byte[] repBody = new byte[msgLen];
                        System.arraycopy(read_buf, 9, repBody, 0, msgLen);
                        ByteArrayInputStream istream = new ByteArrayInputStream(repBody);
                        res = getResponse(msgType, lReqMethod, istream);
                    }

                    lcb.success(res);
                }
            });

        } catch (Exception e) {
            Log.d(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            Log.d(TAG, String.valueOf(e));

        }

    }

    private JSONObject getResponse(int messageType, String reqMethod, InputStream in) {
        Log.d(TAG, "进入到getResponse里面!!!!!!!!!!!!");
        Log.d(TAG, String.valueOf(messageType));
        Log.d(TAG, reqMethod);
        JSONObject errJson = new JSONObject();
        try {
            errJson.put("error", "data parser error");
            JSONObject json = new JSONObject();
            JSONObject d = new JSONObject();
            if (messageType == MsgTypeRequestRejected) {
                Messages.RequestRejected rej = Messages.RequestRejected.parseFrom(in);
                json.put("errcode", rej.getErrCode());
                json.put("errmessage", rej.getErrMessage());
                return json;
            }
            if (reqMethod.equals("getPublicKeyFromPath")) {
                if (messageType == MsgTypeEccGetPublicKeyReply) {
                    Messages.EccGetPublicKeyReply rep = Messages.EccGetPublicKeyReply.parseFrom(in);

                    d.put("ver", 1);
                    d.put("type", "ecc-pubkey");
                    d.put("curve", "secp256k1");
                    d.put("data", Base64.encodeToString(rep.getPubkey().toByteArray(), Base64.NO_WRAP));
                    json.put("data", d);
                    json.put("errcode", 0);
                    json.put("errmessage", "OK");
                    return json;
                }
            } else if (reqMethod.equals("signReq")) {
                Log.d(TAG, "是signReq方法!!!!!!!!");
                Log.d(TAG, String.valueOf(messageType));

                if (messageType == MsgTypeEccSignResult) {
                    // 2!!!!!!!!
                    Messages.EccSignResult res = Messages.EccSignResult.parseFrom(in);

                    JSONObject iObj = new JSONObject();
                    iObj.put("ver", 1);
                    iObj.put("curve", "secp256k1");
                    iObj.put("type", "ecc-signature");
                    iObj.put("input-hash", Base64.encodeToString(res.getHash().toByteArray(), Base64.NO_WRAP));
                    iObj.put("pubkey", Base64.encodeToString(res.getPubkey().toByteArray(), Base64.NO_WRAP));

                    d.put("R", Base64.encodeToString(res.getR().toByteArray(), Base64.NO_WRAP));
                    d.put("S", Base64.encodeToString(res.getS().toByteArray(), Base64.NO_WRAP));
                    d.put("recover_param", res.getRecoverParam());
                    iObj.put("data", d);

                    json.put("data", iObj);
                    json.put("errcode", 0);
                    json.put("errmessage", "OK");

                    return json;
                } else if (messageType == MsgTypeRequestRejected) {
                    json.put("data", null);
                    json.put("errcode", -6);
                    json.put("errmessage", "signing rejected");
                    return json;
                }
            } else if (reqMethod.equals("multiplyReq")) {
                if (messageType == MsgTypeEccMultiplyReply) {
                    Messages.EccMultiplyReply rep = Messages.EccMultiplyReply.parseFrom(in);

                    d.put("ver", 1);
                    d.put("curve", "secp256k1");
                    d.put("type", "ecc-multiply-result");
                    d.put("input-pubkey", Base64.encodeToString(rep.getInputPubkey().toByteArray(), Base64.NO_WRAP));
                    d.put("dev-pubkey", Base64.encodeToString(rep.getDevPubkey().toByteArray(), Base64.NO_WRAP));
                    d.put("data", Base64.encodeToString(rep.getResult().toByteArray(), Base64.NO_WRAP));

                    json.put("data", d);
                    json.put("errcode", 0);
                    json.put("errmessage", "OK");

                    return json;
                } else if (messageType == MsgTypeRequestRejected) {
                    json.put("data", null);
                    json.put("errcode", -6);
                    json.put("errmessage", "signing rejected");
                    return json;
                }
            }
            json.put("error", "method not supported");
            return json;
        } catch (Exception e) {
            // return "data parser error";
            return errJson;
        }

    }

    private void requestPermission(final JSONObject opts, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {

                usbManager = (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);

                if (usbManager.getDeviceList().size() > 0) {
                    // driver = availableDrivers.get(0);
                    Iterator it = usbManager.getDeviceList().values().iterator();
                    usbDevice = (UsbDevice) it.next();

                    PendingIntent pendingIntent = PendingIntent.getBroadcast(cordova.getActivity(), 0,
                            new Intent(UsbBroadcastReceiver.USB_PERMISSION), 0);

                    IntentFilter filter = new IntentFilter();
                    filter.addAction(UsbBroadcastReceiver.USB_PERMISSION);

                    UsbBroadcastReceiver usbReceiver = new UsbBroadcastReceiver(callbackContext, cordova.getActivity());
                    cordova.getActivity().registerReceiver(usbReceiver, filter);

                    usbManager.requestPermission(usbDevice, pendingIntent);
                } else {
                    Log.d(TAG, "No device found!!!!!!");
                    callbackContext.error("No device found!");
                }
            }
        });
    }

    private void getPublicKeyFromPath(String path, final CallbackContext callbackContext) {
        try {
            int messageType = MsgTypeEccGetPublicKeyRequest;
            Messages.EccGetPublicKeyRequest.Builder builder = Messages.EccGetPublicKeyRequest.newBuilder();
            builder.setHdPath(path);
            Messages.EccGetPublicKeyRequest req = builder.build();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            req.writeTo(stream);

            if (UsbInfo.connection != null) {
                composeBufferTransfer(messageType, "getPublicKeyFromPath", stream, callbackContext);
            }
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private void signReq(JSONObject jObj, final CallbackContext callbackContext) {
        Log.d(TAG, "进入到signReq里面");
        Log.d(TAG, jObj.toString());

        try {
            int messageType = MsgTypeEccSignRequest;
            JSONObject jsonObj = jObj;

            Messages.EccSignRequest.Builder builder = Messages.EccSignRequest.newBuilder();
            builder.setHdPath(jsonObj.getString("path"));
            // new String(Base64.decode(strBase64.getBytes(), Base64.DEFAULT));
            builder.setHash(ByteString.copyFrom(Base64.decode(jsonObj.getString("hash").getBytes(), Base64.DEFAULT)));
            builder.setAlgorithm(Messages.EccAlgorithm.SECP256K1);

            Messages.EccSignOptions.Builder esoBuilder = Messages.EccSignOptions.newBuilder();
            JSONObject jsonOpt = (JSONObject) jsonObj.get("options");
            esoBuilder.setRfc6979(jsonOpt.getBoolean("rfc6979"));
            esoBuilder.setGrapheneCanonize(jsonOpt.getBoolean("graphene_canonize"));

            builder.setOptions(esoBuilder.build());

            Messages.EccSignRequest req = builder.build();

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            req.writeTo(stream);

            if (UsbInfo.connection != null) {
                Log.d(TAG, "即将composeBuffer");
                composeBufferTransfer(messageType, "signReq", stream, callbackContext);
            }

        } catch (Exception e) {
            callbackContext.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private void multiplyReq(String jstring, final CallbackContext callbackContext) {
        try {
            int messageType = MsgTypeEccMultiplyRequest;
            JSONObject jsonObj = new JSONObject(jstring);

            Messages.EccMultiplyRequest.Builder builder = Messages.EccMultiplyRequest.newBuilder();
            builder.setHdPath(jsonObj.getString("path"));
            builder.setAlgorithm(Messages.EccAlgorithm.SECP256K1);
            builder.setInputPubkey(
                    ByteString.copyFrom(Base64.decode(jsonObj.getString("pubkey").getBytes(), Base64.DEFAULT)));

            Messages.EccMultiplyRequest req = builder.build();

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            req.writeTo(stream);

            if (UsbInfo.connection != null) {
                composeBufferTransfer(messageType, "multiplyReq", stream, callbackContext);
            }

        } catch (Exception e) {
            callbackContext.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private byte[] htonl(int param) {
        byte[] res = new byte[4];

        res[0] = (byte) ((param / (256 * 256 * 256)) % 256);
        res[1] = (byte) ((param / (256 * 256)) % 256);
        res[2] = (byte) ((param / 256) % 256);
        res[3] = (byte) (param % 256);

        return res;
    }
}
