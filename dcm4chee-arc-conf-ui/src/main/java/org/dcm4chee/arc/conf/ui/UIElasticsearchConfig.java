package org.dcm4chee.arc.conf.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since May 2018
 */
public class UIElasticsearchConfig {
    private String name;
    private final Map<String,UIElasticsearchURL> urls = new HashMap<>();

    public UIElasticsearchConfig(){}
    public UIElasticsearchConfig(String commonName){
        this.setName(commonName);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addURL(UIElasticsearchURL url){
        this.urls.put(url.getUrlName(),url);
    }

    public UIElasticsearchURL removeURL(String name){
        return this.urls.remove(name);
    }

    public UIElasticsearchURL getURL(String name){
        return this.urls.get(name);
    }

    public Collection<UIElasticsearchURL> getURLS(){
        return this.urls.values();
    }
}


