package com.hippo.ehviewer.client.wifi;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ConnectThread extends Thread {

    public static final int DEVICE_CONNECTING = 1;//有设备正在连接热点
    public static final int DEVICE_CONNECTED = 2;//有设备连上热点
    public static final int DEVICE_DISCONNECTED = 3;//有设备连上热点
    public static final int SEND_MSG_SUCCESS = 4;//发送消息成功
    public static final int SEND_MSG_ERROR = 5;//发送消息失败
    public static final int GET_MSG = 6;//获取新消息

    public static final int IS_SERVER = 101;
    public static final int IS_CLIENT = 102;

    public static final int DATA_TYPE_QUICK_SEARCH = 1001;
    public static final String QUICK_SEARCH_DATA_KEY = "quick_search";
    public static final int DATA_TYPE_DOWNLOAD_INFO = 1002;
    public static final String DOWNLOAD_INFO_DATA_KEY = "download_info";
    public static final int DATA_TYPE_DOWNLOAD_LABEL = 1003;
    public static final String DOWNLOAD_LABEL_KEY = "download_label";

    public static final int DATA_TYPE_FAVORITE_INFO = 1004;
    public static final String FAVORITE_INFO_DATA_KEY = "favorite_info";

    /**
     * Pairing handshake (SEC-1 / FIX_QUEUE Q7). The data source (IS_SERVER) sends a single frame of
     * this type as its FIRST transmission, carrying the 6-digit code the user reads off the sender's
     * screen. The data sink (IS_CLIENT) must receive a matching code before it dispatches/persists
     * ANY data frame; a mismatch (or a non-pairing first frame) tears the connection down.
     * NOTE: this is a wire-protocol change — old and new builds are incompatible and BOTH ends must
     * be updated together.
     */
    public static final int DATA_TYPE_PAIR = 1000;
    public static final String PAIR_CODE_KEY = "pair_code";

    /** Hard cap on a single WiFi-sync payload so a peer that never sends the terminator can't OOM us. */
    static final int MAX_PAYLOAD = 8 * 1024 * 1024;
    /** Frame terminator; only ":END" (4 bytes) is stripped, the closing '}' stays part of the JSON. */
    private static final byte[] END_MARKER = {'}', ':', 'E', 'N', 'D'};

    private final Socket socket;
    private final Handler handler;
    private final int connectKind;
    /** The 6-digit code shown on the sender and typed into the receiver; required by both ends. */
    private final String pairCode;
    private OutputStream outputStream;
    Context context;

    private boolean processed = true;

    private boolean close = false;

    /** Receiver-side gate: until a valid pairing frame arrives, no data frame is dispatched/persisted. */
    private boolean paired = false;

    public ConnectThread(Context context, Socket socket, Handler handler, int connectKind) {
        this(context, socket, handler, connectKind, null);
    }

    public ConnectThread(Context context, Socket socket, Handler handler, int connectKind, String pairCode) {
        setName("ConnectThread");
        Log.i("ConnectThread", "ConnectThread");
        this.connectKind = connectKind;
        this.socket = socket;
        this.handler = handler;
        this.context = context;
        this.pairCode = pairCode;
    }

    @Override
    public void run() {
        if (socket == null) {
            return;
        }
        handler.sendEmptyMessage(DEVICE_CONNECTED);
        try {
            InputStream inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            // SEC-1 / Q7 handshake: the sender announces the pairing code as its very first frame so the
            // receiver can authenticate the peer before persisting anything.
            if (connectKind == IS_SERVER) {
                sendPairFrame();
            }

            while (!isInterrupted()) {
                //获取数据流
                WiFiDataHand wiFiDataHand = isToResponse(inputStream);
                if (close) {
                    break;
                }
                if (wiFiDataHand != null) {
                    if (connectKind == IS_CLIENT) {
                        if (!solveTheData(wiFiDataHand)) {
                            break;
                        }
                    } else {
                        sendNextData(wiFiDataHand);
                    }
                }
            }
            socket.close();
        } catch (IOException e) {
            Analytics.recordException(e);
        }
    }

    /** Sender side: transmit the pairing code as the first frame on the wire. */
    private void sendPairFrame() {
        WiFiDataHand pair = new WiFiDataHand(WiFiDataHand.SEND);
        pair.dataType = DATA_TYPE_PAIR;
        pair.addData(PAIR_CODE_KEY, pairCode == null ? "" : pairCode);
        sendData(pair);
    }

    private void sendNextData(WiFiDataHand wiFiDataHand) {
        if (wiFiDataHand.messageType != WiFiDataHand.RECEIVED) {
            return;
        }
        Message message = Message.obtain();
        message.what = SEND_MSG_SUCCESS;
        Bundle bundle = new Bundle();
        bundle.putString("MSG", wiFiDataHand.toString());
        message.setData(bundle);
        handler.sendMessage(message);
    }

    /**
     * Receiver side. Authenticates and dispatches one incoming frame.
     *
     * @return {@code true} to keep the connection open, {@code false} to tear it down (auth failure).
     */
    private boolean solveTheData(WiFiDataHand wiFiDataHand) {
        if (wiFiDataHand.messageType != WiFiDataHand.SEND) {
            // Non-data control/ack frames are ignored but don't affect the connection.
            return true;
        }
        // SEC-1 / Q7: the FIRST data frame must be a valid pairing frame. Reject everything until then.
        if (!paired) {
            if (wiFiDataHand.dataType != DATA_TYPE_PAIR) {
                Log.w("ConnectThread", "First frame was not a pairing frame; refusing peer.");
                return false;
            }
            String received = wiFiDataHand.getData() == null
                    ? null
                    : wiFiDataHand.getData().optString(PAIR_CODE_KEY, null);
            if (!isPairCodeValid(pairCode, received)) {
                Log.w("ConnectThread", "Pairing code mismatch; refusing peer.");
                return false;
            }
            paired = true;
            return true;
        }
        // After pairing, only whitelisted data types are dispatched for persistence.
        if (!isKnownDataType(wiFiDataHand.dataType)) {
            Log.w("ConnectThread", "Rejecting unknown dataType: " + wiFiDataHand.dataType);
            return true;
        }
        Message message = Message.obtain();
        message.what = GET_MSG;
        Bundle bundle = new Bundle();
        bundle.putString("MSG", wiFiDataHand.toString());
        message.setData(bundle);
        handler.sendMessage(message);
        return true;
    }


    /**
     * 发送数据
     */
    public void sendData(WiFiDataHand dataHand) {
        try {
            if (outputStream == null) {
                outputStream = socket.getOutputStream();
            }
            Log.i("ConnectThread", "发送数据:" + (outputStream == null));
            outputStream.write(dataHand.getSendBytes());
            outputStream.flush();
            Log.i("ConnectThread", "发送消息：" + dataHand);
        } catch (IOException e) {
            e.printStackTrace();
            Message message = Message.obtain();
            message.what = SEND_MSG_ERROR;
            Bundle bundle = new Bundle();
            bundle.putString("MSG", dataHand.toString());
            message.setData(bundle);
            handler.sendMessage(message);
        }
    }

    public void dataProcessed(WiFiDataHand response) {
        processed = true;
        WiFiDataHand wiFiDataHand = new WiFiDataHand(WiFiDataHand.RECEIVED);
        wiFiDataHand.setData(response.getData());
        new Thread(()-> sendData(wiFiDataHand)).start();
    }

    private WiFiDataHand isToResponse(InputStream inputStream) {
        try {
            String result = readFramedPayload(inputStream, MAX_PAYLOAD);
            if (result == null || result.isEmpty()) {
                return null;
            }
            return new WiFiDataHand(result);
        } catch (Throwable throwable) {
            Analytics.recordException(throwable);
            if (socket.isClosed()) {
                interrupt();
            }
            return null;
        }
    }

    /**
     * Reads one terminator-framed payload from the stream. Returns the decoded payload with the
     * trailing ":END" delimiter removed (the closing '}' is kept), or {@code null} if the peer sends
     * more than {@code maxPayload} bytes without a terminator (refused instead of buffering to OOM).
     * On EOF without a terminator, returns whatever was accumulated (matching the legacy behavior).
     * Package-private + static so it can be unit-tested on the JVM without Android dependencies.
     */
    static String readFramedPayload(InputStream inputStream, int maxPayload) throws IOException {
        TailMatchingBuffer buffer = new TailMatchingBuffer();
        byte[] bytes = new byte[1024];
        for (int length; (length = inputStream.read(bytes)) != -1; ) {
            buffer.write(bytes, 0, length);
            if (buffer.size() > maxPayload) {
                return null;
            }
            if (buffer.endsWith(END_MARKER)) {
                String result = buffer.toString("UTF-8");
                return result.substring(0, result.length() - 4);
            }
        }
        return buffer.size() == 0 ? null : buffer.toString("UTF-8");
    }

    /**
     * Whether {@code dataType} is one of the known persistable {@code DATA_TYPE_*} payload kinds.
     * The pairing handshake type is intentionally NOT included: a pairing frame must never be treated
     * as data to persist. Pure + static so it can be unit-tested on the JVM.
     */
    static boolean isKnownDataType(int dataType) {
        return dataType == DATA_TYPE_QUICK_SEARCH
                || dataType == DATA_TYPE_DOWNLOAD_INFO
                || dataType == DATA_TYPE_DOWNLOAD_LABEL
                || dataType == DATA_TYPE_FAVORITE_INFO;
    }

    /**
     * Constant-time-ish equality check of the expected pairing code against the code carried by an
     * incoming frame. Both are trimmed; a null/blank expected or actual code never matches. Kept
     * pure + static for JVM unit testing (no Android dependencies).
     */
    static boolean isPairCodeValid(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        String e = expected.trim();
        String a = actual.trim();
        if (e.isEmpty() || a.isEmpty()) {
            return false;
        }
        return e.equals(a);
    }

    /** ByteArrayOutputStream that can test its raw byte tail in O(marker) without copying the buffer. */
    private static final class TailMatchingBuffer extends ByteArrayOutputStream {
        boolean endsWith(byte[] marker) {
            if (count < marker.length) {
                return false;
            }
            int offset = count - marker.length;
            for (int i = 0; i < marker.length; i++) {
                if (buf[offset + i] != marker[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    public void closeConnect() {
        try {
            socket.close();
            interrupt();
            close = true;
        } catch (IOException|NullPointerException e) {
            Analytics.recordException(e);
        }
    }

    public boolean isSocketClose() {
        if (socket==null){
            return true;
        }
        return socket.isClosed();
    }
}
