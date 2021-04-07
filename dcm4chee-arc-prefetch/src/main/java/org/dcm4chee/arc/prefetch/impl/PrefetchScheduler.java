/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.prefetch.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.net.Socket;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2018
 */
@ApplicationScoped
public class PrefetchScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PrefetchScheduler.class);

    @Inject
    private Device device;

    @Inject
    private CFindSCU findSCU;

    @Inject
    private RetrieveManager retrieveManager;

    public void onHL7Connection(@Observes HL7ConnectionEvent event) {
        if (event.getType() != HL7ConnectionEvent.Type.MESSAGE_PROCESSED || event.getException() != null)
            return;

        UnparsedHL7Message hl7Message = event.getHL7Message();
        HL7Application hl7App = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(hl7Message.msh().getReceivingApplicationWithFacility(), true);
        if (hl7App == null)
            return;

        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (arcHL7App == null || !arcHL7App.hasHL7PrefetchRules())
            return;

        Socket sock = event.getSocket();
        String host = ReverseDNS.hostNameOf(sock.getInetAddress());
        HL7Fields hl7Fields = new HL7Fields(hl7Message, hl7App.getHL7DefaultCharacterSet());
        Calendar now = Calendar.getInstance();
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        arcHL7App.hl7PrefetchRules()
                .filter(rule -> rule.match(host, hl7Fields))
                .forEach(rule -> prefetch(sock, hl7Fields, rule, arcdev, now, hl7Message));
    }

    private void prefetch(Socket sock, HL7Fields hl7Fields, HL7PrefetchRule rule, ArchiveDeviceExtension arcdev,
                          Calendar now, UnparsedHL7Message hl7Message) {
        try {
            LOG.info("{}: Apply {}", sock, rule);
            Date notRetrievedAfter = new Date(
                    now.getTimeInMillis() - rule.getSuppressDuplicateRetrieveInterval().getSeconds() * 1000L);
            Calendar hl7PrefetchDateTime = hl7PrefetchDateTime(hl7Fields, rule, now, hl7Message);
            Date scheduledTime = ScheduleExpression.ceil(hl7PrefetchDateTime, rule.getSchedules()).getTime();
            String cx = hl7Fields.get("PID-3", null);
            IDWithIssuer idWithIssuer = idWithIssuer(rule, cx);
            if (idWithIssuer == null) {
                LOG.info("None of the qualified patient identifier pairs in PID-3 {} match with configured " +
                         "HL7 Prefetch Rule[name={}, PrefetchForAssigningAuthorityOfPatientID={}]",
                        cx, rule.getCommonName(), rule.getPrefetchForAssigningAuthorityOfPatientID());
                return;
            }
            IDWithIssuer pid = rule.ignoreAssigningAuthorityOfPatientID(idWithIssuer);
            String batchID = rule.getCommonName() + '[' + pid + ']';
            if (rule.getEntitySelectors().length == 0) {
                prefetch(pid, batchID, new Attributes(0), -1,
                        rule, arcdev, scheduledTime, notRetrievedAfter);
            } else {
                for (EntitySelector selector : rule.getEntitySelectors()) {
                    prefetch(pid, batchID, selector.getQueryKeys(hl7Fields), selector.getNumberOfPriors(),
                            rule, arcdev, scheduledTime, notRetrievedAfter);
                }
            }
        } catch (Exception e) {
            LOG.warn("{}: Failed to apply {}:\n", sock, rule, e);
        }
    }

    private IDWithIssuer idWithIssuer(HL7PrefetchRule rule, String cx) {
        Issuer prefetchForAssigningAuthorityOfPatientID = rule.getPrefetchForAssigningAuthorityOfPatientID();
        if (prefetchForAssigningAuthorityOfPatientID == null)
            return new IDWithIssuer(cx);

        for (String cx1 : cx.split("~")) {
            IDWithIssuer idWithIssuer = new IDWithIssuer(cx1);
            if (prefetchForAssigningAuthorityOfPatientID.equals(idWithIssuer.getIssuer()))
                return idWithIssuer;
        }
        return null;
    }

    private void prefetch(IDWithIssuer pid, String batchID, Attributes queryKeys, int numberOfPriors,
            HL7PrefetchRule rule, ArchiveDeviceExtension arcdev, Date scheduledDate, Date notRetrievedAfter)
            throws Exception {
        Attributes keys = new Attributes(queryKeys.size() + 4);
        keys.addAll(queryKeys);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        if (keys.containsValue(Tag.StudyInstanceUID)) {
            createRetrieveTasks(keys, rule, batchID, scheduledDate, notRetrievedAfter);
            return;
        }
        keys.setString(Tag.PatientID, VR.LO, pid.getID());
        Issuer issuer = pid.getIssuer();
        if (issuer != null)
            issuer.toIssuerOfPatientID(keys);
        if (!keys.contains(Tag.StudyDate))
            keys.setNull(Tag.StudyDate, VR.DA);
        keys.setNull(Tag.StudyInstanceUID, VR.UI);
        ApplicationEntity localAE = arcdev.getDevice().getApplicationEntity(rule.getAETitle(), true);
        List<Attributes> matches = findSCU.find(localAE, rule.getPrefetchCFindSCP(),
                EnumSet.of(QueryOption.DATETIME), Priority.NORMAL, keys);
        if (numberOfPriors > 0 && matches.size() > numberOfPriors) {
            matches.sort(Comparator.comparing(match -> match.getString(Tag.StudyDate, "")));
            do {
                matches.remove(0);
            } while (matches.size() > numberOfPriors);
        }
        for (Attributes match : matches) {
            createRetrieveTasks(match, rule, batchID, scheduledDate, notRetrievedAfter);
        }
    }

    private void createRetrieveTasks(Attributes keys, HL7PrefetchRule rule, String batchID,
            Date scheduledDate, Date notRetrievedAfter) {
        for (String destination : rule.getPrefetchCStoreSCPs()) {
            createRetrieveTask(keys, rule, batchID, scheduledDate, notRetrievedAfter, destination);
        }
    }

    private void createRetrieveTask(Attributes keys, HL7PrefetchRule rule, String batchID,
            Date scheduledDate, Date notRetrievedAfter, String destination) {
        ExternalRetrieveContext ctx = new ExternalRetrieveContext()
                .setDeviceName(device.getDeviceName())
                .setQueueName(rule.getQueueName())
                .setBatchID(batchID)
                .setLocalAET(rule.getAETitle())
                .setFindSCP(rule.getPrefetchCFindSCP())
                .setRemoteAET(rule.getPrefetchCMoveSCP())
                .setDestinationAET(destination)
                .setPriority(rule.getPriority())
                .setScheduledTime(scheduledDate)
                .setKeys(new Attributes(keys, Tag.QueryRetrieveLevel, Tag.StudyInstanceUID));
        retrieveManager.createRetrieveTask(ctx, notRetrievedAfter);
    }

    private Calendar hl7PrefetchDateTime(
            HL7Fields hl7Fields, HL7PrefetchRule rule, Calendar now, UnparsedHL7Message hl7Message) {
        String prefetchDateTimeField = rule.getPrefetchDateTimeField();
        if (prefetchDateTimeField == null)
            return now;

        String value = hl7Fields.get(prefetchDateTimeField, null);
        HL7Segment msh = hl7Message.msh();
        if (value == null || value.length() < 8) {
            LOG.info("Configured PrefetchDateTimeField {} either absent or imprecise in HL7 message[type={}, controlID={}] : {} ",
                    prefetchDateTimeField, msh.getMessageType(), msh.getMessageControlID(), value);
            return now;
        }

        DatePrecision precision = new DatePrecision();
        Date date = DateUtils.parseDT(null, value, precision);
        if (precision.lastField <= 2) {
            LOG.info("Configured PrefetchDateTimeField {} imprecise in HL7 message[type={}, controlID={}] : {} ",
                    prefetchDateTimeField, msh.getMessageType(), msh.getMessageControlID(), value);
            return now;
        }

        Duration prefetchInAdvance = rule.getPrefetchInAdvance();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date.getTime()
                - (prefetchInAdvance != null ? prefetchInAdvance.getSeconds() * 1000L : 0));
        return cal;
    }
}
