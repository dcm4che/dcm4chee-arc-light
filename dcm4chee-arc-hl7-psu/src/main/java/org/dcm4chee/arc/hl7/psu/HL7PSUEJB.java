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

package org.dcm4chee.arc.hl7.psu;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.HL7PSUTask;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2017
 */
@Stateless
public class HL7PSUEJB {
    private static final Logger LOG = LoggerFactory.getLogger(HL7PSUEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private HL7Sender hl7Sender;

    public void createHL7PSUTaskForMPPS(ArchiveAEExtension arcAE, MPPSContext ctx) {
        HL7PSUTask task = new HL7PSUTask();
        task.setDeviceName(device.getDeviceName());
        task.setAETitle(arcAE.getApplicationEntity().getAETitle());
        task.setScheduledTime(scheduledTime(arcAE.hl7PSUTimeout()));
        task.setStudyInstanceUID(ctx.getMPPS().getStudyInstanceUID());
        task.setMpps(ctx.getMPPS());
        em.persist(task);
        LOG.info("{}: Created {}", ctx, task);
    }

    public boolean createOrUpdateHL7PSUTaskForStudy(ArchiveAEExtension arcAE, StoreContext ctx) {
        if (!hasMWLItems(ctx.getStudyInstanceUID()))
            return false;

        try {
            HL7PSUTask task = em.createNamedQuery(HL7PSUTask.FIND_BY_STUDY_IUID, HL7PSUTask.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .getSingleResult();
            task.setScheduledTime(scheduledTime(arcAE.hl7PSUDelay()));
            LOG.info("{}: Updated {}", ctx, task);
        } catch (NoResultException nre) {
            HL7PSUTask task = new HL7PSUTask();
            task.setDeviceName(device.getDeviceName());
            task.setAETitle(arcAE.getApplicationEntity().getAETitle());
            task.setStudyInstanceUID(ctx.getStudyInstanceUID());
            task.setScheduledTime(scheduledTime(arcAE.hl7PSUDelay()));
            em.persist(task);
            LOG.info("{}: Created {}", ctx, task);
        }
        return true;
    }

    private boolean hasMWLItems(String studyIUID) {
        return em.createNamedQuery(MWLItem.COUNT_BY_STUDY_IUID, Long.class)
                .setParameter(1, studyIUID)
                .getSingleResult() > 0;
    }

    private List<MWLItem> findMWLItems(String studyIUID) {
        return em.createNamedQuery(MWLItem.FIND_BY_STUDY_IUID_EAGER, MWLItem.class)
                .setParameter(1, studyIUID)
                .getResultList();
    }


    private Date scheduledTime(Duration duration) {
        return duration != null ? new Date(System.currentTimeMillis() + duration.getSeconds() * 1000L) : null;
    }

    public List<HL7PSUTask> fetchHL7PSUTasksForMPPS(String deviceName, long prevPk, int fetchSize) {
        return em.createNamedQuery(HL7PSUTask.FIND_WITH_MPPS_BY_DEVICE_NAME, HL7PSUTask.class)
                .setParameter(1, deviceName)
                .setParameter(2, prevPk)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    public List<HL7PSUTask> fetchHL7PSUTasksForStudy(String deviceName, int fetchSize) {
        return em.createNamedQuery(HL7PSUTask.FIND_SCHEDULED_BY_DEVICE_NAME, HL7PSUTask.class)
                .setParameter(1, deviceName).setMaxResults(fetchSize).getResultList();
    }

    public void removeHL7PSUTask(HL7PSUTask task) {
        em.remove(em.getReference(task.getClass(), task.getPk()));
    }

    public void scheduleHL7PSUTask(HL7PSUTask task, HL7PSUScheduler.HL7PSU action) {
        ApplicationEntity ae = device.getApplicationEntity(task.getAETitle());
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        Attributes mwlAttrs = null;
        if (task.getMpps() == null) {
            List<MWLItem> mwlItems = findMWLItems(task.getStudyInstanceUID());
            if (mwlItems.isEmpty()) {
                LOG.warn("No MWL for {} - no HL7 Procedure Status Update", task);
                return;
            }
            if (action == HL7PSUScheduler.HL7PSU.MWL || action == HL7PSUScheduler.HL7PSU.BOTH)
                updateStatusToCompleted(arcAE, mwlItems);

            mwlAttrs = mwlItems.get(0).getAttributes();
        }
        if (action != HL7PSUScheduler.HL7PSU.MWL)
            scheduleHL7Msg(arcAE, task, mwlAttrs);
        removeHL7PSUTask(task);
    }

    private void scheduleHL7Msg(ArchiveAEExtension arcAE, HL7PSUTask task,
                               Attributes mwlAttrs) {
        String sendingAppWithFacility = arcAE.hl7PSUSendingApplication();
        if (sendingAppWithFacility == null)
            return;

        String hl7cs = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(sendingAppWithFacility)
                .getHL7SendingCharacterSet();
        HL7PSUMessage msg = new HL7PSUMessage(task);
        if (mwlAttrs != null)
            msg.setMWLItem(mwlAttrs);
        msg.setSendingApplicationWithFacility(sendingAppWithFacility);
        if (!hl7cs.equals("ISO IR-6"))
            msg.setCharacterSet(hl7cs);
        scheduleMessage(arcAE, hl7cs, msg);
    }

    private void updateStatusToCompleted(ArchiveAEExtension arcAE, List<MWLItem> mwlItems) {
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        for (MWLItem mwl : mwlItems) {
            Attributes mwlAttrs = mwl.getAttributes();
            Iterator<Attributes> spsItems = mwlAttrs.getSequence(Tag.ScheduledProcedureStepSequence).iterator();
            while (spsItems.hasNext()) {
                Attributes sps = spsItems.next();
                spsItems.remove();
                sps.setString(Tag.ScheduledProcedureStepStatus, VR.CS, SPSStatus.COMPLETED.toString());
                mwlAttrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps);
            }
            mwl.setAttributes(mwlAttrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
        }
    }

    private void scheduleMessage(ArchiveAEExtension arcAE, String hl7cs, HL7PSUMessage msg) {
        for (String receivingApp : arcAE.hl7PSUReceivingApplications()) {
            msg.setReceivingApplicationWithFacility(receivingApp);
            try {
                scheduleMessage(msg.getHL7Message(), hl7cs);
            } catch (Exception e) {
                LOG.warn("Failed to schedule HL7 Procedure Status Update to {}:\n", receivingApp, e);
            }
        }
    }

    private void scheduleMessage(HL7Message hl7Message, String hl7cs)
            throws ConfigurationException, QueueSizeLimitExceededException {
        HL7Segment msh = hl7Message.get(0);
        hl7Sender.scheduleMessage(
                msh.getField(2, ""),
                msh.getField(3, ""),
                msh.getField(4, ""),
                msh.getField(5, ""),
                msh.getField(8, ""),
                msh.getField(9, ""),
                hl7Message.getBytes(hl7cs),
                null);
    }
}
