/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
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
 * **** END LICENSE BLOCK *****
 */

package org.dcm4chee.archive.conf.ldap;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che3.conf.ldap.LdapUtils;
import org.dcm4che3.data.ValueSelector;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
class LdapArchiveConfiguration extends LdapDicomConfigurationExtension {

    @Override
    protected void storeTo(Device device, Attributes attrs) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        attrs.get("objectclass").add("dcmArchiveDevice");
        LdapUtils.storeNotNull(attrs, "dcmFuzzyAlgorithmClass", arcDev.getFuzzyAlgorithmClass());
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveDevice"))
            return;

        ArchiveDeviceExtension arcdev = new ArchiveDeviceExtension();
        device.addDeviceExtension(arcdev);
        arcdev.setFuzzyAlgorithmClass(LdapUtils.stringValue(attrs.get("dcmFuzzyAlgorithmClass"), null));
    }

    @Override
    protected void storeChilds(String deviceDN, Device device)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcDev = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        storeAttributeFilter(deviceDN, arcDev);
    }

    @Override
    protected void loadChilds(Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcdev == null)
            return;

        loadAttributeFilters(arcdev, deviceDN);
    }

    @Override
    protected void storeDiffs(Device prev, Device device, List<ModificationItem> mods) {
        ArchiveDeviceExtension aa = prev.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmFuzzyAlgorithmClass", aa.getFuzzyAlgorithmClass(), bb.getFuzzyAlgorithmClass());
    }

    @Override
    protected void mergeChilds(Device prev, Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension aa = prev
                .getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        mergeAttributeFilters(aa, bb, deviceDN);
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Attributes attrs) {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        attrs.get("objectclass").add("dcmArchiveNetworkAE");
        LdapUtils.storeNotDef(attrs, "hl7PIXQuery", aeExt.isPixQuery(), false);
        LdapUtils.storeNotNull(attrs, "hl7PIXConsumerApplication", aeExt.getLocalPIXConsumerApplication());
        LdapUtils.storeNotNull(attrs, "hl7PIXManagerApplication", aeExt.getRemotePIXManagerApplication());
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveNetworkAE"))
            return;

        ArchiveAEExtension aeExt = new ArchiveAEExtension();
        ae.addAEExtension(aeExt);
        aeExt.setPixQuery(LdapUtils.booleanValue(attrs.get("hl7PIXQuery"), false));
        aeExt.setLocalPIXConsumerApplication(LdapUtils.stringValue(attrs.get("hl7PIXConsumerApplication"), null));
        aeExt.setRemotePIXManagerApplication(LdapUtils.stringValue(attrs.get("hl7PIXManagerApplication"), null));
    }

    @Override
    protected void storeDiffs(ApplicationEntity prev, ApplicationEntity ae, List<ModificationItem> mods) {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "hl7PIXQuery", aa.isPixQuery(), bb.isPixQuery(), false);
        LdapUtils.storeDiff(mods, "hl7PIXConsumerApplication",
                aa.getLocalPIXConsumerApplication(), bb.getLocalPIXConsumerApplication());
        LdapUtils.storeDiff(mods, "hl7PIXManagerApplication",
                aa.getRemotePIXManagerApplication(), bb.getRemotePIXManagerApplication());
    }

    private void storeAttributeFilter(String deviceDN, ArchiveDeviceExtension arcDev)
            throws NamingException {
        for (Entity entity : Entity.values()) {
            config.createSubcontext(
                    LdapUtils.dnOf("dcmEntity", entity.name(), deviceDN),
                    storeTo(arcDev.getAttributeFilter(entity), entity, new BasicAttributes(true)));
        }
    }

    private static Attributes storeTo(AttributeFilter filter, Entity entity,  BasicAttributes attrs) {
        attrs.put("objectclass", "dcmAttributeFilter");
        attrs.put("dcmEntity", entity.name());
        attrs.put(tagsAttr("dcmTag", filter.getSelection()));
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute1", filter.getCustomAttribute1());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute2", filter.getCustomAttribute2());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute3", filter.getCustomAttribute3());
        return attrs;
    }

    private static Attribute tagsAttr(String attrID, int[] tags) {
        Attribute attr = new BasicAttribute(attrID);
        for (int tag : tags)
            attr.add(TagUtils.toHexString(tag));
        return attr;
    }

    private void loadAttributeFilters(ArchiveDeviceExtension device, String deviceDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmAttributeFilter)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                AttributeFilter filter = new AttributeFilter(tags(attrs.get("dcmTag")));
                filter.setCustomAttribute1(valueSelector(attrs.get("dcmCustomAttribute1")));
                filter.setCustomAttribute2(valueSelector(attrs.get("dcmCustomAttribute2")));
                filter.setCustomAttribute3(valueSelector(attrs.get("dcmCustomAttribute3")));
                device.setAttributeFilter(
                        Entity.valueOf(LdapUtils.stringValue(attrs.get("dcmEntity"), null)),
                        filter);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private static ValueSelector valueSelector(Attribute attr)
            throws NamingException {
        return attr != null ? ValueSelector.valueOf((String) attr.get()) : null;
    }

    private static int[] tags(Attribute attr) throws NamingException {
        int[] is = new int[attr.size()];
        for (int i = 0; i < is.length; i++)
            is[i] = Integer.parseInt((String) attr.get(i), 16);

        return is;
    }


    private void mergeAttributeFilters(ArchiveDeviceExtension prev, ArchiveDeviceExtension devExt,
                                       String deviceDN) throws NamingException {
        for (Entity entity : Entity.values())
            config.modifyAttributes(
                    LdapUtils.dnOf("dcmEntity", entity.toString(), deviceDN),
                    storeDiffs(prev.getAttributeFilter(entity),
                            devExt.getAttributeFilter(entity),
                            new ArrayList<ModificationItem>()));
    }

    private List<ModificationItem> storeDiffs(AttributeFilter prev, AttributeFilter filter,
                                              List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", prev.getSelection(), filter.getSelection());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute1",
                prev.getCustomAttribute1(), filter.getCustomAttribute1());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute2",
                prev.getCustomAttribute2(), filter.getCustomAttribute2());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute3",
                prev.getCustomAttribute3(), filter.getCustomAttribute3());
        return mods;
    }

    private void storeDiffTags(List<ModificationItem> mods, String attrId, int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals))
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, tagsAttr(attrId, vals)));
    }
}
