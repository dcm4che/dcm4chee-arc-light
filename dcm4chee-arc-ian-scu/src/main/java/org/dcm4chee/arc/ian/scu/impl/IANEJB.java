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

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.ian.scu.IANSCU;
import org.dcm4chee.arc.qmgt.TaskManager;

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
    private TaskManager taskManager;

    @Inject
    private Device device;

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
            scheduleMessage(task.getCallingAET(), attrs, remoteAET, new Date());
        removeIANTask(task);
    }

    public void scheduleMessage(String callingAET, Attributes attrs, String remoteAET, Date scheduledTime) {
        Task task = createTask(callingAET, attrs, remoteAET, scheduledTime, null);
        taskManager.scheduleTask(task);
    }

    public void scheduleMessage(String callingAET, Attributes attrs, String remoteAET, Date scheduledTime, String batchID,
                                String studyUID, String seriesUID) {
        Task task = createTask(callingAET, attrs, remoteAET, scheduledTime, batchID);
        task.setStudyInstanceUID(studyUID);
        task.setSeriesInstanceUID(seriesUID);
        taskManager.scheduleTask(task);
    }

    private Task createTask(String callingAET, Attributes attrs, String remoteAET, Date scheduledTime, String batchID) {
        Task task = new Task();
        task.setDeviceName(device.getDeviceName());
        task.setQueueName(IANSCU.QUEUE_NAME);
        task.setType(Task.Type.IAN);
        task.setScheduledTime(scheduledTime);
        task.setLocalAET(callingAET);
        task.setRemoteAET(remoteAET);
        task.setSOPInstanceUID(UIDUtils.createUID());
        task.setPayload(AttributesBlob.encodeAttributes(attrs));
        task.setStatus(Task.Status.SCHEDULED);
        task.setBatchID(batchID);
        return task;
    }

    public void removeIANTask(IanTask task) {
        em.remove(em.getReference(task.getClass(), task.getPk()));
    }

    public boolean addPPSRef(String studyUID, String seriesUID, Attributes ian) {
        try {
            Attributes ppsRef = AttributesBlob.decodeAttributes(
                    em.createNamedQuery(Series.ATTRS_BY_SERIES_IUID, byte[].class)
                            .setParameter(1, studyUID)
                            .setParameter(2, seriesUID)
                            .getSingleResult(),
                    null)
                    .getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence);
            if (ppsRef == null)
                return false;

            ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1)
                    .add(new Attributes(ppsRef));
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }

}
