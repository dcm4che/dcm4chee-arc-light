package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.ActiveParticipant;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.EventIdentification;
import org.dcm4che3.audit.ParticipantObjectIdentification;
import org.dcm4che3.net.audit.AuditLogger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
class ProvideAndRegisterAuditService extends AuditService {

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(destination(auditInfo, eventType));
        if (auditInfo.getField(AuditInfo.CALLED_USERID) == null) {
            activeParticipants.add(archiveRequestor(auditInfo, eventType, auditLogger));
        } else {
            activeParticipants.add(requestor(auditInfo));
            activeParticipants.add(archiveURI(auditInfo, eventType, auditLogger));
        }
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, submissionSet(auditInfo), patient(auditInfo));
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcome);
        ei.setEventOutcomeIndicator(outcome == null
                ? AuditMessages.EventOutcomeIndicator.Success
                : AuditMessages.EventOutcomeIndicator.MinorFailure);
        ei.getEventTypeCode().add(eventType.eventTypeCode);
        return ei;
    }

    private static ParticipantObjectIdentification patient(AuditInfo auditInfo) {
        ParticipantObjectIdentification patient = new ParticipantObjectIdentification();
        patient.setParticipantObjectID(auditInfo.getField(AuditInfo.P_ID));
        patient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        patient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        patient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        patient.setParticipantObjectName(auditInfo.getField(AuditInfo.P_NAME));
        return patient;
    }

    private static ParticipantObjectIdentification submissionSet(AuditInfo auditInfo) {
        ParticipantObjectIdentification submissionSet = new ParticipantObjectIdentification();
        submissionSet.setParticipantObjectID(auditInfo.getField(AuditInfo.SUBMISSION_SET_UID));
        submissionSet.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA);
        submissionSet.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        submissionSet.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Job);
        return submissionSet;
    }

    private static ActiveParticipant destination(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant destination = new ActiveParticipant();
        destination.setUserID(auditInfo.getField(AuditInfo.DEST_USER_ID));
        destination.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        destination.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        destination.getRoleIDCode().add(eventType.destination);
        String destHost = auditInfo.getField(AuditInfo.DEST_NAP_ID);
        destination.setNetworkAccessPointID(destHost);
        destination.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(destHost)
                ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return destination;
    }

    private static ActiveParticipant archiveRequestor(
            AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ActiveParticipant archiveRequestor = new ActiveParticipant();
        archiveRequestor.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        archiveRequestor.setUserIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName);
        archiveRequestor.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveRequestor.setAlternativeUserID(AuditLogger.processID());
        archiveRequestor.getRoleIDCode().add(eventType.source);
        archiveRequestor.setUserIsRequestor(true);
        String archiveRequestorHost = auditLogger.getConnections().get(0).getHostname();
        archiveRequestor.setNetworkAccessPointID(archiveRequestorHost);
        archiveRequestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveRequestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveRequestor;
    }

    private static ActiveParticipant archiveURI(
            AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ActiveParticipant archiveURI = new ActiveParticipant();
        archiveURI.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archiveURI.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        archiveURI.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveURI.setAlternativeUserID(AuditLogger.processID());
        archiveURI.getRoleIDCode().add(eventType.source);
        String archiveURIHost = auditLogger.getConnections().get(0).getHostname();
        archiveURI.setNetworkAccessPointID(archiveURIHost);
        archiveURI.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveURIHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveURI;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorUserID);
        boolean requestorIsIP = AuditMessages.isIP(requestorUserID);
        requestor.setUserIDTypeCode(
                requestorIsIP
                    ? AuditMessages.UserIDTypeCode.NodeID
                    : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(
                requestorIsIP
                    ? AuditMessages.UserTypeCode.Application
                    : AuditMessages.UserTypeCode.Person);
        requestor.setUserIsRequestor(true);
        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestor;
    }
}
