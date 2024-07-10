package org.dcm4chee.arc.conf.ui;

public class UITenantConfig {

    public UITenantConfig(){}
    public UITenantConfig(String name){
        this.setTenantConfigName(name);
    }
    private String tenantConfigName;
    private String tenantConfigDescription;
    private String[] mwlWorklistLabels = {};
    private String[] mwlUWLWorklistLabels = {};
    private String[] applicationEntities = {};
    private String[] exporterDescriptions = {};
    private String[] storageDescriptors = {};
    private String[] storeAccessControlIDs = {};
    private String[] queues = {};
    private String[] acceptedRole = {};

    public String getTenantConfigName() {
        return tenantConfigName;
    }

    public void setTenantConfigName(String tenantConfigName) {
        this.tenantConfigName = tenantConfigName;
    }

    public String getTenantConfigDescription() {
        return tenantConfigDescription;
    }

    public void setTenantConfigDescription(String tenantConfigDescription) {
        this.tenantConfigDescription = tenantConfigDescription;
    }

    public String[] getMwlWorklistLabels() {
        return mwlWorklistLabels;
    }

    public void setMwlWorklistLabels(String[] mwlWorklistLabels) {
        this.mwlWorklistLabels = mwlWorklistLabels;
    }

    public String[] getExporterDescriptions() {
        return exporterDescriptions;
    }

    public String[] getMwlUWLWorklistLabels() {
        return mwlUWLWorklistLabels;
    }

    public void setMwlUWLWorklistLabels(String[] mwlUWLWorklistLabels) {
        this.mwlUWLWorklistLabels = mwlUWLWorklistLabels;
    }

    public void setExporterDescriptions(String[] exporterDescriptions) {
        this.exporterDescriptions = exporterDescriptions;
    }

    public String[] getApplicationEntities() {
        return applicationEntities;
    }

    public void setApplicationEntities(String[] applicationEntities) {
        this.applicationEntities = applicationEntities;
    }

    public String[] getStorageDescriptors() {
        return storageDescriptors;
    }

    public void setStorageDescriptors(String[] storageDescriptors) {
        this.storageDescriptors = storageDescriptors;
    }

    public String[] getQueues() {
        return queues;
    }

    public void setQueues(String[] queues) {
        this.queues = queues;
    }

    public String[] getAcceptedRole() {
        return acceptedRole;
    }

    public void setAcceptedRole(String[] acceptedRole) {
        this.acceptedRole = acceptedRole;
    }

    public String[] getStoreAccessControlIDs() {
        return storeAccessControlIDs;
    }

    public void setStoreAccessControlIDs(String[] storeAccessControlIDs) {
        this.storeAccessControlIDs = storeAccessControlIDs;
    }
}
