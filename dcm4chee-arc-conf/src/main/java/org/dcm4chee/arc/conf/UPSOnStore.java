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

import org.dcm4che3.data.Code;
import org.dcm4che3.util.AttributesFormat;

import java.util.Objects;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public class UPSOnStore {
    public enum IncludeInputInformation {
        NO, SINGLE, APPEND, SINGLE_OR_CREATE, APPEND_OR_CREATE;
    }
    private String commonName;
    private ScheduleExpression[] schedules = {};
    private Conditions conditions = new Conditions();
    private UPSPriority upsPriority = UPSPriority.MEDIUM;
    private InputReadinessState inputReadinessState = InputReadinessState.READY;
    private IncludeInputInformation includeInputInformation = IncludeInputInformation.APPEND;
    private Duration scheduleDelay;
    private AttributesFormat procedureStepLabel;
    private AttributesFormat worklistLabel;
    private AttributesFormat instanceUIDBasedOnName;
    private AttributesFormat scheduledHumanPerformerName;
    private AttributesFormat scheduledHumanPerformerOrganization;
    private Code scheduledWorkitemCode;
    private Code scheduledStationName;
    private Code scheduledStationClass;
    private Code scheduledStationLocation;
    private Code scheduledHumanPerformer;
    private String xsltStylesheetURI;
    private boolean noKeywords;
    private boolean includeStudyInstanceUID;

    public UPSOnStore() {}

    public UPSOnStore(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression[] schedules) {
        this.schedules = schedules;
    }

    public Conditions getConditions() {
        return conditions;
    }

    public void setConditions(Conditions conditions) {
        this.conditions = conditions;
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

    public IncludeInputInformation getIncludeInputInformation() {
        return includeInputInformation;
    }

    public void setIncludeInputInformation(IncludeInputInformation includeInputInformation) {
        this.includeInputInformation = includeInputInformation;
    }

    public boolean isIncludeStudyInstanceUID() {
        return includeStudyInstanceUID;
    }

    public void setIncludeStudyInstanceUID(boolean includeStudyInstanceUID) {
        this.includeStudyInstanceUID = includeStudyInstanceUID;
    }

    public Duration getScheduleDelay() {
        return scheduleDelay;
    }

    public void setScheduleDelay(Duration scheduleDelay) {
        this.scheduleDelay = scheduleDelay;
    }

    public String getProcedureStepLabel() {
        return Objects.toString(procedureStepLabel, null);
    }

    public void setProcedureStepLabel(String procedureStepLabel) {
        this.procedureStepLabel = AttributesFormat.valueOf(procedureStepLabel);
    }

    public String getWorklistLabel() {
        return Objects.toString(worklistLabel, null);
    }

    public void setWorklistLabel(String worklistLabel) {
        this.worklistLabel = AttributesFormat.valueOf(worklistLabel);
    }

    public String getInstanceUIDBasedOnName() {
        return Objects.toString(instanceUIDBasedOnName, null);
    }

    public void setInstanceUIDBasedOnName(String instanceUIDBasedOnName) {
        this.instanceUIDBasedOnName = AttributesFormat.valueOf(instanceUIDBasedOnName);
    }

    public String getScheduledHumanPerformerName() {
        return Objects.toString(scheduledHumanPerformerName, null);
    }

    public void setScheduledHumanPerformerName(String scheduledHumanPerformerName) {
        this.scheduledHumanPerformerName = AttributesFormat.valueOf(scheduledHumanPerformerName);
    }

    public String getScheduledHumanPerformerOrganization() {
        return Objects.toString(scheduledHumanPerformerOrganization, null);
    }

    public void setScheduledHumanPerformerOrganization(String scheduledHumanPerformerOrganization) {
        this.scheduledHumanPerformerOrganization = AttributesFormat.valueOf(scheduledHumanPerformerOrganization);
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
}
