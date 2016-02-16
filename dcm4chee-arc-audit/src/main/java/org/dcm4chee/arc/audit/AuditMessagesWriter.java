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
 * Portions created by the Initial Developer are Copyright (C) 2013
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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.http.HttpServletRequest;
import java.net.Socket;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditMessagesWriter {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    public void emitAuditMessage(Calendar timestamp, AuditMessage msg, AuditLogger log) {
        try {
            log.write(timestamp, msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }

    public void auditApplicationActivity(AuditLogger log, EventTypeCode eventTypeCode, HttpServletRequest request, Device device) {
        Calendar timestamp = log.timeStamp();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.ApplicationActivity);
        ei.getEventTypeCode().add(eventTypeCode);
        ei.setEventActionCode(AuditMessages.EventActionCode.Execute);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        ei.setEventDateTime(timestamp);
        msg.setEventIdentification(ei);
        ActiveParticipant apApplication = new ActiveParticipant();
        apApplication.getRoleIDCode().add(AuditMessages.RoleIDCode.Application);
        apApplication.setUserID(device.getDeviceName());
        StringBuilder aets = new StringBuilder();
        for (ApplicationEntity ae : device.getApplicationEntities()) {
            if (aets.length() == 0)
                aets.append("AETITLE=");
            else
                aets.append(';');
            aets.append(ae.getAETitle());
        }
        apApplication.setAlternativeUserID(aets.toString());
        apApplication.setUserIsRequestor(false);
        msg.getActiveParticipant().add(apApplication);

        if (request != null) {
            ActiveParticipant apUser = new ActiveParticipant();
            apUser.getRoleIDCode().add(AuditMessages.RoleIDCode.ApplicationLauncher);
            String remoteUser = request.getRemoteUser();
            apUser.setUserID(remoteUser != null ? remoteUser : request.getRemoteAddr());
            apUser.setNetworkAccessPointTypeCode(AuditMessages.NetworkAccessPointTypeCode.IPAddress);
            apUser.setNetworkAccessPointID(request.getRemoteAddr());
            apUser.setUserIsRequestor(true);
            msg.getActiveParticipant().add(apUser);
        }
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        emitAuditMessage(timestamp, msg, log);
    }

    public void auditStudyStored(AuditLogger log, String[] header, HashSet<String> accNos,
                                 HashSet<String> mppsUIDs, HashMap<String, List<String>> sopClassMap,
                                 Calendar eventTime, Device device) {
        Calendar timestamp = log.timeStamp();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.DICOMInstancesTransferred);
        ei.setEventActionCode(AuditMessages.EventActionCode.Create);
        ActiveParticipant apSender = new ActiveParticipant();
        apSender.setUserID(header[0]);
        apSender.setAlternativeUserID("AETITLE=" + header[1]);
        apSender.setUserIsRequestor(true);
        apSender.getRoleIDCode().add(AuditMessages.RoleIDCode.Source);
        apSender.setNetworkAccessPointID(header[0]);
        apSender.setNetworkAccessPointTypeCode(AuditMessages.NetworkAccessPointTypeCode.IPAddress);
        msg.getActiveParticipant().add(apSender);
        ActiveParticipant apReceiver = new ActiveParticipant();
        apReceiver.setUserID(device.getDeviceName());
        apReceiver.setAlternativeUserID("AETITLE=" + header[2]);
        apReceiver.setUserIsRequestor(false);
        apReceiver.getRoleIDCode().add(AuditMessages.RoleIDCode.Destination);
        msg.getActiveParticipant().add(apReceiver);
        ei.setEventDateTime(eventTime);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        msg.setEventIdentification(ei);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        ParticipantObjectIdentification poiStudy = new ParticipantObjectIdentification();
        poiStudy.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poiStudy.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        poiStudy.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        poiStudy.setParticipantObjectID(header[3]);
        ParticipantObjectDescriptionType poiStudyDesc = new ParticipantObjectDescriptionType();
        for (String accNo : accNos)
            poiStudyDesc.getAccession().add(AuditMessages.createAccession(accNo));
        for (String mppsUID : mppsUIDs)
            poiStudyDesc.getMPPS().add(AuditMessages.createMPPS(mppsUID));
        for (Map.Entry<String, List<String>> entry : sopClassMap.entrySet())
            poiStudyDesc.getSOPClass().add(AuditMessages.createSOPClass(entry.getKey(), entry.getValue().size()));
        poiStudy.setParticipantObjectDescriptionType(poiStudyDesc);
        msg.getParticipantObjectIdentification().add(poiStudy);
        ParticipantObjectIdentification poiPatient = new ParticipantObjectIdentification();
        poiPatient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        poiPatient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        poiPatient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        poiPatient.setParticipantObjectID(StringUtils.maskEmpty(header[5], "<none>"));
        poiPatient.setParticipantObjectName(StringUtils.maskEmpty(header[6], null));
        msg.getParticipantObjectIdentification().add(poiPatient);
        emitAuditMessage(timestamp, msg, log);
    }

    public void auditInstancesDeleted(StoreContext ctx, AuditLogger log) {
        Calendar timestamp = log.timeStamp();
        RejectionNote rn = ctx.getRejectionNote();
        Attributes attrs = ctx.getAttributes();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.DICOMInstancesAccessed);
        ei.setEventActionCode(AuditMessages.EventActionCode.Delete);
        ei.setEventDateTime(timestamp);
        if (null != ctx.getException()) {
            ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.MinorFailure);
            ei.setEventOutcomeDescription(rn.getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage());
        } else {
            ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
            ei.setEventOutcomeDescription(rn.getRejectionNoteCode().getCodeMeaning());
        }
        msg.setEventIdentification(ei);
        ActiveParticipant ap = new ActiveParticipant();
        ap.setUserID(ctx.getStoreSession().getRemoteHostName());
        ap.setUserIsRequestor(true);
        msg.getActiveParticipant().add(ap);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        ParticipantObjectIdentification poiStudy = new ParticipantObjectIdentification();
        poiStudy.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poiStudy.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        poiStudy.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        poiStudy.setParticipantObjectID(ctx.getStudyInstanceUID());
        ParticipantObjectDescriptionType poiStudyDesc = new ParticipantObjectDescriptionType();
        Sequence currentRequestedProcedureEvidenceSequences = attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (currentRequestedProcedureEvidenceSequences != null) {
            for (Attributes currentRequestedProcedureEvidenceSequence : currentRequestedProcedureEvidenceSequences ) {
                Sequence referencedSeriesSequences = currentRequestedProcedureEvidenceSequence.getSequence(Tag.ReferencedSeriesSequence);
                if (referencedSeriesSequences != null) {
                    for (Attributes referencedSeriesSequence : referencedSeriesSequences) {
                        Sequence referencedSOPSequences = referencedSeriesSequence.getSequence(Tag.ReferencedSOPSequence);
                        if (referencedSOPSequences != null) {
                            for (Attributes referencedSOPSequence : referencedSOPSequences) {
                                poiStudyDesc.getSOPClass().add((AuditMessages.createSOPClass(referencedSOPSequence.getString(Tag.ReferencedSOPClassUID), referencedSOPSequences.size())));
                            }
                        }
                    }
                }
            }
        }
        poiStudy.setParticipantObjectDescriptionType(poiStudyDesc);
        msg.getParticipantObjectIdentification().add(poiStudy);
        ParticipantObjectIdentification poiPatient = new ParticipantObjectIdentification();
        poiPatient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        poiPatient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        poiPatient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        poiPatient.setParticipantObjectID(attrs.getString(Tag.PatientID, ""));
        poiPatient.setParticipantObjectName(attrs.getString(Tag.PatientName, ""));
        msg.getParticipantObjectIdentification().add(poiPatient);
        emitAuditMessage(timestamp, msg, log);
    }

    public void auditConnectionRejected(AuditLogger log, Socket s, Throwable e) {
        Calendar timestamp = log.timeStamp();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.SecurityAlert);
        ei.setEventActionCode(AuditMessages.EventActionCode.Execute);
        ei.setEventDateTime(timestamp);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.MinorFailure);
        ei.getEventTypeCode().add(AuditMessages.EventTypeCode.NodeAuthentication);
        msg.setEventIdentification(ei);
        ActiveParticipant apReporter = new ActiveParticipant();
        apReporter.setUserID(s.getLocalSocketAddress().toString());
        apReporter.setUserIsRequestor(false);
        msg.getActiveParticipant().add(apReporter);
        ActiveParticipant apPerformer = new ActiveParticipant();
        apPerformer.setUserID(s.getRemoteSocketAddress().toString());
        apPerformer.setUserIsRequestor(true);
        msg.getActiveParticipant().add(apPerformer);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        ParticipantObjectIdentification poi = new ParticipantObjectIdentification();
        poi.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poi.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.NodeID);
        poi.setParticipantObjectID(s.getRemoteSocketAddress().toString());
        poi.setParticipantObjectDescription(e.getMessage());
        msg.getParticipantObjectIdentification().add(poi);
        emitAuditMessage(timestamp, msg, log);
    }
}
