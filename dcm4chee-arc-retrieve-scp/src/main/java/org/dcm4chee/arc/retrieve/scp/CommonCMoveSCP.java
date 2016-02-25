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

package org.dcm4chee.arc.retrieve.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.MoveForwardLevel;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.StudyInfo;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.dcm4chee.arc.retrieve.scu.ForwardRetrieveTask;
import org.dcm4chee.arc.store.scu.CStoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class CommonCMoveSCP extends BasicCMoveSCP {

    private static final Logger LOG = LoggerFactory.getLogger(CommonCMoveSCP.class);

    private final EnumSet<QueryRetrieveLevel2> qrLevels;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private CStoreSCU storeSCU;

    @Inject
    private CMoveSCU moveSCU;

    public CommonCMoveSCP(String sopClass, EnumSet<QueryRetrieveLevel2> qrLevels) {
        super(sopClass);
        this.qrLevels = qrLevels;
    }

    @Override
    protected RetrieveTask calculateMatches(Association as, PresentationContext pc, Attributes rq, Attributes keys)
            throws DicomServiceException {
        EnumSet<QueryOption> queryOpts = as.getQueryOptionsFor(rq.getString(Tag.AffectedSOPClassUID));
        QueryRetrieveLevel2 qrLevel = QueryRetrieveLevel2.validateRetrieveIdentifier(
                keys, qrLevels, queryOpts.contains(QueryOption.RELATIONAL));
        RetrieveContext ctx = retrieveService.newRetrieveContextMOVE(as, rq, qrLevel, keys);
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        String fallbackCMoveSCP = arcAE.fallbackCMoveSCP();
        String fallbackCMoveSCPDestination = fallbackCMoveSCP != null ? arcAE.fallbackCMoveSCPDestination() : null;
        MoveForwardLevel moveForwardLevel = fallbackCMoveSCPDestination != null ? arcAE.fallbackCMoveSCPLevel() : null;
        if (!retrieveService.calculateMatches(ctx)) {
            if (fallbackCMoveSCP == null) {
                return null;
            }

            String studyInstanceUIDsAsString = Arrays.toString(ctx.getStudyInstanceUIDs());
            if (fallbackCMoveSCPDestination == null) {
                LOG.info("{}: No objects of study{} found - forward C-MOVE RQ to {}",
                        as, studyInstanceUIDsAsString, fallbackCMoveSCP);
                return moveSCU.newForwardRetrieveTask(ctx.getLocalApplicationEntity(), as, pc, rq, keys,
                        as.getCallingAET(), fallbackCMoveSCP, true, true);
            }

            LOG.info("{}: No objects of study{} found - try to retrieve requested objects from {}",
                    as, studyInstanceUIDsAsString, fallbackCMoveSCP);
            ForwardRetrieveTask retrieveTask = moveSCU.newForwardRetrieveTask(ctx.getLocalApplicationEntity(), as, pc,
                    changeMoveDestination(rq, fallbackCMoveSCPDestination),
                    changeQueryRetrieveLevel(keys, moveForwardLevel),
                    as.getCallingAET(), fallbackCMoveSCP, false, false);
            retrieveTask.run();
            Attributes finalMoveRSP = retrieveTask.getFinalMoveRSP();
            Attributes finalMoveRSPData = retrieveTask.getFinalMoveRSPData();
            int finalMoveRSPStatus = finalMoveRSP.getInt(Tag.Status, -1);
            if (finalMoveRSPStatus != Status.Success && finalMoveRSPStatus != Status.OneOrMoreFailures)
                throw (new DicomServiceException(finalMoveRSPStatus, finalMoveRSP.getString(Tag.ErrorComment))
                        .setDataset(finalMoveRSPData));

            int failed = finalMoveRSP.getInt(Tag.NumberOfFailedSuboperations, 0);
            int retrieved = finalMoveRSP.getInt(Tag.NumberOfCompletedSuboperations, 0) +
                    finalMoveRSP.getInt(Tag.NumberOfWarningSuboperations, 0);
            if (failed > 0) {
                LOG.warn("{}: Failed to retrieve {} from {} objects of study{} from {}",
                        as, failed, failed + retrieved,
                        studyInstanceUIDsAsString, fallbackCMoveSCP);
                String[] failedSOPInstanceUIDList = finalMoveRSPData != null
                        ? finalMoveRSPData.getStrings(Tag.FailedSOPInstanceUIDList)
                        : null;
                String failedSOPInstanceUIDs = "*";
                if (failedSOPInstanceUIDList != null && failedSOPInstanceUIDList.length > 0) {
                    failedSOPInstanceUIDs = StringUtils.concat(failedSOPInstanceUIDList, '\\');
                } else {
                    LOG.warn("{}: Missing Failed SOP Instance UID List in C-MOVE-RSP from {}", as, fallbackCMoveSCP);
                }
                if (moveForwardLevel == MoveForwardLevel.STUDY) {
                    for (String studyInstanceUID : ctx.getStudyInstanceUIDs()) {
                        retrieveService.failedToRetrieveStudy(studyInstanceUID, failedSOPInstanceUIDs);
                    }
                }
            }
            if (!retrieveService.calculateMatches(ctx))
                return null;
        } else if (moveForwardLevel == MoveForwardLevel.STUDY) {
            int maxRetrieveRetries = arcAE.fallbackCMoveSCPRetries();
            int totRetrieved = 0;
            for (StudyInfo studyInfo : ctx.getStudyInfos()) {
                String failedSOPInstanceUIDs = studyInfo.getFailedSOPInstanceUIDList();
                if (failedSOPInstanceUIDs == null) {
                    continue;
                }
                String studyInstanceUID = studyInfo.getStudyInstanceUID();
                if (maxRetrieveRetries >= 0 && studyInfo.getFailedRetrieves() > maxRetrieveRetries ) {
                    LOG.warn("{}: Maximal number of retries[{}] to retrieve objects of study[{}] from {} exceeded",
                            as, maxRetrieveRetries, studyInstanceUID, fallbackCMoveSCP);
                    continue;
                }

                LOG.info("{}: retry to retrieve objects of study[{}] from {}",
                        as, studyInstanceUID, fallbackCMoveSCP);
                ForwardRetrieveTask retrieveTask = moveSCU.newForwardRetrieveTask(
                        ctx.getLocalApplicationEntity(), as, pc,
                        changeMoveDestination(rq, fallbackCMoveSCPDestination),
                        failedSOPInstanceUIDs.equals("*")
                                ? mkStudyRequest(studyInstanceUID)
                                : mkInstanceRequest(studyInstanceUID, failedSOPInstanceUIDs),
                        as.getCallingAET(), fallbackCMoveSCP, false, false);
                retrieveTask.run();
                Attributes finalMoveRSP = retrieveTask.getFinalMoveRSP();
                Attributes finalMoveRSPData = retrieveTask.getFinalMoveRSPData();
                int finalMoveRSPStatus = finalMoveRSP.getInt(Tag.Status, -1);
                if (finalMoveRSPStatus != Status.Success && finalMoveRSPStatus != Status.OneOrMoreFailures) {
                    LOG.warn("{}: Failed to retry retrieve of {} from {} objects of study[{}] from {}",
                            as, studyInstanceUID, fallbackCMoveSCP, Integer.toHexString(finalMoveRSPStatus),
                            finalMoveRSP.getString(Tag.ErrorComment));
                    continue;
                }
                int failed = finalMoveRSP.getInt(Tag.NumberOfFailedSuboperations, 0);
                int retrieved = finalMoveRSP.getInt(Tag.NumberOfCompletedSuboperations, 0) +
                        finalMoveRSP.getInt(Tag.NumberOfWarningSuboperations, 0);
                totRetrieved += retrieved;
                if (failed == 0) {
                    retrieveService.clearFailedSOPInstanceUIDList(studyInstanceUID);
                    continue;
                }
                if (failed > 0) {
                    LOG.warn("{}: Failed to retry retrieve {} from {} objects of study[{}] from {}",
                            as, failed, failed + retrieved, studyInstanceUID, fallbackCMoveSCP);
                    String[] failedSOPInstanceUIDList = finalMoveRSPData != null
                            ? finalMoveRSPData.getStrings(Tag.FailedSOPInstanceUIDList)
                            : null;
                    if (failedSOPInstanceUIDList != null && failedSOPInstanceUIDList.length > 0) {
                        failedSOPInstanceUIDs = StringUtils.concat(failedSOPInstanceUIDList, '\\');
                    } else {
                        LOG.warn("{}: Missing Failed SOP Instance UID List in C-MOVE-RSP from {}",
                                as, fallbackCMoveSCP);
                    }
                    retrieveService.failedToRetrieveStudy(studyInstanceUID, failedSOPInstanceUIDs);
                }
            }
            if (totRetrieved > 0)
                retrieveService.calculateMatches(ctx);
        }

        String altCMoveSCP = arcAE.alternativeCMoveSCP();
        if (altCMoveSCP != null) {
            Collection<InstanceLocations> notAccessable = retrieveService.removeNotAccessableMatches(ctx);
            if (ctx.getMatches().isEmpty()) {
                LOG.info("{}: Requested objects not locally accessable - forward C-MOVE RQ to {}",
                        as, altCMoveSCP);
                return moveSCU.newForwardRetrieveTask(ctx.getLocalApplicationEntity(), as, pc, rq, keys,
                        as.getCallingAET(), altCMoveSCP, true, true);
            }

            if (!notAccessable.isEmpty()) {
                LOG.warn("{}: {} of {} requested objects not locally accessable",
                        as, notAccessable.size(), ctx.getNumberOfMatches());
                for (InstanceLocations remoteMatch : notAccessable)
                    ctx.addFailedSOPInstanceUID(remoteMatch.getSopInstanceUID());
            }
        }
        return storeSCU.newRetrieveTaskMOVE(as, pc, rq, ctx);
    }

    private Attributes mkStudyRequest(String studyInstanceUID) {
        Attributes keys = new Attributes(2);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        return keys;
    }

    private Attributes mkInstanceRequest(String studyInstanceUID, String failedSOPInstanceUIDList) {
        Attributes keys = new Attributes(3);
        keys.setString(Tag.SOPInstanceUID, VR.UI, failedSOPInstanceUIDList);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        return keys;
    }

    private Attributes changeQueryRetrieveLevel(Attributes keys, MoveForwardLevel moveForwardLevel) {
        return moveForwardLevel != null ? moveForwardLevel.changeQueryRetrieveLevel(keys) : keys;
    }

    private Attributes changeMoveDestination(Attributes rq, String newMoveDest) {
        Attributes changed = new Attributes(rq);
        changed.setString(Tag.MoveDestination, VR.AE, newMoveDest);
        return changed;
    }

}
