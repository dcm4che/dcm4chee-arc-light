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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.*;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.net.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2019
 */
class ExternalRetrieveAuditService extends AuditService {
    private final static Logger LOG = LoggerFactory.getLogger(ExternalRetrieveAuditService.class);

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType, IApplicationEntityCache aeCache) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        if (auditInfo.getField(AuditInfo.CALLING_HOST) == null) {
            activeParticipants.add(requestorAE(auditInfo, auditLogger));
            activeParticipants.add(moveOriginator(auditInfo, aeCache, eventType));
            activeParticipants.add(destination(auditInfo, eventType));
        } else {
            activeParticipants.add(requestor(auditInfo));
            activeParticipants.add(archiveURI(auditInfo, auditLogger));
            activeParticipants.add(moveOriginator(auditInfo, aeCache, eventType));
            activeParticipants.add(destination(auditInfo, eventType));
        }
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study(auditInfo));
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        String warning = auditInfo.getField(AuditInfo.WARNING);
        String errorCode = auditInfo.getField(AuditInfo.ERROR_CODE);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(warning == null ? outcome : outcome + "\n" + warning);
        ei.setEventOutcomeIndicator(outcome == null || errorCode.equals("0")
                ? AuditMessages.EventOutcomeIndicator.Success
                : AuditMessages.EventOutcomeIndicator.MinorFailure);
        if (!errorCode.equals("0"))
            ei.getEventTypeCode().add(AuditUtils.errorEventTypeCode(errorCode));
        return ei;
    }

    private static ParticipantObjectIdentification study(AuditInfo auditInfo) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(auditInfo.getField(AuditInfo.STUDY_UID));
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)));
        return study;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorUserID);
        boolean requestorUserIDIsIP = AuditMessages.isIP(requestorUserID);
        requestor.setUserIDTypeCode(requestorUserIDIsIP
                                    ? AuditMessages.UserIDTypeCode.NodeID
                                    : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(requestorUserIDIsIP
                                    ? AuditMessages.UserTypeCode.Application
                                    : AuditMessages.UserTypeCode.Person);
        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(AuditMessages.isIP(requestorHost)
                                                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                                                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        requestor.setUserIsRequestor(true);
        return requestor;
    }

    private static ActiveParticipant archiveURI(AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant archiveURI = new ActiveParticipant();
        archiveURI.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archiveURI.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        archiveURI.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveURI.setAlternativeUserID(AuditLogger.processID());
        String archiveHost = auditLogger.getConnections().get(0).getHostname();
        archiveURI.setNetworkAccessPointID(archiveHost);
        archiveURI.setNetworkAccessPointTypeCode(AuditMessages.isIP(archiveHost)
                ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveURI;
    }

    private static ActiveParticipant requestorAE(AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant requestorAE = new ActiveParticipant();
        requestorAE.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        requestorAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        requestorAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        requestorAE.setAlternativeUserID(AuditLogger.processID());
        String requestorHost = auditLogger.getConnections().get(0).getHostname();
        requestorAE.setNetworkAccessPointID(requestorHost);
        requestorAE.setNetworkAccessPointTypeCode(AuditMessages.isIP(requestorHost)
                ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        requestorAE.setUserIsRequestor(true);
        return requestorAE;
    }

    private static ActiveParticipant moveOriginator(
            AuditInfo auditInfo, IApplicationEntityCache aeCache, AuditUtils.EventType eventType) {
        ActiveParticipant moveOriginator = new ActiveParticipant();
        moveOriginator.setUserID(auditInfo.getField(AuditInfo.C_MOVE_ORIGINATOR));
        moveOriginator.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        moveOriginator.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        try {
            String moveOriginatorHost = auditInfo.getField(AuditInfo.CALLED_HOST) == null
                    ? aeCache.findApplicationEntity(auditInfo.getField(AuditInfo.C_MOVE_ORIGINATOR))
                        .getConnections()
                        .get(0)
                        .getHostname()
                    : auditInfo.getField(AuditInfo.CALLED_HOST);
            moveOriginator.setNetworkAccessPointID(moveOriginatorHost);
            moveOriginator.setNetworkAccessPointTypeCode(AuditMessages.isIP(moveOriginatorHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        } catch (ConfigurationException e) {
            LOG.info("Exception caught on getting hostname for C-MOVESCP : {}\n",
                    auditInfo.getField(AuditInfo.C_MOVE_ORIGINATOR), e);
        }
        moveOriginator.getRoleIDCode().add(eventType.source);
        return moveOriginator;
    }

    private static ActiveParticipant destination(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant destination = new ActiveParticipant();
        destination.setUserID(auditInfo.getField(AuditInfo.DEST_USER_ID));
        destination.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        destination.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        destination.getRoleIDCode().add(eventType.destination);
        return destination;
    }
}
