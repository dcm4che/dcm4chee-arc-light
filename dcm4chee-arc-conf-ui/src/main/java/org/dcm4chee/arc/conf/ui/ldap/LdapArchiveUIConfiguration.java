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

package org.dcm4chee.arc.conf.ui.ldap;

import org.dcm4che3.conf.api.ConfigurationChanges;
import org.dcm4che3.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che3.conf.ldap.LdapUtils;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ui.*;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2017
 */
public class LdapArchiveUIConfiguration extends LdapDicomConfigurationExtension {

    @Override
    protected void storeChilds(ConfigurationChanges diffs, String deviceDN, Device device)
            throws NamingException {
        UIConfigDeviceExtension ext = device.getDeviceExtension(UIConfigDeviceExtension.class);
        if (ext == null)
            return;

        for (UIConfig ui : ext.getUIConfigs())
            store(diffs, deviceDN, ui);
    }

    private String uiConfigDN(UIConfig uiConfig, String deviceDN) {
        return LdapUtils.dnOf("dcmuiConfigName" , uiConfig.getName(), deviceDN);
    }

    private void store(ConfigurationChanges diffs, String deviceDN, UIConfig uiConfig)
            throws NamingException {
        String uiConfigDN = uiConfigDN(uiConfig, deviceDN);
        ConfigurationChanges.ModifiedObject ldapObj =
                ConfigurationChanges.addModifiedObject(diffs, uiConfigDN, ConfigurationChanges.ChangeType.C);
        config.createSubcontext(uiConfigDN,
                storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                        uiConfig, new BasicAttributes(true)));

