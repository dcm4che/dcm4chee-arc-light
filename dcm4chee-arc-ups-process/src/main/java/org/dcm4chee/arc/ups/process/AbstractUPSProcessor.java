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
import org.dcm4che3.dcmr.ProcedureDiscontinuationReasons;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.UPSProcessingRule;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Apr 2020
 */
public abstract class AbstractUPSProcessor implements UPSProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractUPSProcessor.class);
    private static final int[] EXCLUDE_FROM_REPLACEMENT = {
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.ScheduledProcedureStepModificationDateTime,
            Tag.ProcedureStepProgressInformationSequence,
            Tag.UnifiedProcedureStepPerformedProcedureSequence
    };
    public static final Code BAD_UPS = new Code(
            "BAD_UPS",
            "99DCM4CHEE",
            null,
            "Unified Procedure Step does not meet processing requirements");
    public static final Code NOOP_UPS = new Code(
            "NOP_UPS",
            "99DCM4CHEE",
            null,
            "Processing Unified Procedure Step with no operation");
    public static final Code FAILED_UPS = new Code(
            "FAILED_UPS",
            "99DCM4CHEE",
            null,
            "Failure on processing Unified Procedure Step");
    protected final UPSProcessingRule rule;
    protected final UPSService upsService;
    protected final boolean inputInformationRequired;


    public AbstractUPSProcessor(UPSProcessingRule rule, UPSService upsService, boolean inputInformationRequired) {
        this.rule = rule;
        this.upsService = upsService;
        this.inputInformationRequired = inputInformationRequired;
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
        Attributes performedProcedure = initPerformedProcedure(upsCtx, ups);
        try {
            upsService.changeUPSState(upsCtx);
        } catch (DicomServiceException e) {
            switch(e.getStatus()) {
                case Status.UPSAlreadyInProgress:
                    LOG.info("UPS[uid={}] already IN PROCESS", upsCtx.getUPSInstanceUID());
                    break;
                case Status.UPSMayNoLongerBeUpdated:
                    LOG.info("UPS[uid={}] may no longer be updated", upsCtx.getUPSInstanceUID());
                    break;
                default:
                    LOG.warn("Failed to change status of UPS[uid={}] to IN PROCESS:\n", upsCtx.getUPSInstanceUID(), e);
            }
            return;
        }
        Attributes replacement = null;
        try {
            verify(ups);
            try {
                processA(upsCtx, ups);
            } catch (DicomServiceException e) {
                throw new UPSProcessorException(reasonCodeOf(e.getStatus()), e);
            } catch (Exception e) {
                throw new UPSProcessorException(FAILED_UPS, e);
            }
            transaction.setString(Tag.ProcedureStepState, VR.CS, "COMPLETED");
            performedProcedure.setDate(Tag.PerformedProcedureStepEndDateTime, VR.DT, new Date());
        } catch (UPSProcessorException e) {
            if (rule.isIgnoreDiscontinuationReasonCodes(e.reasonCode)) {
                transaction.setString(Tag.ProcedureStepState, VR.CS, "COMPLETED");
                performedProcedure.setDate(Tag.PerformedProcedureStepEndDateTime, VR.DT, new Date());
                if (!performedProcedure.containsValue(Tag.PerformedProcedureStepDescription)) {
                    performedProcedure.setString(Tag.PerformedProcedureStepDescription, VR.LO, e.getMessage());
                }
            } else {
                transaction.setString(Tag.ProcedureStepState, VR.CS, "CANCELED");
                long delay;
                if (BAD_UPS.equalsIgnoreMeaning(e.reasonCode)
                        || !rule.isRescheduleDiscontinuationReasonCodes(e.reasonCode)
                        || (delay = retryDelay(ups)) < 0) {
                    LOG.warn("Failed to process UPS[uid={}]:\n", ups.getString(Tag.SOPInstanceUID), e);
                    setReasonForCancellation(upsCtx, e.getMessage(), e.reasonCode);
                } else {
                    LOG.info("Failed to process UPS[uid={}] - retry:\n", ups.getString(Tag.SOPInstanceUID), e);
                    setReasonForCancellation(upsCtx, e.getMessage(),
                            ProcedureDiscontinuationReasons.DiscontinuedProcedureStepRescheduled);
                    replacement = new Attributes(ups.size());
                    replacement.addNotSelected(ups, EXCLUDE_FROM_REPLACEMENT);
                    replacement.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT,
                            new Date(System.currentTimeMillis() + delay * 1000));
                    replacement.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
                    replacement.newSequence(Tag.ReplacedProcedureStepSequence, 1)
                            .add(refSOP(ups.getString(Tag.SOPClassUID), ups.getString(Tag.SOPInstanceUID)));
                    countFailed(replacement);
                 }
            }
        }
        try {
            upsService.changeUPSState(upsCtx);
        } catch (DicomServiceException e) {
            LOG.warn("Failed to change status of UPS[uid={}] to {}:\n",
                    upsCtx.getUPSInstanceUID(),
                    transaction.getString(Tag.ProcedureStepState),
                    e);
        }
        if (replacement != null) {
            try {
                upsCtx.setUPSInstanceUID(UIDUtils.createUID());
                upsCtx.setAttributes(replacement);
                upsService.createUPS(upsCtx);
            } catch (DicomServiceException e) {
                LOG.warn("Failed to schedule replacement of UPS[uid={}]\n", ups.getString(Tag.SOPInstanceUID), e);
            }
        }
    }

    private void countFailed(Attributes replacement) {
        Attributes spp = replacement.getNestedDataset(Tag.ScheduledProcessingParametersSequence);
        if (spp == null)
            replacement.newSequence(Tag.ScheduledProcessingParametersSequence, 1).add(scheduledProcessingParameters());
        else
            spp.setString(Tag.NumericValue, VR.DS, String.valueOf(numericValue(spp) + 1));
    }

    private Attributes scheduledProcessingParameters() {
        Attributes item = new Attributes();
        item.setString(Tag.ValueType, VR.CS, "NUMERIC");
        item.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(codeItem("NUM_UPS_FAILED",
                        "99DCM4CHEE",
                        "Number of failed attempts to process this Procedure Step"));
        item.newSequence(Tag.MeasurementUnitsCodeSequence, 1)
                .add(codeItem("1",
                        "UCUM",
                        "no units"));
        item.setString(Tag.NumericValue, VR.DS, "1");
        return item;
    }

    private Attributes codeItem(String value, String scheme, String meaning) {
        Attributes code = new Attributes();
        code.setString(Tag.CodeValue, VR.SH, value);
        code.setString(Tag.CodingSchemeDesignator, VR.SH, scheme);
        code.setString(Tag.CodeMeaning, VR.LO, meaning);
        return code;
    }

    protected void verify(Attributes ups) throws UPSProcessorException {
        if (inputInformationRequired && !ups.containsValue(Tag.InputInformationSequence))
            throw new UPSProcessorException(BAD_UPS, "Missing Input Information");
    }

    private long retryDelay(Attributes ups) {
        Attributes spp = ups.getNestedDataset(Tag.ScheduledProcessingParametersSequence);
        return rule.getRetryDelayInSeconds((spp != null ? numericValue(spp) : 0) + 1);
    }

    private int numericValue(Attributes spp) {
        String numericVal = spp.getString(Tag.NumericValue);
        try {
            return Integer.parseInt(numericVal);
        } catch (NumberFormatException e) {
            LOG.info("Invalid numeric value: {}", numericVal);
        }
        return 0;
    }

    private static Attributes refSOP(String cuid, String iuid) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return item;
    }

    protected Attributes initPerformedProcedure(UPSContext upsCtx, Attributes ups) {
        Attributes mergeAttributes = new Attributes(2);
        mergeAttributes.ensureSequence(Tag.ProcedureStepProgressInformationSequence, 1);
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
        return performedProcedure;
    }

    protected Attributes getPerformedProcedureStep(UPSContext upsCtx) {
        return upsCtx.getMergeAttributes().getNestedDataset(Tag.UnifiedProcedureStepPerformedProcedureSequence);
    }

    protected Attributes getProgressInformation(UPSContext upsCtx) {
        Sequence sq = upsCtx.getMergeAttributes().getSequence(Tag.ProcedureStepProgressInformationSequence);
        if (!sq.isEmpty()) {
            return sq.get(0);
        }
        Attributes progressInformation = new Attributes();
        sq.add(progressInformation);
        return progressInformation;
    }

    private void setReasonForCancellation(UPSContext upsCtx, String reason, Code reasonCode) {
        Attributes progressInformation = getProgressInformation(upsCtx);
        progressInformation.setDate(Tag.ProcedureStepCancellationDateTime, VR.DT, new Date());
        progressInformation.newSequence(Tag.ProcedureStepDiscontinuationReasonCodeSequence, 1)
                .add(reasonCode.toItem());
        progressInformation.setString(Tag.ReasonForCancellation, VR.LT,reason);
    }

    protected abstract void processA(UPSContext upsCtx, Attributes ups) throws Exception;

    private static Code reasonCodeOf(int status) {
        String codeValue = TagUtils.shortToHexString(status);
        return new Code(
                codeValue + "_UPS",
                "99DCM4CHEE",
                null,
                "Processing Unified Procedure Step failed with DICOM Status Code: " + codeValue);
    }
}
