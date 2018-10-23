package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;

import java.net.URI;
import java.util.Calendar;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class ProvideAndRegisterAuditService {

    static AuditInfoBuilder provideRegisterAuditInfo(ExportContext ctx, ArchiveDeviceExtension arcDev) {
        return ctx.getHttpServletRequestInfo() != null
                ? restfulTriggeredProvideRegisterAuditInfo(ctx, arcDev)
                : schedulerTriggeredProvideRegisterAuditInfo(ctx, arcDev);
    }

    private static AuditInfoBuilder restfulTriggeredProvideRegisterAuditInfo(
            ExportContext ctx, ArchiveDeviceExtension arcDev) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        URI destination = ctx.getExporter().getExporterDescriptor().getExportURI();
        return new AuditInfoBuilder.Builder()
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledUserID(httpServletRequestInfo.requestURI)
                .destUserID(destination.toString())
                .destNapID(destinationHost(destination))
                .outcome(outcome(ctx))
                .pIDAndName(ctx.getXDSiManifest(), arcDev)
                .submissionSetUID(ctx.getSubmissionSetUID()).build();
    }

    private static AuditInfoBuilder schedulerTriggeredProvideRegisterAuditInfo(
            ExportContext ctx, ArchiveDeviceExtension arcDev) {
        URI destination = ctx.getExporter().getExporterDescriptor().getExportURI();
        return new AuditInfoBuilder.Builder()
                .callingUserID(arcDev.getDevice().getDeviceName())
                .destUserID(destination.toString())
                .destNapID(destinationHost(destination))
                .outcome(outcome(ctx))
                .pIDAndName(ctx.getXDSiManifest(), arcDev)
                .submissionSetUID(ctx.getSubmissionSetUID()).build();
    }

    private static String destinationHost(URI destination) {
        String schemeSpecificPart = destination.getSchemeSpecificPart();
        return schemeSpecificPart.substring(schemeSpecificPart.indexOf("://") + 3, schemeSpecificPart.lastIndexOf(":"));
    }

    private static String outcome(ExportContext ctx) {
        return ctx.getException() != null ? ctx.getException().getMessage() : null;
    }

    static AuditMessage provideRegisterAuditMsg(
            AuditInfo auditInfo, AuditLogger auditLogger, AuditServiceUtils.EventType eventType, Calendar eventTime) {
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentificationBuilder eventIdentificationBuilder
                = new EventIdentificationBuilder.Builder(
                        eventType.eventID,
                        eventType.eventActionCode,
                        eventTime,
                        outcome != null ? AuditMessages.EventOutcomeIndicator.MinorFailure : AuditMessages.EventOutcomeIndicator.Success)
                        .outcomeDesc(outcome)
                        .eventTypeCode(eventType.eventTypeCode).build();

        ParticipantObjectIdentificationBuilder patientPOI = new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(auditInfo.getField(AuditInfo.P_NAME))
                .build();

        ParticipantObjectIdentificationBuilder submissionSetPOI = new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.SUBMISSION_SET_UID),
                AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Job)
                .build();

        return AuditMessages.createMessage(
                eventIdentificationBuilder,
                activeParticipantBuilders(auditLogger, eventType, auditInfo),
                patientPOI,
                submissionSetPOI);
    }

    private static ActiveParticipantBuilder[] activeParticipantBuilders(
            AuditLogger auditLogger, AuditServiceUtils.EventType eventType, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                .roleIDCode(eventType.destination)
                .build();
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        if (auditInfo.getField(AuditInfo.CALLING_HOST) != null) {
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    auditInfo.getField(AuditInfo.CALLED_USERID),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                    .altUserID(AuditLogger.processID())
                    .roleIDCode(eventType.source)
                    .build();
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                    .isRequester()
                    .build();
        } else
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    getLocalHostName(auditLogger))
                    .altUserID(AuditLogger.processID())
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .isRequester()
                    .roleIDCode(eventType.source)
                    .build();
        return activeParticipantBuilder;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }
}
