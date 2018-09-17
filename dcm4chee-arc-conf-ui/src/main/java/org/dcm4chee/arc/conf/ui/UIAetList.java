package org.dcm4chee.arc.conf.ui;

/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since Sep 2018
 */
public class UIAetList {
    private String aetListName;
    private String aetListDescription;
    private String mode;
    private String[] aets = {};
    private String[] acceptedRole = {};

    public UIAetList() {
    }

    public UIAetList(String name) {
        setAetListName(name);
    }

    public String getAetListName() {
        return aetListName;
    }

    public String getAetListDescription() {
        return aetListDescription;
    }

    public void setAetListDescription(String aetListDescription) {
        this.aetListDescription = aetListDescription;
    }

    public void setAetListName(String aetListName) {
        this.aetListName = aetListName;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String[] getAets() {
        return aets;
    }

    public void setAets(String[] aets) {
        this.aets = aets;
    }

    public String[] getAcceptedRole() {
        return acceptedRole;
    }

    public void setAcceptedRole(String[] acceptedRole) {
        this.acceptedRole = acceptedRole;
    }
}
