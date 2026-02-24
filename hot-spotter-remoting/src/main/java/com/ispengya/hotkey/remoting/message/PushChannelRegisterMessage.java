package com.ispengya.hotkey.remoting.message;

import java.io.Serializable;

public class PushChannelRegisterMessage implements Serializable {

    private String appName;

    public PushChannelRegisterMessage() {
    }

    public PushChannelRegisterMessage(String appName) {
        this.appName = appName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
}
