package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.*;
import org.dcm4che3.net.Association;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.AssociationEvent;
import java.util.Calendar;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class AssociationEventsAuditService {

    static AuditInfoBuilder associationFailureAuditInfo(AssociationEvent associationEvent) {
        return associationEvent.getType() == AssociationEvent.Type.FAILED
                ? associationFailedAuditInfo(associationEvent) : associationRejectAuditInfo(associationEvent);
    }

    private static AuditInfoBuilder associationFailedAuditInfo(AssociationEvent associationEvent) {
        Association association = associationEvent.getAssociation();
        return new AuditInfoBuilder.Builder()
                .callingUserID(association.getLocalAET())
                .callingHost(association.getConnection().getHostname())
                .calledUserID(association.getRemoteAET())
                .calledHost(ReverseDNS.hostNameOf(association.getSocket().getInetAddress()))
                .outcome(associationEvent.getException().getMessage())
                .build();
    }

    private static AuditInfoBuilder associationRejectAuditInfo(AssociationEvent associationEvent) {
        Association association = associationEvent.getAssociation();
        return new AuditInfoBuilder.Builder()
                .callingUserID(association.getRemoteAET())
                .callingHost(ReverseDNS.hostNameOf(association.getSocket().getInetAddress()))
                .calledUserID(association.getLocalAET())
                .calledHost(association.getConnection().getHostname())
                .outcome(associationEvent.getException().getMessage())
                .build();
    }

    static AuditMessage associationFailureAuditMsg(
            AuditInfo auditInfo, AuditServiceUtils.EventType eventType, Calendar eventTime) {
        EventIdentificationBuilder eventIdentificationBuilder = new EventIdentificationBuilder.Builder(
                eventType.eventID, eventType.eventActionCode, eventTime, AuditMessages.EventOutcomeIndicator.MinorFailure)
                .outcomeDesc(auditInfo.getField(AuditInfo.OUTCOME))
                .eventTypeCode(eventType.eventTypeCode)
                .build();

        ActiveParticipantBuilder[] activeParticipantBuilders = new ActiveParticipantBuilder[2];
        activeParticipantBuilders[0] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.CALLING_USERID), auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .isRequester()
                .build();
        activeParticipantBuilders[1] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.CALLED_USERID), auditInfo.getField(AuditInfo.CALLED_HOST))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .build();

        return AuditMessages.createMessage(eventIdentificationBuilder, activeParticipantBuilders);
    }
}
