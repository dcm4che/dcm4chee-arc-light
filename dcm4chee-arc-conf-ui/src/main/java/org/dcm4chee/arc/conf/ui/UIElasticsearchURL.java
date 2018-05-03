package org.dcm4chee.arc.conf.ui;

/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since May 2018
 */
public class UIElasticsearchURL {
    private String urlName;
    private String url;

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
}
