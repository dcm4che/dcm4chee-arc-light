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

package org.dcm4chee.arc.ups.process;

import org.dcm4che3.data.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.UPSProcessingRule;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;

import java.util.Date;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Apr 2020
 */
public abstract class AbstractUPSProcessor implements UPSProcessor {
    protected final UPSProcessingRule rule;
    protected final UPSService upsService;

    public AbstractUPSProcessor(UPSProcessingRule rule, UPSService upsService) {
        this.rule = rule;
        this.upsService = upsService;
    }

    @Override
    public UPSProcessingRule getUPSProcessingRule() {
        return rule;
    }

    @Override
    public void process(ArchiveAEExtension arcAE, Attributes ups) {
        UPSContext upsCtx = upsService.newUPSContext(null, arcAE);
        upsCtx.setUPSInstanceUID(ups.getString(Tag.SOPInstanceUID));
        upsCtx.setRequesterAET(arcAE.getApplicationEntity().getAETitle());
        Attributes transaction =  new Attributes(2);
        transaction.setString(Tag.TransactionUID, VR.UI, UIDUtils.createUID());
        transaction.setString(Tag.ProcedureStepState, VR.CS, "IN PROGRESS");
        upsCtx.setAttributes(transaction);
        initPerformedProcedure(upsCtx, ups);
        try {
            upsService.changeUPSState(upsCtx);
        } catch (DicomServiceException e) {
            //TODO
            e.printStackTrace();
            return;
        }
        try {
            processA(ups);
            transaction.setString(Tag.ProcedureStepState, VR.CS, "COMPLETED");
        } catch (Exception e) {
            transaction.setString(Tag.ProcedureStepState, VR.CS, "CANCELED");
        }
        setPerformedProcedureStepEndDateTime(upsCtx);
        try {
            upsService.changeUPSState(upsCtx);
        } catch (DicomServiceException e) {
            //TODO
            e.printStackTrace();
        }
    }

    protected void initPerformedProcedure(UPSContext upsCtx, Attributes ups) {
        Attributes mergeAttributes = new Attributes(1);
        Sequence sq = mergeAttributes.newSequence(Tag.UnifiedProcedureStepPerformedProcedureSequence, 1);
        Attributes performedProcedure = new Attributes();
        performedProcedure.newSequence(Tag.PerformedWorkitemCodeSequence, 1)
                .add(rule.getPerformedWorkitemCode().toItem());
        performedProcedure.newSequence(Tag.PerformedStationNameCodeSequence, 1)
                .add(rule.getPerformedStationNameCode().toItem());
        performedProcedure.newSequence(Tag.OutputInformationSequence, 10);
        performedProcedure.setDate(Tag.PerformedProcedureStepStartDateTime, VR.DT, new Date());
        sq.add(performedProcedure);
        upsCtx.setMergeAttributes(mergeAttributes);
    }

    private void setPerformedProcedureStepEndDateTime(UPSContext upsCtx) {
        Attributes performedProcedure = upsCtx.getMergeAttributes()
                .getNestedDataset(Tag.UnifiedProcedureStepPerformedProcedureSequence);
        performedProcedure.setDate(Tag.PerformedProcedureStepEndDateTime, VR.DT, new Date());
    }

    protected abstract void processA(Attributes ups) throws Exception;
}
