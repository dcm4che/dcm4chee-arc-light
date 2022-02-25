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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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
 *
 */

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2020
 */
public class UPSOnHL7 {
    public static final UPSOnHL7[] EMPTY = {};
    private String upsOnHL7ID;
    private HL7Conditions conditions = new HL7Conditions();
    private ScheduleExpression[] schedules = {};
    private String procedureStepLabel;
    private String worklistLabel;
    private UPSPriority upsPriority = UPSPriority.MEDIUM;
    private InputReadinessState inputReadinessState = InputReadinessState.READY;
    private Duration startDateTimeDelay;
    private Duration completionDateTimeDelay;
    private String instanceUIDBasedOnName;
    private String destinationAE;
    private Code scheduledWorkitemCode;
    private Code[] scheduledStationNames = {};
    private Code[] scheduledStationClasses = {};
    private Code[] scheduledStationLocations = {};
    private Code[] scheduledHumanPerformers = {};
    private String scheduledHumanPerformerName;
    private String scheduledHumanPerformerOrganization;
    private String studyInstanceUID;
    private String admissionID;
    private String accessionNumber;
    private String requestedProcedureID;
    private String requestedProcedureDescription;
    private String requestingPhysician;
    private String requestingService;
    private Issuer issuerOfAdmissionID;
    private Issuer issuerOfAccessionNumber;
    private String xsltStylesheetURI;
    private boolean includeStudyInstanceUID;
    private boolean includeReferencedRequest;

    public UPSOnHL7() {}

    public UPSOnHL7(String upsOnHL7ID) {
        this.upsOnHL7ID = upsOnHL7ID;
    }

    public String getUPSOnHL7ID() {
        return upsOnHL7ID;
    }

    public void setUPSOnHL7ID(String UPSOnHL7ID) {
        this.upsOnHL7ID = UPSOnHL7ID;
    }

    public HL7Conditions getConditions() {
        return conditions;
    }

    public void setConditions(HL7Conditions conditions) {
        this.conditions = conditions;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression[] schedules) {
        this.schedules = schedules;
    }

    public String getProcedureStepLabel() {
        return procedureStepLabel;
    }

    public void setProcedureStepLabel(String procedureStepLabel) {
        this.procedureStepLabel = procedureStepLabel;
    }

    public String getProcedureStepLabel(HL7Fields hl7Fields) {
        return Objects.requireNonNull(hl7Fields.get(procedureStepLabel, null),
                "Missing value for Procedure Step Label at " + procedureStepLabel
                        + " configured in UPSOnHL7[cn=" + upsOnHL7ID + "]");
    }

    public String getWorklistLabel() {
        return worklistLabel;
    }

    public void setWorklistLabel(String worklistLabel) {
        this.worklistLabel = worklistLabel;
    }

    public String getWorklistLabel(HL7Fields hl7Fields) {
        return worklistLabel != null ? hl7Fields.get(worklistLabel, null) : null;
    }

    public UPSPriority getUPSPriority() {
        return upsPriority;
    }

    public void setUPSPriority(UPSPriority upsPriority) {
        this.upsPriority = upsPriority;
    }

    public InputReadinessState getInputReadinessState() {
        return inputReadinessState;
    }

    public void setInputReadinessState(InputReadinessState inputReadinessState) {
        this.inputReadinessState = inputReadinessState;
    }

    public Duration getStartDateTimeDelay() {
        return startDateTimeDelay;
    }

    public void setStartDateTimeDelay(Duration startDateTimeDelay) {
        this.startDateTimeDelay = startDateTimeDelay;
    }

    public Duration getCompletionDateTimeDelay() {
        return completionDateTimeDelay;
    }

    public void setCompletionDateTimeDelay(Duration completionDateTimeDelay) {
        this.completionDateTimeDelay = completionDateTimeDelay;
    }

    public String getInstanceUIDBasedOnName() {
        return instanceUIDBasedOnName;
    }

    public void setInstanceUIDBasedOnName(String instanceUIDBasedOnName) {
        this.instanceUIDBasedOnName = instanceUIDBasedOnName;
    }

    public String getInstanceUID(HL7Fields hl7Fields) {
        return instanceUIDBasedOnName != null
                ? UIDUtils.createNameBasedUID(hl7Fields.get(instanceUIDBasedOnName, "*").getBytes(StandardCharsets.UTF_8))
                : UIDUtils.createUID();
    }

    public String getDestinationAE() {
        return destinationAE;
    }

    public void setDestinationAE(String destinationAE) {
        this.destinationAE = destinationAE;
    }

    public Code getScheduledWorkitemCode() {
        return scheduledWorkitemCode;
    }

    public void setScheduledWorkitemCode(Code scheduledWorkitemCode) {
        this.scheduledWorkitemCode = scheduledWorkitemCode;
    }

    public Code[] getScheduledStationNames() {
        return scheduledStationNames;
    }

    public void setScheduledStationNames(Code... scheduledStationNames) {
        this.scheduledStationNames = scheduledStationNames;
    }

    public Code[] getScheduledStationClasses() {
        return scheduledStationClasses;
    }

