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

package org.dcm4chee.arc.hl7.psu;

import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.entity.HL7PSUTask;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */
@Stateless
public class HL7ProcedureStatusUpdateEJB {
    private static final Logger LOG = LoggerFactory.getLogger(HL7ProcedureStatusUpdateEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueueManager queueManager;

    @Inject
    private HL7Sender hl7Sender;

    public void createHL7PSUTaskForMPPS(ArchiveAEExtension arcAE, MPPSContext ctx) {
        List<MWLItem> mwlItems = findMWLItems(ctx.getMPPS().getStudyInstanceUID());
        for (MWLItem mwl : mwlItems) {
            ApplicationEntity ae = arcAE.getApplicationEntity();
            HL7PSUTask task = new HL7PSUTask();
            task.setDeviceName(ae.getDevice().getDeviceName());
            task.setCalledAET(ctx.getCalledAET());
            task.setHl7psuDestinations(arcAE.hl7psuDestinations());
            task.setScheduledTime(scheduledTime(arcAE.hl7psuTimeout()));
            task.setStudyInstanceUID(ctx.getMPPS().getStudyInstanceUID());
            task.setMpps(ctx.getMPPS());
            task.setMwl(mwl);
            em.persist(task);
            LOG.info("{}: Created {}", ctx, task);
        }
    }

    private List<MWLItem> findMWLItems(String studyIUID) {
        return em.createNamedQuery(MWLItem.FIND_BY_STUDY_IUID, MWLItem.class)
                .setParameter(1, studyIUID).getResultList();
    }

    public void createOrUpdateHL7PSUTaskForStudy(ArchiveAEExtension arcAE, StoreContext ctx) {
        List<MWLItem> mwlItems = findMWLItems(ctx.getStudyInstanceUID());
        for (MWLItem mwl : mwlItems) {
            try {
                HL7PSUTask task = em.createNamedQuery(HL7PSUTask.FIND_BY_STUDY_IUID, HL7PSUTask.class)
                        .setParameter(1, ctx.getStudyInstanceUID()).setParameter(2, mwl)
                        .getSingleResult();
                task.setScheduledTime(scheduledTime(arcAE.hl7psuDelay()));
                LOG.info("{}: Updated {}", ctx, task);
            } catch (NoResultException nre) {
                HL7PSUTask task = new HL7PSUTask();
                task.setDeviceName(arcAE.getApplicationEntity().getDevice().getDeviceName());
                task.setCalledAET(ctx.getStoreSession().getCalledAET());
                task.setHl7psuDestinations(arcAE.hl7psuDestinations());
                task.setStudyInstanceUID(ctx.getStudyInstanceUID());
                task.setScheduledTime(scheduledTime(arcAE.hl7psuDelay()));
                task.setMwl(mwl);
                em.persist(task);
                LOG.info("{}: Created {}", ctx, task);
            }
        }
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

    public void scheduleHL7PSUTask(HL7PSUTask task, HL7Message hl7msg) {
        HL7Segment msh = hl7msg.get(0);
        hl7Sender.scheduleMessage(msh.getField(2, ""), msh.getField(3, ""),
                msh.getField(4, ""), msh.getField(5, ""),
                msh.getMessageType(), "1111", hl7msg.getBytes(null));
        removeHL7PSUTask(task);
    }

    public void removeHL7PSUTask(HL7PSUTask task) {
        em.remove(em.getReference(task.getClass(), task.getPk()));
    }

}
