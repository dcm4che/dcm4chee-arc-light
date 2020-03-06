package org.dcm4chee.arc.conf.ui;

public class UILanguageProfile {
    private String profileName;
    private String defaultLanguage;
    private String[] acceptedUserRoles = {};
    private String userName;

    public UILanguageProfile(){}

    public UILanguageProfile(String profileName){
        this.setProfileName(profileName);
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public String[] getAcceptedUserRoles() {
        return acceptedUserRoles;
    }

    public void setAcceptedUserRoles(String[] acceptedUserRoles) {
        this.acceptedUserRoles = acceptedUserRoles;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
