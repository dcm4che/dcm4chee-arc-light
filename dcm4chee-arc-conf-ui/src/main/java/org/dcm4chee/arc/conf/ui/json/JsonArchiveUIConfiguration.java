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

package org.dcm4chee.arc.conf.ui.json;

import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfigurationExtension;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ui.*;

import javax.json.stream.JsonParser;
import java.util.Collection;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since Nov 2017
 */
public class JsonArchiveUIConfiguration extends JsonConfigurationExtension {
    @Override
    protected void storeTo(Device device, JsonWriter writer) {
        UIConfigDeviceExtension ext = device.getDeviceExtension(UIConfigDeviceExtension.class);
        if (ext == null)
            return;

        writer.writeStartArray("dcmuiConfig");
        for (UIConfig uiConfig : ext.getUIConfigs())
            writeTo(uiConfig, writer);
        writer.writeEnd();
    }

    @Override
    public boolean loadDeviceExtension(Device device, JsonReader reader, ConfigurationDelegate config) {
        if (!reader.getString().equals("dcmuiConfig"))
            return false;

        UIConfigDeviceExtension ext = new UIConfigDeviceExtension();
        loadFrom(ext, reader);
        device.addDeviceExtension(ext);
        return true;
    }

    private void writeTo(UIConfig uiConfig, JsonWriter writer) {
        writer.writeStartObject();
        writer.writeNotNullOrDef("dcmuiConfigName", uiConfig.getName(), null);
        writer.writeNotEmpty("dcmuiModalities", uiConfig.getModalities());
        writer.writeNotEmpty("dcmuiWidgetAets", uiConfig.getWidgetAets());
        writer.writeNotNullOrDef("dcmuiXDSInterfaceURL", uiConfig.getXdsUrl(),null);
        writer.writeNotEmpty("dcmuiDefaultWidgetAets", uiConfig.getDefaultWidgetAets());
        writeUIPermissions(writer, uiConfig.getPermissions());
        writeUIDiffConfigs(writer, uiConfig.getDiffConfigs());
        writeUIDashboardConfigs(writer, uiConfig.getDashboardConfigs());
        writeUIElasticsearchConfigs(writer, uiConfig.getElasticsearchConfigs());
        writeUIDeviceURLs(writer, uiConfig.getDeviceURLs());
        writeUIDeviceClusters(writer, uiConfig.getDeviceClusters());
        writeUIFilterTemplate(writer, uiConfig.getFilterTemplates());
        writeUIAetList(writer, uiConfig.getAetLists());
        writer.writeEnd();
    }

