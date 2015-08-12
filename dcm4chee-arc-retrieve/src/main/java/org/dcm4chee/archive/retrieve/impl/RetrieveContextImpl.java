/*
 * *** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.archive.retrieve.impl;

import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.retrieve.InstanceLocations;
import org.dcm4chee.archive.retrieve.RetrieveContext;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public class RetrieveContextImpl implements RetrieveContext {
    private final ApplicationEntity ae;
    private IDWithIssuer[] patientIDs;
    private String[] studyInstanceUIDs;
    private String[] seriesInstanceUIDs;
    private String[] sopInstanceUIDs;
    private Collection<InstanceLocations> instances;
    private final ArrayList<InstanceLocations> completed = new ArrayList<>();
    private final ArrayList<InstanceLocations> failed = new ArrayList<>();
    private final ArrayList<InstanceLocations> warning = new ArrayList<>();
    private int priority;
    private int moveOriginatorMessageID;
    private String moveOriginatorAETitle;
    private Association as;


    public RetrieveContextImpl(ApplicationEntity ae) {
        this.ae = ae;
    }

    @Override
    public ApplicationEntity getLocalApplicationEntity() {
        return ae;
    }

    @Override
    public ArchiveAEExtension getArchiveAEExtension() {
        return ae.getAEExtension(ArchiveAEExtension.class);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public int getMoveOriginatorMessageID() {
        return moveOriginatorMessageID;
    }

    @Override
    public void setMoveOriginatorMessageID(int moveOriginatorMessageID) {
        this.moveOriginatorMessageID = moveOriginatorMessageID;
    }

    @Override
    public String getMoveOriginatorAETitle() {
        return moveOriginatorAETitle;
    }

    @Override
    public void setMoveOriginatorAETitle(String moveOriginatorAETitle) {
        this.moveOriginatorAETitle = moveOriginatorAETitle;
    }

    @Override
    public IDWithIssuer[] getPatientIDs() {
        return patientIDs;
    }

    @Override
    public void setPatientIDs(IDWithIssuer... patientIDs) {
        this.patientIDs = patientIDs;
    }

    @Override
    public String[] getStudyInstanceUIDs() {
        return studyInstanceUIDs;
    }

    @Override
    public void setStudyInstanceUIDs(String[] studyInstanceUIDs) {
        this.studyInstanceUIDs = studyInstanceUIDs;
    }

    @Override
    public String[] getSeriesInstanceUIDs() {
        return seriesInstanceUIDs;
    }

    @Override
    public void setSeriesInstanceUIDs(String[] seriesInstanceUIDs) {
        this.seriesInstanceUIDs = seriesInstanceUIDs;
    }

    @Override
    public String[] getSopInstanceUIDs() {
        return sopInstanceUIDs;
    }

    @Override
    public void setSopInstanceUIDs(String[] sopInstanceUIDs) {
        this.sopInstanceUIDs = sopInstanceUIDs;
    }

    @Override
    public void setStoreAssociation(Association as) {
        this.as = as;
    }

    @Override
    public Collection<InstanceLocations> getInstances() {
        return instances;
    }

    void setInstances(Collection<InstanceLocations> instances) {
        this.instances = instances;
    }

    public void addCompleted(InstanceLocations inst) {
        completed.add(inst);
    }

    public void addWarning(InstanceLocations inst) {
        warning.add(inst);
    }

    public void addFailed(InstanceLocations inst) {
        failed.add(inst);
    }

    public int getNumberOfRemainingSubOperations() {
        return instances != null ? instances.size() - completed.size() - warning.size() - failed.size() : -1;
    }

    public int getNumberOfCompletedSubOperations() {
        return completed.size();
    }

    public int getNumberOfWarningSubOperations() {
        return warning.size();
    }

    public int getNumberOfFailedSubOperations() {
        return failed.size();
    }

    public String[] getFailedSOPInstanceUIDs() {
        if (failed.isEmpty())
            return StringUtils.EMPTY_STRING;

        String[] uids = new String[failed.size()];
        for (int i = 0; i < uids.length; i++)
            uids[i] = failed.get(i).getSopInstanceUID();
        return uids;
    }
}
