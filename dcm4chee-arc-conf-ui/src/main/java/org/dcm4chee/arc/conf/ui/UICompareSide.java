package org.dcm4chee.arc.conf.ui;

public class UICompareSide {
    private String name;
    private String description;
    private String cluster;
    private String elasticsearch;
    private String queueName;
    private boolean installed = true;

    public UICompareSide() {
    }

    public UICompareSide(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(String elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }
}
