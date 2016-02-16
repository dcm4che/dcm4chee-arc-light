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
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.action.GetPropertyAction;

import static java.security.AccessController.doPrivileged;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final String tmpdir = doPrivileged(new GetPropertyAction("java.io.tmpdir"));

    @Inject
    private Device device;

    @Inject
    private AuditMessagesWriter auditMessagesWriter;

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
        HttpServletRequest request = event.getRequest();
        auditMessagesWriter.auditApplicationActivity(log(), eventTypeCode, request, device);
    }

    public void onStore(@Observes StoreContext ctx) {
        if ((null != ctx.getRejectionNote())) {
            auditMessagesWriter.auditInstancesDeleted(ctx, log());
            return;
        }
        if (ctx.getLocation() == null)
            return;

        StoreSession session = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        Path file = dir.resolve(
                    "onstore-" + session.getCallingAET() + '-' + session.getCalledAET() + '-' + ctx.getStudyInstanceUID());
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.append(session.getRemoteHostName())
                            .append('\\').append(session.getCallingAET())
                            .append('\\').append(session.getCalledAET())
                            .append('\\').append(ctx.getStudyInstanceUID())
                            .append('\\').append(attrs.getString(Tag.AccessionNumber,""))
                            .append('\\').append(attrs.getString(Tag.PatientID,""))
                            .append('\\').append(attrs.getString(Tag.PatientName, ""));
                    writer.newLine();
                }
                writer.append(ctx.getSopClassUID())
                        .append('\\').append(ctx.getSopInstanceUID())
                        .append('\\').append(StringUtils.maskNull(ctx.getMppsInstanceUID(), ""));
                Sequence reqAttrs = attrs.getSequence(Tag.RequestAttributesSequence);
                if (reqAttrs != null)
                    for (Attributes reqAttr : reqAttrs) {
                        String accno = reqAttr.getString(Tag.AccessionNumber);
                        if (accno != null)
                            writer.append('\\').append(accno);
                    }
                writer.newLine();
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (IOException e) {
            LOG.warn("Failed write to Audit Spool File - {} ", file, e);
        }
    }

    public void aggregateAuditMessage(Path path) {
        String[] header;
        HashSet<String> accNos = new HashSet<>();
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, List<String>> sopClassMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            header = StringUtils.split(reader.readLine(), '\\');
            String line;
            while ((line = reader.readLine()) != null) {
                String[] uids = StringUtils.split(line, '\\');
                List<String> iuids = sopClassMap.get(uids[0]);
                if (iuids == null)
                    sopClassMap.put(uids[0], iuids = new ArrayList<String>());
                iuids.add(uids[1]);
                mppsUIDs.add(uids[2]);
                for (int i = 3; i < uids.length; i++)
                    accNos.add(uids[3]);
            }
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", path, e);
            return;
        }
        accNos.add(header[4]);
        accNos.remove("");
        mppsUIDs.remove("");
        Calendar eventTime = log().timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", path, e);
        }
        auditMessagesWriter.auditStudyStored(log(), header, accNos, mppsUIDs, sopClassMap, eventTime, device);
        try {
            Files.delete(path);
        } catch (IOException e) {
            LOG.warn("Failed to delete Audit Spool File - {}", path, e);
        }
    }

    public void onConnection(@Observes ConnectionEvent event) {
        switch (event.getType()) {
            case ESTABLISHED:
                onConnectionEstablished(event.getConnection(), event.getRemoteConnection(), event.getSocket());
                break;
            case FAILED:
                onConnectionFailed(event.getConnection(), event.getRemoteConnection(), event.getSocket(),
                        event.getException());
                break;
            case REJECTED:
                onConnectionRejected(event.getConnection(), event.getSocket(), event.getException());
                break;
            case REJECTED_BLACKLISTED:
                onConnectionRejectedBlacklisted(event.getConnection(), event.getSocket());
                break;
            case ACCEPTED:
                onConnectionAccepted(event.getConnection(), event.getSocket());
                break;
        }
    }

    private void onConnectionEstablished(Connection conn, Connection remoteConn, Socket s) {
    }

    private void onConnectionFailed(Connection conn, Connection remoteConn, Socket s, Throwable e) {
    }

    private void onConnectionRejectedBlacklisted(Connection conn, Socket s) {
    }

    private void onConnectionRejected(Connection conn, Socket s, Throwable e) {
        auditMessagesWriter.auditConnectionRejected(log(), s, e);
    }

    private void onConnectionAccepted(Connection conn, Socket s) {
    }
}
