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
import org.dcm4chee.arc.conf.ui.UIConfig;
import org.dcm4chee.arc.conf.ui.UIConfigDeviceExtension;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchResult;

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

    private String dnOf(UIConfig conf, String deviceDN) {
        return LdapUtils.dnOf("dcmuiConfigName" , conf.getName(), deviceDN);
    }

    private void store(ConfigurationChanges diffs, String deviceDN, UIConfig conf)
            throws NamingException {
        String confDN = dnOf(conf, deviceDN);
        ConfigurationChanges.ModifiedObject diffs1 =
                ConfigurationChanges.addModifiedObject(diffs, confDN, ConfigurationChanges.ChangeType.C);
        config.createSubcontext(confDN,
                storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, diffs1),
                        conf, new BasicAttributes(true)));
        storePermissions(diffs, conf, confDN);
        storeDiffConfigs(diffs, conf, confDN);
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject diffs, UIConfig conf, Attributes attrs) {
        attrs.put("objectclass", "dcmuiConfig");
        attrs.put("dcmuiConfigName", conf.getName());
        return attrs;
    }

    private void storePermissions(ConfigurationChanges diffs, UIConfig conf, String configDN)
            throws NamingException {
        //TODO
    }

    private void storeDiffConfigs(ConfigurationChanges diffs, UIConfig conf, String configDN)
            throws NamingException {
        //TODO
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
        UIConfig conf = new UIConfig(LdapUtils.stringValue(attrs.get("dcmuiDiffConfigName"), null));
        String confDN = dnOf(conf, deviceDN);
        loadPermissions(conf, confDN);
        loadDiffConfigs(conf, confDN);
        return conf;
    }

    private void loadPermissions(UIConfig conf, String uiDN) throws NamingException {
        //TODO
    }

    private void loadDiffConfigs(UIConfig conf, String uiDN) throws NamingException {
        //TODO
    }

    @Override
    protected void mergeChilds(ConfigurationChanges diffs, Device prev, Device device, String deviceDN)
            throws NamingException {
        UIConfigDeviceExtension prevUIConfigExt = prev.getDeviceExtension(UIConfigDeviceExtension.class);
        UIConfigDeviceExtension uiConfigExt = device.getDeviceExtension(UIConfigDeviceExtension.class);
        if (prevUIConfigExt != null)
            for (UIConfig prevConf : prevUIConfigExt.getUIConfigs()) {
                if (uiConfigExt == null || uiConfigExt.getUIConfig(prevConf.getName()) == null) {
                    String dn = dnOf(prevConf, deviceDN);
                    config.destroySubcontextWithChilds(dn);
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
                }
            }

        if (uiConfigExt == null)
            return;

        for (UIConfig conf : uiConfigExt.getUIConfigs()) {
            if (prevUIConfigExt == null || prevUIConfigExt.getUIConfig(conf.getName()) == null)
                store(diffs, deviceDN, conf);
            else
                merge(diffs, prevUIConfigExt.getUIConfig(conf.getName()), conf, deviceDN);
        }
    }

    private void merge(ConfigurationChanges diffs, UIConfig prevConf, UIConfig conf, String dn) throws NamingException {
        ConfigurationChanges.ModifiedObject diffs1 =
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
        mergePermissions(diffs, prevConf, conf, dn);
        mergeDiffConfigs(diffs, prevConf, conf, dn);
    }

    private void mergePermissions(ConfigurationChanges diffs, UIConfig prevConf, UIConfig conf, String configDN)
            throws NamingException {
        //TODO
    }

    private void mergeDiffConfigs(ConfigurationChanges diffs, UIConfig prevConf, UIConfig conf, String configDN)
            throws NamingException {
        //TODO
    }

}