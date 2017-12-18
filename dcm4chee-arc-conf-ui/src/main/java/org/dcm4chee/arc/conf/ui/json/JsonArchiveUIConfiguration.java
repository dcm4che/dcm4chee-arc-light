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
        writeUIPermissions(writer, uiConfig.getPermissions());
        writeUIDiffConfigs(writer, uiConfig.getDiffConfigs());
        writeUIDashboardConfigs(writer, uiConfig.getDashboardConfigs());
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

    private void writeUIDiffCriteria(JsonWriter writer, Collection<UIDiffCriteria> uiDiffCriterias) {
        if (uiDiffCriterias.isEmpty())
            return;

        writer.writeStartArray("dcmuiDiffCriteria");
        for (UIDiffCriteria uiDiffCriteria : uiDiffCriterias) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmuiDiffCriteriaTitle", uiDiffCriteria.getTitle(), null);
            writer.writeNotNullOrDef("dicomDescription", uiDiffCriteria.getDescription(), null);
            writer.writeNotDef("dcmuiDiffIncludeMissing", uiDiffCriteria.isIncludeMissing(), false);
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
            writer.writeNotEmpty("dcmQueueName", uiDashboardConfig.getQueueNames());
            writer.writeNotEmpty("dicomDeviceName", uiDashboardConfig.getDeviceNames());
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
                case "dcmuiPermission":
                    loadUIPermissions(uiConfig.getPermissions(), reader);
                    break;
                case "dcmuiDiffConfig":
                    loadUIDiffConfigs(uiConfig.getDiffConfigs(), reader);
                    break;
                case "dcmuiDashboardConfig":
                    loadUIDashboardConfigs(uiConfig.getDashboardConfigs(), reader);
                    break;
            }
        }
    }

    private void loadUIPermissions(Collection<UIPermission> uiPermissions, JsonReader reader) {
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
            uiPermissions.add(uiPermission);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadUIDiffConfigs(Collection<UIDiffConfig> uiDiffConfigs, JsonReader reader) {
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
                        loadUIDiffCriterias(uiDiffConfig.getCriterias(), reader);
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            uiDiffConfigs.add(uiDiffConfig);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadUIDiffCriterias(Collection<UIDiffCriteria> uiDiffCriterias, JsonReader reader) {
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
            uiDiffCriterias.add(uiDiffCriteria);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadUIDashboardConfigs(Collection<UIDashboardConfig> dashboardConfigs, JsonReader reader) {
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
                    case "dcmQueueName":
                        uiDashboardConfig.setQueueNames(reader.stringArray());
                        break;
                    case "dicomDeviceName":
                        uiDashboardConfig.setDeviceNames(reader.stringArray());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            dashboardConfigs.add(uiDashboardConfig);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }
}
