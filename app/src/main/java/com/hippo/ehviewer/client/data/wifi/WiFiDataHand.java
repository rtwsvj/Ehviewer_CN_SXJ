package com.hippo.ehviewer.client.data.wifi;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.util.JsonUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class WiFiDataHand {
    public final static int ERROR = 0;
    public final static int RECEIVED = 1;
    public final static int SEND = 2;

    public int messageType;

    public int dataType;

    public long pageSize = 1;

    public long pageIndex = 1;

    public String errorMessage;

    private JSONObject data;

    public WiFiDataHand(int messageType) {
        this(messageType, null);
    }

    public WiFiDataHand(int messageType, JSONObject data) {
        this.messageType = messageType;
        this.data = data;
    }

    public WiFiDataHand(String msg) {
        try {
            JSONObject object = new JSONObject(msg);
            this.messageType = object.optInt("messageType");
            this.dataType = object.optInt("dataType");
            this.data = object.optJSONObject("data");
            this.pageSize = object.optLong("totalSize");
            this.pageIndex = object.optLong("part");
        } catch (Throwable throwable) {
            Analytics.recordException(throwable);
            messageType = ERROR;
            errorMessage = throwable.getMessage();
            data = new JSONObject();
        }
    }

    public JSONObject getData() {
        return data;
    }

    public void addData(String key, Object object) {
        if (data == null) {
            data = new JSONObject();
        }
        JsonUtils.put(data, key, object);
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public JSONObject toJsonObject() {
        JSONObject object = new JSONObject();
        JsonUtils.put(object, "messageType", messageType);
        JsonUtils.put(object, "dataType", dataType);
        JsonUtils.put(object, "data", data != null ? data : JSONObject.NULL);
        JsonUtils.put(object, "totalSize", pageSize);
        JsonUtils.put(object, "part", pageIndex);
        return object;
    }

    @NonNull
    @Override
    public String toString() {
        return toJsonObject().toString();
    }

    public String toSendString() {
        return toJsonObject().toString() + ":END";
    }

    public byte[] getSendBytes() {
        // Encode as UTF-8 to match the receiver, which decodes the frame as UTF-8
        // (ConnectThread.readFramedPayload). Using the platform default charset here would
        // corrupt non-ASCII payloads (e.g. Chinese quick-search keywords / tag names) across devices.
        return toSendString().getBytes(StandardCharsets.UTF_8);
    }
}
