package org.dcm4chee.arc.conf.ui;


/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since May 2018
 */
public class UIDeviceURL {
    private String deviceName;
    private String deviceURL;
    private String description;
    private boolean installed = true;

    public UIDeviceURL() {
    }

    public UIDeviceURL(String name) {
        setDeviceName(name);
    }
    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceURL() {
        return deviceURL;
    }

    public void setDeviceURL(String deviceURL) {
        this.deviceURL = deviceURL;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