    public void setScheduledStationClasses(Code... scheduledStationClasses) {
        this.scheduledStationClasses = scheduledStationClasses;
    }

    public Code[] getScheduledStationLocations() {
        return scheduledStationLocations;
    }

    public void setScheduledStationLocations(Code... scheduledStationLocations) {
        this.scheduledStationLocations = scheduledStationLocations;
    }

    public Code[] getScheduledHumanPerformers() {
        return scheduledHumanPerformers;
    }

    public void setScheduledHumanPerformers(Code[] scheduledHumanPerformers) {
        this.scheduledHumanPerformers = scheduledHumanPerformers;
    }

    public String getScheduledHumanPerformerName() {
        return scheduledHumanPerformerName;
    }

    public void setScheduledHumanPerformerName(String scheduledHumanPerformerName) {
        this.scheduledHumanPerformerName = scheduledHumanPerformerName;
    }

    public String getScheduledHumanPerformerName(HL7Fields hl7Fields) {
        return scheduledHumanPerformerName != null ? hl7Fields.get(scheduledHumanPerformerName, null) : null;
    }

    public String getScheduledHumanPerformerOrganization() {
        return scheduledHumanPerformerOrganization;
    }

    public void setScheduledHumanPerformerOrganization(String scheduledHumanPerformerOrganization) {
        this.scheduledHumanPerformerOrganization = scheduledHumanPerformerOrganization;
    }

    public String getScheduledHumanPerformerOrganization(HL7Fields hl7Fields) {
        return scheduledHumanPerformerOrganization != null
                ? hl7Fields.get(scheduledHumanPerformerOrganization, null) : null;
    }

    public String getRequestingService() {
        return requestingService;
    }

    public void setRequestingService(String requestingService) {
        this.requestingService = requestingService;
    }

    public String getRequestingService(HL7Fields hl7Fields) {
        return requestingService != null ? hl7Fields.get(requestingService, null) : null;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public void setStudyInstanceUID(String studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    public String getStudyInstanceUID(HL7Fields hl7Fields) {
        return studyInstanceUID != null ? hl7Fields.get(studyInstanceUID, null) : null;
    }

    public String getAdmissionID() {
        return admissionID;
    }

    public void setAdmissionID(String admissionID) {
        this.admissionID = admissionID;
    }

    public String getAdmissionID(HL7Fields hl7Fields) {
        return admissionID != null ? hl7Fields.get(admissionID, null) : null;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getAccessionNumber(HL7Fields hl7Fields) {
        return accessionNumber != null ? hl7Fields.get(accessionNumber, null) : null;
    }

    public String getRequestedProcedureID() {
        return requestedProcedureID;
    }

    public void setRequestedProcedureID(String requestedProcedureID) {
        this.requestedProcedureID = requestedProcedureID;
    }

    public String getRequestedProcedureID(HL7Fields hl7Fields) {
        return requestedProcedureID != null ? hl7Fields.get(requestedProcedureID, null) : null;
    }

    public String getRequestedProcedureDescription() {
        return requestedProcedureDescription;
    }

    public void setRequestedProcedureDescription(String requestedProcedureDescription) {
        this.requestedProcedureDescription = requestedProcedureDescription;
    }

    public String getRequestedProcedureDescription(HL7Fields hl7Fields) {
        return requestedProcedureDescription != null ? hl7Fields.get(requestedProcedureDescription, null) : null;
    }

    public String getRequestingPhysician() {
        return requestingPhysician;
    }

    public void setRequestingPhysician(String requestingPhysician) {
        this.requestingPhysician = requestingPhysician;
    }

    public String getRequestingPhysician(HL7Fields hl7Fields) {
        return requestingPhysician != null ? hl7Fields.get(requestingPhysician, null) : null;
    }

    public Issuer getIssuerOfAdmissionID() {
        return issuerOfAdmissionID;
    }

    public void setIssuerOfAdmissionID(Issuer issuerOfAdmissionID) {
        this.issuerOfAdmissionID = issuerOfAdmissionID;
    }

    public Issuer getIssuerOfAccessionNumber() {
        return issuerOfAccessionNumber;
    }

    public void setIssuerOfAccessionNumber(Issuer issuerOfAccessionNumber) {
        this.issuerOfAccessionNumber = issuerOfAccessionNumber;
    }

    public boolean isIncludeStudyInstanceUID() {
        return includeStudyInstanceUID;
    }

    public void setIncludeStudyInstanceUID(boolean includeStudyInstanceUID) {
        this.includeStudyInstanceUID = includeStudyInstanceUID;
    }

    public boolean isIncludeReferencedRequest() {
        return includeReferencedRequest;
    }

    public void setIncludeReferencedRequest(boolean includeReferencedRequest) {
        this.includeReferencedRequest = includeReferencedRequest;
    }

    public String getXSLTStylesheetURI() {
        return xsltStylesheetURI;
    }

    public void setXSLTStylesheetURI(String xsltStylesheetURI) {
        this.xsltStylesheetURI = xsltStylesheetURI;
    }

    @Override
    public String toString() {
        return "UPSOnHL7{" +
                "commonName='" + upsOnHL7ID + '\'' +
                '}';
    }
}
