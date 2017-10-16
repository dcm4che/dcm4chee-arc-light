/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.retrieve.mgt.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2017
 */
@ApplicationScoped
public class RetrieveManagerImpl implements RetrieveManager {
    private static final Logger LOG = LoggerFactory.getLogger(RetrieveManagerImpl.class);

    @Inject
    private Device device;

    @Inject
    private Event<ExternalRetrieveContext> externalRetrieve;

    @Inject
    private CMoveSCU moveSCU;

    @Inject
    private RetrieveManagerEJB ejb;

    @Override
    public Outcome cmove(int priority, ExternalRetrieveContext ctx, QueueMessage queueMessage) throws Exception {
        ApplicationEntity localAE = device.getApplicationEntity(ctx.getLocalAET(), true);
        Association as = moveSCU.openAssociation(localAE, ctx.getRemoteAET());
        ctx.setRemoteHostName(as.getSocket().getInetAddress().getHostName());
        try {
            ejb.resetRetrieveTask(queueMessage);
            final DimseRSP rsp = moveSCU.cmove(as, priority, ctx.getDestinationAET(), ctx.getKeys());
            while (rsp.next()) {
                ejb.updateRetrieveTask(queueMessage, rsp.getCommand());
            }
            Attributes cmd = rsp.getCommand();
            int status = cmd.getInt(Tag.Status, -1);
            if (status == Status.Success || status == Status.OneOrMoreFailures) {
                externalRetrieve.fire(ctx.setResponse(cmd));
                return new Outcome(
                        status == Status.Success ? QueueMessage.Status.COMPLETED : QueueMessage.Status.WARNING,
                        toOutcomeMessage(
                                ctx.getRemoteAET(),
                                ctx.getDestinationAET(),
                                ctx.getKeys(),
                                cmd));
            }
            throw new DicomServiceException(status, cmd.getString(Tag.ErrorComment));
        } finally {
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
        }
    }

    private String toOutcomeMessage(String remoteAET, String destAET, Attributes keys, Attributes rsp) {
        int completed = rsp.getInt(Tag.NumberOfCompletedSuboperations, 0);
        int warning = rsp.getInt(Tag.NumberOfWarningSuboperations, 0);
        int failed = rsp.getInt(Tag.NumberOfFailedSuboperations, 0);
        StringBuilder sb = new StringBuilder(256)
                .append("Export ")
                .append(keys.getString(Tag.QueryRetrieveLevel))
                .append("[suid:")
                .append(keys.getString(Tag.StudyInstanceUID))
                .append("] from ")
                .append(remoteAET)
                .append(" to ")
                .append(destAET)
                .append(" - completed:")
                .append(completed);
        if (warning > 0)
            sb.append(", ").append("warning:").append(warning);
        if (failed > 0)
            sb.append(", ").append("failed:").append(failed);
        return sb.toString();
    }

    @Override
    public void scheduleRetrieveTask(int priority, ExternalRetrieveContext ctx) {
        ejb.scheduleRetrieveTask(device, priority, ctx);
    }

    @Override
    public List<RetrieveTask> search(
            String deviceName,
            String localAET,
            String remoteAET,
            String destinationAET,
            String studyUID,
            Date updatedBefore,
            QueueMessage.Status status,
            int offset,
            int limit) {
        return ejb.search(deviceName, localAET, remoteAET, destinationAET, studyUID, updatedBefore, status,
                offset, limit);
    }

    @Override
    public boolean deleteRetrieveTask(Long pk) {
        return ejb.deleteRetrieveTask(pk);
    }

    @Override
    public boolean cancelProcessing(Long pk) throws IllegalTaskStateException {
        return ejb.cancelProcessing(pk);
    }

    @Override
    public boolean rescheduleRetrieveTask(Long pk) throws IllegalTaskStateException {
        return ejb.rescheduleRetrieveTask(pk);
    }
}
