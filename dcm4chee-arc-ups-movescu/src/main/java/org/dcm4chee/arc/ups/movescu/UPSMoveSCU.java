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

package org.dcm4chee.arc.ups.movescu;

import org.dcm4che3.data.*;
import org.dcm4che3.dcmr.ScopeOfAccumulation;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.UPSProcessingRule;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.ups.UPSUtils;
import org.dcm4chee.arc.ups.process.AbstractUPSProcessor;
import org.dcm4chee.arc.ups.process.UPSProcessorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2020
 */
public class UPSMoveSCU extends AbstractUPSProcessor  {
    private static final Logger LOG = LoggerFactory.getLogger(UPSMoveSCU.class);
    private final CMoveSCU moveSCU;
    private final String defDestinationAE;

    public UPSMoveSCU(UPSProcessingRule rule, UPSService upsService, CMoveSCU moveSCU) {
        super(rule, upsService, true);
        this.moveSCU = moveSCU;
        this.defDestinationAE = rule.getUPSProcessorURI().getSchemeSpecificPart();
    }

    @Override
    protected void processA(UPSContext upsCtx, Attributes ups) throws Exception {
        String moveDest = moveDestOf(ups);
        String retrieveAET = retrieveAETOf(ups);
        List<Attributes> keyss = new KeysBuilder(ups).keys();
        Association as = moveSCU.openAssociation(upsCtx.getApplicationEntity(), retrieveAET);
        try {
            Sum sum = new Sum();
            for (Attributes keys : keyss) {
                DimseRSP rsp = moveSCU.cmove(as, 0, moveDest, keys);
                while (rsp.next());
                sum.add(rsp);
            }
            getPerformedProcedureStep(upsCtx).setString(Tag.PerformedProcedureStepDescription, VR.LO,
                    toDescription(retrieveAET, moveDest, sum));
            if (sum.getStatus() != Status.Success) {
                throw new DicomServiceException(sum.getStatus(), sum.getErrorComment());
            }
            if (sum.getNumberOfCompletedSuboperations() == 0
                    && sum.getNumberOfWarningSuboperations() == 0) {
                throw new UPSProcessorException(NOOP_UPS,
                        "No DICOM instances transferred from " + retrieveAET + " to " + moveDest);
            }
        } finally {
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
        }
    }

    private String toDescription(String retrieveAET, String moveDest, Sum sum) {
        StringBuilder sb = new StringBuilder(256)
                .append("Transfer Instances from ")
                .append(retrieveAET)
                .append(" to ")
                .append(moveDest);
            sb.append(" - completed:").append(sum.getNumberOfCompletedSuboperations());
            int warning = sum.getNumberOfWarningSuboperations();
            if (warning > 0)
                sb.append(", warning:").append(warning);
            int failed = sum.getNumberOfFailedSuboperations();
            if (failed > 0)
                sb.append(", failed:").append(failed);
        return sb.length() > 64 ? sb.substring(0, 64) : sb.toString();
    }

    private String moveDestOf(Attributes ups) {
        Attributes outputDestination, dicomStorage;
        return (outputDestination = ups.getNestedDataset(Tag.OutputDestinationSequence)) != null
                && (dicomStorage = outputDestination.getNestedDataset(Tag.DICOMStorageSequence)) != null
                ? dicomStorage.getString(Tag.DestinationAE, defDestinationAE)
                : defDestinationAE;
    }

    private String retrieveAETOf(Attributes ups) throws UPSProcessorException {
        return ups.getSequence(Tag.InputInformationSequence).stream()
                .map(item -> item.getNestedDataset(Tag.DICOMRetrievalSequence))
                .filter(Objects::nonNull)
                .map(item -> item.getString(Tag.RetrieveAETitle))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(UPSMoveSCU::missingRetrieveAETitle);
    }

    private static UPSProcessorException missingRetrieveAETitle() {
        return new UPSProcessorException(BAD_UPS, "Missing Retrieve AE Title in Input Information Sequence");
    }

    private static class KeysBuilder {
        private final Set<String> studies = new HashSet<>();
        private final Map<String, Set<String>> series = new HashMap<>();
        private final Map<String, Map<String, List<String>>> insts = new HashMap<>();

        KeysBuilder(Attributes ups) {
            Consumer<Attributes> retrieve = retrieveOf(
                    UPSUtils.getScheduledProcessingCodeParameter(ups, ScopeOfAccumulation.CODE));
            ups.getSequence(Tag.InputInformationSequence).stream().forEach(retrieve);
        }

        private Consumer<Attributes> retrieveOf(Optional<Code> scopeOfAccumlation) {
            return scopeOfAccumlation.isPresent() ?
                    (scopeOfAccumlation.get().equalsIgnoreMeaning(ScopeOfAccumulation.Study)
                            ? this::retrieveStudies
                            : (scopeOfAccumlation.get().equalsIgnoreMeaning(ScopeOfAccumulation.Series)
                                    ? this::retrieveSeries
                                    : this::retrieveInstances))
                    : this::retrieveInstances;
        }

        private void retrieveStudies(Attributes item) {
            String studyIUID = item.getString(Tag.StudyInstanceUID);
            studies.add(studyIUID);
        }