        for (UIPermission uiPermission : uiConfig.getPermissions())
            storePermission(diffs, uiPermission, uiConfigDN);
        for (UIDiffConfig uiDiffConfig : uiConfig.getDiffConfigs())
            storeDiffConfig(diffs, uiDiffConfig, uiConfigDN);
        for (UIDashboardConfig uiDashboardConfig : uiConfig.getDashboardConfigs())
            storeDashboardConfig(diffs, uiDashboardConfig, uiConfigDN);
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIConfig uiConfig, Attributes attrs) {
        attrs.put("objectclass", "dcmuiConfig");
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiConfigName", uiConfig.getName(), null);
        return attrs;
    }

    private void storePermission(ConfigurationChanges diffs, UIPermission uiPermission, String uiConfigDN)
            throws NamingException {
        String uiPermissionDN = LdapUtils.dnOf("dcmuiPermissionName", uiPermission.getName(), uiConfigDN);
        ConfigurationChanges.ModifiedObject ldapObj1 =
                ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiPermissionDN, ConfigurationChanges.ChangeType.C);
        config.createSubcontext(uiPermissionDN, storeTo(ldapObj1, uiPermission, new BasicAttributes(true)));
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIPermission uiPermission, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiPermission"));
        attrs.put(new BasicAttribute("dcmuiPermissionName", uiPermission.getName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiAction", uiPermission.getAction(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiActionParam", uiPermission.getActionParams());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedUserRole", uiPermission.getAcceptedUserRoles());
        return attrs;
    }

    private void storeDiffConfig(ConfigurationChanges diffs, UIDiffConfig uiDiffConfig, String uiConfigDN)
            throws NamingException {
        String uiDiffConfigDN = LdapUtils.dnOf("dcmuiDiffConfigName", uiDiffConfig.getName(), uiConfigDN);
        config.createSubcontext(uiDiffConfigDN, storeTo(diffs, uiDiffConfigDN, uiDiffConfig, new BasicAttributes(true)));
        for (UIDiffCriteria uiDiffCriteria : uiDiffConfig.getCriterias())
            storeDiffCriteria(diffs, uiDiffConfigDN, uiDiffCriteria);
    }

    private Attributes storeTo(ConfigurationChanges diffs, String uiDiffConfigDN, UIDiffConfig uiDiffConfig, Attributes attrs) {
        ConfigurationChanges.ModifiedObject ldapObj =
                ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDiffConfigDN, ConfigurationChanges.ChangeType.C);
        attrs.put(new BasicAttribute("objectclass", "dcmuiDiffConfig"));
        attrs.put(new BasicAttribute("dcmuiDiffConfigName", uiDiffConfig.getName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDiffCallingAET", uiDiffConfig.getCallingAET(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDiffPrimaryCFindSCP", uiDiffConfig.getPrimaryCFindSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDiffPrimaryCMoveSCP", uiDiffConfig.getPrimaryCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDiffPrimaryCStoreSCP", uiDiffConfig.getPrimaryCStoreSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDiffSecondaryCFindSCP", uiDiffConfig.getSecondaryCFindSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDiffSecondaryCMoveSCP", uiDiffConfig.getSecondaryCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDiffSecondaryCStoreSCP", uiDiffConfig.getSecondaryCStoreSCP(), null);
        return attrs;
    }

    private void storeDiffCriteria(ConfigurationChanges diffs, String uiDiffConfigDN, UIDiffCriteria uiDiffCriteria)
            throws NamingException {
        String uiDiffCriteriaDN = LdapUtils.dnOf("dcmuiDiffCriteriaTitle", uiDiffCriteria.getTitle(), uiDiffConfigDN);
        ConfigurationChanges.ModifiedObject ldapObj =
                ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDiffCriteriaDN, ConfigurationChanges.ChangeType.C);
        config.createSubcontext(uiDiffCriteriaDN, storeTo(ldapObj, uiDiffCriteria, new BasicAttributes(true)));
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIDiffCriteria uiDiffCriteria, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiDiffCriteria"));
        attrs.put(new BasicAttribute("dcmuiDiffCriteriaTitle", uiDiffCriteria.getTitle()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomDescription", uiDiffCriteria.getDescription(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmuiDiffCriteriaNumber", uiDiffCriteria.getNumber(), 0);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmuiDiffIncludeMissing", uiDiffCriteria.isIncludeMissing(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAttributeSetID", uiDiffCriteria.getAttributeSetID(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiDiffGroupButton", uiDiffCriteria.getGroupButtons());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiDiffAction", uiDiffCriteria.getActions());
        return attrs;
    }

    private void storeDashboardConfig(ConfigurationChanges diffs, UIDashboardConfig uiDashboardConfig, String uiConfigDN)
            throws NamingException {
        String uiDashboardConfigDN = LdapUtils.dnOf("dcmuiDashboardConfigName", uiDashboardConfig.getName(), uiConfigDN);
        ConfigurationChanges.ModifiedObject ldapObj1 =
                ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDashboardConfigDN, ConfigurationChanges.ChangeType.C);
        config.createSubcontext(uiDashboardConfigDN, storeTo(ldapObj1, uiDashboardConfig, new BasicAttributes(true)));
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIDashboardConfig uiDashboardConfig, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiDashboardConfig"));
        attrs.put(new BasicAttribute("dcmuiDashboardConfigName", uiDashboardConfig.getName()));
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmQueueName", uiDashboardConfig.getQueueNames());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dicomDeviceName", uiDashboardConfig.getDeviceNames());
        return attrs;
    }

    @Override
    protected void loadChilds(Device device, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmuiConfig)");
        try {
            if (!ne.hasMore())
                return;

            UIConfigDeviceExtension ext = new UIConfigDeviceExtension();
            device.addDeviceExtension(ext);
            do {
                ext.addUIConfig(loadUIConfig(ne.next(), deviceDN));
            } while (ne.hasMore());
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private UIConfig loadUIConfig(SearchResult sr, String deviceDN) throws NamingException {
        Attributes attrs = sr.getAttributes();
        UIConfig uiConfig = new UIConfig(LdapUtils.stringValue(attrs.get("dcmuiConfigName"), null));
        String uiConfigDN = uiConfigDN(uiConfig, deviceDN);
        loadPermissions(uiConfig, uiConfigDN);
        loadDiffConfigs(uiConfig, uiConfigDN);
        loadDashboardConfigs(uiConfig, uiConfigDN);
        return uiConfig;
    }

    private void loadPermissions(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiPermission)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIPermission uiPermission = new UIPermission((String) attrs.get("dcmuiPermissionName").get());
                uiPermission.setAction(LdapUtils.stringValue(attrs.get("dcmuiAction"), null));
                uiPermission.setActionParams(LdapUtils.stringArray(attrs.get("dcmuiActionParam")));
                uiPermission.setAcceptedUserRoles(LdapUtils.stringArray(attrs.get("dcmAcceptedUserRole")));
                uiConfig.addPermission(uiPermission);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadDiffConfigs(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiDiffConfig)");

        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIDiffConfig uiDiffConfig = new UIDiffConfig((String) attrs.get("dcmuiDiffConfigName").get());
                String uiDiffConfigDN = LdapUtils.dnOf("dcmuiDiffConfigName" , uiDiffConfig.getName(), uiConfigDN);
                uiDiffConfig.setCallingAET(LdapUtils.stringValue(attrs.get("dcmuiDiffCallingAET"), null));
                uiDiffConfig.setPrimaryCFindSCP(LdapUtils.stringValue(attrs.get("dcmuiDiffPrimaryCFindSCP"), null));
                uiDiffConfig.setPrimaryCMoveSCP(LdapUtils.stringValue(attrs.get("dcmuiDiffPrimaryCMoveSCP"), null));
                uiDiffConfig.setPrimaryCStoreSCP(LdapUtils.stringValue(attrs.get("dcmuiDiffPrimaryCStoreSCP"), null));
                uiDiffConfig.setSecondaryCFindSCP(LdapUtils.stringValue(attrs.get("dcmuiDiffSecondaryCFindSCP"), null));
                uiDiffConfig.setSecondaryCMoveSCP(LdapUtils.stringValue(attrs.get("dcmuiDiffSecondaryCMoveSCP"), null));
                uiDiffConfig.setSecondaryCStoreSCP(LdapUtils.stringValue(attrs.get("dcmuiDiffSecondaryCStoreSCP"), null));
                loadDiffCriterias(uiDiffConfig, uiDiffConfigDN);
                uiConfig.addDiffConfig(uiDiffConfig);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadDiffCriterias(UIDiffConfig uiDiffConfig, String uiDiffConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiDiffConfigDN, "(objectclass=dcmuiDiffCriteria)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIDiffCriteria uiDiffCriteria = new UIDiffCriteria((String) attrs.get("dcmuiDiffCriteriaTitle").get());
                uiDiffCriteria.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                uiDiffCriteria.setNumber(LdapUtils.intValue(attrs.get("dcmuiDiffCriteriaNumber"), 0));
                uiDiffCriteria.setIncludeMissing(LdapUtils.booleanValue(attrs.get("dcmuiDiffIncludeMissing"), false));
                uiDiffCriteria.setAttributeSetID(LdapUtils.stringValue(attrs.get("dcmAttributeSetID"), null));
                uiDiffCriteria.setActions(LdapUtils.stringArray(attrs.get("dcmuiDiffAction")));
                uiDiffCriteria.setGroupButtons(LdapUtils.stringArray(attrs.get("dcmuiDiffGroupButton")));
                uiDiffConfig.addCriteria(uiDiffCriteria);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadDashboardConfigs(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiDashboardConfig)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIDashboardConfig uiDashboardConfig = new UIDashboardConfig((String) attrs.get("dcmuiDashboardConfigName").get());
                uiDashboardConfig.setQueueNames(LdapUtils.stringArray(attrs.get("dcmQueueName")));
                uiDashboardConfig.setDeviceNames(LdapUtils.stringArray(attrs.get("dicomDeviceName")));
                uiConfig.addDashboardConfig(uiDashboardConfig);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    @Override
    protected void mergeChilds(ConfigurationChanges diffs, Device prev, Device device, String deviceDN)
            throws NamingException {
        UIConfigDeviceExtension prevUIConfigExt = prev.getDeviceExtension(UIConfigDeviceExtension.class);
        UIConfigDeviceExtension uiConfigExt = device.getDeviceExtension(UIConfigDeviceExtension.class);
        if (prevUIConfigExt != null)
            for (UIConfig prevUIConfig : prevUIConfigExt.getUIConfigs()) {
                if (uiConfigExt == null || uiConfigExt.getUIConfig(prevUIConfig.getName()) == null) {
                    String dn = uiConfigDN(prevUIConfig, deviceDN);
                    config.destroySubcontextWithChilds(dn);
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
                }
            }

        if (uiConfigExt == null)
            return;

        for (UIConfig uiConfig : uiConfigExt.getUIConfigs()) {
            if (prevUIConfigExt == null || prevUIConfigExt.getUIConfig(uiConfig.getName()) == null)
                store(diffs, deviceDN, uiConfig);
            else
                merge(diffs, prevUIConfigExt.getUIConfig(uiConfig.getName()), uiConfig, deviceDN);
        }
    }

    private void merge(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String deviceDN) throws NamingException {
        String uiConfigDN = uiConfigDN(uiConfig, deviceDN);
        ConfigurationChanges.ModifiedObject ldapObj =
                ConfigurationChanges.addModifiedObject(diffs, uiConfigDN, ConfigurationChanges.ChangeType.U);
        mergePermissions(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeDiffConfigs(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeDashboardConfigs(diffs, prevUIConfig, uiConfig, uiConfigDN);
    }

    private void mergePermissions(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIPermission prevUIPermission : prevUIConfig.getPermissions()) {
            String prevUIPermissionName = prevUIPermission.getName();
            if (uiConfig.getPermission(prevUIPermissionName) == null) {
                String dn = LdapUtils.dnOf("dcmuiPermissionName", prevUIPermissionName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIPermission uiPermission : uiConfig.getPermissions()) {
            String uiPermissionName = uiPermission.getName();
            String dn = LdapUtils.dnOf("dcmuiPermissionName", uiPermissionName, uiConfigDN);
            UIPermission prevUIPermission = prevUIConfig.getPermission(uiPermissionName);
            if (prevUIPermission == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiPermission, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUIPermission, uiPermission,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIPermission a,
                                              UIPermission b, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiAction",
                a.getAction(),
                b.getAction(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiActionParam",
                a.getActionParams(),
                b.getActionParams());
        LdapUtils.storeDiff(ldapObj, mods, "dcmAcceptedUserRole",
                a.getAcceptedUserRoles(),
                b.getAcceptedUserRoles());
        return mods;
    }

    private void mergeDashboardConfigs(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDashboardConfig prevUIDashboardConfig : prevUIConfig.getDashboardConfigs()) {
            String prevUIDashboardConfigName = prevUIDashboardConfig.getName();
            if (uiConfig.getDashboardConfig(prevUIDashboardConfigName) == null) {
                String dn = LdapUtils.dnOf("dcmuiDashboardConfigName", prevUIDashboardConfigName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIDashboardConfig uiDashboardConfig : uiConfig.getDashboardConfigs()) {
            String uiDashboardConfigName = uiDashboardConfig.getName();
            String dn = LdapUtils.dnOf("dcmuiDashboardConfigName", uiDashboardConfigName, uiConfigDN);
            UIDashboardConfig prevUIDashboardConfig = prevUIConfig.getDashboardConfig(uiDashboardConfigName);
            if (prevUIDashboardConfig == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiDashboardConfig, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUIDashboardConfig, uiDashboardConfig,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIDashboardConfig a,
                                              UIDashboardConfig b, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(ldapObj, mods, "dcmQueueName",
                a.getQueueNames(),
                b.getQueueNames());
        LdapUtils.storeDiff(ldapObj, mods, "dicomDeviceName",
                a.getDeviceNames(),
                b.getDeviceNames());
        return mods;
    }

    private void mergeDiffConfigs(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDiffConfig prevUIDiffConfig : prevUIConfig.getDiffConfigs()) {
            String prevUIDiffConfigName = prevUIDiffConfig.getName();
            if (uiConfig.getDiffConfig(prevUIDiffConfigName) == null) {
                String dn = LdapUtils.dnOf("dcmuiDiffConfigName", prevUIDiffConfigName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIDiffConfig uiDiffConfig : uiConfig.getDiffConfigs()) {
            String uiDiffConfigName = uiDiffConfig.getName();
            String uiDiffConfigDN = LdapUtils.dnOf("dcmuiDiffConfigName", uiDiffConfigName, uiConfigDN);
            UIDiffConfig prevUIDiffConfig = prevUIConfig.getDiffConfig(uiDiffConfigName);
            if (prevUIDiffConfig == null)
                storeDiffConfig(diffs, uiDiffConfig, uiConfigDN);
            else
                config.modifyAttributes(uiDiffConfigDN, storeDiffs(diffs, uiDiffConfigDN, prevUIDiffConfig, uiDiffConfig,
                        new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges diffs, String uiDiffConfigDN, UIDiffConfig a,
                                              UIDiffConfig b, ArrayList<ModificationItem> mods)
        throws NamingException {
        ConfigurationChanges.ModifiedObject ldapObj =
                ConfigurationChanges.addModifiedObject(diffs, uiDiffConfigDN, ConfigurationChanges.ChangeType.U);

        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffCallingAET",
                a.getCallingAET(),
                b.getCallingAET(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffPrimaryCFindSCP",
                a.getPrimaryCFindSCP(),
                b.getPrimaryCFindSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffPrimaryCMoveSCP",
                a.getPrimaryCMoveSCP(),
                b.getPrimaryCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffPrimaryCStoreSCP",
                a.getPrimaryCStoreSCP(),
                b.getPrimaryCStoreSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffSecondaryCFindSCP",
                a.getSecondaryCFindSCP(),
                b.getSecondaryCFindSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffSecondaryCMoveSCP",
                a.getSecondaryCMoveSCP(),
                b.getSecondaryCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffSecondaryCStoreSCP",
                a.getSecondaryCStoreSCP(),
                b.getSecondaryCStoreSCP(), null);
        mergeUIDiffCriteria(diffs, a, b, uiDiffConfigDN);
        ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
        return mods;
    }

    private void mergeUIDiffCriteria(
            ConfigurationChanges diffs, UIDiffConfig prevUIDiffConfig, UIDiffConfig uiDiffConfig, String uiDiffConfigDN)
        throws NamingException {
        for (UIDiffCriteria prevUIDiffCriteria : prevUIDiffConfig.getCriterias()) {
            String prevUIDiffCriteriaTitle = prevUIDiffCriteria.getTitle();
            if (uiDiffConfig.getCriteria(prevUIDiffCriteriaTitle) == null) {
                String dn = LdapUtils.dnOf("dcmuiDiffCriteriaTitle", prevUIDiffCriteriaTitle, uiDiffConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIDiffCriteria uiDiffCriteria : uiDiffConfig.getCriterias()) {
            String uiDiffCriteriaTitle = uiDiffCriteria.getTitle();
            UIDiffCriteria prevUIDiffCriteria = prevUIDiffConfig.getCriteria(uiDiffCriteriaTitle);
            if (prevUIDiffCriteria == null)
                storeDiffCriteria(diffs, uiDiffConfigDN, uiDiffCriteria);
            else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiDiffConfigDN, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(uiDiffConfigDN, storeDiffs(ldapObj, prevUIDiffCriteria, uiDiffCriteria,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIDiffCriteria a,
                                              UIDiffCriteria b, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomDescription",
                a.getDescription(),
                b.getDescription(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffCriteriaNumber",
                a.getNumber(),
                b.getNumber(), 0);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffIncludeMissing",
                a.isIncludeMissing(),
                b.isIncludeMissing(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAttributeSetID",
                a.getAttributeSetID(),
                b.getAttributeSetID(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffAction",
                a.getActions(),
                b.getActions());
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffGroupButton",
                a.getGroupButtons(),
                b.getGroupButtons());
        return mods;
    }

}