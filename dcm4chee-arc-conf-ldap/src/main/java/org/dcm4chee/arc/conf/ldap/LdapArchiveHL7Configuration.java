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

package org.dcm4chee.arc.conf.ldap;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.ldap.LdapUtils;
import org.dcm4che3.conf.ldap.hl7.LdapHL7ConfigurationExtension;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class LdapArchiveHL7Configuration extends LdapHL7ConfigurationExtension {
    @Override
    public void storeTo(HL7Application hl7App, String deviceDN, Attributes attrs) {
        ArchiveHL7ApplicationExtension ext =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveHL7Application");
        LdapUtils.storeNotNull(attrs, "hl7PatientUpdateTemplateURI", ext.getPatientUpdateTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7ImportReportTemplateURI", ext.getImportReportTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7ScheduleProcedureTemplateURI", ext.getScheduleProcedureTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7LogFilePattern", ext.getHl7LogFilePattern());
        LdapUtils.storeNotNull(attrs, "hl7ErrorLogFilePattern", ext.getHl7ErrorLogFilePattern());
        LdapUtils.storeNotNull(attrs, "dicomAETitle", ext.getAETitle());
    }

    @Override
    public void storeChilds(String appDN, HL7Application hl7App) throws NamingException {
        ArchiveHL7ApplicationExtension ext =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (ext == null)
            return;

        LdapArchiveConfiguration.storeHL7ForwardRules(ext.getHL7ForwardRules(), appDN, getDicomConfiguration());
        LdapArchiveConfiguration.storeScheduledStations(ext.getHL7OrderScheduledStations(), appDN, getDicomConfiguration());
        LdapArchiveConfiguration.storeHL7OrderSPSStatus(ext.getHL7OrderSPSStatuses(), appDN, getDicomConfiguration());
    }

    @Override
    public void loadFrom(HL7Application hl7App, Attributes attrs)
            throws NamingException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveHL7Application"))
            return;

        ArchiveHL7ApplicationExtension ext = new ArchiveHL7ApplicationExtension();
        hl7App.addHL7ApplicationExtension(ext);
        ext.setPatientUpdateTemplateURI(LdapUtils.stringValue(attrs.get("hl7PatientUpdateTemplateURI"), null));
        ext.setImportReportTemplateURI(LdapUtils.stringValue(attrs.get("hl7ImportReportTemplateURI"), null));
        ext.setScheduleProcedureTemplateURI(LdapUtils.stringValue(attrs.get("hl7ScheduleProcedureTemplateURI"), null));
        ext.setHl7LogFilePattern(LdapUtils.stringValue(attrs.get("hl7LogFilePattern"), null));
        ext.setHl7ErrorLogFilePattern(LdapUtils.stringValue(attrs.get("hl7ErrorLogFilePattern"), null));
        ext.setAETitle(LdapUtils.stringValue(attrs.get("dicomAETitle"), null));
    }

    @Override
    public void loadChilds(HL7Application hl7App, String appDN) throws NamingException, ConfigurationException {
        ArchiveHL7ApplicationExtension ext =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (ext == null)
            return;

        LdapArchiveConfiguration.loadHL7ForwardRules(ext.getHL7ForwardRules(), appDN, getDicomConfiguration());
        LdapArchiveConfiguration.loadScheduledStations(ext.getHL7OrderScheduledStations(), appDN, getDicomConfiguration());
        LdapArchiveConfiguration.loadHL7OrderSPSStatus(ext.getHL7OrderSPSStatuses(), appDN, getDicomConfiguration());
    }

    @Override
    public void storeDiffs(HL7Application a, HL7Application b,
                           List<ModificationItem> mods) {
        ArchiveHL7ApplicationExtension aa =
                a.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        ArchiveHL7ApplicationExtension bb =
                b.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "hl7PatientUpdateTemplateURI",
                aa.getPatientUpdateTemplateURI(), bb.getPatientUpdateTemplateURI());
        LdapUtils.storeDiff(mods, "hl7ImportReportTemplateURI",
                aa.getImportReportTemplateURI(), bb.getImportReportTemplateURI());
        LdapUtils.storeDiff(mods, "hl7ScheduleProcedureTemplateURI",
                aa.getScheduleProcedureTemplateURI(),
                bb.getScheduleProcedureTemplateURI());
        LdapUtils.storeDiff(mods, "hl7LogFilePattern", aa.getHl7LogFilePattern(), bb.getHl7LogFilePattern());
        LdapUtils.storeDiff(mods, "hl7ErrorLogFilePattern", aa.getHl7ErrorLogFilePattern(), bb.getHl7ErrorLogFilePattern());
        LdapUtils.storeDiff(mods, "dicomAETitle", aa.getAETitle(), bb.getAETitle());
    }

    @Override
    public void mergeChilds(HL7Application prev, HL7Application hl7App, String appDN) throws NamingException {
        ArchiveHL7ApplicationExtension aa = prev.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        ArchiveHL7ApplicationExtension bb = hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (aa == null || bb == null)
            return;

        LdapArchiveConfiguration.mergeHL7ForwardRules(
                aa.getHL7ForwardRules(), bb.getHL7ForwardRules(), appDN, getDicomConfiguration());
        LdapArchiveConfiguration.mergeScheduledStations(
                aa.getHL7OrderScheduledStations(), bb.getHL7OrderScheduledStations(), appDN, getDicomConfiguration());
        LdapArchiveConfiguration.mergeHL7OrderSPSStatus(
                aa.getHL7OrderSPSStatuses(), bb.getHL7OrderSPSStatuses(), appDN, getDicomConfiguration());
    }

}