        private void retrieveSeries(Attributes item) {
            String studyIUID = item.getString(Tag.StudyInstanceUID);
            if (studies.contains(studyIUID)) { // Already STUDY Level
                return;
            }
            String seriesIUID = item.getString(Tag.SeriesInstanceUID);
            if (seriesIUID == null) { // STUDY Level
                studies.add(studyIUID);
                series.remove(studyIUID);
            } else {
                Set<String> seriesOfStudy = series.get(studyIUID);
                if (seriesOfStudy == null) {
                    series.put(studyIUID, seriesOfStudy = new HashSet<>());
                }
                seriesOfStudy.add(seriesIUID);
            }
        }

        private void retrieveInstances(Attributes item) {
            String studyIUID = item.getString(Tag.StudyInstanceUID);
            if (studies.contains(studyIUID)) { // Already STUDY Level
                return;
            }
            String seriesIUID = item.getString(Tag.SeriesInstanceUID);
            if (seriesIUID == null) { // STUDY Level
                studies.add(studyIUID);
                series.remove(studyIUID);
                insts.remove(studyIUID);
            } else {
                Set<String> seriesOfStudy = series.get(studyIUID);
                if (seriesOfStudy != null && seriesOfStudy.contains(seriesIUID)) { // Already SERIES Level
                    return;
                }
                Map<String, List<String>> instsOfStudy = insts.get(studyIUID);
                Sequence refSOPSeq = item.getSequence(Tag.ReferencedSOPSequence);
                if (refSOPSeq == null || refSOPSeq.isEmpty()) {  // SERIES Level
                    if (seriesOfStudy == null) {
                        series.put(studyIUID, seriesOfStudy = new HashSet<>());
                    }
                    seriesOfStudy.add(seriesIUID);
                    if (instsOfStudy != null) {
                        instsOfStudy.remove(seriesIUID);
                    }
                } else {  // INSTANCE Level
                    if (instsOfStudy == null) {
                        insts.put(studyIUID, instsOfStudy = new HashMap<>());
                    }
                    List<String> iuids = refSOPSeq.stream()
                            .map(sopRef -> sopRef.getString(Tag.ReferencedSOPInstanceUID))
                            .collect(Collectors.toList());
                    instsOfStudy.put(seriesIUID, iuids);
                }
            }
        }

        List<Attributes> keys() {
            Stream<Attributes> studyRetrieveKeys = studies.stream()
                    .map(KeysBuilder::toStudyRetrieveKeys);
            Stream<Attributes> seriesRetrieveKeys = series.entrySet().stream()
                    .map(KeysBuilder::toSeriesRetrieveKeys);
            Stream<Attributes> instanceRetrieveKeys = insts.entrySet().stream()
                    .flatMap(KeysBuilder::toInstanceRetrieveKeys);
            return Stream.concat(Stream.concat(studyRetrieveKeys, seriesRetrieveKeys), instanceRetrieveKeys)
                    .collect(Collectors.toList());
        }

        private static Attributes toStudyRetrieveKeys(String studyInstanceUID) {
            Attributes attrs = new Attributes(2);
            attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
            attrs.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
            return attrs;
        }

        private static Attributes toSeriesRetrieveKeys(Map.Entry<String, Set<String>> study) {
            Attributes attrs = new Attributes(3);
            attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "SERIES");
            attrs.setString(Tag.StudyInstanceUID, VR.UI, study.getKey());
            attrs.setString(Tag.SeriesInstanceUID, VR.UI, study.getValue().toArray(StringUtils.EMPTY_STRING));
            return attrs;
        }

        private static Stream<Attributes> toInstanceRetrieveKeys(
                Map.Entry<String, Map<String, List<String>>> study) {
            return study.getValue().entrySet().stream()
                    .map(series -> toInstanceRetrieveKeys(study, series));
        }

        private static Attributes toInstanceRetrieveKeys(
                Map.Entry<String, Map<String, List<String>>> study,
                Map.Entry<String, List<String>> series) {
            Attributes attrs = new Attributes(4);
            attrs.setString(Tag.SOPInstanceUID, VR.UI, series.getValue().toArray(StringUtils.EMPTY_STRING));
            attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
            attrs.setString(Tag.StudyInstanceUID, VR.UI, study.getKey());
            attrs.setString(Tag.SeriesInstanceUID, VR.UI, series.getKey());
            return attrs;
        }

    }

    private static class Sum {
        private int status = Status.Success;
        private String errorComment;
        private int completed;
        private int warning;
        private int failed;

        public int getStatus() {
            return status;
        }

        public String getErrorComment() {
            return errorComment;
        }

        public int getNumberOfCompletedSuboperations() {
            return completed;
        }

        public int getNumberOfWarningSuboperations() {
            return warning;
        }

        public int getNumberOfFailedSuboperations() {
            return failed;
        }

        public void add(DimseRSP rsp) {
            Attributes cmd = rsp.getCommand();
            switch (cmd.getInt(Tag.Status, -1)) {
                case Status.OneOrMoreFailures:
                    if (status == Status.Success)
                        updateStatus(cmd);
                case Status.Success:
                    updateNumbers(
                            cmd.getInt(Tag.NumberOfCompletedSuboperations, 0),
                            cmd.getInt(Tag.NumberOfWarningSuboperations, 0),
                            cmd.getInt(Tag.NumberOfFailedSuboperations, 0));
                    break;
                default:
                    updateStatus(cmd);
            }
        }

        private void updateStatus(Attributes cmd) {
            status = cmd.getInt(Tag.Status, -1);
            errorComment = cmd.getString(Tag.ErrorComment);
        }

        private void updateNumbers(int completed, int warning, int failed) {
            this.completed += completed;
            this.warning += warning;
            this.failed += failed;
        }
    }
}
