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

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2020
 */
public class UPSTemplate {
    private String upsTemplateID;
    private UPSPriority upsPriority = UPSPriority.MEDIUM;
    private InputReadinessState inputReadinessState = InputReadinessState.READY;
    private Duration startDateTimeDelay;
    private Duration completionDateTimeDelay;
    private String procedureStepLabel;
    private String worklistLabel;
    private String scheduledHumanPerformerName;
    private String scheduledHumanPerformerOrganization;
    private Code scheduledWorkitemCode;
    private Code scheduledStationName;
    private Code scheduledStationClass;
    private Code scheduledStationLocation;
    private Code scheduledHumanPerformer;
    private String destinationAE;
    private Entity scopeOfAccumulation;
    private boolean includeStudyInstanceUID;

    public UPSTemplate() {
    }

    public UPSTemplate(String upsTemplateID) {
        this.upsTemplateID = upsTemplateID;
    }

    public String getUPSTemplateID() {
        return upsTemplateID;
    }

    public void setUPSTemplateID(String upsTemplateID) {
        this.upsTemplateID = upsTemplateID;
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
        return procedureStepLabel;
    }

    public void setProcedureStepLabel(String procedureStepLabel) {
        this.procedureStepLabel = procedureStepLabel;
    }

    public String getWorklistLabel() {
        return worklistLabel;
    }

    public void setWorklistLabel(String worklistLabel) {
        this.worklistLabel = worklistLabel;
    }

    public String getScheduledHumanPerformerName() {
        return scheduledHumanPerformerName;
    }

    public void setScheduledHumanPerformerName(String scheduledHumanPerformerName) {
        this.scheduledHumanPerformerName = scheduledHumanPerformerName;
    }

    public String getScheduledHumanPerformerOrganization() {
        return scheduledHumanPerformerOrganization;
    }

    public void setScheduledHumanPerformerOrganization(String scheduledHumanPerformerOrganization) {
        this.scheduledHumanPerformerOrganization = scheduledHumanPerformerOrganization;
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

    public Attributes getScheduledHumanPerformerItem() {
        if (scheduledHumanPerformer == null) return null;
        Attributes item = new Attributes(3);
        item.newSequence(Tag.HumanPerformerCodeSequence, 1).add(scheduledHumanPerformer.toItem());
        item.setString(Tag.HumanPerformerOrganization, VR.LO, getScheduledHumanPerformerOrganization());
        item.setString(Tag.HumanPerformerName, VR.PN, getScheduledHumanPerformerName());
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
}
