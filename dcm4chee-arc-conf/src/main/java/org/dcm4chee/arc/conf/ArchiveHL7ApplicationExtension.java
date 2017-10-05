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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7ApplicationExtension;

import java.util.*;

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
    private Boolean hl7IncludeNullValues;
    private final ArrayList<HL7ForwardRule> hl7ForwardRules = new ArrayList<>();
    private final ArrayList<HL7OrderScheduledStation> hl7OrderScheduledStations = new ArrayList<>();
    private final EnumMap<SPSStatus,HL7OrderSPSStatus> hl7OrderSPSStatuses = new EnumMap<>(SPSStatus.class);
    private final LinkedHashSet<String> hl7NoPatientCreateMessageTypes = new LinkedHashSet<>();

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
        hl7IncludeNullValues = arcapp.hl7IncludeNullValues;
        hl7ForwardRules.clear();
        hl7ForwardRules.addAll(arcapp.hl7ForwardRules);
        hl7OrderScheduledStations.clear();
        hl7OrderScheduledStations.addAll(arcapp.hl7OrderScheduledStations);
        hl7OrderSPSStatuses.clear();
        hl7OrderSPSStatuses.putAll(arcapp.hl7OrderSPSStatuses);
        hl7NoPatientCreateMessageTypes.clear();
        hl7NoPatientCreateMessageTypes.addAll(arcapp.hl7NoPatientCreateMessageTypes);
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

    public String getHl7LogFilePattern() {
        return hl7LogFilePattern;
    }

    public void setHl7LogFilePattern(String hl7LogFilePattern) {
        this.hl7LogFilePattern = hl7LogFilePattern;
    }

    public String hl7LogFilePattern() {
        return hl7LogFilePattern != null ? hl7LogFilePattern
                : getArchiveDeviceExtension().getHl7LogFilePattern();
    }

    public String getHl7ErrorLogFilePattern() {
        return hl7ErrorLogFilePattern;
    }

    public void setHl7ErrorLogFilePattern(String hl7ErrorLogFilePattern) {
        this.hl7ErrorLogFilePattern = hl7ErrorLogFilePattern;
    }

    public String hl7ErrorLogFilePattern() {
        return hl7ErrorLogFilePattern != null ? hl7ErrorLogFilePattern
                : getArchiveDeviceExtension().getHl7ErrorLogFilePattern();
    }

    public String[] getHl7NoPatientCreateMessageTypes() {
        return hl7NoPatientCreateMessageTypes.toArray(
                new String[hl7NoPatientCreateMessageTypes.size()]);
    }

    public void setHl7NoPatientCreateMessageTypes(String... messageTypes) {
        hl7NoPatientCreateMessageTypes.clear();
        for (String messageType : messageTypes)
            hl7NoPatientCreateMessageTypes.add(messageType);
    }

    public boolean isHl7NoPatientCreateMessageType(String messageType) {
        return hl7NoPatientCreateMessageTypes.isEmpty()
            ? getArchiveDeviceExtension().isHl7NoPatientCreateMessageType(messageType)
            : hl7NoPatientCreateMessageTypes.contains(messageType);
    }

    public Boolean getHl7IncludeNullValues() {
        return hl7IncludeNullValues;
    }

    public void setHl7IncludeNullValues(Boolean hl7IncludeNullValues) {
        this.hl7IncludeNullValues = hl7IncludeNullValues;
    }

    public boolean hl7IncludeNullValues() {
        return hl7IncludeNullValues != null
                ? hl7IncludeNullValues
                : getArchiveDeviceExtension().isHl7IncludeNullValues();
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

    public Collection<String> forwardDestinations(String hostName, HL7Segment msh) {
        HashSet<String> dests = new HashSet<>();
        for (Collection<HL7ForwardRule> rules
                : new Collection[]{hl7ForwardRules, getArchiveDeviceExtension().getHL7ForwardRules() })
            for (HL7ForwardRule rule : rules)
                if (rule.match(hostName, msh))
                    for (String dest : rule.getDestinations()) {
                        dests.add(dest);
                    }
        return dests;
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

    public Collection<Device> hl7OrderScheduledStation(String hostName, HL7Segment msh, Attributes attrs) {
        ArrayList<Device> scheduledStations = new ArrayList<>();
        int priority = 0;
        for (Collection<HL7OrderScheduledStation> stations
                : new Collection[]{scheduledStations, getArchiveDeviceExtension().getHL7OrderScheduledStations() })
            for (HL7OrderScheduledStation station : stations)
                if (station.match(hostName, msh, attrs))
                    if (priority <= station.getPriority()) {
                        if (priority < station.getPriority()) {
                            priority = station.getPriority();
                            scheduledStations.clear();
                        }
                        scheduledStations.add(station.getDevice());
                    }
        return scheduledStations;
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

    public ScheduledProtocolCodeInOrder getHl7ScheduledProtocolCodeInOrder() {
        return hl7ScheduledProtocolCodeInOrder;
    }

    public void setHl7ScheduledProtocolCodeInOrder(ScheduledProtocolCodeInOrder hl7ScheduledProtocolCodeInOrder) {
        this.hl7ScheduledProtocolCodeInOrder = hl7ScheduledProtocolCodeInOrder;
    }

    public ScheduledProtocolCodeInOrder hl7ScheduledProtocolCodeInOrder() {
        return hl7ScheduledProtocolCodeInOrder != null
                ? hl7ScheduledProtocolCodeInOrder
                : getArchiveDeviceExtension().getHl7ScheduledProtocolCodeInOrder();
    }

    public ScheduledStationAETInOrder getHl7ScheduledStationAETInOrder() {
        return hl7ScheduledStationAETInOrder;
    }

    public void setHl7ScheduledStationAETInOrder(ScheduledStationAETInOrder hl7ScheduledStationAETInOrder) {
        this.hl7ScheduledStationAETInOrder = hl7ScheduledStationAETInOrder;
    }

    public ScheduledStationAETInOrder hl7ScheduledStationAETInOrder() {
        return hl7ScheduledStationAETInOrder != null
                ? hl7ScheduledStationAETInOrder
                : getArchiveDeviceExtension().getHl7ScheduledStationAETInOrder();
    }
}
