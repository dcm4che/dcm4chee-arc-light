package org.dcm4chee.arc.conf.ui;

/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since May 2018
 */
public class UIElasticsearchURL {
    private String urlName;
    private String url;
    private String auditEnterpriseSiteID;
    private String elasticsearchURLKeycloakServer;
    private boolean isDefault;
    private boolean installed = true;

    public UIElasticsearchURL(){}

    public UIElasticsearchURL(String urlName){
        this.setUrlName(urlName);
    }

    public String getUrlName() {
        return urlName;
    }

    public void setUrlName(String urlName) {
        this.urlName = urlName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAuditEnterpriseSiteID() {
        return auditEnterpriseSiteID;
    }

    public void setAuditEnterpriseSiteID(String auditEnterpriseSiteID) {
        this.auditEnterpriseSiteID = auditEnterpriseSiteID;
    }

    public String getElasticsearchURLKeycloakServer() {
        return elasticsearchURLKeycloakServer;
    }

    public void setElasticsearchURLKeycloakServer(String elasticsearchURLKeycloakServer) {
        this.elasticsearchURLKeycloakServer = elasticsearchURLKeycloakServer;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }
}
