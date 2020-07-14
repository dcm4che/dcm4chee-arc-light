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

package org.dcm4chee.arc.ups.ianscu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.UPSProcessingRule;
import org.dcm4chee.arc.ian.scu.IANSCU;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.ups.process.AbstractUPSProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2020
 */
public class UPSIANSCU extends AbstractUPSProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(UPSIANSCU.class);

    private final IANSCU ianSCU;
    private final QueryService queryService;
    private final String defDestinationAE;
    private final Device device;

    public UPSIANSCU(UPSProcessingRule rule, UPSService upsService, IANSCU ianSCU, QueryService queryService,
                     Device device) {
        super(rule, upsService, true);
        this.ianSCU = ianSCU;
        this.queryService = queryService;
        this.device = device;
        this.defDestinationAE = rule.getUPSProcessorURI().getSchemeSpecificPart();
    }

    @Override
    protected void processA(UPSContext upsCtx, Attributes ups) throws Exception {
        String destinationAE = destinationAEOf(ups);
        ApplicationEntity ae = Objects.requireNonNull(
                device.getApplicationEntity(rule.getAETitle(), true),
                () -> String.format("No such Archive AE - %s", rule.getAETitle()));
        for (Map.Entry<String, IanInfo> entry : studyIanInfoFrom(ups).entrySet()) {
            IanInfo ianInfo = entry.getValue();
            Attributes ian = queryService.createIAN(
                    ae, entry.getKey(), ianInfo.getSeriesRefSOPSeq().keySet().toArray(new String[0]));
            if (ian == null) {
                LOG.info("Ignore IAN of Study[uid={}] without referenced objects.", entry.getKey());
                continue;
            }
            ianInfo.setIan(ian);
            String sopInstanceUID = UIDUtils.createUID();
            DimseRSP dimseRSP = ianSCU.sendIANRQ(rule.getAETitle(), destinationAE, sopInstanceUID, ianInfo.getIan());
            dimseRSP.next();
            Attributes cmd = dimseRSP.getCommand();
            int status = cmd.getInt(Tag.Status, -1);
            getPerformedProcedureStep(upsCtx)
                    .setString(Tag.PerformedProcedureStepDescription, VR.LO,
                            outcomeDesc(status, cmd.getString(Tag.ErrorComment), sopInstanceUID, destinationAE));
        }
    }

    private String destinationAEOf(Attributes ups) {
        Attributes outputDestination, dicomStorage;
        return (outputDestination = ups.getNestedDataset(Tag.OutputDestinationSequence)) != null
                && (dicomStorage = outputDestination.getNestedDataset(Tag.DICOMStorageSequence)) != null
                ? dicomStorage.getString(Tag.DestinationAE, defDestinationAE)
                : defDestinationAE;
    }

    private String outcomeDesc(int status, String errorComment, String sopInstanceUID, String destinationAE) {
        return status != Status.Success
                ? "Send IAN[uid=" + sopInstanceUID + "] to AE: " + destinationAE
                    + " failed with status: " + TagUtils.shortToHexString(status)
                    + "H, error comment: " + errorComment
                : "Send IAN[uid=" + sopInstanceUID + "] to AE: " + destinationAE;
    }

    private Map<String, IanInfo> studyIanInfoFrom(Attributes ups) {
        Map<String, IanInfo> studyIanInfo = new HashMap<>();
        ups.getSequence(Tag.InputInformationSequence)
                .forEach(inputInformation ->
                    studyIanInfo.computeIfAbsent(
                            inputInformation.getString(Tag.StudyInstanceUID),
                            ianInfo -> new IanInfo(inputInformation, rule.getAETitle()))
                            .addSeriesRefSopSeq(inputInformation));
        return studyIanInfo;
    }

    static class IanInfo {
        private Attributes ian;
        private final Map<String, Sequence> seriesRefSOPSeq = new HashMap<>();
        private final String retrieveAET;

        IanInfo(Attributes inputInformation, String retrieveAET) {
            this.retrieveAET = retrieveAET;
            addSeriesRefSopSeq(inputInformation);
        }

        Attributes getIan() {
            return ian;
        }

        public void setIan(Attributes ian) {
            this.ian = compute(ian);
        }

        Map<String, Sequence> getSeriesRefSOPSeq() {
            return seriesRefSOPSeq;
        }

        void addSeriesRefSopSeq(Attributes inputInformation) {
            seriesRefSOPSeq.put(inputInformation.getString(Tag.SeriesInstanceUID),
                    inputInformation.getSequence(Tag.ReferencedSOPSequence));
        }

        Attributes compute(Attributes ian) {
            for (Map.Entry<String, Sequence> entry : seriesRefSOPSeq.entrySet()) {
                String seriesUID = entry.getKey();
                Sequence refSOPSeq = entry.getValue();
                Sequence refSeriesSeqIAN = ian.getSequence(Tag.ReferencedSeriesSequence);
                if (removeNotReferredAddUnavailableInstances(seriesUID, refSOPSeq, refSeriesSeqIAN))
                    continue;

                addUnavailableSeries(seriesUID, refSOPSeq, refSeriesSeqIAN);
            }
            return ian;
        }

        private boolean removeNotReferredAddUnavailableInstances(
                String seriesUID, Sequence refSOPSeq, Sequence refSeriesSeqIAN) {
            for (Attributes refSeriesIAN : refSeriesSeqIAN)
                if (seriesUID.equals(refSeriesIAN.getString(Tag.SeriesInstanceUID))) {
                    Sequence refSOPSeqIAN = refSeriesIAN.getSequence(Tag.ReferencedSOPSequence);
                    refSOPSeqIAN.removeIf(refSOPIAN -> !referred(refSOPSeq, refSOPIAN));
                    addUnavailableInstances(refSOPSeqIAN, refSOPSeq);
                    return true;
                }

            return false;
        }

        private void addUnavailableInstances(Sequence refSOPSeqIAN, Sequence refSOPSeq) {
            for (Attributes refSOP : refSOPSeq)
                if (!availableInstance(refSOPSeqIAN, refSOP))
                    refSOPSeqIAN.add(unavailable(refSOP));
        }

        private boolean availableInstance(Sequence refSOPSeqIAN, Attributes refSOP) {
            String iuid = refSOP.getString(Tag.ReferencedSOPInstanceUID);
            for (Attributes refSOPIAN : refSOPSeqIAN)
                if (iuid.equals(refSOPIAN.getString(Tag.ReferencedSOPInstanceUID)))
                    return true;

            return false;
        }

        private void addUnavailableSeries(String seriesUID, Sequence refSOPSeq, Sequence refSeriesSeqIAN) {
            Attributes refSeries = new Attributes(2);
            refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
            Sequence refSOPSeq1 = refSeries.newSequence(Tag.ReferencedSOPSequence, refSOPSeq.size());
            refSOPSeq.forEach(refSOP -> refSOPSeq1.add(unavailable(refSOP)));
            refSeriesSeqIAN.add(refSeries);
        }

        private Attributes unavailable(Attributes refSOP) {
            Attributes unavailable = new Attributes(4);
            unavailable.setString(Tag.ReferencedSOPClassUID, VR.UI, refSOP.getString(Tag.ReferencedSOPClassUID));
            unavailable.setString(Tag.ReferencedSOPInstanceUID, VR.UI, refSOP.getString(Tag.ReferencedSOPInstanceUID));
            unavailable.setString(Tag.RetrieveAETitle, VR.AE, retrieveAET);
            unavailable.setString(Tag.InstanceAvailability, VR.CS, Availability.UNAVAILABLE.name());
            return unavailable;
        }

        private boolean referred(Sequence refSOPSeq, Attributes refSOP) {
            String iuid = refSOP.getString(Tag.ReferencedSOPInstanceUID);
            for (Attributes ref : refSOPSeq)
                if (iuid.equals(ref.getString(Tag.ReferencedSOPInstanceUID)))
                    return true;

            return false;
        }
    }
}