    private void writeUIPermissions(JsonWriter writer, Collection<UIPermission> uiPermissions) {
        if (uiPermissions.isEmpty())
            return;

        writer.writeStartArray("dcmuiPermission");
        for (UIPermission uiPermission : uiPermissions) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiPermissionName", uiPermission.getName(), null);
            writer.writeNotNullOrDef("dcmuiAction", uiPermission.getAction(), null);
            writer.writeNotEmpty("dcmuiActionParam", uiPermission.getActionParams());
            writer.writeNotEmpty("dcmAcceptedUserRole", uiPermission.getAcceptedUserRoles());
            writer.writeEnd();
        }
        writer.writeEnd();
    }
    private void writeUIAetList(JsonWriter writer, Collection<UIAetList> uiAetLists) {
        if (uiAetLists.isEmpty())
            return;

        writer.writeStartArray("dcmuiAetConfig");
        for (UIAetList uiAetList : uiAetLists) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiAetListName", uiAetList.getAetListName(), null);
            writer.writeNotNullOrDef("dcmuiAetListDescription", uiAetList.getAetListDescription(), null);
            writer.writeNotNullOrDef("dcmuiMode", uiAetList.getMode(), null);
            writer.writeNotEmpty("dcmuiAets", uiAetList.getAets());
            writer.writeNotEmpty("dcmAcceptedUserRole", uiAetList.getAcceptedRole());
            writer.writeEnd();
        }
        writer.writeEnd();
    }
    private void writeUIDeviceURLs(JsonWriter writer, Collection<UIDeviceURL> uiDeviceURLs) {
        if (uiDeviceURLs.isEmpty())
            return;

        writer.writeStartArray("dcmuiDeviceURLObject");
        for (UIDeviceURL uiDeviceURL : uiDeviceURLs) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiDeviceURLName", uiDeviceURL.getDeviceName(), null);
            writer.writeNotNullOrDef("dcmuiDeviceURL", uiDeviceURL.getDeviceURL(), null);
            writer.writeNotNullOrDef("dcmuiDeviceURLDescription", uiDeviceURL.getDescription(), null);
            writer.writeNotDef("dcmuiDeviceURLInstalled", uiDeviceURL.isInstalled(), true);
            writer.writeEnd();
        }
        writer.writeEnd();
    }
    private void writeUIDeviceClusters(JsonWriter writer, Collection<UIDeviceCluster> uiDeviceClusters) {
        if (uiDeviceClusters.isEmpty())
            return;

        writer.writeStartArray("dcmuiDeviceClusterObject");
        for (UIDeviceCluster uiDeviceCluster : uiDeviceClusters) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiDeviceClusterName", uiDeviceCluster.getClusterName(), null);
            writer.writeNotNullOrDef("dcmuiDeviceClusterDescription", uiDeviceCluster.getDescription(), null);
            writer.writeNotNullOrDef("dcmuiDeviceClusterLoadBalancer", uiDeviceCluster.getLoadBalancer(), null);
            writer.writeNotNullOrDef("dcmuiDeviceClusterKeycloakServer", uiDeviceCluster.getKeycloakServer(), null);
            writer.writeNotEmpty("dcmuiDeviceClusterDevices", uiDeviceCluster.getDevices());
            writer.writeNotDef("dcmuiDeviceClusterInstalled", uiDeviceCluster.isInstalled(), true);
            writer.writeEnd();
        }
        writer.writeEnd();
    }
    private void writeUIFilterTemplate(JsonWriter writer, Collection<UIFiltersTemplate> uiFiltersTemplates) {
        if (uiFiltersTemplates.isEmpty())
            return;

        writer.writeStartArray("dcmuiFilterTemplateObject");
        for (UIFiltersTemplate uiFiltersTemplate : uiFiltersTemplates) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiFilterTemplateGroupName", uiFiltersTemplate.getFilterGroupName(), null);
            writer.writeNotNullOrDef("dcmuiFilterTemplateID", uiFiltersTemplate.getFilterGroupID(), null);
            writer.writeNotNullOrDef("dcmuiFilterTemplateDescription", uiFiltersTemplate.getFilterGroupDescription(), null);
            writer.writeNotNullOrDef("dcmuiFilterTemplateUsername", uiFiltersTemplate.getFilterGroupUsername(), null);
            writer.writeNotNullOrDef("dcmuiFilterTemplateRole", uiFiltersTemplate.getFilterGroupRole(), null);
            writer.writeNotEmpty("dcmuiFilterTemplateFilters", uiFiltersTemplate.getFilters());
            writer.writeNotDef("dcmuiFilterTemplateDefault", uiFiltersTemplate.isDefault(), false);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeUIDiffConfigs(JsonWriter writer, Collection<UIDiffConfig> uiDiffConfigs) {
        if (uiDiffConfigs.isEmpty())
            return;

        writer.writeStartArray("dcmuiDiffConfig");
        for (UIDiffConfig uiDiffConfig : uiDiffConfigs) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiDiffConfigName", uiDiffConfig.getName(), null);
            writer.writeNotNullOrDef("dcmuiDiffCallingAET", uiDiffConfig.getCallingAET(), null);
            writer.writeNotNullOrDef("dcmuiDiffPrimaryCFindSCP", uiDiffConfig.getPrimaryCFindSCP(), null);
            writer.writeNotNullOrDef("dcmuiDiffPrimaryCMoveSCP", uiDiffConfig.getPrimaryCMoveSCP(), null);
            writer.writeNotNullOrDef("dcmuiDiffPrimaryCStoreSCP", uiDiffConfig.getPrimaryCStoreSCP(), null);
            writer.writeNotNullOrDef("dcmuiDiffSecondaryCFindSCP", uiDiffConfig.getSecondaryCFindSCP(), null);
            writer.writeNotNullOrDef("dcmuiDiffSecondaryCMoveSCP", uiDiffConfig.getSecondaryCMoveSCP(), null);
            writer.writeNotNullOrDef("dcmuiDiffSecondaryCStoreSCP", uiDiffConfig.getSecondaryCStoreSCP(), null);
            writeUIDiffCriteria(writer, uiDiffConfig.getCriterias());
            writer.writeEnd();
        }
        writer.writeEnd();
    }
    private void writeUIElasticsearchConfigs(JsonWriter writer, Collection<UIElasticsearchConfig> uiElasticsearchConfigs) {
        if (uiElasticsearchConfigs.isEmpty())
            return;

        writer.writeStartArray("dcmuiElasticsearchConfig");
        for (UIElasticsearchConfig uiElasticsearchConfig : uiElasticsearchConfigs) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiElasticsearchConfigName", uiElasticsearchConfig.getName(), null);
            writeUIElasticsearchURL(writer, uiElasticsearchConfig.getURLS());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeUIElasticsearchURL(JsonWriter writer, Collection<UIElasticsearchURL> uiElasticsearchURLS) {
        if (uiElasticsearchURLS.isEmpty())
            return;

        writer.writeStartArray("dcmuiElasticsearchURLObjects");
        for (UIElasticsearchURL uiElasticsearchURL : uiElasticsearchURLS) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiElasticsearchURLName", uiElasticsearchURL.getUrlName(), null);
            writer.writeNotNullOrDef("dcmuiElasticsearchURL", uiElasticsearchURL.getUrl(),null);
            writer.writeNotNullOrDef("dcmuiElasticsearchURLKeycloakServer", uiElasticsearchURL.getElasticsearchURLKeycloakServer(),null);
            writer.writeNotNullOrDef("dcmuiAuditEnterpriseSiteID", uiElasticsearchURL.getAuditEnterpriseSiteID(),null);
            writer.writeNotDef("dcmuiElasticsearchIsDefault", uiElasticsearchURL.isDefault(), false);
            writer.writeNotDef("dcmuiElasticsearchInstalled", uiElasticsearchURL.isInstalled(), true);
            writer.writeEnd();
        }
        writer.writeEnd();
    }
    private void writeUIDiffCriteria(JsonWriter writer, Collection<UIDiffCriteria> uiDiffCriterias) {
        if (uiDiffCriterias.isEmpty())
            return;

        writer.writeStartArray("dcmuiDiffCriteria");
        for (UIDiffCriteria uiDiffCriteria : uiDiffCriterias) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiDiffCriteriaTitle", uiDiffCriteria.getTitle(), null);
            writer.writeNotNullOrDef("dicomDescription", uiDiffCriteria.getDescription(), null);
            writer.writeNotDef("dcmuiDiffCriteriaNumber", uiDiffCriteria.getNumber(), 0);
            writer.writeNotDef("dcmuiDiffIncludeMissing", uiDiffCriteria.isIncludeMissing(), false);
            writer.writeNotNullOrDef("dcmAttributeSetID", uiDiffCriteria.getAttributeSetID(), null);
            writer.writeNotEmpty("dcmuiDiffGroupButton", uiDiffCriteria.getGroupButtons());
            writer.writeNotEmpty("dcmuiDiffAction", uiDiffCriteria.getActions());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeUIDashboardConfigs(JsonWriter writer, Collection<UIDashboardConfig> dashboardConfigs) {
        if (dashboardConfigs.isEmpty())
            return;

        writer.writeStartArray("dcmuiDashboardConfig");
        for (UIDashboardConfig uiDashboardConfig : dashboardConfigs) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiDashboardConfigName", uiDashboardConfig.getName(), null);
            writer.writeNotEmpty("dcmuiQueueName", uiDashboardConfig.getQueueNames());
            writer.writeNotEmpty("dcmuiExportName", uiDashboardConfig.getExportNames());
            writer.writeNotDef("dcmuiShowStarBlock", uiDashboardConfig.isShowStarBlock(), true);
            writer.writeNotEmpty("dicomuiDeviceName", uiDashboardConfig.getDeviceNames());
            writer.writeNotEmpty("dicomuiIgnoreParams", uiDashboardConfig.getIgnoreParams());
            writer.writeNotEmpty("dicomuiDockerContainer", uiDashboardConfig.getDockerContainers());
            writer.writeNotNullOrDef("dcmuiCountAET", uiDashboardConfig.getCountAet(),null);
            writeUICompareSide(writer, uiDashboardConfig.getCompareSides());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeUICompareSide(JsonWriter writer, Collection<UICompareSide> uiCompareSides) {
        if (uiCompareSides.isEmpty())
            return;

        writer.writeStartArray("dcmuiCompareSideObjects");
        for (UICompareSide uiCompareSide : uiCompareSides) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiCompareSideName", uiCompareSide.getName(), null);
            writer.writeNotNullOrDef("dcmuiCompareSideDescription", uiCompareSide.getDescription(),null);
            writer.writeNotNullOrDef("dcmuiCompareSideCluster", uiCompareSide.getCluster(),null);
            writer.writeNotNullOrDef("dcmuiCompareSideElasticsearch", uiCompareSide.getElasticsearch(),null);
            writer.writeNotNullOrDef("dcmuiCompareSideQueueName", uiCompareSide.getQueueName(),null);
            writer.writeNotDef("dcmuiCompareSideInstalled", uiCompareSide.isInstalled(), true);
            writer.writeEnd();
        }
        writer.writeEnd();
    }
    private void loadFrom(UIConfigDeviceExtension ext, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            UIConfig uiConfig = new UIConfig();
            loadFrom(uiConfig, reader);
            reader.expect(JsonParser.Event.END_OBJECT);
            ext.addUIConfig(uiConfig);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadFrom(UIConfig uiConfig, JsonReader reader) {
        while (reader.next() == JsonParser.Event.KEY_NAME) {
            switch (reader.getString()) {
                case "dcmuiConfigName":
                    uiConfig.setName(reader.stringValue());
                    break;
                case "dcmuiModalities":
                    uiConfig.setModalities(reader.stringArray());
                    break;
                case "dcmuiWidgetAets":
                    uiConfig.setWidgetAets(reader.stringArray());
                    break;
                case "dcmuiXDSInterfaceURL":
                    uiConfig.setXdsUrl(reader.stringValue());
                    break;
                case "dcmuiDefaultWidgetAets":
                    uiConfig.setDefaultWidgetAets(reader.stringArray());
                    break;
                case "dcmuiPermission":
                    loadUIPermissions(uiConfig, reader);
                    break;
                case "dcmuiAetConfig":
                    loadUIAetList(uiConfig, reader);
                    break;
                case "dcmuiDiffConfig":
                    loadUIDiffConfigs(uiConfig, reader);
                    break;
                case "dcmuiDashboardConfig":
                    loadUIDashboardConfigs(uiConfig, reader);
                    break;
                case "dcmuiElasticsearchConfig":
                    loadUIElasticsearchConfigs(uiConfig, reader);
                    break;
                case "dcmuiDeviceURLObject":
                    loadUIDeviceURLs(uiConfig, reader);
                    break;
                case "dcmuiDeviceClusterObject":
                    loadUIDeviceClusters(uiConfig, reader);
                    break;
                case "dcmuiFilterTemplateObject":
                    loadUIFilterTemplate(uiConfig, reader);
                    break;
                default:
                    reader.skipUnknownProperty();
            }
        }
    }

    private void loadUIPermissions(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIPermission uiPermission = new UIPermission();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiPermissionName":
                        uiPermission.setName(reader.stringValue());
                        break;
                    case "dcmuiAction":
                        uiPermission.setAction(reader.stringValue());
                        break;
                    case "dcmuiActionParam":
                        uiPermission.setActionParams(reader.stringArray());
                        break;
                    case "dcmAcceptedUserRole":
                        uiPermission.setAcceptedUserRoles(reader.stringArray());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addPermission(uiPermission);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadUIAetList(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIAetList uiAetList = new UIAetList();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiAetListName":
                        uiAetList.setAetListName(reader.stringValue());
                        break;
                    case "dcmuiAetListDescription":
                        uiAetList.setAetListDescription(reader.stringValue());
                        break;
                    case "dcmuiMode":
                        uiAetList.setMode(reader.stringValue());
                        break;
                    case "dcmuiAets":
                        uiAetList.setAets(reader.stringArray());
                        break;
                    case "dcmAcceptedUserRole":
                        uiAetList.setAcceptedRole(reader.stringArray());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addAetList(uiAetList);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
    private void loadUIDeviceURLs(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIDeviceURL uiDeviceURL = new UIDeviceURL();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiDeviceURLName":
                        uiDeviceURL.setDeviceName(reader.stringValue());
                        break;
                    case "dcmuiDeviceURL":
                        uiDeviceURL.setDeviceURL(reader.stringValue());
                        break;
                    case "dcmuiDeviceURLDescription":
                        uiDeviceURL.setDescription(reader.stringValue());
                        break;
                    case "dcmuiDeviceURLInstalled":
                        uiDeviceURL.setInstalled(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addDeviceURL(uiDeviceURL);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
    private void loadUIDeviceClusters(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIDeviceCluster uiDeviceCluster = new UIDeviceCluster();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiDeviceClusterName":
                        uiDeviceCluster.setClusterName(reader.stringValue());
                        break;
                    case "dcmuiDeviceClusterDevices":
                        uiDeviceCluster.setDevices(reader.stringArray());
                        break;
                    case "dcmuiDeviceClusterDescription":
                        uiDeviceCluster.setDescription(reader.stringValue());
                        break;
                    case "dcmuiDeviceClusterLoadBalancer":
                        uiDeviceCluster.setLoadBalancer(reader.stringValue());
                        break;
                    case "dcmuiDeviceClusterKeycloakServer":
                        uiDeviceCluster.setKeycloakServer(reader.stringValue());
                        break;
                    case "dcmuiDeviceClusterInstalled":
                        uiDeviceCluster.setInstalled(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addDeviceCluster(uiDeviceCluster);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
    private void loadUIFilterTemplate(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIFiltersTemplate uiFiltersTemplate = new UIFiltersTemplate();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiFilterTemplateGroupName":
                        uiFiltersTemplate.setFilterGroupName(reader.stringValue());
                        break;
                    case "dcmuiFilterTemplateID":
                        uiFiltersTemplate.setFilterGroupID(reader.stringValue());
                        break;
                    case "dcmuiFilterTemplateDescription":
                        uiFiltersTemplate.setFilterGroupDescription(reader.stringValue());
                        break;
                    case "dcmuiFilterTemplateUsername":
                        uiFiltersTemplate.setFilterGroupUsername(reader.stringValue());
                        break;
                    case "dcmuiFilterTemplateRole":
                        uiFiltersTemplate.setFilterGroupRole(reader.stringValue());
                        break;
                    case "dcmuiFilterTemplateFilters":
                        uiFiltersTemplate.setFilters(reader.stringArray());
                        break;
                    case "dcmuiFilterTemplateDefault":
                        uiFiltersTemplate.setDefault(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addFilterTemplate(uiFiltersTemplate);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadUIElasticsearchConfigs(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIElasticsearchConfig uiElasticsearchConfig = new UIElasticsearchConfig();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiElasticsearchConfigName":
                        uiElasticsearchConfig.setName(reader.stringValue());
                        break;
                    case "dcmuiElasticsearchURLObjects":
                        loadUIElasticsearchURL(uiElasticsearchConfig, reader);
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addElasticsearchConfig(uiElasticsearchConfig);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
    private void loadUIElasticsearchURL(UIElasticsearchConfig uiDiffConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIElasticsearchURL uiElasticsearchURL = new UIElasticsearchURL();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiElasticsearchURLName":
                        uiElasticsearchURL.setUrlName(reader.stringValue());
                        break;
                    case "dcmuiElasticsearchURL":
                        uiElasticsearchURL.setUrl(reader.stringValue());
                        break;
                    case "dcmuiElasticsearchURLKeycloakServer":
                        uiElasticsearchURL.setElasticsearchURLKeycloakServer(reader.stringValue());
                        break;
                    case "dcmuiAuditEnterpriseSiteID":
                        uiElasticsearchURL.setAuditEnterpriseSiteID(reader.stringValue());
                        break;
                    case "dcmuiElasticsearchIsDefault":
                        uiElasticsearchURL.setDefault(reader.booleanValue());
                        break;
                    case "dcmuiElasticsearchInstalled":
                        uiElasticsearchURL.setInstalled(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiDiffConfig.addURL(uiElasticsearchURL);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
    private void loadUIDiffConfigs(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIDiffConfig uiDiffConfig = new UIDiffConfig();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiDiffConfigName":
                        uiDiffConfig.setName(reader.stringValue());
                        break;
                    case "dcmuiDiffCallingAET":
                        uiDiffConfig.setCallingAET(reader.stringValue());
                        break;
                    case "dcmuiDiffPrimaryCFindSCP":
                        uiDiffConfig.setPrimaryCFindSCP(reader.stringValue());
                        break;
                    case "dcmuiDiffPrimaryCMoveSCP":
                        uiDiffConfig.setPrimaryCMoveSCP(reader.stringValue());
                        break;
                    case "dcmuiDiffPrimaryCStoreSCP":
                        uiDiffConfig.setPrimaryCStoreSCP(reader.stringValue());
                        break;
                    case "dcmuiDiffSecondaryCFindSCP":
                        uiDiffConfig.setSecondaryCFindSCP(reader.stringValue());
                        break;
                    case "dcmuiDiffSecondaryCMoveSCP":
                        uiDiffConfig.setSecondaryCMoveSCP(reader.stringValue());
                        break;
                    case "dcmuiDiffSecondaryCStoreSCP":
                        uiDiffConfig.setSecondaryCStoreSCP(reader.stringValue());
                        break;
                    case "dcmuiDiffCriteria":
                        loadUIDiffCriterias(uiDiffConfig, reader);
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addDiffConfig(uiDiffConfig);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadUIDiffCriterias(UIDiffConfig uiDiffConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIDiffCriteria uiDiffCriteria = new UIDiffCriteria();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiDiffCriteriaTitle":
                        uiDiffCriteria.setTitle(reader.stringValue());
                        break;
                    case "dicomDescription":
                        uiDiffCriteria.setDescription(reader.stringValue());
                        break;
                    case "dcmuiDiffCriteriaNumber":
                        uiDiffCriteria.setNumber(reader.intValue());
                        break;
                    case "dcmuiDiffIncludeMissing":
                        uiDiffCriteria.setIncludeMissing(reader.booleanValue());
                        break;
                    case "dcmAttributeSetID":
                        uiDiffCriteria.setAttributeSetID(reader.stringValue());
                        break;
                    case "dcmuiDiffGroupButton":
                        uiDiffCriteria.setGroupButtons(reader.stringArray());
                        break;
                    case "dcmuiDiffAction":
                        uiDiffCriteria.setActions(reader.stringArray());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiDiffConfig.addCriteria(uiDiffCriteria);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadUIDashboardConfigs(UIConfig uiConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UIDashboardConfig uiDashboardConfig = new UIDashboardConfig();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiDashboardConfigName":
                        uiDashboardConfig.setName(reader.stringValue());
                        break;
                    case "dcmuiQueueName":
                        uiDashboardConfig.setQueueNames(reader.stringArray());
                        break;
                    case "dcmuiExportName":
                        uiDashboardConfig.setExportNames(reader.stringArray());
                        break;
                    case "dicomuiDeviceName":
                        uiDashboardConfig.setDeviceNames(reader.stringArray());
                        break;
                    case "dicomuiIgnoreParams":
                        uiDashboardConfig.setIgnoreParams(reader.stringArray());
                        break;
                    case "dcmuiShowStarBlock":
                        uiDashboardConfig.setShowStarBlock(reader.booleanValue());
                        break;
                    case "dicomuiDockerContainer":
                        uiDashboardConfig.setDockerContainers(reader.stringArray());
                        break;
                    case "dcmuiCountAET":
                        uiDashboardConfig.setCountAet(reader.stringValue());
                        break;
                    case "dcmuiCompareSideObjects":
                        loadCompareSides(uiDashboardConfig, reader);
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiConfig.addDashboardConfig(uiDashboardConfig);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
    private void loadCompareSides(UIDashboardConfig uiDashboardConfig, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            UICompareSide uiCompareSide = new UICompareSide();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmuiCompareSideName":
                        uiCompareSide.setName(reader.stringValue());
                        break;
                    case "dcmuiCompareSideDescription":
                        uiCompareSide.setDescription(reader.stringValue());
                        break;
                    case "dcmuiCompareSideCluster":
                        uiCompareSide.setCluster(reader.stringValue());
                        break;
                    case "dcmuiCompareSideElasticsearch":
                        uiCompareSide.setElasticsearch(reader.stringValue());
                        break;
                    case "dcmuiCompareSideQueueName":
                        uiCompareSide.setQueueName(reader.stringValue());
                        break;
                    case "dcmuiCompareSideInstalled":
                        uiCompareSide.setInstalled(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiDashboardConfig.addCompareSide(uiCompareSide);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
}
