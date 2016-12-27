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

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.dcm4chee.arc.store.scu.CStoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
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
        LOG.debug("{}: Process C-MOVE RQ:\n{}", as, keys);
        EnumSet<QueryOption> queryOpts = as.getQueryOptionsFor(rq.getString(Tag.AffectedSOPClassUID));
        QueryRetrieveLevel2 qrLevel = QueryRetrieveLevel2.validateRetrieveIdentifier(
                keys, qrLevels, queryOpts.contains(QueryOption.RELATIONAL));
        ArchiveAEExtension arcAE = as.getApplicationEntity().getAEExtension(ArchiveAEExtension.class);
        if (!arcAE.isAcceptedMoveDestination(rq.getString(Tag.MoveDestination)))
            throw new DicomServiceException(RetrieveService.MOVE_DESTINATION_NOT_ALLOWED,
                    RetrieveService.MOVE_DESTINATION_NOT_ALLOWED_MSG);

        RetrieveContext ctx = newRetrieveContext(arcAE, as, rq, qrLevel, keys);
        String fallbackCMoveSCP = arcAE.fallbackCMoveSCP();
        String fallbackCMoveSCPDestination = arcAE.fallbackCMoveSCPDestination();
        if (!retrieveService.calculateMatches(ctx)) {
            if (fallbackCMoveSCP == null)
                return null;

            ctx.setRetryFailedRetrieve(true);
            LOG.info("{}: No objects of study{} found - forward C-MOVE RQ to {}",
                    as, Arrays.toString(ctx.getStudyInstanceUIDs()), fallbackCMoveSCP);
            return moveSCU.newForwardRetrieveTask(ctx, pc, rq, keys, fallbackCMoveSCP, fallbackCMoveSCPDestination);
        }
        Map<String, Collection<InstanceLocations>> notAccessable = retrieveService.removeNotAccessableMatches(ctx);
        String altCMoveSCP = arcAE.alternativeCMoveSCP();
        if (ctx.getMoveOriginatorAETitle().equals(altCMoveSCP)) {
            // mask altCMoveSCP in Move Originator AET in C-STORE RQ - assume Destination originated Move RQ to altCMoveSCP
            ctx.setMoveOriginatorAETitle(ctx.getDestinationAETitle());
            // ignore not accessible matches - assume they are handled by altCMoveSCP
            ctx.setNumberOfMatches(ctx.getMatches().size());
        } else {
            boolean retryFailedRetrieve = fallbackCMoveSCP != null
                    && fallbackCMoveSCPDestination != null
                    && retryFailedRetrieve(ctx, qrLevel);
            Collection<InstanceLocations> notRetrieveable = notAccessable.remove(null); //TODO
            Iterator<Map.Entry<String, Collection<InstanceLocations>>> notAccessableIter = notAccessable.entrySet().iterator();
            if (notAccessableIter.hasNext()) {
                Map.Entry<String, Collection<InstanceLocations>> notAccessableNext = notAccessableIter.next();
                String otherCMoveSCP = notAccessableNext.getKey();
                String otherMoveDest = otherCMoveSCP.equals(altCMoveSCP) ? null : arcAE.externalRetrieveAEDestination();
                while (notAccessableIter.hasNext()) {
                    LOG.info("{}: {} objects of study{} not locally accessable - send C-MOVE RQ to {}",
                            as, notAccessableNext.getValue().size(), Arrays.toString(ctx.getStudyInstanceUIDs()),
                            otherCMoveSCP);
                    moveSCU.forwardMoveRQ(ctx, pc, rq, keys, otherCMoveSCP, otherMoveDest);
                    notAccessableNext = notAccessableIter.next();
                    otherCMoveSCP = notAccessableNext.getKey();
                    otherMoveDest = otherCMoveSCP.equals(altCMoveSCP) ? null : arcAE.externalRetrieveAEDestination();
                }
                LOG.info("{}: {} objects of study{} not locally accessable - send C-MOVE RQ to {}",
                    as, notAccessableNext.getValue().size(), Arrays.toString(ctx.getStudyInstanceUIDs()),
                    otherCMoveSCP);
                if (!retryFailedRetrieve && ctx.getMatches().isEmpty()) {
                    return moveSCU.newForwardRetrieveTask(ctx, pc, rq, keys, otherCMoveSCP, otherMoveDest);
                }
                moveSCU.forwardMoveRQ(ctx, pc, rq, keys, otherCMoveSCP, otherMoveDest);
            }
            if (retryFailedRetrieve) {
                ctx.setRetryFailedRetrieve(true);
                LOG.info("{}: Some objects of study{} not found - retry forward C-MOVE RQ to {}",
                        as, Arrays.toString(ctx.getStudyInstanceUIDs()), fallbackCMoveSCP);
                if (ctx.getMatches().isEmpty())
                    return moveSCU.newForwardRetrieveTask(ctx, pc, rq, keys, fallbackCMoveSCP, fallbackCMoveSCPDestination);

                moveSCU.forwardMoveRQ(ctx, pc, rq, keys, fallbackCMoveSCP, fallbackCMoveSCPDestination);
            }
        }
        return storeSCU.newRetrieveTaskMOVE(as, pc, rq, ctx);
    }

    private RetrieveContext newRetrieveContext(ArchiveAEExtension arcAE,
           Association as, Attributes rq, QueryRetrieveLevel2 qrLevel, Attributes keys) throws DicomServiceException {
        try {
            return retrieveService.newRetrieveContextMOVE(arcAE, as, rq, qrLevel, keys);
        } catch (ConfigurationNotFoundException e) {
            throw new DicomServiceException(Status.MoveDestinationUnknown, e.getMessage());
        } catch (ConfigurationException e) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
        }
    }

    private boolean retryFailedRetrieve(RetrieveContext ctx, QueryRetrieveLevel2 qrLevel) {
        HashSet<String> uids = new HashSet<>();
        ArchiveAEExtension arcae = ctx.getArchiveAEExtension();
        int maxRetrieveRetries = arcae.fallbackCMoveSCPRetries();
        switch (qrLevel) {
            case STUDY:
                uids.addAll(Arrays.asList(ctx.getStudyInstanceUIDs()));
                for (StudyInfo studyInfo : ctx.getStudyInfos()) {
                    if (studyInfo.getFailedSOPInstanceUIDList() != null) {
                        if (maxRetrieveRetries == 0 || studyInfo.getFailedRetrieves() < maxRetrieveRetries)
                            return true;
                        LOG.warn("{}: Maximal number of retries[{}] to retrieve objects of study[{}] from {} exceeded",
                                ctx.getRequestAssociation(), maxRetrieveRetries, studyInfo.getStudyInstanceUID(),
                                arcae.fallbackCMoveSCP());
                    }
                    uids.remove(studyInfo.getStudyInstanceUID());
                }
            case SERIES:
                uids.addAll(Arrays.asList(ctx.getSeriesInstanceUIDs()));
                for (SeriesInfo seriesInfo : ctx.getSeriesInfos()) {
                    if (seriesInfo.getFailedSOPInstanceUIDList() != null) {
                        if (maxRetrieveRetries == 0 || seriesInfo.getFailedRetrieves() < maxRetrieveRetries)
                            return true;
                        LOG.warn("{}: Maximal number of retries[{}] to retrieve objects of series[{}] from {} exceeded",
                                ctx.getRequestAssociation(), maxRetrieveRetries, seriesInfo.getSeriesInstanceUID(),
                                arcae.fallbackCMoveSCP());
                    }
                    uids.remove(seriesInfo.getSeriesInstanceUID());
                }
        }
        return !uids.isEmpty();
    }
}
