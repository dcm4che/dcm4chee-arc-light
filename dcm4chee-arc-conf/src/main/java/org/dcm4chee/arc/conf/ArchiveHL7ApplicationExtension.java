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
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013-2019
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

package org.dcm4chee.arc.conf;

import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7ApplicationExtension;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveHL7ApplicationExtension extends HL7ApplicationExtension{

    private String aeTitle;
    private String patientUpdateTemplateURI;
    private String importReportTemplateURI;
    private String scheduleProcedureTemplateURI;
    private String hl7LogFilePattern;
    private String hl7ErrorLogFilePattern;
    private ScheduledProtocolCodeInOrder hl7ScheduledProtocolCodeInOrder;
    private ScheduledStationAETInOrder hl7ScheduledStationAETInOrder;
    private Boolean hl7UseNullValue;
    private HL7OrderMissingStudyIUIDPolicy hl7OrderMissingStudyIUIDPolicy;
    private HL7ImportReportMissingStudyIUIDPolicy hl7ImportReportMissingStudyIUIDPolicy;
    private String hl7DicomCharacterSet;
    private Boolean hl7VeterinaryUsePatientName;

    private final ArrayList<HL7ExportRule> hl7ExportRules = new ArrayList<>();
    private final ArrayList<HL7PrefetchRule> hl7PrefetchRules = new ArrayList<>();
    private final ArrayList<HL7ForwardRule> hl7ForwardRules = new ArrayList<>();
    private final ArrayList<HL7OrderScheduledStation> hl7OrderScheduledStations = new ArrayList<>();
    private final ArrayList<HL7StudyRetentionPolicy> hl7StudyRetentionPolicies = new ArrayList<>();
    private final EnumMap<SPSStatus,HL7OrderSPSStatus> hl7OrderSPSStatuses = new EnumMap<>(SPSStatus.class);
    private final LinkedHashSet<String> hl7NoPatientCreateMessageTypes = new LinkedHashSet<>();
    private final Map<String, String> importReportTemplateParams = new HashMap<>();

    public ArchiveDeviceExtension getArchiveDeviceExtension() {
        return hl7App.getDevice().getDeviceExtension(ArchiveDeviceExtension.class);
    }

    @Override
    public void reconfigure(HL7ApplicationExtension src) {
        ArchiveHL7ApplicationExtension arcapp = (ArchiveHL7ApplicationExtension) src;
        aeTitle = arcapp.aeTitle;
        patientUpdateTemplateURI = arcapp.patientUpdateTemplateURI;
        importReportTemplateURI = arcapp.importReportTemplateURI;
        scheduleProcedureTemplateURI = arcapp.scheduleProcedureTemplateURI;
        hl7LogFilePattern = arcapp.hl7LogFilePattern;
        hl7ErrorLogFilePattern = arcapp.hl7ErrorLogFilePattern;
        hl7ScheduledProtocolCodeInOrder = arcapp.hl7ScheduledProtocolCodeInOrder;
        hl7ScheduledStationAETInOrder = arcapp.hl7ScheduledStationAETInOrder;
        hl7UseNullValue = arcapp.hl7UseNullValue;
        hl7OrderMissingStudyIUIDPolicy = arcapp.hl7OrderMissingStudyIUIDPolicy;
        hl7ImportReportMissingStudyIUIDPolicy = arcapp.hl7ImportReportMissingStudyIUIDPolicy;
        hl7DicomCharacterSet = arcapp.hl7DicomCharacterSet;
        hl7VeterinaryUsePatientName = arcapp.hl7VeterinaryUsePatientName;
        hl7ExportRules.clear();
        hl7ExportRules.addAll(arcapp.hl7ExportRules);
        hl7PrefetchRules.clear();
        hl7PrefetchRules.addAll(arcapp.hl7PrefetchRules);
        hl7ForwardRules.clear();
        hl7ForwardRules.addAll(arcapp.hl7ForwardRules);
        hl7OrderScheduledStations.clear();
        hl7OrderScheduledStations.addAll(arcapp.hl7OrderScheduledStations);
        hl7StudyRetentionPolicies.clear();
        hl7StudyRetentionPolicies.addAll(arcapp.hl7StudyRetentionPolicies);
        hl7OrderSPSStatuses.clear();
        hl7OrderSPSStatuses.putAll(arcapp.hl7OrderSPSStatuses);
        hl7NoPatientCreateMessageTypes.clear();
        hl7NoPatientCreateMessageTypes.addAll(arcapp.hl7NoPatientCreateMessageTypes);
        importReportTemplateParams.clear();
        importReportTemplateParams.putAll(arcapp.importReportTemplateParams);
    }

    public String getAETitle() {
        return aeTitle;
    }

    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getPatientUpdateTemplateURI() {
        return patientUpdateTemplateURI;
    }

    public void setPatientUpdateTemplateURI(String patientUpdateTemplateURI) {
        this.patientUpdateTemplateURI = patientUpdateTemplateURI;
    }

    public String patientUpdateTemplateURI() {
        return patientUpdateTemplateURI != null ? patientUpdateTemplateURI
                : getArchiveDeviceExtension().getPatientUpdateTemplateURI();
    }

    public String getImportReportTemplateURI() {
        return importReportTemplateURI;
    }

    public void setImportReportTemplateURI(String importReportTemplateURI) {
        this.importReportTemplateURI = importReportTemplateURI;
    }

    public String importReportTemplateURI() {
        return importReportTemplateURI != null ? importReportTemplateURI
                : getArchiveDeviceExtension().getImportReportTemplateURI();
    }

    public String getScheduleProcedureTemplateURI() {
        return scheduleProcedureTemplateURI;
    }

    public void setScheduleProcedureTemplateURI(String scheduleProcedureTemplateURI) {
        this.scheduleProcedureTemplateURI = scheduleProcedureTemplateURI;
    }

    public String scheduleProcedureTemplateURI() {
        return scheduleProcedureTemplateURI != null ? scheduleProcedureTemplateURI
                : getArchiveDeviceExtension().getScheduleProcedureTemplateURI();
    }

    public String getHL7LogFilePattern() {
        return hl7LogFilePattern;
    }

    public void setHL7LogFilePattern(String hl7LogFilePattern) {
        this.hl7LogFilePattern = hl7LogFilePattern;
    }

    public String hl7LogFilePattern() {
        return hl7LogFilePattern != null ? hl7LogFilePattern
                : getArchiveDeviceExtension().getHL7LogFilePattern();
    }

    public String getHL7ErrorLogFilePattern() {
        return hl7ErrorLogFilePattern;
    }

    public void setHL7ErrorLogFilePattern(String hl7ErrorLogFilePattern) {
        this.hl7ErrorLogFilePattern = hl7ErrorLogFilePattern;
    }

    public String hl7ErrorLogFilePattern() {
        return hl7ErrorLogFilePattern != null ? hl7ErrorLogFilePattern
                : getArchiveDeviceExtension().getHL7ErrorLogFilePattern();
    }

    public String[] getHL7NoPatientCreateMessageTypes() {
        return hl7NoPatientCreateMessageTypes.toArray(
                new String[hl7NoPatientCreateMessageTypes.size()]);
    }

    public void setHL7NoPatientCreateMessageTypes(String... messageTypes) {
        hl7NoPatientCreateMessageTypes.clear();
        for (String messageType : messageTypes)
            hl7NoPatientCreateMessageTypes.add(messageType);
    }

    public boolean isHL7NoPatientCreateMessageType(String messageType) {
        return hl7NoPatientCreateMessageTypes.isEmpty()
            ? getArchiveDeviceExtension().isHL7NoPatientCreateMessageType(messageType)
            : hl7NoPatientCreateMessageTypes.contains(messageType);
    }

    public Boolean getHL7UseNullValue() {
        return hl7UseNullValue;
    }

    public void setHL7UseNullValue(Boolean hl7UseNullValue) {
        this.hl7UseNullValue = hl7UseNullValue;
    }

    public boolean hl7UseNullValue() {
        return hl7UseNullValue != null
                ? hl7UseNullValue
                : getArchiveDeviceExtension().isHL7UseNullValue();
    }

    public void removeHL7ExportRule(HL7ExportRule rule) {
        hl7ExportRules.remove(rule);
    }

    public void clearHL7ExportRules() {
        hl7ExportRules.clear();
    }

    public void addHL7ExportRule(HL7ExportRule rule) {
        hl7ExportRules.add(rule);
    }

    public Collection<HL7ExportRule> getHL7ExportRules() {
        return hl7ExportRules;
    }

    public Stream<HL7ExportRule> hl7ExportRules() {
        return Stream.concat(hl7ExportRules.stream(), getArchiveDeviceExtension().getHL7ExportRules().stream());
    }

    public boolean hasHL7ExportRules() {
        return !hl7ExportRules.isEmpty() || !getArchiveDeviceExtension().getHL7ExportRules().isEmpty();
    }

    public void removeHL7PrefetchRule(HL7PrefetchRule rule) {
        hl7PrefetchRules.remove(rule);
    }

    public void clearHL7PrefetchRules() {
        hl7PrefetchRules.clear();
    }

    public void addHL7PrefetchRule(HL7PrefetchRule rule) {
        hl7PrefetchRules.add(rule);
    }

    public Collection<HL7PrefetchRule> getHL7PrefetchRules() {
        return hl7PrefetchRules;
    }

    public Stream<HL7PrefetchRule> hl7PrefetchRules() {
        return Stream.concat(hl7PrefetchRules.stream(), getArchiveDeviceExtension().getHL7PrefetchRules().stream());
    }

    public boolean hasHL7PrefetchRules() {
        return !hl7PrefetchRules.isEmpty() || !getArchiveDeviceExtension().getHL7PrefetchRules().isEmpty();
    }

    public void removeHL7ForwardRule(HL7ForwardRule rule) {
        hl7ForwardRules.remove(rule);
    }

    public void clearHL7ForwardRules() {
        hl7ForwardRules.clear();
    }

    public void addHL7ForwardRule(HL7ForwardRule rule) {
        hl7ForwardRules.add(rule);
    }

    public Collection<HL7ForwardRule> getHL7ForwardRules() {
        return hl7ForwardRules;
    }

    public Stream<HL7ForwardRule> hl7ForwardRules() {
        return Stream.concat(hl7ForwardRules.stream(), getArchiveDeviceExtension().getHL7ForwardRules().stream());
    }

    public boolean hasHL7ForwardRules() {
        return !hl7ForwardRules.isEmpty() || !getArchiveDeviceExtension().getHL7ForwardRules().isEmpty();
    }

    public void removeHL7OrderScheduledStation(HL7OrderScheduledStation rule) {
        hl7OrderScheduledStations.remove(rule);
    }

    public void clearHL7OrderScheduledStations() {
        hl7OrderScheduledStations.clear();
    }

    public void addHL7OrderScheduledStation(HL7OrderScheduledStation rule) {
        hl7OrderScheduledStations.add(rule);
    }

    public Collection<HL7OrderScheduledStation> getHL7OrderScheduledStations() {
        return hl7OrderScheduledStations;
    }

    public Collection<Device> hl7OrderScheduledStation(String hostName, HL7Fields hl7Fields) {
        ArrayList<Device> scheduledStations = new ArrayList<>();
        int priority = 0;
        for (Collection<HL7OrderScheduledStation> stations
                : new Collection[]{scheduledStations, getArchiveDeviceExtension().getHL7OrderScheduledStations() })
            for (HL7OrderScheduledStation station : stations)
                if (station.match(hostName, hl7Fields))
                    if (priority <= station.getPriority()) {
                        if (priority < station.getPriority()) {
                            priority = station.getPriority();
                            scheduledStations.clear();
                        }
                        scheduledStations.add(station.getDevice());
                    }
        return scheduledStations;
    }

    public void removeHL7StudyRetentionPolicy(HL7StudyRetentionPolicy policy) {
        hl7StudyRetentionPolicies.remove(policy);
    }

    public void clearHL7StudyRetentionPolicies() {
        hl7StudyRetentionPolicies.clear();
    }

    public void addHL7StudyRetentionPolicy(HL7StudyRetentionPolicy policy) {
        hl7StudyRetentionPolicies.add(policy);
    }

    public Collection<HL7StudyRetentionPolicy> getHL7StudyRetentionPolicies() {
        return hl7StudyRetentionPolicies;
    }

    public Stream<HL7StudyRetentionPolicy> hl7StudyRetentionPolicies() {
        return Stream.concat(hl7StudyRetentionPolicies.stream(),
                getArchiveDeviceExtension().getHL7StudyRetentionPolicies().stream());
    }

    public boolean hasHL7StudyRetentionPolicies() {
        return !hl7StudyRetentionPolicies.isEmpty()
                || !getArchiveDeviceExtension().getHL7StudyRetentionPolicies().isEmpty();
    }

    public void removeHL7OrderSPSStatus(HL7OrderSPSStatus rule) {
        hl7OrderSPSStatuses.remove(rule.getSPSStatus());
    }

    public void clearHL7OrderSPSStatuses() {
        hl7OrderSPSStatuses.clear();
    }

    public void addHL7OrderSPSStatus(HL7OrderSPSStatus rule) {
        hl7OrderSPSStatuses.put(rule.getSPSStatus(), rule);
    }

    public Map<SPSStatus, HL7OrderSPSStatus> getHL7OrderSPSStatuses() {
        return hl7OrderSPSStatuses;
    }

    public Collection<HL7OrderSPSStatus> hl7OrderSPSStatuses() {
        return (hl7OrderSPSStatuses.isEmpty()
                ? getArchiveDeviceExtension().getHL7OrderSPSStatuses()
                : hl7OrderSPSStatuses).values();
    }

    public ScheduledProtocolCodeInOrder getHL7ScheduledProtocolCodeInOrder() {
        return hl7ScheduledProtocolCodeInOrder;
    }

    public void setHL7ScheduledProtocolCodeInOrder(ScheduledProtocolCodeInOrder hl7ScheduledProtocolCodeInOrder) {
        this.hl7ScheduledProtocolCodeInOrder = hl7ScheduledProtocolCodeInOrder;
    }

    public ScheduledProtocolCodeInOrder hl7ScheduledProtocolCodeInOrder() {
        return hl7ScheduledProtocolCodeInOrder != null
                ? hl7ScheduledProtocolCodeInOrder
                : getArchiveDeviceExtension().getHL7ScheduledProtocolCodeInOrder();
    }

    public ScheduledStationAETInOrder getHL7ScheduledStationAETInOrder() {
        return hl7ScheduledStationAETInOrder;
    }

    public void setHL7ScheduledStationAETInOrder(ScheduledStationAETInOrder hl7ScheduledStationAETInOrder) {
        this.hl7ScheduledStationAETInOrder = hl7ScheduledStationAETInOrder;
    }

    public ScheduledStationAETInOrder hl7ScheduledStationAETInOrder() {
        return hl7ScheduledStationAETInOrder != null
                ? hl7ScheduledStationAETInOrder
                : getArchiveDeviceExtension().getHL7ScheduledStationAETInOrder();
    }

    public Map<String, String> importReportTemplateParams() {
        return !importReportTemplateParams.isEmpty()
                ? importReportTemplateParams
                : getArchiveDeviceExtension().getImportReportTemplateParams();
    }

    public Map<String, String> getImportReportTemplateParams() {
        return importReportTemplateParams;
    }

    public void setImportReportTemplateParam(String name, String value) {
        importReportTemplateParams.put(name, value);
    }

    public void setImportReportTemplateParams(String[] ss) {
        importReportTemplateParams.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("XSLT parameter in incorrect format : " + s);
            setImportReportTemplateParam(s.substring(0, index), s.substring(index+1));
        }
    }

    public HL7OrderMissingStudyIUIDPolicy hl7OrderMissingStudyIUIDPolicy() {
        return hl7OrderMissingStudyIUIDPolicy != null
                ? hl7OrderMissingStudyIUIDPolicy
                : getArchiveDeviceExtension().getHl7OrderMissingStudyIUIDPolicy();
    }

    public HL7OrderMissingStudyIUIDPolicy getHL7OrderMissingStudyIUIDPolicy() {
        return hl7OrderMissingStudyIUIDPolicy;
    }

    public void setHL7OrderMissingStudyIUIDPolicy(HL7OrderMissingStudyIUIDPolicy hl7OrderMissingStudyIUIDPolicy) {
        this.hl7OrderMissingStudyIUIDPolicy = hl7OrderMissingStudyIUIDPolicy;
    }

    public HL7ImportReportMissingStudyIUIDPolicy hl7ImportReportMissingStudyIUIDPolicy() {
        return hl7ImportReportMissingStudyIUIDPolicy != null
                ? hl7ImportReportMissingStudyIUIDPolicy
                : getArchiveDeviceExtension().getHl7ImportReportMissingStudyIUIDPolicy();
    }

    public HL7ImportReportMissingStudyIUIDPolicy getHl7ImportReportMissingStudyIUIDPolicy() {
        return hl7ImportReportMissingStudyIUIDPolicy;
    }

    public void setHl7ImportReportMissingStudyIUIDPolicy(HL7ImportReportMissingStudyIUIDPolicy hl7ImportReportMissingStudyIUIDPolicy) {
        this.hl7ImportReportMissingStudyIUIDPolicy = hl7ImportReportMissingStudyIUIDPolicy;
    }

    public String hl7DicomCharacterSet() {
        return hl7DicomCharacterSet != null
                ? hl7DicomCharacterSet
                : getArchiveDeviceExtension().getHl7DicomCharacterSet();
    }

    public String getHl7DicomCharacterSet() {
        return hl7DicomCharacterSet;
    }

    public void setHl7DicomCharacterSet(String hl7DicomCharacterSet) {
        this.hl7DicomCharacterSet = hl7DicomCharacterSet;
    }

    public Boolean getHl7VeterinaryUsePatientName() {
        return hl7VeterinaryUsePatientName;
    }

    public void setHl7VeterinaryUsePatientName(Boolean hl7VeterinaryUsePatientName) {
        this.hl7VeterinaryUsePatientName = hl7VeterinaryUsePatientName;
    }

    public boolean hl7VeterinaryUsePatientName() {
        return hl7VeterinaryUsePatientName != null
                ? hl7VeterinaryUsePatientName
                : getArchiveDeviceExtension().isHl7VeterinaryUsePatientName();
    }
}
