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
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Calendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    @Inject
    private Device device;

    private AuditLogger log() {
        return device.getDeviceExtension(AuditLogger.class);
    }

    public void onArchiveServiceEvent(@Observes ArchiveServiceEvent event) {
        EventTypeCode eventTypeCode = null;
        switch (event.getType()) {
            case STARTED:
                eventTypeCode = AuditMessages.EventTypeCode.ApplicationStart;
                break;
            case STOPPED:
                eventTypeCode = AuditMessages.EventTypeCode.ApplicationStop;
                break;
            case RELOADED:
                return;
        }

        Calendar timestamp = log().timeStamp();
        HttpServletRequest request = event.getRequest();
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
        AuditSourceIdentification asi = new AuditSourceIdentification();
        asi.setAuditSourceID(device.getDeviceName());
        msg.getAuditSourceIdentification().add(asi);
        emitAuditMessage(timestamp, msg);
    }

    private void emitAuditMessage(Calendar timestamp, AuditMessage msg) {
        try {
            log().write(timestamp, msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }

    public void onStore(@Observes StoreContext ctx) {
        StoreSession session = ctx.getStoreSession();
        String hostname = session.getRemoteHostName();
        String sendingAET = session.getCallingAET();
        String receivingAET = session.getCalledAET();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String auditSpoolDir = arcDev.getAuditSpoolDirectory();
        String studyFile = ctx.getStudyInstanceUID() + ".txt";
        if (auditSpoolDir == null)
            return;

        Path dir = Paths.get(StringUtils.replaceSystemProperties(auditSpoolDir));
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectory(dir);
            } catch (Exception e) {
                LOG.warn("Failed to create audit-spool directory " + e);
            }
        } else {
            Path filePath = dir.resolve(studyFile);
            if (!Files.exists(filePath, new LinkOption[]{LinkOption.NOFOLLOW_LINKS})) {
                try {
                    Files.createFile(filePath);
                    try(BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.APPEND)){
                        writer.append("Hostname is :" + hostname);
                        writer.newLine();
                        writer.append("Sending AE Title is :" + sendingAET);
                        writer.newLine();
                        writer.append("Receiving AE Title is :" + receivingAET);
                        writer.newLine();
                        writer.append("Study Instance UID is :" + ctx.getStudyInstanceUID());
                        writer.newLine();
                        writer.flush();
                    } catch (Exception e) {
                        LOG.warn("Failed to append to file: " + e);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to create file: " + e);
                }
            }
            try(BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.APPEND)){
                writer.append("SOP Class UID is :" + ctx.getSopClassUID());
                writer.newLine();
                writer.append("SOP instance UID is :" + ctx.getSopInstanceUID());
                writer.newLine();
                writer.flush();
            } catch (Exception e) {
                LOG.warn("Failed to append to file: " + e);
            }
        }
    }

    public void aggregateAuditMessage(Path path) {
        System.out.println("path received is " + path);
        String sender = "";
        String receiver = "";
        String remoteHostAddress = "";
        String studyInstanceUID = "";
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ArrayList lines = new ArrayList();
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.startsWith("Hostname"))
                    remoteHostAddress = line.substring(line.indexOf(":")+1);
                if (line.startsWith("Sending"))
                    sender = line.substring(line.indexOf(":")+1);
                if (line.startsWith("Receiving"))
                    receiver = line.substring(line.indexOf(":")+1);
                if (line.startsWith("Study"))
                    studyInstanceUID = line.substring(line.indexOf(":")+1);
                lines.add(line);
            }
        } catch (Exception e) {
            LOG.warn("Exception caught while reading the file : " + e);
        }
        AuditMessage msg = new AuditMessage();
        Calendar timestamp = log().timeStamp();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.DICOMInstancesTransferred);
        ei.setEventActionCode(AuditMessages.EventActionCode.Create);
        ei.setEventDateTime(timestamp);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        msg.setEventIdentification(ei);
        ActiveParticipant apSender = new ActiveParticipant();
        apSender.setUserID(sender);
        apSender.setUserIsRequestor(true);
        apSender.getRoleIDCode().add(AuditMessages.RoleIDCode.Source);
        apSender.setNetworkAccessPointID(remoteHostAddress);
        apSender.setNetworkAccessPointTypeCode(AuditMessages.NetworkAccessPointTypeCode.IPAddress);
        msg.getActiveParticipant().add(apSender);
        ActiveParticipant apReceiver = new ActiveParticipant();
        apReceiver.setUserID(receiver);
        apReceiver.setUserIsRequestor(false);
        apReceiver.getRoleIDCode().add(AuditMessages.RoleIDCode.Destination);
        msg.getActiveParticipant().add(apReceiver);
        AuditSourceIdentification asi = new AuditSourceIdentification();
        asi.setAuditSourceID(device.getDeviceName());
        msg.getAuditSourceIdentification().add(asi);
        ParticipantObjectIdentification poiStudy = new ParticipantObjectIdentification();
        poiStudy.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poiStudy.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        poiStudy.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        poiStudy.setParticipantObjectID(studyInstanceUID);
//        poiStudy.getParticipantObjectDescriptionType().getSOPClass().add(sopclass);
        msg.getParticipantObjectIdentification().add(poiStudy);
//        ParticipantObjectIdentification poiPatient = new ParticipantObjectIdentification();           #discuss
        emitAuditMessage(timestamp, msg);
    }
}
