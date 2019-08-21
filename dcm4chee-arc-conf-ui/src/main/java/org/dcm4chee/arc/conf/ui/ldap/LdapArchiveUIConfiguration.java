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
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ui.*;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.rmi.server.UID;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Shefki Esadi <shralsheki@gmail.com>
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

    @Override
    protected void storeTo(ConfigurationChanges.ModifiedObject ldapObj, Device device, Attributes attrs) {
//        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiModalities", uiConfig.getModalities());
        super.storeTo(ldapObj, device, attrs);
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
        storePermissions(diffs, uiConfig, uiConfigDN);
        storeDiffConfigs(diffs, uiConfig, uiConfigDN);
        storeDashboardConfigs(diffs, uiConfig, uiConfigDN);
        storeElasticsearchConfigs(diffs, uiConfig, uiConfigDN);
        storeDeviceURL(diffs, uiConfig, uiConfigDN);
        storeDeviceCluster(diffs, uiConfig, uiConfigDN);
        storeFiltersTemplate(diffs, uiConfig, uiConfigDN);
        storeAets(diffs, uiConfig, uiConfigDN);

    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIConfig uiConfig, Attributes attrs) {
        attrs.put("objectclass", "dcmuiConfig");
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiConfigName", uiConfig.getName(), null);
        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiModalities", uiConfig.getModalities());
        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiWidgetAets", uiConfig.getWidgetAets());
        LdapUtils.storeNotNullOrDef(ldapObj,attrs, "dcmuiXDSInterfaceURL", uiConfig.getXdsUrl(),null);
        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiDefaultWidgetAets", uiConfig.getDefaultWidgetAets());
        return attrs;
    }

    private void storePermissions(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIPermission uiPermission : uiConfig.getPermissions()) {
            String uiPermissionDN = LdapUtils.dnOf("dcmuiPermissionName", uiPermission.getName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiPermissionDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiPermissionDN, storeTo(ldapObj1, uiPermission, new BasicAttributes(true)));
        }
    }
    private void storeAets(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIAetList uiAets : uiConfig.getAetLists()) {
            String uiAetListDN = LdapUtils.dnOf("dcmuiAetListName", uiAets.getAetListName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiAetListDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiAetListDN, storeTo(ldapObj1, uiAets, new BasicAttributes(true)));
        }
    }
    private void storeDeviceURL(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN) throws NamingException {
        for (UIDeviceURL uiDeviceURL : uiConfig.getDeviceURLs()) {
            String uiDeviceURLDN = LdapUtils.dnOf("dcmuiDeviceURLName", uiDeviceURL.getDeviceName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDeviceURLDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiDeviceURLDN, storeTo(ldapObj1, uiDeviceURL, new BasicAttributes(true)));
        }
    }
    private void storeDeviceCluster(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN) throws NamingException {
        for (UIDeviceCluster uiDeviceCluster : uiConfig.getDeviceClusters()) {
            String uiDeviceClusterDN = LdapUtils.dnOf("dcmuiDeviceClusterName", uiDeviceCluster.getClusterName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDeviceClusterDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiDeviceClusterDN, storeTo(ldapObj1, uiDeviceCluster, new BasicAttributes(true)));
        }
    }
    private void storeFiltersTemplate(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN) throws NamingException {
        for (UIFiltersTemplate uiFiltersTemplate : uiConfig.getFilterTemplates()) {
            String uiFilterTemplateDN = LdapUtils.dnOf("dcmuiFilterTemplateGroupName", uiFiltersTemplate.getFilterGroupName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiFilterTemplateDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiFilterTemplateDN, storeTo(ldapObj1, uiFiltersTemplate, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIPermission uiPermission, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiPermission"));
        attrs.put(new BasicAttribute("dcmuiPermissionName", uiPermission.getName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiAction", uiPermission.getAction(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiActionParam", uiPermission.getActionParams());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedUserRole", uiPermission.getAcceptedUserRoles());
        return attrs;
    }
/*    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIAetList uiAetList, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiAetConfig"));
        attrs.put(new BasicAttribute("dcmuiAetListName", uiAetList.getAetListName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiAetListDescription", uiAetList.getAetListDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiMode", uiAetList.getMode(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiAets", uiAetList.getAets());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedUserRole", uiAetList.getAcceptedRole());
        return attrs;
    }*/
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIAetList uiAetList, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiAetConfig"));
        attrs.put(new BasicAttribute("dcmuiAetListName", uiAetList.getAetListName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiAetListDescription", uiAetList.getAetListDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiMode", uiAetList.getMode(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiAets", uiAetList.getAets());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedUserRole", uiAetList.getAcceptedRole());
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIDeviceURL uiDeviceURL, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiDeviceURLObject"));
        attrs.put(new BasicAttribute("dcmuiDeviceURLName", uiDeviceURL.getDeviceName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDeviceURL", uiDeviceURL.getDeviceURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDeviceURLDescription", uiDeviceURL.getDescription(), null);
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiDeviceURLInstalled",uiDeviceURL.isInstalled(),true);
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIDeviceCluster uiDeviceCluster, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiDeviceClusterObject"));
        attrs.put(new BasicAttribute("dcmuiDeviceClusterName", uiDeviceCluster.getClusterName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDeviceClusterDescription", uiDeviceCluster.getDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDeviceClusterLoadBalancer", uiDeviceCluster.getLoadBalancer(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDeviceClusterKeycloakServer", uiDeviceCluster.getKeycloakServer(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiDeviceClusterDevices", uiDeviceCluster.getDevices());
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiDeviceClusterInstalled",uiDeviceCluster.isInstalled(),true);
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIFiltersTemplate uiFilterTemplate, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiFilterTemplateObject"));
        attrs.put(new BasicAttribute("dcmuiFilterTemplateGroupName", uiFilterTemplate.getFilterGroupName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiFilterTemplateID", uiFilterTemplate.getFilterGroupID(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiFilterTemplateDescription", uiFilterTemplate.getFilterGroupDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiFilterTemplateUsername", uiFilterTemplate.getFilterGroupUsername(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiFilterTemplateRole", uiFilterTemplate.getFilterGroupRole(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiFilterTemplateFilters", uiFilterTemplate.getFilters());
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiFilterTemplateDefault",uiFilterTemplate.isDefault(),false);
        return attrs;
    }

    private void storeDiffConfigs(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDiffConfig uiDiffConfig : uiConfig.getDiffConfigs()) {
            String uiDiffConfigDN = LdapUtils.dnOf("dcmuiDiffConfigName", uiDiffConfig.getName(), uiConfigDN);
            config.createSubcontext(uiDiffConfigDN, storeTo(diffs, uiDiffConfigDN, uiDiffConfig, new BasicAttributes(true)));
            storeDiffCriterias(diffs, uiDiffConfigDN, uiDiffConfig);
        }
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

    private void storeDiffCriterias(ConfigurationChanges diffs, String uiDiffConfigDN, UIDiffConfig uiDiffConfig)
            throws NamingException {
        for (UIDiffCriteria uiDiffCriteria : uiDiffConfig.getCriterias()) {
            String uiDiffCriteriaDN = LdapUtils.dnOf("dcmuiDiffCriteriaTitle", uiDiffCriteria.getTitle(), uiDiffConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDiffCriteriaDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiDiffCriteriaDN, storeTo(ldapObj, uiDiffCriteria, new BasicAttributes(true)));
        }
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

    private void storeDashboardConfigs(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDashboardConfig uiDashboardConfig : uiConfig.getDashboardConfigs()) {
            String uiDashboardConfigDN = LdapUtils.dnOf("dcmuiDashboardConfigName", uiDashboardConfig.getName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDashboardConfigDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiDashboardConfigDN, storeTo(ldapObj1, uiDashboardConfig, new BasicAttributes(true)));
            storeCompareSides(diffs, uiDashboardConfigDN, uiDashboardConfig);
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIDashboardConfig uiDashboardConfig, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiDashboardConfig"));
        attrs.put(new BasicAttribute("dcmuiDashboardConfigName", uiDashboardConfig.getName()));
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiQueueName", uiDashboardConfig.getQueueNames());
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiShowStarBlock",uiDashboardConfig.isShowStarBlock(),true);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiExportName", uiDashboardConfig.getExportNames());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dicomuiDeviceName", uiDashboardConfig.getDeviceNames());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dicomuiIgnoreParams", uiDashboardConfig.getIgnoreParams());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dicomuiDockerContainer", uiDashboardConfig.getDockerContainers());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs,"dcmuiCountAET",uiDashboardConfig.getCountAet(),null);
        return attrs;
    }

    private void storeElasticsearchConfigs(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIElasticsearchConfig uiElasticSearchConfig : uiConfig.getElasticsearchConfigs()) {
            String uiElasticsearchConfigDN = LdapUtils.dnOf("dcmuiElasticsearchConfigName", uiElasticSearchConfig.getName(), uiConfigDN);
            config.createSubcontext(
                    uiElasticsearchConfigDN,
                    storeTo(uiElasticSearchConfig, new BasicAttributes(true))
            );
            storeElasticsearchURLs(diffs, uiElasticsearchConfigDN, uiElasticSearchConfig);
        }
    }

    private void storeElasticsearchURLs(ConfigurationChanges diffs, String uiElasticsearchConfigDN, UIElasticsearchConfig uiElasticsearchConfig)
            throws NamingException {
        for (UIElasticsearchURL uiElasticsearchURL : uiElasticsearchConfig.getURLS()) {
            String uiElasticsearchURLDN = LdapUtils.dnOf("dcmuiElasticsearchURLName", uiElasticsearchURL.getUrlName(), uiElasticsearchConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                    diffs,
                    uiElasticsearchURLDN,
                    ConfigurationChanges.ChangeType.C
            );
            config.createSubcontext(uiElasticsearchURLDN, storeTo(ldapObj, uiElasticsearchURL, new BasicAttributes(true)));
        }
    }
    private void storeCompareSides(ConfigurationChanges diffs, String uiDashboardConfigDN, UIDashboardConfig uiDashboardConfig)
            throws NamingException {
        for (UICompareSide uiCompareSide : uiDashboardConfig.getCompareSides()) {
            String uiCompareSideDN = LdapUtils.dnOf("dcmuiCompareSideName", uiCompareSide.getName(), uiDashboardConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                    diffs,
                    uiCompareSideDN,
                    ConfigurationChanges.ChangeType.C
            );
            config.createSubcontext(uiCompareSideDN, storeTo(ldapObj, uiCompareSide, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(UIElasticsearchConfig uiElasticsearchConfig, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiElasticsearchConfig"));
        attrs.put(new BasicAttribute("dcmuiElasticsearchConfigName", uiElasticsearchConfig.getName()));
        return attrs;
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIElasticsearchURL uiElasticsearchURL, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiElasticsearchURLObjects"));
        attrs.put(new BasicAttribute("dcmuiElasticsearchURLName", uiElasticsearchURL.getUrlName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiElasticsearchURL", uiElasticsearchURL.getUrl(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiElasticsearchURLKeycloakServer", uiElasticsearchURL.getElasticsearchURLKeycloakServer(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiAuditEnterpriseSiteID", uiElasticsearchURL.getAuditEnterpriseSiteID(), null);
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiElasticsearchIsDefault",uiElasticsearchURL.isDefault(),false);
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiElasticsearchInstalled",uiElasticsearchURL.isInstalled(),true);
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UICompareSide uiCompareSide, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiCompareSideObjects"));
        attrs.put(new BasicAttribute("dcmuiCompareSideName", uiCompareSide.getName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiCompareSideDescription", uiCompareSide.getDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiCompareSideCluster", uiCompareSide.getCluster(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiCompareSideElasticsearch", uiCompareSide.getElasticsearch(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiCompareSideQueueName", uiCompareSide.getQueueName(), null);
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiCompareSideInstalled",uiCompareSide.isInstalled(),true);
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
        uiConfig.setModalities(LdapUtils.stringArray(attrs.get("dcmuiModalities")));
        uiConfig.setWidgetAets(LdapUtils.stringArray(attrs.get("dcmuiWidgetAets")));
        uiConfig.setXdsUrl(LdapUtils.stringValue(attrs.get("dcmuiXDSInterfaceURL"),null));
        uiConfig.setDefaultWidgetAets(LdapUtils.stringArray(attrs.get("dcmuiDefaultWidgetAets")));
        loadPermissions(uiConfig, uiConfigDN);
        loadDiffConfigs(uiConfig, uiConfigDN);
        loadDashboardConfigs(uiConfig, uiConfigDN);
        loadElasticsearchConfigs(uiConfig, uiConfigDN);
        loadDeviceURLs(uiConfig, uiConfigDN);
        loadDeviceClusters(uiConfig, uiConfigDN);
        loadFilterTemplates(uiConfig, uiConfigDN);
        loadAetList(uiConfig, uiConfigDN);
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
    private void loadAetList(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiAetConfig)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIAetList uiAetList = new UIAetList((String) attrs.get("dcmuiAetListName").get());
                uiAetList.setAetListDescription(LdapUtils.stringValue(attrs.get("dcmuiAetListDescription"), null));
                uiAetList.setMode(LdapUtils.stringValue(attrs.get("dcmuiMode"), null));
                uiAetList.setAets(LdapUtils.stringArray(attrs.get("dcmuiAets")));
                uiAetList.setAcceptedRole(LdapUtils.stringArray(attrs.get("dcmAcceptedUserRole")));
                uiConfig.addAetList(uiAetList);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadDeviceURLs(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiDeviceURLObject)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIDeviceURL uiDeviceURL = new UIDeviceURL((String) attrs.get("dcmuiDeviceURLName").get());
                uiDeviceURL.setDeviceURL(LdapUtils.stringValue(attrs.get("dcmuiDeviceURL"), null));
                uiDeviceURL.setDescription(LdapUtils.stringValue(attrs.get("dcmuiDeviceURLDescription"), null));
                uiDeviceURL.setInstalled(LdapUtils.booleanValue(attrs.get("dcmuiDeviceURLInstalled"),true));
                uiConfig.addDeviceURL(uiDeviceURL);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadDeviceClusters(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiDeviceClusterObject)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIDeviceCluster uiDeviceCluster = new UIDeviceCluster((String) attrs.get("dcmuiDeviceClusterName").get());
                uiDeviceCluster.setDescription(LdapUtils.stringValue(attrs.get("dcmuiDeviceClusterDescription"), null));
                uiDeviceCluster.setLoadBalancer(LdapUtils.stringValue(attrs.get("dcmuiDeviceClusterLoadBalancer"), null));
                uiDeviceCluster.setKeycloakServer(LdapUtils.stringValue(attrs.get("dcmuiDeviceClusterKeycloakServer"), null));
                uiDeviceCluster.setDevices(LdapUtils.stringArray(attrs.get("dcmuiDeviceClusterDevices")));
                uiDeviceCluster.setInstalled(LdapUtils.booleanValue(attrs.get("dcmuiDeviceClusterInstalled"),true));
                uiConfig.addDeviceCluster(uiDeviceCluster);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadFilterTemplates(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiFilterTemplateObject)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIFiltersTemplate uiFiltersTemplate = new UIFiltersTemplate((String) attrs.get("dcmuiFilterTemplateGroupName").get());
                uiFiltersTemplate.setFilterGroupID(LdapUtils.stringValue(attrs.get("dcmuiFilterTemplateID"), null));
                uiFiltersTemplate.setFilterGroupDescription(LdapUtils.stringValue(attrs.get("dcmuiFilterTemplateDescription"), null));
                uiFiltersTemplate.setFilterGroupUsername(LdapUtils.stringValue(attrs.get("dcmuiFilterTemplateUsername"), null));
                uiFiltersTemplate.setFilterGroupRole(LdapUtils.stringValue(attrs.get("dcmuiFilterTemplateRole"), null));
                uiFiltersTemplate.setFilters(LdapUtils.stringArray(attrs.get("dcmuiFilterTemplateFilters")));
                uiFiltersTemplate.setDefault(LdapUtils.booleanValue(attrs.get("dcmuiFilterTemplateDefault"),false));
                uiConfig.addFilterTemplate(uiFiltersTemplate);
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
                uiDashboardConfig.setShowStarBlock(LdapUtils.booleanValue(attrs.get("dcmuiShowStarBlock"), true));
                uiDashboardConfig.setCountAet(LdapUtils.stringValue(attrs.get("dcmuiCountAET"),null));
                uiDashboardConfig.setQueueNames(LdapUtils.stringArray(attrs.get("dcmuiQueueName")));
                uiDashboardConfig.setExportNames(LdapUtils.stringArray(attrs.get("dcmuiExportName")));
                uiDashboardConfig.setDeviceNames(LdapUtils.stringArray(attrs.get("dicomuiDeviceName")));
                uiDashboardConfig.setIgnoreParams(LdapUtils.stringArray(attrs.get("dicomuiIgnoreParams")));
                uiDashboardConfig.setDockerContainers(LdapUtils.stringArray(attrs.get("dicomuiDockerContainer")));
                String uiDashboardConfigDN = LdapUtils.dnOf("dcmuiDashboardConfigName" , uiDashboardConfig.getName(), uiConfigDN);
                loadCompareSide(uiDashboardConfig, uiDashboardConfigDN);
                uiConfig.addDashboardConfig(uiDashboardConfig);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadElasticsearchConfigs(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiElasticsearchConfig)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIElasticsearchConfig uiElasticsearchConfig = new UIElasticsearchConfig((String) attrs.get("dcmuiElasticsearchConfigName").get());
                String uiElasticsearchConfigDN = LdapUtils.dnOf("dcmuiElasticsearchConfigName" , uiElasticsearchConfig.getName(), uiConfigDN);
                loadElasticsearchURL(uiElasticsearchConfig, uiElasticsearchConfigDN);
                uiConfig.addElasticsearchConfig(uiElasticsearchConfig);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadElasticsearchURL(UIElasticsearchConfig uiElasticsearchConfig, String uiElasticsearchConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiElasticsearchConfigDN, "(objectclass=dcmuiElasticsearchURLObjects)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIElasticsearchURL uiElasticsearchURL = new UIElasticsearchURL((String) attrs.get("dcmuiElasticsearchURLName").get());
                uiElasticsearchURL.setUrl(LdapUtils.stringValue(attrs.get("dcmuiElasticsearchURL"), null));
                uiElasticsearchURL.setElasticsearchURLKeycloakServer(LdapUtils.stringValue(attrs.get("dcmuiElasticsearchURLKeycloakServer"), null));
                uiElasticsearchURL.setAuditEnterpriseSiteID(LdapUtils.stringValue(attrs.get("dcmuiAuditEnterpriseSiteID"), null));
                uiElasticsearchURL.setDefault(LdapUtils.booleanValue(attrs.get("dcmuiElasticsearchIsDefault"),false));
                uiElasticsearchURL.setInstalled(LdapUtils.booleanValue(attrs.get("dcmuiElasticsearchInstalled"),true));
                uiElasticsearchConfig.addURL(uiElasticsearchURL);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadCompareSide(UIDashboardConfig uiDashboardConfig, String uiDashboardConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiDashboardConfigDN, "(objectclass=dcmuiCompareSideObjects)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UICompareSide uiCompareSide = new UICompareSide((String) attrs.get("dcmuiCompareSideName").get());
                uiCompareSide.setDescription(LdapUtils.stringValue(attrs.get("dcmuiCompareSideDescription"), null));
                uiCompareSide.setCluster(LdapUtils.stringValue(attrs.get("dcmuiCompareSideCluster"), null));
                uiCompareSide.setElasticsearch(LdapUtils.stringValue(attrs.get("dcmuiCompareSideElasticsearch"), null));
                uiCompareSide.setQueueName(LdapUtils.stringValue(attrs.get("dcmuiCompareSideQueueName"), null));
                uiCompareSide.setInstalled(LdapUtils.booleanValue(attrs.get("dcmuiCompareSideInstalled"),true));
                uiDashboardConfig.addCompareSide(uiCompareSide);
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
                ConfigurationChanges.addModifiedObject(diffs, uiConfigDN, ConfigurationChanges.ChangeType.U); //Add logstash entry title
        config.modifyAttributes(uiConfigDN, storeDiff(ldapObj, prevUIConfig, uiConfig,
                new ArrayList<ModificationItem>()));
        ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj); //remove logstash entry title if empty/no changes

        mergeDiffConfigs(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeDashboardConfigs(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeElasticsearchConfigs(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeDeviceURL(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeDeviceCluster(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeFilterTemplate(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergePermissions(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeAetLists(diffs, prevUIConfig, uiConfig, uiConfigDN);
    }

    private List<ModificationItem> storeDiff(ConfigurationChanges.ModifiedObject ldapObj, UIConfig prevUIConfig, UIConfig uiConfig, ArrayList<ModificationItem> mods) throws NamingException {
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiModalities",prevUIConfig.getModalities(),uiConfig.getModalities());
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiWidgetAets",prevUIConfig.getWidgetAets(),uiConfig.getWidgetAets());
        LdapUtils.storeDiffObject(ldapObj,mods,"dcmuiXDSInterfaceURL",prevUIConfig.getXdsUrl(),uiConfig.getXdsUrl(),null);
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiDefaultWidgetAets",prevUIConfig.getDefaultWidgetAets(),uiConfig.getDefaultWidgetAets());
        return mods;
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
    private void mergeAetLists(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIAetList prevUIAetList : prevUIConfig.getAetLists()) {
            String prevUIAetListName = prevUIAetList.getAetListName();
            if (uiConfig.getAetList(prevUIAetListName) == null) {
                String dn = LdapUtils.dnOf("dcmuiAetListName", prevUIAetListName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIAetList uiAetList : uiConfig.getAetLists()) {
            String uiAetListName = uiAetList.getAetListName();
            String dn = LdapUtils.dnOf("dcmuiAetListName", uiAetListName, uiConfigDN);
            UIAetList prevUIAetList = prevUIConfig.getAetList(uiAetListName);
            if (prevUIAetList == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiAetList, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUIAetList, uiAetList,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }
    private void mergeDeviceURL(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDeviceURL prevUIDeviceURL : prevUIConfig.getDeviceURLs()) {
            String prevUIDeviceURLName = prevUIDeviceURL.getDeviceName();
            if (uiConfig.getDeviceURL(prevUIDeviceURLName) == null) {
                String dn = LdapUtils.dnOf("dcmuiDeviceURLName", prevUIDeviceURLName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIDeviceURL uiDeviceURL : uiConfig.getDeviceURLs()) {
            String uiDeviceURLName = uiDeviceURL.getDeviceName();
            String dn = LdapUtils.dnOf("dcmuiDeviceURLName", uiDeviceURLName, uiConfigDN);
            UIDeviceURL prevUIDeviceURL = prevUIConfig.getDeviceURL(uiDeviceURLName);
            if (prevUIDeviceURL == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiDeviceURL, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUIDeviceURL, uiDeviceURL,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }
    private void mergeFilterTemplate(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIFiltersTemplate prevUIFiltersTemplate : prevUIConfig.getFilterTemplates()) {
            String prevUIFiltersTemplateName = prevUIFiltersTemplate.getFilterGroupName();
            if (uiConfig.getFilterTemplate(prevUIFiltersTemplateName) == null) {
                String dn = LdapUtils.dnOf("dcmuiFilterTemplateGroupName", prevUIFiltersTemplateName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIFiltersTemplate uiFiltersTemplate : uiConfig.getFilterTemplates()) {
            String uiFiltersTemplateGroupName = uiFiltersTemplate.getFilterGroupName();
            String dn = LdapUtils.dnOf("dcmuiFilterTemplateGroupName", uiFiltersTemplateGroupName, uiConfigDN);
            UIFiltersTemplate prevUIFiltersTemplate = prevUIConfig.getFilterTemplate(uiFiltersTemplateGroupName);
            if (prevUIFiltersTemplate == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiFiltersTemplate, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUIFiltersTemplate, uiFiltersTemplate,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }
    private void mergeDeviceCluster(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDeviceCluster prevUIDeviceCluster : prevUIConfig.getDeviceClusters()) {
            String prevUIDeviceClusterName = prevUIDeviceCluster.getClusterName();
            if (uiConfig.getDeviceCluster(prevUIDeviceClusterName) == null) {
                String dn = LdapUtils.dnOf("dcmuiDeviceClusterName", prevUIDeviceClusterName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIDeviceCluster uiDeviceCluster : uiConfig.getDeviceClusters()) {
            String uiDeviceClusterName = uiDeviceCluster.getClusterName();
            String dn = LdapUtils.dnOf("dcmuiDeviceClusterName", uiDeviceClusterName, uiConfigDN);
            UIDeviceCluster prevUIDeviceCluster = prevUIConfig.getDeviceCluster(uiDeviceClusterName);
            if (prevUIDeviceCluster == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiDeviceCluster, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUIDeviceCluster, uiDeviceCluster,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIPermission prev,
                                              UIPermission uiPermission, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiAction",
                prev.getAction(),
                uiPermission.getAction(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiActionParam",
                prev.getActionParams(),
                uiPermission.getActionParams());
        LdapUtils.storeDiff(ldapObj, mods, "dcmAcceptedUserRole",
                prev.getAcceptedUserRoles(),
                uiPermission.getAcceptedUserRoles());
        return mods;
    }
    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIAetList prev,
                                              UIAetList uiAetList, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiAetListDescription",
                prev.getAetListDescription(),
                uiAetList.getAetListDescription(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiMode",
                prev.getMode(),
                uiAetList.getMode(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiAets",
                prev.getAets(),
                uiAetList.getAets());
        LdapUtils.storeDiff(ldapObj, mods, "dcmAcceptedUserRole",
                prev.getAcceptedRole(),
                uiAetList.getAcceptedRole());
        return mods;
    }
    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIDeviceURL prev,
                                              UIDeviceURL uiDeviceURL, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDeviceURL",
                prev.getDeviceURL(),
                uiDeviceURL.getDeviceURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDeviceURLDescription",
                prev.getDescription(),
                uiDeviceURL.getDescription(),null);
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiDeviceURLInstalled",prev.isInstalled(),uiDeviceURL.isInstalled(),true);
        return mods;
    }
    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIFiltersTemplate prev,
                                              UIFiltersTemplate uiFiltersTemplate, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiFilterTemplateID",
                prev.getFilterGroupID(),
                uiFiltersTemplate.getFilterGroupID(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiFilterTemplateDescription",
                prev.getFilterGroupDescription(),
                uiFiltersTemplate.getFilterGroupDescription(),null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiFilterTemplateUsername",
                prev.getFilterGroupUsername(),
                uiFiltersTemplate.getFilterGroupUsername(),null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiFilterTemplateRole",
                prev.getFilterGroupRole(),
                uiFiltersTemplate.getFilterGroupRole(),null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiFilterTemplateFilters",
                prev.getFilters(),
                uiFiltersTemplate.getFilters());
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiFilterTemplateDefault",prev.isDefault(),uiFiltersTemplate.isDefault(),false);
        return mods;
    }
    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIDeviceCluster prev,
                                              UIDeviceCluster uiDeviceCluster, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDeviceClusterDevices",
                prev.getDevices(),
                uiDeviceCluster.getDevices());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDeviceClusterDescription",
                prev.getDescription(),
                uiDeviceCluster.getDescription(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDeviceClusterLoadBalancer",
                prev.getLoadBalancer(),
                uiDeviceCluster.getLoadBalancer(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDeviceClusterKeycloakServer",
                prev.getKeycloakServer(),
                uiDeviceCluster.getKeycloakServer(), null);
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiDeviceClusterInstalled",prev.isInstalled(),uiDeviceCluster.isInstalled(),true);
        return mods;
    }

    private void mergeDashboardConfigs(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDashboardConfig prevUIDashboardConfig : prevUIConfig.getDashboardConfigs()) {
            String prevUIDashboardConfigName = prevUIDashboardConfig.getName();
            if (uiConfig.getDashboardConfig(prevUIDashboardConfigName) == null) {
                String dn = LdapUtils.dnOf("dcmuiDashboardConfigName", prevUIDashboardConfigName, uiConfigDN);
                for (UICompareSide prevCompareSide : prevUIDashboardConfig.getCompareSides())
                    deleteCompareSide(diffs, prevCompareSide.getName(), dn);
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
                storeCompareSides(diffs, dn, uiDashboardConfig);
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(diffs, dn, ldapObj,prevUIDashboardConfig,uiDashboardConfig,new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }
    private List<ModificationItem> storeUIDashboardConfig(
            ConfigurationChanges diffs, String uiDashboardConfigDN, UIDashboardConfig a, UIDashboardConfig b,
            ArrayList<ModificationItem> mods) throws NamingException {
        ConfigurationChanges.ModifiedObject ldapObj =  ConfigurationChanges.addModifiedObject(diffs, uiDashboardConfigDN, ConfigurationChanges.ChangeType.U);
        mergeUICompareSide(diffs, a, b, uiDashboardConfigDN);
        ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
        return mods;
    }
    private void mergeUICompareSide(
            ConfigurationChanges diffs, UIDashboardConfig prevUIDashboardConfig,
            UIDashboardConfig uiDashboardConfig, String uiDashboardConfigDN) throws NamingException {
        for (UICompareSide prevUICompareSide : prevUIDashboardConfig.getCompareSides()) {
            String prevUICompareSideName = prevUICompareSide.getName();
            if (uiDashboardConfig.getCompareSide(prevUICompareSideName) == null)
                deleteUICompareSide(diffs, prevUICompareSideName, uiDashboardConfigDN);
        }
        for (UICompareSide uiCompareSide : uiDashboardConfig.getCompareSides()) {
            String uiCompareSideName = uiCompareSide.getName();
            UICompareSide prevUICompareSide = prevUIDashboardConfig.getCompareSide(uiCompareSideName);
            String uiUICompareSideDN = LdapUtils.dnOf("dcmuiCompareSideName", uiCompareSide.getName(), uiDashboardConfigDN);
            if (prevUICompareSide == null) {
                ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                        diffs,
                        uiUICompareSideDN,
                        ConfigurationChanges.ChangeType.C
                );
                config.createSubcontext(uiUICompareSideDN, storeTo(ldapObj, uiCompareSide, new BasicAttributes(true)));
            }
            else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiDashboardConfigDN, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(uiUICompareSideDN, storeDiff(ldapObj, prevUICompareSide, uiCompareSide,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }
    private void deleteUICompareSide(ConfigurationChanges diffs, String prevUIDashboardConfigName, String uiDashboardConfigDN)
            throws NamingException {
        String dn = LdapUtils.dnOf("dcmuiCompareSideName", prevUIDashboardConfigName, uiDashboardConfigDN);
        config.destroySubcontext(dn);
        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
    }
    private void mergeElasticsearchConfigs(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN) throws NamingException {
        for (UIElasticsearchConfig prevUIElasticsearchConfig : prevUIConfig.getElasticsearchConfigs()) {
            String prevUIElasticsearchConfigName = prevUIElasticsearchConfig.getName();
            if (uiConfig.getElasticsearchConfig(prevUIElasticsearchConfigName) == null) {
                String dn = LdapUtils.dnOf("dcmuiElasticsearchConfigName", prevUIElasticsearchConfigName, uiConfigDN);
                for (UIElasticsearchURL prevUIElasticsearchURL : prevUIElasticsearchConfig.getURLS())
                    deleteUIElasticsearchURL(diffs, prevUIElasticsearchURL.getUrlName(), dn);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIElasticsearchConfig uiElasticsearchConfig : uiConfig.getElasticsearchConfigs()) {
            String uiElasticsearchConfigName = uiElasticsearchConfig.getName();
            String uiElasticsearchConfigDN = LdapUtils.dnOf("dcmuiElasticsearchConfigName", uiElasticsearchConfigName, uiConfigDN);
            UIElasticsearchConfig prevUIElasticsearchConfig = prevUIConfig.getElasticsearchConfig(uiElasticsearchConfigName);
            if (prevUIElasticsearchConfig == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiElasticsearchConfigDN, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(
                        uiElasticsearchConfigDN,
                        storeTo(uiElasticsearchConfig, new BasicAttributes(true))
                );
                storeElasticsearchURLs(diffs, uiElasticsearchConfigDN, uiElasticsearchConfig);
            }
            else{
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiElasticsearchConfigDN, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(uiElasticsearchConfigDN, storeUIElasticsearchConfig(diffs, uiElasticsearchConfigDN, prevUIElasticsearchConfig, uiElasticsearchConfig,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void deleteUIElasticsearchURL(ConfigurationChanges diffs, String prevUIElasticsearchConfigName, String uiElasticsearcConfigDN)
            throws NamingException {
        String dn = LdapUtils.dnOf("dcmuiElasticsearchURLName", prevUIElasticsearchConfigName, uiElasticsearcConfigDN);
        config.destroySubcontext(dn);
        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
    }
    private void deleteCompareSide(ConfigurationChanges diffs, String prevCompareSideName, String uiUIDashboardConfigDN)
            throws NamingException {
        String dn = LdapUtils.dnOf("dcmuiCompareSideName", prevCompareSideName, uiUIDashboardConfigDN);
        config.destroySubcontext(dn);
        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
    }

    private List<ModificationItem> storeUIElasticsearchConfig(
            ConfigurationChanges diffs, String uiElasticsearcConfigDN, UIElasticsearchConfig a, UIElasticsearchConfig b,
            ArrayList<ModificationItem> mods) throws NamingException {
        ConfigurationChanges.ModifiedObject ldapObj =  ConfigurationChanges.addModifiedObject(diffs, uiElasticsearcConfigDN, ConfigurationChanges.ChangeType.U);
        mergeUIElasticsearchURLs(diffs, a, b, uiElasticsearcConfigDN);
        ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
        return mods;
    }

    private void mergeUIElasticsearchURLs(
            ConfigurationChanges diffs, UIElasticsearchConfig prevUIElasticsearchConfig,
            UIElasticsearchConfig uiElasticsearchConfig, String uiElasticsearchConfigDN) throws NamingException {
        for (UIElasticsearchURL prevUIElasticsearchURL : prevUIElasticsearchConfig.getURLS()) {
            String prevUIElasticsearchURLUrlName = prevUIElasticsearchURL.getUrlName();
            if (uiElasticsearchConfig.getURL(prevUIElasticsearchURLUrlName) == null)
                deleteUIElasticsearchURL(diffs, prevUIElasticsearchURLUrlName, uiElasticsearchConfigDN);
        }
        for (UIElasticsearchURL uiElasticsearchURL : uiElasticsearchConfig.getURLS()) {
            String uiElasticserachURLName = uiElasticsearchURL.getUrlName();
            UIElasticsearchURL prevElasticserachURL = prevUIElasticsearchConfig.getURL(uiElasticserachURLName);
            String uiElasticsearchURLDN = LdapUtils.dnOf("dcmuiElasticsearchURLName", uiElasticsearchURL.getUrlName(), uiElasticsearchConfigDN);
            if (prevElasticserachURL == null) {
                ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                        diffs,
                        uiElasticsearchURLDN,
                        ConfigurationChanges.ChangeType.C
                );
                config.createSubcontext(uiElasticsearchURLDN, storeTo(ldapObj, uiElasticsearchURL, new BasicAttributes(true)));
            }
            else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiElasticsearchConfigDN, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(uiElasticsearchURLDN, storeDiff(ldapObj, prevElasticserachURL, uiElasticsearchURL,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiff(ConfigurationChanges.ModifiedObject ldapObj, UIElasticsearchURL prev,
                                                              UIElasticsearchURL uiElasticsearchURL, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiElasticsearchURL",
                prev.getUrl(),
                uiElasticsearchURL.getUrl(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiElasticsearchURLKeycloakServer",
                prev.getElasticsearchURLKeycloakServer(),
                uiElasticsearchURL.getElasticsearchURLKeycloakServer(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiAuditEnterpriseSiteID",
                prev.getAuditEnterpriseSiteID(),
                uiElasticsearchURL.getAuditEnterpriseSiteID(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiElasticsearchIsDefault",
                prev.isDefault(),
                uiElasticsearchURL.isDefault(), false) ;
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiElasticsearchInstalled",
                prev.isInstalled(),
                uiElasticsearchURL.isInstalled(), true) ;
        return mods;
    }
    private List<ModificationItem> storeDiff(ConfigurationChanges.ModifiedObject ldapObj, UICompareSide prev,
                                                              UICompareSide uiCompareSide, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiCompareSideName",
                prev.getName(),
                uiCompareSide.getName(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiCompareSideDescription",
                prev.getDescription(),
                uiCompareSide.getDescription(),null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiCompareSideCluster",
                prev.getCluster(),
                uiCompareSide.getCluster(),null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiCompareSideElasticsearch",
                prev.getElasticsearch(),
                uiCompareSide.getElasticsearch(),null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiCompareSideQueueName",
                prev.getQueueName(),
                uiCompareSide.getQueueName(),null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiCompareSideInstalled",
                prev.isInstalled(),
                uiCompareSide.isInstalled(), true) ;
        return mods;
    }
    private List<ModificationItem> storeDiffs(ConfigurationChanges diffs,String dn, ConfigurationChanges.ModifiedObject ldapObj, UIDashboardConfig prev,
                                              UIDashboardConfig uiDashboardConfig, ArrayList<ModificationItem> mods)throws NamingException{
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiQueueName",
                prev.getQueueNames(),
                uiDashboardConfig.getQueueNames());
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiExportName",
                prev.getExportNames(),
                uiDashboardConfig.getExportNames());
        LdapUtils.storeDiff(ldapObj, mods, "dicomuiDeviceName",
                prev.getDeviceNames(),
                uiDashboardConfig.getDeviceNames());
        LdapUtils.storeDiff(ldapObj, mods, "dicomuiIgnoreParams",
                prev.getIgnoreParams(),
                uiDashboardConfig.getIgnoreParams());
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiShowStarBlock",
                prev.isShowStarBlock(),
                uiDashboardConfig.isShowStarBlock(),true);
        LdapUtils.storeDiff(ldapObj, mods, "dicomuiDockerContainer",
                prev.getDockerContainers(),
                uiDashboardConfig.getDockerContainers());
        LdapUtils.storeDiffObject(ldapObj, mods,"dcmuiCountAET",
                prev.getCountAet(),
                uiDashboardConfig.getCountAet(), null);
        mergeUICompareSide(diffs, prev, uiDashboardConfig, dn);
        return mods;
    }

    private void mergeDiffConfigs(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIDiffConfig prevUIDiffConfig : prevUIConfig.getDiffConfigs()) {
            String prevUIDiffConfigName = prevUIDiffConfig.getName();
            if (uiConfig.getDiffConfig(prevUIDiffConfigName) == null) {
                String dn = LdapUtils.dnOf("dcmuiDiffConfigName", prevUIDiffConfigName, uiConfigDN);
                for (UIDiffCriteria prevUIDiffCriteria : prevUIDiffConfig.getCriterias())
                    deleteCriteria(diffs, prevUIDiffCriteria.getTitle(), dn);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIDiffConfig uiDiffConfig : uiConfig.getDiffConfigs()) {
            String uiDiffConfigName = uiDiffConfig.getName();
            String uiDiffConfigDN = LdapUtils.dnOf("dcmuiDiffConfigName", uiDiffConfigName, uiConfigDN);
            UIDiffConfig prevUIDiffConfig = prevUIConfig.getDiffConfig(uiDiffConfigName);
            if (prevUIDiffConfig == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiDiffConfigDN, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(uiDiffConfigDN, storeTo(diffs, uiDiffConfigDN, uiDiffConfig, new BasicAttributes(true)));
                storeDiffCriterias(diffs, uiDiffConfigDN, uiDiffConfig);
            }
            else{
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiDiffConfigDN, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(uiDiffConfigDN, storeDiffs(diffs, uiDiffConfigDN, prevUIDiffConfig, uiDiffConfig,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges diffs, String uiDiffConfigDN, UIDiffConfig prev,
                                              UIDiffConfig uiDiffConfig, ArrayList<ModificationItem> mods)
        throws NamingException {
        ConfigurationChanges.ModifiedObject ldapObj =
                ConfigurationChanges.addModifiedObject(diffs, uiDiffConfigDN, ConfigurationChanges.ChangeType.U);

        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffCallingAET",
                prev.getCallingAET(),
                uiDiffConfig.getCallingAET(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffPrimaryCFindSCP",
                prev.getPrimaryCFindSCP(),
                uiDiffConfig.getPrimaryCFindSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffPrimaryCMoveSCP",
                prev.getPrimaryCMoveSCP(),
                uiDiffConfig.getPrimaryCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffPrimaryCStoreSCP",
                prev.getPrimaryCStoreSCP(),
                uiDiffConfig.getPrimaryCStoreSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffSecondaryCFindSCP",
                prev.getSecondaryCFindSCP(),
                uiDiffConfig.getSecondaryCFindSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffSecondaryCMoveSCP",
                prev.getSecondaryCMoveSCP(),
                uiDiffConfig.getSecondaryCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDiffSecondaryCStoreSCP",
                prev.getSecondaryCStoreSCP(),
                uiDiffConfig.getSecondaryCStoreSCP(), null);
        mergeUIDiffCriteria(diffs, prev, uiDiffConfig, uiDiffConfigDN);
        ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
        return mods;
    }

    private void mergeUIDiffCriteria(
            ConfigurationChanges diffs, UIDiffConfig prevUIDiffConfig, UIDiffConfig uiDiffConfig, String uiDiffConfigDN)
        throws NamingException {
        for (UIDiffCriteria prevUIDiffCriteria : prevUIDiffConfig.getCriterias()) {
            String prevUIDiffCriteriaTitle = prevUIDiffCriteria.getTitle();
            if (uiDiffConfig.getCriteria(prevUIDiffCriteriaTitle) == null)
                deleteCriteria(diffs, prevUIDiffCriteriaTitle, uiDiffConfigDN);
        }
        for (UIDiffCriteria uiDiffCriteria : uiDiffConfig.getCriterias()) {
            String uiDiffCriteriaTitle = uiDiffCriteria.getTitle();
            UIDiffCriteria prevUIDiffCriteria = prevUIDiffConfig.getCriteria(uiDiffCriteriaTitle);
            String uiDiffCriteriaDN = LdapUtils.dnOf("dcmuiDiffCriteriaTitle", uiDiffCriteria.getTitle(), uiDiffConfigDN);
            if (prevUIDiffCriteria == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiDiffCriteriaDN, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(uiDiffCriteriaDN, storeTo(ldapObj, uiDiffCriteria, new BasicAttributes(true)));
            }
            else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiDiffCriteriaDN, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(uiDiffCriteriaDN, storeDiffs(ldapObj, prevUIDiffCriteria, uiDiffCriteria,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void deleteCriteria(ConfigurationChanges diffs, String prevUIDiffCriteriaTitle, String uiDiffConfigDN)
        throws NamingException {
        String dn = LdapUtils.dnOf("dcmuiDiffCriteriaTitle", prevUIDiffCriteriaTitle, uiDiffConfigDN);
        config.destroySubcontext(dn);
        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIDiffCriteria prev,
                                              UIDiffCriteria uiDiffCriteria, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomDescription",
                prev.getDescription(),
                uiDiffCriteria.getDescription(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffCriteriaNumber",
                prev.getNumber(),
                uiDiffCriteria.getNumber(), 0);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffIncludeMissing",
                prev.isIncludeMissing(),
                uiDiffCriteria.isIncludeMissing(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAttributeSetID",
                prev.getAttributeSetID(),
                uiDiffCriteria.getAttributeSetID(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffAction",
                prev.getActions(),
                uiDiffCriteria.getActions());
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiDiffGroupButton",
                prev.getGroupButtons(),
                uiDiffCriteria.getGroupButtons());
        return mods;
    }

}