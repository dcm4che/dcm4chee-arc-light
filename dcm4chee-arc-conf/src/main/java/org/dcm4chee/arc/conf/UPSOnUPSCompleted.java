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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.StringUtils;

import java.util.Calendar;
import java.util.Objects;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public class UPSOnUPSCompleted {
    public static final Code DEFAULT_PERFORMED_WORKITEM_CODE = new Code(
            "NO_WORKITEM_CODE",
            "99DCM4CHEE",
            null,
            "No Workitem Code specified");

    public static final UPSOnUPSCompleted[] EMPTY = {};
    private String upsOnUPSCompletedID;
    private Code performedWorkitemCode = DEFAULT_PERFORMED_WORKITEM_CODE;
    private UPSPriority upsPriority = UPSPriority.MEDIUM;
    private InputReadinessState inputReadinessState = InputReadinessState.READY;
    private Duration startDateTimeDelay;
    private Duration completionDateTimeDelay;
    private AttributesFormat procedureStepLabel;
    private AttributesFormat worklistLabel;
    private AttributesFormat scheduledHumanPerformerName;
    private AttributesFormat scheduledHumanPerformerOrganization;
    private AttributesFormat admissionID;
    private AttributesFormat accessionNumber;
    private AttributesFormat requestedProcedureID;
    private AttributesFormat requestedProcedureDescription;
    private AttributesFormat requestingPhysician;
    private AttributesFormat requestingService;
    private Issuer issuerOfAdmissionID;
    private Issuer issuerOfAccessionNumber;
    private Code scheduledWorkitemCode;
    private Code scheduledStationName;
    private Code scheduledStationClass;
    private Code scheduledStationLocation;
    private Code scheduledHumanPerformer;
    private String destinationAE;
    private Entity scopeOfAccumulation;
    private String xsltStylesheetURI;
    private boolean noKeywords;
    private boolean includeStudyInstanceUID;
    private boolean includeReferencedRequest;

    public UPSOnUPSCompleted() {}

    public UPSOnUPSCompleted(String upsOnUPSCompletedID) {
        setUPSonUPSCompletedID(upsOnUPSCompletedID);
    }

    public String getUPSonUPSCompletedID() {
        return upsOnUPSCompletedID;
    }

    public void setUPSonUPSCompletedID(String UPSonUPSCompletedID) {
        this.upsOnUPSCompletedID = UPSonUPSCompletedID;
    }

    public Code getPerformedWorkitemCode() {
        return performedWorkitemCode;
    }

    public void setPerformedWorkitemCode(Code performedWorkitemCode) {
        this.performedWorkitemCode = Objects.requireNonNull(performedWorkitemCode);
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

    public String getProcedureStepLabel() {
        return Objects.toString(procedureStepLabel, null);
    }

    public void setProcedureStepLabel(String procedureStepLabel) {
        this.procedureStepLabel = AttributesFormat.valueOf(procedureStepLabel);
    }

    public String getProcedureStepLabel(Attributes attrs) {
        return format(procedureStepLabel, attrs);
    }

    public String getWorklistLabel() {
        return Objects.toString(worklistLabel, null);
    }

    public void setWorklistLabel(String worklistLabel) {
        this.worklistLabel = AttributesFormat.valueOf(worklistLabel);
    }

    public String getWorklistLabel(Attributes attrs) {
        return format(worklistLabel, attrs);
    }

    public String getScheduledHumanPerformerName() {
        return Objects.toString(scheduledHumanPerformerName, null);
    }

    public void setScheduledHumanPerformerName(String scheduledHumanPerformerName) {
        this.scheduledHumanPerformerName = AttributesFormat.valueOf(scheduledHumanPerformerName);
    }

    public String getScheduledHumanPerformerName(Attributes attrs) {
        return format(scheduledHumanPerformerName, attrs);
    }

    public String getScheduledHumanPerformerOrganization() {
        return Objects.toString(scheduledHumanPerformerOrganization, null);
    }

    public void setScheduledHumanPerformerOrganization(String scheduledHumanPerformerOrganization) {
        this.scheduledHumanPerformerOrganization = AttributesFormat.valueOf(scheduledHumanPerformerOrganization);
    }

    public String getScheduledHumanPerformerOrganization(Attributes attrs) {
        return format(scheduledHumanPerformerOrganization, attrs);
    }

    public String getAdmissionID() {
        return Objects.toString(admissionID, null);
    }

    public void setAdmissionID(String admissionID) {
        this.admissionID = AttributesFormat.valueOf(admissionID);
    }

    public String getAdmissionID(Attributes attrs) {
        return format(admissionID, attrs);
    }

    public String getAccessionNumber() {
        return Objects.toString(accessionNumber, null);
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = AttributesFormat.valueOf(accessionNumber);
    }

    public String getAccessionNumber(Attributes attrs) {
        return format(accessionNumber, attrs);
    }

    public String getRequestedProcedureID() {
        return Objects.toString(requestedProcedureID, null);
    }

    public void setRequestedProcedureID(String requestedProcedureID) {
        this.requestedProcedureID = AttributesFormat.valueOf(requestedProcedureID);
    }

    public String getRequestedProcedureID(Attributes attrs) {
        return format(requestedProcedureID, attrs);
    }

    public String getRequestedProcedureDescription() {
        return Objects.toString(requestedProcedureDescription, null);
    }

    public void setRequestedProcedureDescription(String requestedProcedureDescription) {
        this.requestedProcedureDescription = AttributesFormat.valueOf(requestedProcedureDescription);
    }

    public String getRequestedProcedureDescription(Attributes attrs) {
        return format(requestedProcedureDescription, attrs);
    }

    public String getRequestingPhysician() {
        return Objects.toString(requestingPhysician, null);
    }

    public void setRequestingPhysician(String requestingPhysician) {
        this.requestingPhysician = AttributesFormat.valueOf(requestingPhysician);
    }

    public String getRequestingPhysician(Attributes attrs) {
        return format(requestingPhysician, attrs);
    }

    public String getRequestingService() {
        return Objects.toString(requestingService, null);
    }

    public void setRequestingService(String requestingService) {
        this.requestingService = AttributesFormat.valueOf(requestingService);
    }

    public String getRequestingService(Attributes attrs) {
        return format(requestingService, attrs);
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

    public Code getScheduledWorkitemCode() {
        return scheduledWorkitemCode;
    }

    public void setScheduledWorkitemCode(Code scheduledWorkitemCode) {
        this.scheduledWorkitemCode = scheduledWorkitemCode;
    }

    public Code getScheduledStationName() {
        return scheduledStationName;
    }

    public void setScheduledStationName(Code scheduledStationName) {
        this.scheduledStationName = scheduledStationName;
    }

    public Code getScheduledStationClass() {
        return scheduledStationClass;
    }

    public void setScheduledStationClass(Code scheduledStationClass) {
        this.scheduledStationClass = scheduledStationClass;
    }

    public Code getScheduledStationLocation() {
        return scheduledStationLocation;
    }

    public void setScheduledStationLocation(Code scheduledStationLocation) {
        this.scheduledStationLocation = scheduledStationLocation;
    }

    public Code getScheduledHumanPerformer() {
        return scheduledHumanPerformer;
    }

    public void setScheduledHumanPerformer(Code scheduledHumanPerformer) {
        this.scheduledHumanPerformer = scheduledHumanPerformer;
    }

    public Attributes getScheduledHumanPerformerItem(Attributes attrs) {
        if (scheduledHumanPerformer == null) return null;
        Attributes item = new Attributes(3);
        item.newSequence(Tag.HumanPerformerCodeSequence, 1).add(scheduledHumanPerformer.toItem());
        item.setString(Tag.HumanPerformerOrganization, VR.LO, getScheduledHumanPerformerOrganization(attrs));
        item.setString(Tag.HumanPerformerName, VR.PN, getScheduledHumanPerformerName(attrs));
        return item;
    }

    public String getDestinationAE() {
        return destinationAE;
    }

    public void setDestinationAE(String destinationAE) {
        this.destinationAE = destinationAE;
    }

    public Entity getScopeOfAccumulation() {
        return scopeOfAccumulation;
    }

    public void setScopeOfAccumulation(Entity scopeOfAccumulation) {
        this.scopeOfAccumulation = scopeOfAccumulation;
    }

    public String getXSLTStylesheetURI() {
        return xsltStylesheetURI;
    }

    public void setXSLTStylesheetURI(String xsltStylesheetURI) {
        this.xsltStylesheetURI = xsltStylesheetURI;
    }

    public boolean isNoKeywords() {
        return noKeywords;
    }

    public void setNoKeywords(boolean noKeywords) {
        this.noKeywords = noKeywords;
    }

    @Override
    public String toString() {
        return "UPSOnUPSCompleted{" +
                "cn='" + upsOnUPSCompletedID + '\'' +
                '}';
    }

    private static String format(AttributesFormat format, Attributes attrs) {
        return format != null ? StringUtils.nullify(format.format(attrs), "null") : null;
    }

}
