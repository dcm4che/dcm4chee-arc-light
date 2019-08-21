/*
 * ** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ** BEGIN LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf.ui;

import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since Nov 2017
 */
public class UIConfig {

    private String name;
    private String[] modalities = {};
    private String[] widgetAets = {};
    private String xdsUrl;
    private String[] defaultWidgetAets = {};
    private Map<String, UIPermission> permissions = new HashMap<>();
    private Map<String, UIDiffConfig> diffConfigs = new HashMap<>();
    private Map<String, UIDashboardConfig> dashboardConfigs = new HashMap<>();
    private Map<String, UIElasticsearchConfig> elasticsearchConfigs = new HashMap<>();
    private Map<String, UIDeviceURL> deviceURL = new HashMap<>();
    private Map<String, UIDeviceCluster> deviceCluster = new HashMap<>();
    private Map<String, UIFiltersTemplate> filterTemplatte = new HashMap<>();
    private Map<String, UIAetList> aetList  = new HashMap<>();

    public UIConfig() {
    }

    public UIConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getModalities() {
        return modalities;
    }

    public void setModalities(String[] modalities) {
        this.modalities = modalities;
    }

    public String[] getWidgetAets() {
        return widgetAets;
    }

    public void setWidgetAets(String[] widgetAets) {
        this.widgetAets = widgetAets;
    }

    public String getXdsUrl() {
        return xdsUrl;
    }

    public void setXdsUrl(String xdsUrl) {
        this.xdsUrl = xdsUrl;
    }

    public String[] getDefaultWidgetAets() {
        return defaultWidgetAets;
    }

    public void setDefaultWidgetAets(String[] defaultWidgetAets) {
        this.defaultWidgetAets = defaultWidgetAets;
    }

    public UIPermission getPermission(String name) {
        return permissions.get(name);
    }

    public void addPermission(UIPermission permission) {
        permissions.put(permission.getName(), permission);
    }

    public UIPermission removePermission(String name) {
        return permissions.remove(name);
    }

    public Collection<UIPermission> getPermissions() {
        return permissions.values();
    }

    public UIDiffConfig getDiffConfig(String name) {
        return diffConfigs.get(name);
    }

    public void addDiffConfig(UIDiffConfig diffConfig) {
        diffConfigs.put(diffConfig.getName(), diffConfig);
    }

    public UIDiffConfig removeDiffConfig(String name) {
        return diffConfigs.remove(name);
    }

    public Collection<UIDiffConfig> getDiffConfigs() {
        return diffConfigs.values();
    }

    public UIDashboardConfig getDashboardConfig(String name) {
        return dashboardConfigs.get(name);
    }

    public void addDashboardConfig(UIDashboardConfig dashboardConfig) {
        dashboardConfigs.put(dashboardConfig.getName(), dashboardConfig);
    }

    public UIDashboardConfig removeDashboardConfig(String name) {
        return dashboardConfigs.remove(name);
    }

    public Collection<UIDashboardConfig> getDashboardConfigs() {
        return dashboardConfigs.values();
    }

    public UIElasticsearchConfig getElasticsearchConfig(String name) {
        return elasticsearchConfigs.get(name);
    }

    public void addElasticsearchConfig(UIElasticsearchConfig elasticsearchConfig) {
        elasticsearchConfigs.put(elasticsearchConfig.getName(), elasticsearchConfig);
    }

    public UIElasticsearchConfig removeElasticsearchConfig(String name) {
        return elasticsearchConfigs.remove(name);
    }

    public Collection<UIElasticsearchConfig> getElasticsearchConfigs() {
        return elasticsearchConfigs.values();
    }

    public UIDeviceURL getDeviceURL(String name) {
        return deviceURL.get(name);
    }

    public void addDeviceURL(UIDeviceURL permission) {
        deviceURL.put(permission.getDeviceName(), permission);
    }

    public UIDeviceURL removeDeviceURL(String name) {
        return deviceURL.remove(name);
    }

    public Collection<UIDeviceURL> getDeviceURLs() {
        return deviceURL.values();
    }

    public UIDeviceCluster getDeviceCluster(String name) {
        return deviceCluster.get(name);
    }

    public void addDeviceCluster(UIDeviceCluster cluster) {
        deviceCluster.put(cluster.getClusterName(), cluster);
    }

    public UIDeviceCluster removeDeviceCluster(String name) {
        return deviceCluster.remove(name);
    }

    public Collection<UIDeviceCluster> getDeviceClusters() {
        return deviceCluster.values();
    }


    public UIFiltersTemplate getFilterTemplate(String id) {
        return filterTemplatte.get(id);
    }

    public void addFilterTemplate(UIFiltersTemplate filtersTemplate) {
        filterTemplatte.put(filtersTemplate.getFilterGroupName(), filtersTemplate);
    }

    public UIFiltersTemplate removeFilterTemplate(String name) {
        return filterTemplatte.remove(name);
    }

    public Collection<UIFiltersTemplate> getFilterTemplates() {
        return filterTemplatte.values();
    }


    public void setAetList(Map<String, UIAetList> aetList) {
        this.aetList = aetList;
    }



    public UIAetList getAetList(String name) {
        return this.aetList.get(name);
    }

    public void addAetList(UIAetList aetList) {
        this.aetList.put(aetList.getAetListName(), aetList);
    }

    public UIAetList removeAetList(String name){
        return this.aetList.remove(name);
    }

    public Collection<UIAetList> getAetLists() {
        return this.aetList.values();
    }
}
