package com.ispengya.hotkey.remoting.protocol;

import com.alibaba.fastjson2.JSON;

public class Fastjson2Serializer implements Serializer {

    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            return new byte[0];
        }
        return JSON.toJSONBytes(value);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return JSON.parseObject(bytes, type);
    }
}

