package org.dcm4chee.arc.conf.ui;

/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since Jan 2020
 */
public class UIWebAppList {
    private String webAppListName;
    private String webAppListDescription;
    private String mode;
    private String[] webApps = {};
    private String[] acceptedRole = {};
    private String[] acceptedUserName = {};

    public UIWebAppList() {
    }

    public UIWebAppList(String name) {
        setWebAppListName(name);
    }

    public String getWebAppListName() {
        return webAppListName;
    }

    public void setWebAppListName(String webAppListName) {
        this.webAppListName = webAppListName;
    }

    public String getWebAppListDescription() {
        return webAppListDescription;
    }

    public void setWebAppListDescription(String webAppListDescription) {
        this.webAppListDescription = webAppListDescription;
    }

    public String[] getWebApps() {
        return webApps;
    }

    public void setWebApps(String[] webApps) {
        this.webApps = webApps;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String[] getAcceptedUserName() {
        return acceptedUserName;
    }

    public void setAcceptedUserName(String[] acceptedUserName) {
        this.acceptedUserName = acceptedUserName;
    }

    public String[] getAcceptedRole() {
        return acceptedRole;
    }

    public void setAcceptedRole(String[] acceptedRole) {
        this.acceptedRole = acceptedRole;
    }
}
