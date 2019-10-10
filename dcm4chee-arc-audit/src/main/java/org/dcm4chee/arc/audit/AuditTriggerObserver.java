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
 * Portions created by the Initial Developer are Copyright (C) 2017
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

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.SoftwareConfiguration;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveWADO;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveStart;
import org.dcm4chee.arc.study.StudyMgtContext;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditTriggerObserver {
    @Inject
    private AuditService auditService;

    @Inject
    private Device device;

    public void onArchiveServiceEvent(@Observes ArchiveServiceEvent event) {
        if (event.getType() == ArchiveServiceEvent.Type.RELOADED)
            return;

        if (deviceHasAuditLoggers())
            auditService.spoolApplicationActivity(event);
    }

    public void onStore(@Observes StoreContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolStoreEvent(ctx);
    }

    public void onQuery(@Observes QueryContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolQuery(ctx);
    }

    public void onRetrieveStart(@Observes @RetrieveStart RetrieveContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolRetrieve(AuditUtils.EventType.RTRV_BEGIN, ctx);
    }

    public void onRetrieveEnd(@Observes @RetrieveEnd RetrieveContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolRetrieve(AuditUtils.EventType.RTRV___TRF, ctx);
    }

    public void onRetrieveWADO(@Observes @RetrieveWADO RetrieveContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolRetrieveWADO(ctx);
    }

    public void onStudyDeleted(@Observes StudyDeleteContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolStudyDeleted(ctx);
    }

    public void onExport(@Observes ExportContext ctx) {
        if (ctx.getXDSiManifest() == null)
            return;

        if (deviceHasAuditLoggers())
            auditService.spoolProvideAndRegister(ctx);
    }

    public void onConnection(@Observes ConnectionEvent event) {
        if (deviceHasAuditLoggers())
            switch (event.getType()) {
                case ESTABLISHED:
                case REJECTED_BLACKLISTED:
                case ACCEPTED:
                    break;
                case FAILED:
                case REJECTED:
                    auditService.spoolConnectionFailure(event);
                    break;
            }
    }

    public void onPatientUpdate(@Observes PatientMgtContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolPatientRecord(ctx);
    }

    public void onProcedureUpdate(@Observes ProcedureContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolProcedureRecord(ctx);
    }

    public void onStudyUpdate(@Observes StudyMgtContext ctx) {
        if (ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create))
            return;

        if (deviceHasAuditLoggers())
            auditService.spoolStudyRecord(ctx);
    }

    public void onStorageCommit(@Observes StgCmtContext stgCmtContext) {
        if (deviceHasAuditLoggers())
            auditService.spoolStgCmt(stgCmtContext);
    }

    public void onRejectionNoteSent(@Observes RejectionNoteSent rejectionNoteSent) {
        if (deviceHasAuditLoggers())
            auditService.spoolExternalRejection(rejectionNoteSent);
    }

    public void onExternalRetrieve(@Observes ExternalRetrieveContext ctx) {
        if (deviceHasAuditLoggers())
            auditService.spoolExternalRetrieve(ctx);
    }

    public void onSoftwareConfiguration(@Observes SoftwareConfiguration softwareConfiguration) {
        if (deviceHasAuditLoggers())
            auditService.spoolSoftwareConfiguration(softwareConfiguration);
    }

    public void onQueueMessageEvent(@Observes QueueMessageEvent queueMsgEvent) {
        if (deviceHasAuditLoggers())
            auditService.spoolQueueMessageEvent(queueMsgEvent);
    }

    public void onBulkQueueMessageEvent(@Observes BulkQueueMessageEvent bulkQueueMsgEvent) {
        if (deviceHasAuditLoggers())
            auditService.spoolBulkQueueMessageEvent(bulkQueueMsgEvent);
    }

    public void onHL7Message(@Observes HL7ConnectionEvent hl7ConnectionEvent) {
        if (deviceHasAuditLoggers())
            auditService.spoolHL7Message(hl7ConnectionEvent);
    }

    public void onAssociation(@Observes AssociationEvent associationEvent) {
        if (deviceHasAuditLoggers()) {
            switch (associationEvent.getType()) {
                case ACCEPTED:
                case ESTABLISHED:
                    break;
                case FAILED:
                case REJECTED:
                    auditService.spoolAssociationFailure(associationEvent);
                    break;
            }
        }
    }

    private boolean deviceHasAuditLoggers() {
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        return ext != null && !ext.getAuditLoggers().isEmpty();
    }
}
