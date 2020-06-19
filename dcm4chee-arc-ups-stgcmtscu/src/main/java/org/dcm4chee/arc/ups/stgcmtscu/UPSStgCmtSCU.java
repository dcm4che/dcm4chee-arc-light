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

package org.dcm4chee.arc.ups.stgcmtscu;

import org.dcm4che3.data.*;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.UPSProcessingRule;
import org.dcm4chee.arc.stgcmt.StgCmtSCU;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.ups.process.AbstractUPSProcessor;

import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2020
 */
public class UPSStgCmtSCU extends AbstractUPSProcessor {

    private final StgCmtSCU stgCmtSCU;
    private final String defDestinationAE;

    public UPSStgCmtSCU(UPSProcessingRule rule, UPSService upsService, StgCmtSCU stgCmtSCU) {
        super(rule, upsService, true);
        this.stgCmtSCU = stgCmtSCU;
        this.defDestinationAE = rule.getUPSProcessorURI().getSchemeSpecificPart();
    }

    @Override
    protected void processA(UPSContext upsCtx, Attributes ups) throws Exception {
        String destinationAE = destinationAEOf(ups);
        for (Map.Entry<String, StgCmtInfo> entry : seriesStgCmtInfoFrom(ups).entrySet()) {
            String seriesIUID = entry.getKey();
            StgCmtInfo stgCmtInfo = entry.getValue();
            DimseRSP dimseRSP = stgCmtSCU.sendNActionRQ(
                    rule.getAETitle(),
                    destinationAE,
                    stgCmtInfo.getStudyIUID(),
                    seriesIUID,
                    null,
                    null, null,
                    ups.getString(Tag.ProcedureStepLabel),
                    stgCmtInfo.getActionInfo());
            Attributes cmd = dimseRSP.getCommand();
            int status = cmd.getInt(Tag.Status, -1);
            getPerformedProcedureStep(upsCtx)
                    .setString(Tag.PerformedProcedureStepDescription, VR.LO,
                            outcomeDesc(status, cmd.getString(Tag.ErrorComment), destinationAE));
        }
    }

    private Map<String, StgCmtInfo> seriesStgCmtInfoFrom(Attributes ups) {
        Map<String, StgCmtInfo> seriesStgCmtInfo = new HashMap<>();
        ups.getSequence(Tag.InputInformationSequence).forEach(inputInformation -> {
            Attributes actionInfo = new Attributes(inputInformation);
            actionInfo.remove(Tag.StudyInstanceUID);
            actionInfo.remove(Tag.SeriesInstanceUID);
            actionInfo.setString(Tag.TransactionUID, VR.UI, UIDUtils.createUID());
            seriesStgCmtInfo.putIfAbsent(
                    inputInformation.getString(Tag.SeriesInstanceUID),
                    new StgCmtInfo(inputInformation.getString(Tag.StudyInstanceUID), actionInfo));
        });
        return seriesStgCmtInfo;
    }

    private String destinationAEOf(Attributes ups) {
        Attributes outputDestination, dicomStorage;
        return (outputDestination = ups.getNestedDataset(Tag.OutputDestinationSequence)) != null
                && (dicomStorage = outputDestination.getNestedDataset(Tag.DICOMStorageSequence)) != null
                ? dicomStorage.getString(Tag.DestinationAE, defDestinationAE)
                : defDestinationAE;
    }

    private String outcomeDesc(int status, String errorComment, String destinationAE) {
        return status != Status.Success
                ? "Request Storage Commitment from AE: " + destinationAE
                    + " failed with status: " + TagUtils.shortToHexString(status)
                    + "H, error comment: " + errorComment
                : "Request Storage Commitment from AE: " + destinationAE;
    }

    static class StgCmtInfo {
        private final String studyIUID;
        private final Attributes actionInfo;

        public StgCmtInfo(String studyIUID, Attributes actionInfo) {
            this.studyIUID = studyIUID;
            this.actionInfo = actionInfo;
        }

        public String getStudyIUID() {
            return studyIUID;
        }

        public Attributes getActionInfo() {
            return actionInfo;
        }
    }
}
