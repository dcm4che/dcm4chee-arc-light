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
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ui.*;

import javax.enterprise.context.ApplicationScoped;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since Nov 2017
 */
@ApplicationScoped
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
        storeLanguageConfigs(diffs, uiConfig, uiConfigDN);
        storeLTableConfigs(diffs, uiConfig, uiConfigDN);
        storeDeviceURL(diffs, uiConfig, uiConfigDN);
        storeDeviceCluster(diffs, uiConfig, uiConfigDN);
        storeFiltersTemplate(diffs, uiConfig, uiConfigDN);
        storeAets(diffs, uiConfig, uiConfigDN);
        storeCreateDialogTemplate(diffs, uiConfig, uiConfigDN);
        storeWebApps(diffs, uiConfig, uiConfigDN);

    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIConfig uiConfig, Attributes attrs) {
        attrs.put("objectclass", "dcmuiConfig");
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiConfigName", uiConfig.getName(), null);
        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiModalities", uiConfig.getModalities());
        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiWidgetAets", uiConfig.getWidgetAets());
        LdapUtils.storeNotNullOrDef(ldapObj,attrs, "dcmuiXDSInterfaceURL", uiConfig.getXdsUrl(),null);
        LdapUtils.storeNotNullOrDef(ldapObj,attrs, "dcmuiBackgroundURL", uiConfig.getBackgroundUrl(),null);
        LdapUtils.storeNotNullOrDef(ldapObj,attrs, "dcmuiDateTimeFormat", uiConfig.getDateTimeFormat(),null);
        LdapUtils.storeNotDef(ldapObj,attrs, "dcmuiHideClock", uiConfig.isHideClock(),false);
        LdapUtils.storeNotDef(ldapObj,attrs, "dcmuiPatientIdVisibility", uiConfig.isShowAllPatientIDs(),false);
        LdapUtils.storeNotNullOrDef(ldapObj,attrs, "dcmuiPageTitle", uiConfig.getPageTitle(),null);
        LdapUtils.storeNotNullOrDef(ldapObj,attrs, "dcmuiPersonNameFormat", uiConfig.getPersonNameFormat(),null);
        LdapUtils.storeNotNullOrDef(ldapObj,attrs, "dcmuiLogoURL", uiConfig.getLogoUrl(),null);
        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiDefaultWidgetAets", uiConfig.getDefaultWidgetAets());
        LdapUtils.storeNotEmpty(ldapObj,attrs, "dcmuiMWLWorklistLabel", uiConfig.getMWLWorklistLabels());
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
    private void storeCreateDialogTemplate(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UICreateDialogTemplate uiDialogTemplate : uiConfig.getCreateDialogTemplates()) {
            String uiTemplateDN = LdapUtils.dnOf("dcmuiTemplateName", uiDialogTemplate.getTemplateName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiTemplateDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiTemplateDN, storeTo(ldapObj1, uiDialogTemplate, new BasicAttributes(true)));
        }
    }
    private void storeWebApps(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIWebAppList uiWebApps : uiConfig.getWebAppLists()) {
            String uiWebAppListDN = LdapUtils.dnOf("dcmuiWebAppListName", uiWebApps.getWebAppListName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiWebAppListDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiWebAppListDN, storeTo(ldapObj1, uiWebApps, new BasicAttributes(true)));
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
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UICreateDialogTemplate uiCreateDialogTemplate, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiCreateDialogTemplate"));
        attrs.put(new BasicAttribute("dcmuiTemplateName", uiCreateDialogTemplate.getTemplateName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomDescription", uiCreateDialogTemplate.getDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiDialog", uiCreateDialogTemplate.getDialog(), UIFunction.mwl);
        storeNotEmptyTags(ldapObj, attrs, "dcmTag", uiCreateDialogTemplate.getTags());
        return attrs;
    }

    private void storeNotEmptyTags(ConfigurationChanges.ModifiedObject ldapObj, Attributes attrs, String attrid, int[] vals) {
        if (vals != null && vals.length > 0) {
            attrs.put(tagsAttr(attrid, vals));
            if (ldapObj != null) {
                ConfigurationChanges.ModifiedAttribute attribute = new ConfigurationChanges.ModifiedAttribute(attrid);
                for (int val : vals)
                    attribute.addValue(val);
                ldapObj.add(attribute);
            }
        }
    }

    private Attribute tagsAttr(String attrID, int[] tags) {
        Attribute attr = new BasicAttribute(attrID);
        for (int tag : tags)
            attr.add(TagUtils.toHexString(tag));
        return attr;
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIWebAppList uiWebAppList, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiWebAppConfig"));
        attrs.put(new BasicAttribute("dcmuiWebAppListName", uiWebAppList.getWebAppListName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiWebAppListDescription", uiWebAppList.getWebAppListDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiMode", uiWebAppList.getMode(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiWebApps", uiWebAppList.getWebApps());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedUserRole", uiWebAppList.getAcceptedRole());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedUserName", uiWebAppList.getAcceptedUserName());
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
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiClusterWebApp", uiDeviceCluster.getClusterWebApp(), null);
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
        LdapUtils.storeNotNullOrDef(ldapObj, attrs,"dcmuiCountWebApp",uiDashboardConfig.getCountWebApp(),null);
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

    private void storeLanguageProfiles(ConfigurationChanges diffs, String uiLanguageConfigDN, UILanguageConfig uiLanguageConfig)
            throws NamingException {
        for (UILanguageProfile uiLanguageProfile : uiLanguageConfig.getLanguageProfiles()) {
            String uiLanguageProfileDN = LdapUtils.dnOf("dcmuiLanguageProfileName", uiLanguageProfile.getProfileName(), uiLanguageConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                    diffs,
                    uiLanguageProfileDN,
                    ConfigurationChanges.ChangeType.C
            );
            config.createSubcontext(uiLanguageProfileDN, storeTo(ldapObj, uiLanguageProfile, new BasicAttributes(true)));
        }
    }
    private void storeTableColumns(ConfigurationChanges diffs, String uiTableConfigDN, UITableConfig uiTableConfig)
            throws NamingException {
        for (UITableColumn uiTableColumn : uiTableConfig.getTableColumns()) {
            String uiTableColumnDN = LdapUtils.dnOf("dcmuiColumnName", uiTableColumn.getColumnName(), uiTableConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                    diffs,
                    uiTableColumnDN,
                    ConfigurationChanges.ChangeType.C
            );
            config.createSubcontext(uiTableColumnDN, storeTo(ldapObj, uiTableColumn, new BasicAttributes(true)));
        }
    }

    private void storeLanguageConfigs(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UILanguageConfig uiLanguageConfig : uiConfig.getLanguageConfigs()) {
            String uiLanguageConfigDN = LdapUtils.dnOf("dcmuiLanguageConfigName", uiLanguageConfig.getName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 =
                    ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiLanguageConfigDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiLanguageConfigDN, storeTo(ldapObj1, uiLanguageConfig, new BasicAttributes(true)));
            storeLanguageProfiles(diffs, uiLanguageConfigDN, uiLanguageConfig);
        }
    }
    private void storeLTableConfigs(ConfigurationChanges diffs, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UITableConfig uiTableConfig : uiConfig.getTableConfigs()) {
            String uiTableConfigDN = LdapUtils.dnOf("dcmuiTableConfigName", uiTableConfig.getName(), uiConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj1 = ConfigurationChanges.addModifiedObjectIfVerbose(diffs, uiTableConfigDN, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(uiTableConfigDN, storeTo(ldapObj1, uiTableConfig, new BasicAttributes(true)));
            storeTableColumns(diffs, uiTableConfigDN, uiTableConfig);
        }
    }

/*    private void storeLanguageProfile(ConfigurationChanges diffs, String uiLanguageConfigDN, UILanguageConfig uiLanguageConfig)
            throws NamingException {
        for (UILanguageProfile uiLanguageProfile : uiLanguageConfig.getLanguageProfiles()) {
            String uiLanguageProfileDN = LdapUtils.dnOf("dcmuiLanguageProfileName", uiLanguageProfile.getProfileName(), uiLanguageConfigDN);
            ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                    diffs,
                    uiLanguageProfileDN,
                    ConfigurationChanges.ChangeType.C
            );
            config.createSubcontext(uiLanguageProfileDN, storeTo(ldapObj, uiLanguageProfile, new BasicAttributes(true)));
        }
    }*/
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

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UILanguageConfig uiLanguageConfig, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiLanguageConfig"));
        attrs.put(new BasicAttribute("dcmuiLanguageConfigName", uiLanguageConfig.getName()));
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmLanguages", uiLanguageConfig.getLanguages());
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UITableConfig tableConfig, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiTableConfig"));
        attrs.put(new BasicAttribute("dcmTableConfigName", tableConfig.getName()));
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiTableConfigUsername", tableConfig.getUsername());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiTableConfigRoles", tableConfig.getRoles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiTableID", tableConfig.getTableId(), null);
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiTableConfigIsDefault",tableConfig.isDefault(),false);
        return attrs;
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UIElasticsearchURL uiElasticsearchURL, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiElasticsearchURLObjects"));
        attrs.put(new BasicAttribute("dcmuiElasticsearchURLName", uiElasticsearchURL.getUrlName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiElasticsearchWebApp", uiElasticsearchURL.getUrl(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiAuditEnterpriseSiteID", uiElasticsearchURL.getAuditEnterpriseSiteID(), null);
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiElasticsearchIsDefault",uiElasticsearchURL.isDefault(),false);
        LdapUtils.storeNotDef(ldapObj,attrs,"dcmuiElasticsearchInstalled",uiElasticsearchURL.isInstalled(),true);
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UILanguageProfile uiLanguageProfile, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiLanguageProfileObjects"));
        attrs.put(new BasicAttribute("dcmuiLanguageProfileName", uiLanguageProfile.getProfileName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDefaultLanguage", uiLanguageProfile.getDefaultLanguage(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmuiLanguageProfileRole", uiLanguageProfile.getAcceptedUserRoles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiLanguageProfileUsername", uiLanguageProfile.getUserName(), null);
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UITableColumn uiTableColumn, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiTableColumnConfigObjects"));
        attrs.put(new BasicAttribute("dcmuiColumnName", uiTableColumn.getColumnName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiColumnId", uiTableColumn.getColumnId(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiColumnTitle", uiTableColumn.getColumnTitle(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiValuePath", uiTableColumn.getValuePath(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiValueType", uiTableColumn.getValueType(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiColumnWidth", uiTableColumn.getColumnWidth(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmuiColumnOrder", uiTableColumn.getColumnOrder(), 0);
        return attrs;
    }
    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, UICompareSide uiCompareSide, Attributes attrs) {
        attrs.put(new BasicAttribute("objectclass", "dcmuiCompareSideObjects"));
        attrs.put(new BasicAttribute("dcmuiCompareSideName", uiCompareSide.getName()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmuiCompareSideDescription", uiCompareSide.getDescription(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmuiCompareSideOrder", uiCompareSide.getOrder(), 0);
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
        uiConfig.setBackgroundUrl(LdapUtils.stringValue(attrs.get("dcmuiBackgroundURL"),null));
        uiConfig.setDateTimeFormat(LdapUtils.stringValue(attrs.get("dcmuiDateTimeFormat"),null));
        uiConfig.setHideClock(LdapUtils.booleanValue(attrs.get("dcmuiHideClock"),false));
        uiConfig.setShowAllPatientIDs(LdapUtils.booleanValue(attrs.get("dcmuiPatientIdVisibility"),false));
        uiConfig.setPageTitle(LdapUtils.stringValue(attrs.get("dcmuiPageTitle"),null));
        uiConfig.setPersonNameFormat(LdapUtils.stringValue(attrs.get("dcmuiPersonNameFormat"),null));
        uiConfig.setLogoUrl(LdapUtils.stringValue(attrs.get("dcmuiLogoURL"),null));
        uiConfig.setDefaultWidgetAets(LdapUtils.stringArray(attrs.get("dcmuiDefaultWidgetAets")));
        uiConfig.setMWLWorklistLabels(LdapUtils.stringArray(attrs.get("dcmuiMWLWorklistLabel")));
        loadPermissions(uiConfig, uiConfigDN);
        loadDiffConfigs(uiConfig, uiConfigDN);
        loadDashboardConfigs(uiConfig, uiConfigDN);
        loadElasticsearchConfigs(uiConfig, uiConfigDN);
        loadLanguageConfigs(uiConfig, uiConfigDN);
        loadTableConfigs(uiConfig, uiConfigDN);
        loadDeviceURLs(uiConfig, uiConfigDN);
        loadDeviceClusters(uiConfig, uiConfigDN);
        loadFilterTemplates(uiConfig, uiConfigDN);
        loadAetList(uiConfig, uiConfigDN);
        loadCreateDialogTemplate(uiConfig, uiConfigDN);
        loadWebAppList(uiConfig, uiConfigDN);
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
    private void loadCreateDialogTemplate(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiCreateDialogTemplate)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UICreateDialogTemplate uiCreateDialogTemplate = new UICreateDialogTemplate((String) attrs.get("dcmuiTemplateName").get());
                uiCreateDialogTemplate.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                uiCreateDialogTemplate.setDialog(LdapUtils.enumValue(UIFunction.class, attrs.get("dcmuiDialog"), UIFunction.mwl));
                uiCreateDialogTemplate.setTags(tags(attrs.get("dcmTag")));
                uiConfig.addCreatDialogTemplate(uiCreateDialogTemplate);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private int[] tags(Attribute attr) throws NamingException {
        if (attr == null)
            return ByteUtils.EMPTY_INTS;

        int[] is = new int[attr.size()];
        for (int i = 0; i < is.length; i++)
            is[i] = TagUtils.intFromHexString((String) attr.get(i));

        return is;
    }

    private void loadWebAppList(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiWebAppConfig)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UIWebAppList uiWebAppList = new UIWebAppList((String) attrs.get("dcmuiWebAppListName").get());
                uiWebAppList.setWebAppListDescription(LdapUtils.stringValue(attrs.get("dcmuiWebAppListDescription"), null));
                uiWebAppList.setMode(LdapUtils.stringValue(attrs.get("dcmuiMode"), null));
                uiWebAppList.setWebApps(LdapUtils.stringArray(attrs.get("dcmuiWebApps")));
                uiWebAppList.setAcceptedRole(LdapUtils.stringArray(attrs.get("dcmAcceptedUserRole")));
                uiWebAppList.setAcceptedUserName(LdapUtils.stringArray(attrs.get("dcmAcceptedUserName")));
                uiConfig.addWebAppList(uiWebAppList);
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
                uiDeviceCluster.setClusterWebApp(LdapUtils.stringValue(attrs.get("dcmuiClusterWebApp"), null));
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
                uiDashboardConfig.setCountWebApp(LdapUtils.stringValue(attrs.get("dcmuiCountWebApp"),null));
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
                uiElasticsearchURL.setUrl(LdapUtils.stringValue(attrs.get("dcmuiElasticsearchWebApp"), null));
                uiElasticsearchURL.setAuditEnterpriseSiteID(LdapUtils.stringValue(attrs.get("dcmuiAuditEnterpriseSiteID"), null));
                uiElasticsearchURL.setDefault(LdapUtils.booleanValue(attrs.get("dcmuiElasticsearchIsDefault"),false));
                uiElasticsearchURL.setInstalled(LdapUtils.booleanValue(attrs.get("dcmuiElasticsearchInstalled"),true));
                uiElasticsearchConfig.addURL(uiElasticsearchURL);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadLanguageConfigs(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiLanguageConfig)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UILanguageConfig uiLanguageConfig = new UILanguageConfig((String) attrs.get("dcmuiLanguageConfigName").get());
                String uiLanguageConfigDN = LdapUtils.dnOf("dcmuiLanguageConfigName" , uiLanguageConfig.getName(), uiConfigDN);
                uiLanguageConfig.setLanguages(LdapUtils.stringArray(attrs.get("dcmLanguages")));
                loadLanguageProfile(uiLanguageConfig, uiLanguageConfigDN);
                uiConfig.addLanguageConfig(uiLanguageConfig);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadTableConfigs(UIConfig uiConfig, String uiConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiConfigDN, "(objectclass=dcmuiTableConfig)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UITableConfig uiTableConfig = new UITableConfig((String) attrs.get("dcmuiTableConfigName").get());
                String uiTableConfigDN = LdapUtils.dnOf("dcmuiTableConfigName" , uiTableConfig.getName(), uiConfigDN);
                uiTableConfig.setUsername(LdapUtils.stringArray(attrs.get("dcmuiTableConfigUsername")));
                uiTableConfig.setRoles(LdapUtils.stringArray(attrs.get("dcmuiTableConfigRoles")));
                uiTableConfig.setTableId(LdapUtils.stringValue(attrs.get("dcmuiTableID"),null));
                uiTableConfig.setDefault(LdapUtils.booleanValue(attrs.get("dcmuiTableConfigIsDefault"),false));
                loadTableColumn(uiTableConfig, uiTableConfigDN);
                uiConfig.addTableConfig(uiTableConfig);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadLanguageProfile(UILanguageConfig uiLanguageConfig, String uiLanguageConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiLanguageConfigDN, "(objectclass=dcmuiLanguageProfileObjects)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UILanguageProfile uiLanguageProfile = new UILanguageProfile((String) attrs.get("dcmuiLanguageProfileName").get());
                uiLanguageProfile.setDefaultLanguage(LdapUtils.stringValue(attrs.get("dcmDefaultLanguage"),null));
                uiLanguageProfile.setAcceptedUserRoles(LdapUtils.stringArray(attrs.get("dcmuiLanguageProfileRole")));
                uiLanguageProfile.setUserName(LdapUtils.stringValue(attrs.get("dcmuiLanguageProfileUsername"),null));
                uiLanguageConfig.addLanguageProfile(uiLanguageProfile);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }
    private void loadTableColumn(UITableConfig uiTableConfig, String uiTableConfigDN) throws NamingException {
        NamingEnumeration<SearchResult> ne =
                config.search(uiTableConfigDN, "(objectclass=dcmuiTableColumnConfigObjects)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                UITableColumn uiTableColumn = new UITableColumn((String) attrs.get("dcmuiColumnName").get());
                uiTableColumn.setColumnId(LdapUtils.stringValue(attrs.get("dcmuiColumnId"),null));
                uiTableColumn.setColumnTitle(LdapUtils.stringValue(attrs.get("dcmuiColumnTitle"),null));
                uiTableColumn.setValuePath(LdapUtils.stringValue(attrs.get("dcmuiValuePath"),null));
                uiTableColumn.setValueType(LdapUtils.stringValue(attrs.get("dcmuiValueType"),null));
                uiTableColumn.setColumnWidth(LdapUtils.stringValue(attrs.get("dcmuiColumnWidth"),null));
                uiTableColumn.setColumnOrder(LdapUtils.intValue(attrs.get("dcmuiColumnOrder"),null));
                uiTableConfig.addTableColumn(uiTableColumn);
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
                uiCompareSide.setOrder(LdapUtils.intValue(attrs.get("dcmuiCompareSideOrder"), 0));
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
        mergeLanguageConfigs(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeDeviceURL(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeDeviceCluster(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeFilterTemplate(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergePermissions(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeAetLists(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeCreateDialogTemplate(diffs, prevUIConfig, uiConfig, uiConfigDN);
        mergeWebAppLists(diffs, prevUIConfig, uiConfig, uiConfigDN);
    }

    private List<ModificationItem> storeDiff(ConfigurationChanges.ModifiedObject ldapObj, UIConfig prevUIConfig, UIConfig uiConfig, ArrayList<ModificationItem> mods) throws NamingException {
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiModalities",prevUIConfig.getModalities(),uiConfig.getModalities());
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiWidgetAets",prevUIConfig.getWidgetAets(),uiConfig.getWidgetAets());
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiMWLWorklistLabel",prevUIConfig.getMWLWorklistLabels(),uiConfig.getMWLWorklistLabels());
        LdapUtils.storeDiffObject(ldapObj,mods,"dcmuiXDSInterfaceURL",prevUIConfig.getXdsUrl(),uiConfig.getXdsUrl(),null);
        LdapUtils.storeDiffObject(ldapObj,mods,"dcmuiBackgroundURL",prevUIConfig.getBackgroundUrl(),uiConfig.getBackgroundUrl(),null);
        LdapUtils.storeDiffObject(ldapObj,mods,"dcmuiDateTimeFormat",prevUIConfig.getDateTimeFormat(),uiConfig.getDateTimeFormat(),null);
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiHideClock",prevUIConfig.isHideClock(),uiConfig.isHideClock(),false);
        LdapUtils.storeDiff(ldapObj,mods,"dcmuiPatientIdVisibility",prevUIConfig.isShowAllPatientIDs(),uiConfig.isShowAllPatientIDs(),false);
        LdapUtils.storeDiffObject(ldapObj,mods,"dcmuiPageTitle",prevUIConfig.getPageTitle(),uiConfig.getPageTitle(),null);
        LdapUtils.storeDiffObject(ldapObj,mods,"dcmuiPersonNameFormat",prevUIConfig.getPersonNameFormat(),uiConfig.getPersonNameFormat(),null);
        LdapUtils.storeDiffObject(ldapObj,mods,"dcmuiLogoURL",prevUIConfig.getLogoUrl(),uiConfig.getLogoUrl(),null);
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
    private void mergeCreateDialogTemplate(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UICreateDialogTemplate prevUICreateDialogTemplate : prevUIConfig.getCreateDialogTemplates()) {
            String prevUICreateDialogTemplateName = prevUICreateDialogTemplate.getTemplateName();
            if (uiConfig.getCreateDialogTemplate(prevUICreateDialogTemplateName) == null) {
                String dn = LdapUtils.dnOf("dcmuiTemplateName", prevUICreateDialogTemplateName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UICreateDialogTemplate uiCreateDialogTemplate : uiConfig.getCreateDialogTemplates()) {
            String uiCreateDialogTemplateName = uiCreateDialogTemplate.getTemplateName();
            String dn = LdapUtils.dnOf("dcmuiTemplateName", uiCreateDialogTemplateName, uiConfigDN);
            UICreateDialogTemplate prevUICreateDialogTemplate = prevUIConfig.getCreateDialogTemplate(uiCreateDialogTemplateName);
            if (prevUICreateDialogTemplate == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiCreateDialogTemplate, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUICreateDialogTemplate, uiCreateDialogTemplate,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }
    private void mergeWebAppLists(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN)
            throws NamingException {
        for (UIWebAppList prevUIWebAppList : prevUIConfig.getWebAppLists()) {
            String prevUIWebAppListName = prevUIWebAppList.getWebAppListName();
            if (uiConfig.getWebAppList(prevUIWebAppListName) == null) {
                String dn = LdapUtils.dnOf("dcmuiWebAppListName", prevUIWebAppListName, uiConfigDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UIWebAppList uiWebAppList : uiConfig.getWebAppLists()) {
            String uiWebAppListName = uiWebAppList.getWebAppListName();
            String dn = LdapUtils.dnOf("dcmuiWebAppListName", uiWebAppListName, uiConfigDN);
            UIWebAppList prevUIWebAppList = prevUIConfig.getWebAppList(uiWebAppListName);
            if (prevUIWebAppList == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiWebAppList, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevUIWebAppList, uiWebAppList,
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
    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UICreateDialogTemplate prev,
                                              UICreateDialogTemplate uiCreateDialogTemplate, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomDescription",
                prev.getDescription(),
                uiCreateDialogTemplate.getDescription(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiDialog",
                prev.getDialog(),
                uiCreateDialogTemplate.getDialog(), UIFunction.mwl);
        storeDiffTags(mods, "dcmTag", prev.getTags(), uiCreateDialogTemplate.getTags());
        return mods;
    }

    private void storeDiffTags(List<ModificationItem> mods, String attrId, int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals))
            mods.add((vals == null || vals.length == 0)
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(attrId))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE, tagsAttr(attrId, vals)));
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, UIWebAppList prev,
                                              UIWebAppList uiWebAppList, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiWebAppListDescription",
                prev.getWebAppListDescription(),
                uiWebAppList.getWebAppListDescription(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiMode",
                prev.getMode(),
                uiWebAppList.getMode(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiWebApps",
                prev.getWebApps(),
                uiWebAppList.getWebApps());
        LdapUtils.storeDiff(ldapObj, mods, "dcmAcceptedUserRole",
                prev.getAcceptedRole(),
                uiWebAppList.getAcceptedRole());
        LdapUtils.storeDiff(ldapObj, mods, "dcmAcceptedUserName",
                prev.getAcceptedUserName(),
                uiWebAppList.getAcceptedUserName());
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
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiClusterWebApp",
                prev.getClusterWebApp(),
                uiDeviceCluster.getClusterWebApp(), null);
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
    private void mergeLanguageConfigs(ConfigurationChanges diffs, UIConfig prevUIConfig, UIConfig uiConfig, String uiConfigDN) throws NamingException {
        for (UILanguageConfig prevUILanguageConfig : prevUIConfig.getLanguageConfigs()) {
            String prevUILanguageConfigName = prevUILanguageConfig.getName();
            if (uiConfig.getLanguageConfig(prevUILanguageConfigName) == null) {
                String dn = LdapUtils.dnOf("dcmuiLanguageConfigName", prevUILanguageConfigName, uiConfigDN);
                for (UILanguageProfile prevLanguageProfile : prevUILanguageConfig.getLanguageProfiles())
                    deleteUILanguageProfile(diffs, prevLanguageProfile.getProfileName(), dn);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (UILanguageConfig uiLanguageConfig : uiConfig.getLanguageConfigs()) {
            String uiLanguageConfigName = uiLanguageConfig.getName();
            String dn = LdapUtils.dnOf("dcmuiLanguageConfigName", uiLanguageConfigName, uiConfigDN);
            UILanguageConfig prevUILanguageConfig = prevUIConfig.getLanguageConfig(uiLanguageConfigName);
            if (prevUILanguageConfig == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                uiLanguageConfig, new BasicAttributes(true)));
                storeLanguageProfiles(diffs, dn, uiLanguageConfig);
            }
            else{
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeUILanguageConfig(diffs, dn, prevUILanguageConfig, uiLanguageConfig,
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
    private void deleteUILanguageProfile(ConfigurationChanges diffs, String prevUILanguageConfigName, String uiLanguageConfigDN)
            throws NamingException {
        String dn = LdapUtils.dnOf("dcmuiLanguageProfileName", prevUILanguageConfigName, uiLanguageConfigDN);
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
    private List<ModificationItem> storeUILanguageConfig(
            ConfigurationChanges diffs, String uiLanguageConfigDN, UILanguageConfig a, UILanguageConfig b,
            ArrayList<ModificationItem> mods) throws NamingException {
        ConfigurationChanges.ModifiedObject ldapObj =  ConfigurationChanges.addModifiedObject(diffs, uiLanguageConfigDN, ConfigurationChanges.ChangeType.U);
        mergeUILanguageProfiles(diffs, a, b, uiLanguageConfigDN);
        ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
        return mods;
    }

    private void mergeUILanguageProfiles(
            ConfigurationChanges diffs, UILanguageConfig prevUILanguageConfig,
            UILanguageConfig uiLanguageConfig, String uiLanguageConfigDN) throws NamingException {
        for (UILanguageProfile prevUILanguageProfile : prevUILanguageConfig.getLanguageProfiles()) {
            String prevUILanguageProfileName = prevUILanguageProfile.getProfileName();
            if (uiLanguageConfig.getLanguageProfile(prevUILanguageProfileName) == null)
                deleteUILanguageProfile(diffs, prevUILanguageProfileName, uiLanguageConfigDN);
        }
        for (UILanguageProfile uiLanguageProfile : uiLanguageConfig.getLanguageProfiles()) {
            String uiLanguageProfileName = uiLanguageProfile.getProfileName();
            UILanguageProfile prevLanguageProfile = prevUILanguageConfig.getLanguageProfile(uiLanguageProfileName);
            String uiLanguageProfileDN = LdapUtils.dnOf("dcmuiLanguageProfileName", uiLanguageProfile.getProfileName(), uiLanguageConfigDN);
            if (prevLanguageProfile == null) {
                ConfigurationChanges.ModifiedObject ldapObj = ConfigurationChanges.addModifiedObjectIfVerbose(
                        diffs,
                        uiLanguageProfileDN,
                        ConfigurationChanges.ChangeType.C
                );
                config.createSubcontext(uiLanguageProfileDN, storeTo(ldapObj, uiLanguageProfile, new BasicAttributes(true)));
            }
            else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, uiLanguageConfigDN, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(uiLanguageProfileDN, storeLanguageProfile(ldapObj, prevLanguageProfile, uiLanguageProfile,
                        new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
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
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiElasticsearchWebApp",
                prev.getUrl(),
                uiElasticsearchURL.getUrl(), null);
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
    private List<ModificationItem> storeLanguageProfile(ConfigurationChanges.ModifiedObject ldapObj, UILanguageProfile prev,
                                                              UILanguageProfile uiLanguageProfile, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDefaultLanguage",
                prev.getDefaultLanguage(),
                uiLanguageProfile.getDefaultLanguage(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmuiLanguageProfileRole",
                prev.getAcceptedUserRoles(),
                uiLanguageProfile.getAcceptedUserRoles());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiLanguageProfileUsername",
                prev.getUserName(),
                uiLanguageProfile.getUserName(), null);
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
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmuiCompareSideOrder",
                prev.getOrder(),
                uiCompareSide.getOrder(),null);
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
        LdapUtils.storeDiffObject(ldapObj, mods,"dcmuiCountWebApp",
                prev.getCountWebApp(),
                uiDashboardConfig.getCountWebApp(), null);
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