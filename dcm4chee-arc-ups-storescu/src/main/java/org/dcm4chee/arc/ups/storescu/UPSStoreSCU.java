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

package org.dcm4chee.arc.ups.storescu;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.data.*;
import org.dcm4che3.dcmr.ScopeOfAccumulation;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.UPSProcessingRule;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.scu.CStoreSCU;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.ups.UPSUtils;
import org.dcm4chee.arc.ups.process.AbstractUPSProcessor;
import org.dcm4chee.arc.ups.process.UPSProcessorException;

import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Mar 2020
 */
public class UPSStoreSCU extends AbstractUPSProcessor {
    private final RetrieveService retrieveService;
    private final CStoreSCU storeSCU;
    private final String defDestinationAE;

    public UPSStoreSCU(UPSProcessingRule rule, UPSService upsService, RetrieveService retrieveService,
            CStoreSCU storeSCU) {
        super(rule, upsService, true);
        this.retrieveService = retrieveService;
        this.storeSCU = storeSCU;
        this.defDestinationAE = rule.getUPSProcessorURI().getSchemeSpecificPart();
    }

    @Override
    protected void processA(UPSContext upsCtx, Attributes ups) throws Exception {
        String destinationAE = destinationAEOf(ups);
        RetrieveContext retrieveContext = calculateMatches(ups, destinationAE);
        if (retrieveContext == null) {
            throw new UPSProcessorException(NOOP_UPS, "No matching Instances found");
        }
        if (retrieveService.restrictRetrieveAccordingTransferCapabilities(retrieveContext)) {
            storeSCU.newRetrieveTaskSTORE(retrieveContext).run();
            String outcomeDescription = retrieveContext.getOutcomeDescription();
            Attributes performedProcedure = getPerformedProcedureStep(upsCtx);
            performedProcedure.setString(Tag.PerformedProcedureStepDescription, VR.LO, outcomeDescription);
            Sequence outputInformationSeq = performedProcedure.getSequence(Tag.OutputInformationSequence);
            for (InstanceLocations match : retrieveContext.getMatches()) {
                if (!retrieveContext.isFailedSOPInstanceUID(match.getSopInstanceUID())) {
                    refSOPSequence(outputInformationSeq, match, destinationAE).add(toSOPRef(match));
                }
            }
            if (retrieveContext.status() != Status.Success)
                throw new DicomServiceException(retrieveContext.status(), outcomeDescription);
        }
    }

    private String destinationAEOf(Attributes ups) {
        Attributes outputDestination, dicomStorage;
        return (outputDestination = ups.getNestedDataset(Tag.OutputDestinationSequence)) != null
                && (dicomStorage = outputDestination.getNestedDataset(Tag.DICOMStorageSequence)) != null
                ? dicomStorage.getString(Tag.DestinationAE, defDestinationAE)
                : defDestinationAE;
    }

    private RetrieveContext calculateMatches(Attributes ups, String destAET)
            throws DicomServiceException {
        RetrieveContext retrieveContext = null;
        RetrieveLevel retrieveLevel = RetrieveLevel.of(
                UPSUtils.getScheduledProcessingCodeParameter(ups, ScopeOfAccumulation.CODE));
        Set<String> suids = new HashSet<>();
        for (Attributes inputInformation : ups.getSequence(Tag.InputInformationSequence)) {
            RetrieveContext tmp = newRetrieveContext(retrieveLevel, inputInformation, destAET, suids);
            if (tmp != null && retrieveService.calculateMatches(tmp)) {
                if (retrieveContext == null) {
                    retrieveContext = tmp;
                } else {
                    retrieveContext.getMatches().addAll(tmp.getMatches());
                    retrieveContext.setNumberOfMatches(
                            retrieveContext.getNumberOfMatches() + tmp.getNumberOfMatches());
                }
            }
        }
        return retrieveContext;
    }

