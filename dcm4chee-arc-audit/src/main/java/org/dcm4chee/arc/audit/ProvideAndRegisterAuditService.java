package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.*;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;

import java.net.URI;
import java.nio.file.Path;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
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
                .calledUserID(requestURLWithQueryParams(httpServletRequestInfo))
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

    private static String requestURLWithQueryParams(HttpServletRequestInfo httpServletRequestInfo) {
        return httpServletRequestInfo.queryString == null
                ? httpServletRequestInfo.requestURI
                : httpServletRequestInfo.requestURI + "?" + httpServletRequestInfo.queryString;
    }

    private static String destinationHost(URI destination) {
        String schemeSpecificPart = destination.getSchemeSpecificPart();
        return schemeSpecificPart.substring(schemeSpecificPart.indexOf("://") + 3, schemeSpecificPart.lastIndexOf(":"));
    }

    private static String outcome(ExportContext ctx) {
        return ctx.getException() != null ? ctx.getException().getMessage() : null;
    }

    static AuditMessage provideRegisterAuditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(auditLogger, eventType, auditInfo),
                ParticipantObjectID.submissionSetParticipants(auditInfo));
    }

    private static ActiveParticipant[] activeParticipants(
            AuditLogger auditLogger, AuditUtils.EventType eventType, AuditInfo auditInfo) {
        ActiveParticipant[] activeParticipants = new ActiveParticipant[3];
        activeParticipants[0] = new ActiveParticipantBuilder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                .roleIDCode(eventType.destination)
                .build();
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        if (auditInfo.getField(AuditInfo.CALLING_HOST) != null) {
            activeParticipants[1] = new ActiveParticipantBuilder(
                    auditInfo.getField(AuditInfo.CALLED_USERID),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                    .altUserID(AuditLogger.processID())
                    .roleIDCode(eventType.source)
                    .build();
            activeParticipants[2] = new ActiveParticipantBuilder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                    .isRequester()
                    .build();
        } else
            activeParticipants[1] = new ActiveParticipantBuilder(
                    callingUserID,
                    getLocalHostName(auditLogger))
                    .altUserID(AuditLogger.processID())
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .isRequester()
                    .roleIDCode(eventType.source)
                    .build();
        return activeParticipants;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }
}
