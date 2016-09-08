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

package org.dcm4chee.arc.ian.scu.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.entity.IanTask;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.ian.scu.IANSCU;
import org.dcm4chee.arc.qmgt.QueueManager;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2016
 */
@Stateless
public class IANEJB {

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueueManager queueManager;

    public enum Action { CREATED, UPDATED}
    public static class IanTaskAction {
        public final IanTask ianTask;
        public final Action action;

        public IanTaskAction(IanTask ianTask, Action action) {
            this.ianTask = ianTask;
            this.action = action;
        }
    }

    public IanTask createIANTaskForMPPS(ArchiveAEExtension arcAE, String callingAET, MPPS mpps) {
        ApplicationEntity ae = arcAE.getApplicationEntity();
        IanTask task = new IanTask();
        task.setDeviceName(ae.getDevice().getDeviceName());
        task.setCallingAET(callingAET);
        task.setIanDestinations(arcAE.ianDestinations());
        task.setScheduledTime(scheduledTime(arcAE.ianTimeout()));
        task.setMpps(mpps);
        em.persist(task);
        return task;
    }

    public IanTaskAction createOrUpdateIANTaskForStudy(ArchiveAEExtension arcAE, String callingAET, String studyInstanceUID) {
        try {
            IanTask task = em.createNamedQuery(IanTask.FIND_BY_STUDY_IUID, IanTask.class)
                    .setParameter(1, studyInstanceUID)
                    .getSingleResult();
            task.setScheduledTime(scheduledTime(arcAE.ianDelay()));
            return new IanTaskAction(task, Action.UPDATED);
        } catch (NoResultException nre) {
            IanTask task = new IanTask();
            task.setDeviceName(arcAE.getApplicationEntity().getDevice().getDeviceName());
            task.setCallingAET(callingAET);
            task.setIanDestinations(arcAE.ianDestinations());
            task.setStudyInstanceUID(studyInstanceUID);
            task.setScheduledTime(scheduledTime(arcAE.ianDelay()));
            em.persist(task);
            return new IanTaskAction(task, Action.CREATED);
        }
    }

    private Date scheduledTime(Duration duration) {
        return duration != null ? new Date(System.currentTimeMillis() + duration.getSeconds() * 1000L) : null;
    }

    public List<IanTask> fetchIANTasksForMPPS(String deviceName, long prevPk, int fetchSize) {
        return em.createNamedQuery(IanTask.FIND_WITH_MPPS_BY_DEVICE_NAME, IanTask.class)
                .setParameter(1, deviceName)
                .setParameter(2, prevPk)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    public List<IanTask> fetchIANTasksForStudy(String deviceName, int fetchSize) {
        return em.createNamedQuery(IanTask.FIND_SCHEDULED_BY_DEVICE_NAME, IanTask.class)
                .setParameter(1, deviceName)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    public void scheduleIANTask(IanTask task, Attributes attrs) {
        for (String remoteAET : task.getIanDestinations())
            scheduleMessage(task.getCallingAET(), attrs, remoteAET);
        removeIANTask(task);
    }

    public void scheduleMessage(String callingAET, Attributes attrs, String remoteAET) {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(attrs);
            msg.setStringProperty("LocalAET", callingAET);
            msg.setStringProperty("RemoteAET", remoteAET);
            msg.setStringProperty("SOPInstanceUID", UIDUtils.createUID());
            queueManager.scheduleMessage(IANSCU.QUEUE_NAME, msg);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }

    public void removeIANTask(IanTask task) {
        em.remove(em.getReference(task.getClass(), task.getPk()));
    }

}