    private RetrieveContext newRetrieveContext(RetrieveLevel retrieveLevel, Attributes inputInformation,
            String destAET, Set<String> suids) throws DicomServiceException {
        try {
            return retrieveLevel.newRetrieveContextSTORE(
                    retrieveService, rule.getAETitle(), inputInformation, destAET, suids);
        } catch (ConfigurationNotFoundException e) {
            throw new DicomServiceException(Status.MoveDestinationUnknown, e.getMessage());
        } catch (ConfigurationException e) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
        }
    }

    private enum RetrieveLevel {
        STUDY {
            @Override
            RetrieveContext newRetrieveContextSTORE(RetrieveService retrieveService, String aet,
                    Attributes inputInformation, String destAET, Set<String> suids) throws ConfigurationException {
                String suid = inputInformation.getString(Tag.StudyInstanceUID);
                return suids.add(suid)
                        ? retrieveService.newRetrieveContextSTORE(aet, suid, (String) null, (String) null, destAET)
                        : null;
            }
        },
        SERIES {
            @Override
            RetrieveContext newRetrieveContextSTORE(RetrieveService retrieveService, String aet,
                    Attributes inputInformation, String destAET, Set<String> suids) throws ConfigurationException {
                return retrieveService.newRetrieveContextSTORE(
                        aet,
                        inputInformation.getString(Tag.StudyInstanceUID),
                        inputInformation.getString(Tag.SeriesInstanceUID),
                        (String) null,
                        destAET);
            }
        },
        IMAGE {
            @Override
            RetrieveContext newRetrieveContextSTORE(RetrieveService retrieveService, String aet,
                    Attributes inputInformation, String destAET, Set<String> suids) throws ConfigurationException {
                return retrieveService.newRetrieveContextSTORE(
                        aet,
                        inputInformation.getString(Tag.StudyInstanceUID),
                        inputInformation.getString(Tag.SeriesInstanceUID),
                        inputInformation.getSequence(Tag.ReferencedSOPSequence),
                        destAET);
            }
        };

        public static RetrieveLevel of(Optional<Code> scopeOfAccumlation) {
            return scopeOfAccumlation.isPresent() ?
                    (scopeOfAccumlation.get().equalsIgnoreMeaning(ScopeOfAccumulation.Study) ? STUDY :
                            (scopeOfAccumlation.get().equalsIgnoreMeaning(ScopeOfAccumulation.Series) ? SERIES : IMAGE))
                    : IMAGE;
        }

        abstract RetrieveContext newRetrieveContextSTORE(RetrieveService retrieveService, String aet,
                Attributes inputInformation, String destAET, Set<String> suids) throws ConfigurationException;
    }

    private static Attributes toSOPRef(InstanceLocations inst) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, inst.getSopClassUID());
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.getSopInstanceUID());
        return item;
    }

    private static Sequence refSOPSequence(Sequence sq, InstanceLocations inst, String destAET) {
        String studyIUID = inst.getAttributes().getString(Tag.StudyInstanceUID);
        String seriesIUID = inst.getAttributes().getString(Tag.SeriesInstanceUID);
        for (Attributes item : sq) {
             if (studyIUID.equals(item.getString(Tag.StudyInstanceUID))
                    && seriesIUID.equals(item.getString(Tag.SeriesInstanceUID))) {
                return item.getSequence(Tag.ReferencedSOPSequence);
            }
        }
        Attributes item = new Attributes(5);
        sq.add(item);
        Sequence refSOPSequence = item.newSequence(Tag.ReferencedSOPSequence, 10);
        item.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        item.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
        item.setString(Tag.TypeOfInstances, VR.CS, "DICOM");
        item.newSequence(Tag.DICOMRetrievalSequence, 1).add(retrieveAETItem(destAET));
        return refSOPSequence;
    }

    private static Attributes retrieveAETItem(String destAET) {
        Attributes item = new Attributes(1);
        item.setString(Tag.RetrieveAETitle, VR.AE, destAET);
        return item;
    }
}
