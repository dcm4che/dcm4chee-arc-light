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
 * Portions created by the Initial Developer are Copyright (C) 2016-2019
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

package org.dcm4chee.arc.stgcmt.impl;

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.stgcmt.StgCmtSCP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class StgCmtSCPMDB implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(StgCmtSCPMDB.class);

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private Event<StgCmtContext> stgCmtEvent;

    @Inject
    private QueueManager queueManager;

    @Inject
    private StgCmtSCP stgCmtSCP;

    @Inject
    private StgCmtManager stgCmtMgr;

    @Override
    public void onMessage(Message msg) {
        String msgID = null;
        try {
            msgID = msg.getJMSMessageID();
        } catch (JMSException e) {
            LOG.error("Failed to process {}", msg, e);
        }
        if (queueManager.onProcessingStart(msgID) == null)
            return;
        try {
            String localAET = msg.getStringProperty("LocalAET");
            ApplicationEntity localAE = device.getApplicationEntity(localAET, true);
            ApplicationEntity remoteAE = aeCache.findApplicationEntity(msg.getStringProperty("RemoteAET"));
            Attributes actionInfo = (Attributes) ((ObjectMessage) msg).getObject();
            StgCmtContext ctx = new StgCmtContext(localAE, localAET)
                    .setRemoteAE(remoteAE)
                    .setTransactionUID(actionInfo.getString(Tag.TransactionUID));
            stgCmtMgr.calculateResult(ctx, actionInfo.getSequence(Tag.ReferencedSOPSequence));
            stgCmtEvent.fire(ctx);
            Outcome outcome = stgCmtSCP.sendNEventReport(localAET, remoteAE,
                    removeExtendedEventInfo(ctx.getEventInfo()));
            queueManager.onProcessingSuccessful(msgID, outcome);
        } catch (Throwable e) {
            LOG.warn("Failed to process {}", msg, e);
            queueManager.onProcessingFailed(msgID, e);
        }
    }

    private Attributes removeExtendedEventInfo(Attributes eventInfo) {
        eventInfo.remove(Tag.StudyInstanceUID);
        eventInfo.remove(Tag.PatientName);
        eventInfo.remove(Tag.PatientID);
        eventInfo.remove(Tag.IssuerOfPatientID);
        return eventInfo;
    }
}
