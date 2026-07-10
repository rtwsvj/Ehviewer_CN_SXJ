package com.hippo.ehviewer.client.wifi;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.client.data.wifi.WiFiDataHand;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
    private final LegacyFrameDecoder frameDecoder = new LegacyFrameDecoder();
    private final Object outputLock = new Object();
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
        bundle.putString("MSG", describeFrame(wiFiDataHand));
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
        synchronized (outputLock) {
            try {
                if (outputStream == null) {
                    outputStream = socket.getOutputStream();
                }
                outputStream.write(dataHand.getSendBytes());
                outputStream.flush();
                Log.d("ConnectThread", "Sent " + describeFrame(dataHand));
            } catch (IOException e) {
                Analytics.recordException(e);
                Message message = Message.obtain();
                message.what = SEND_MSG_ERROR;
                Bundle bundle = new Bundle();
                bundle.putString("MSG", describeFrame(dataHand));
                message.setData(bundle);
                handler.sendMessage(message);
            }
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
            String result = frameDecoder.readFrame(inputStream, MAX_PAYLOAD);
            if (result == null || result.isEmpty()) {
                close = true;
                return null;
            }
            return new WiFiDataHand(result);
        } catch (Throwable throwable) {
            Analytics.recordException(throwable);
            close = true;
            try {
                socket.close();
            } catch (IOException ignored) {
                // The connection is already unusable after a framing error.
            }
            interrupt();
            return null;
        }
    }

    /**
     * Reads one terminator-framed payload from the stream. Returns the decoded payload with the
     * trailing ":END" delimiter removed (the closing '}' is kept), or {@code null} if the peer sends
     * more than {@code maxPayload} bytes without a terminator. EOF with a partial frame is a protocol
     * error instead of being treated as valid JSON.
     * Package-private + static so it can be unit-tested on the JVM without Android dependencies.
     */
    static String readFramedPayload(InputStream inputStream, int maxPayload) throws IOException {
        return new LegacyFrameDecoder().readFrame(inputStream, maxPayload);
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

    static String describeFrame(WiFiDataHand dataHand) {
        if (dataHand == null) {
            return "frame(null)";
        }
        return "frame(type=" + dataHand.dataType + ", part=" + dataHand.pageIndex
                + "/" + dataHand.pageSize + ")";
    }

    /**
     * Connection-scoped decoder for the legacy delimiter protocol. It returns exactly one JSON
     * object at a time and retains bytes belonging to later coalesced TCP frames.
     */
    static final class LegacyFrameDecoder {
        private final FrameBuffer buffer = new FrameBuffer();
        private final byte[] readBuffer = new byte[1024];
        private int scanOffset;
        private int depth;
        private int rootEndOffset = -1;
        private boolean started;
        private boolean inString;
        private boolean escaped;

        String readFrame(InputStream inputStream, int maxPayload) throws IOException {
            if (maxPayload <= 0) {
                throw new IllegalArgumentException("maxPayload must be positive");
            }

            while (true) {
                String frame = extractFrame(maxPayload);
                if (frame != null) {
                    return frame;
                }
                if (buffer.size() > maxPayload + END_MARKER.length) {
                    throw new IOException("WiFi sync frame exceeds " + maxPayload + " bytes");
                }

                int length = inputStream.read(readBuffer);
                if (length == -1) {
                    if (buffer.size() == 0) {
                        return null;
                    }
                    throw new EOFException("WiFi sync stream ended with an incomplete frame");
                }
                buffer.write(readBuffer, 0, length);
            }
        }

        private String extractFrame(int maxPayload) throws IOException {
            if (rootEndOffset < 0) {
                for (int i = scanOffset; i < buffer.size(); i++) {
                    byte value = buffer.byteAt(i);
                    if (!started) {
                        if (isJsonWhitespace(value)) {
                            continue;
                        }
                        if (value != '{') {
                            throw new IOException("WiFi sync frame must be a JSON object");
                        }
                        started = true;
                        depth = 1;
                        continue;
                    }
                    if (inString) {
                        if (escaped) {
                            escaped = false;
                        } else if (value == '\\') {
                            escaped = true;
                        } else if (value == '"') {
                            inString = false;
                        }
                        continue;
                    }
                    if (value == '"') {
                        inString = true;
                    } else if (value == '{') {
                        depth++;
                    } else if (value == '}') {
                        depth--;
                        if (depth < 0) {
                            throw new IOException("WiFi sync frame has unbalanced JSON braces");
                        }
                        if (depth == 0) {
                            rootEndOffset = i;
                            break;
                        }
                    }
                }
                scanOffset = buffer.size();
            }

            if (rootEndOffset < 0 || buffer.size() < rootEndOffset + END_MARKER.length) {
                return null;
            }
            if (!buffer.matchesAt(rootEndOffset, END_MARKER)) {
                throw new IOException("WiFi sync frame has an invalid terminator");
            }
            int payloadLength = rootEndOffset + 1;
            if (payloadLength > maxPayload) {
                throw new IOException("WiFi sync frame exceeds " + maxPayload + " bytes");
            }
            String frame = buffer.decodePrefix(payloadLength);
            buffer.discardPrefix(rootEndOffset + END_MARKER.length);
            resetParserState();
            return frame;
        }

        private void resetParserState() {
            scanOffset = 0;
            depth = 0;
            rootEndOffset = -1;
            started = false;
            inString = false;
            escaped = false;
        }

        private static boolean isJsonWhitespace(byte value) {
            return value == ' ' || value == '\n' || value == '\r' || value == '\t';
        }
    }

    /** Mutable byte buffer with prefix extraction, avoiding a full copy on every socket read. */
    private static final class FrameBuffer extends ByteArrayOutputStream {
        byte byteAt(int index) {
            return buf[index];
        }

        boolean matchesAt(int offset, byte[] marker) {
            if (offset < 0 || offset + marker.length > count) {
                return false;
            }
            for (int i = 0; i < marker.length; i++) {
                if (buf[offset + i] != marker[i]) {
                    return false;
                }
            }
            return true;
        }

        String decodePrefix(int length) {
            return new String(buf, 0, length, StandardCharsets.UTF_8);
        }

        void discardPrefix(int length) {
            int remaining = count - length;
            if (remaining > 0) {
                System.arraycopy(buf, length, buf, 0, remaining);
            }
            count = Math.max(0, remaining);
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
