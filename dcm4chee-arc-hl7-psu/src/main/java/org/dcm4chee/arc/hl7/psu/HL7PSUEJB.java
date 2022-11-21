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

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.hl7.HL7SenderUtils;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerConfigurationException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
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

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private ProcedureService procedureService;

    public void createHL7PSUTaskForMPPS(ArchiveAEExtension arcAE, MPPSContext ctx) {
        HL7PSUTask task = new HL7PSUTask();
        task.setDeviceName(device.getDeviceName());
        task.setAETitle(arcAE.getApplicationEntity().getAETitle());
        task.setScheduledTime(scheduledTime(arcAE.hl7PSUTimeout()));
        task.setStudyInstanceUID(ctx.getMPPS().getStudyInstanceUID());
        task.setAccessionNumber(ctx.getMPPS().getAccessionNumber());
        task.setMpps(ctx.getMPPS());
        em.persist(task);
        LOG.info("{}: Created {}", ctx, task);
    }

    public void createOrUpdateHL7PSUTaskForStudy(ArchiveAEExtension arcAE, StoreContext ctx) {
        try {
            HL7PSUTask task = em.createNamedQuery(HL7PSUTask.FIND_BY_STUDY_IUID, HL7PSUTask.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .getSingleResult();
            task.setScheduledTime(scheduledTime(arcAE.hl7PSUDelay()));
            task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
            LOG.info("{}: Updated {}", ctx.getStoreSession(), task);
        } catch (NoResultException nre) {
            HL7PSUTask task = new HL7PSUTask();
            task.setDeviceName(device.getDeviceName());
            task.setAETitle(arcAE.getApplicationEntity().getAETitle());
            task.setStudyInstanceUID(ctx.getStudyInstanceUID());
            task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
            task.setAccessionNumber(ctx.getAttributes().getString(Tag.AccessionNumber));
            task.setScheduledTime(scheduledTime(arcAE.hl7PSUDelay()));
            em.persist(task);
            LOG.info("{}: Created {}", ctx.getStoreSession(), task);
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

    public void removeHL7PSUTask(HL7PSUTask task) {
        em.remove(em.getReference(task.getClass(), task.getPk()));
    }

    public void scheduleHL7PSUTask(HL7PSUTask task) {
        LOG.info("Schedule {}", task);
        scheduleHL7Msg(task);
        removeHL7PSUTask(task);
    }

    private MWLItem updateMWLStatus(ArchiveAEExtension arcAE, HL7PSUTask task) {
        SPSStatus status = spsStatus(task);
        List<MWLItem> mwlItems = procedureService.updateMWLStatus(arcAE, task, status);
        if (mwlItems.size() > 0)
            LOG.info("{} MWL Items status updated to {} by HL7 PSU task {}.", mwlItems.size(), status, task);
        else
            LOG.info("Study referenced in the HL7 PSU task {} does not have any associated MWL items.", task);

        return !mwlItems.isEmpty() ? mwlItems.get(0) : null;
    }

    private SPSStatus spsStatus(HL7PSUTask task) {
        MPPS.Status ppsStatus;
        if (task.getMpps() == null || (ppsStatus = task.getPPSStatus()) == MPPS.Status.COMPLETED)
            return SPSStatus.COMPLETED;

        return ppsStatus == MPPS.Status.DISCONTINUED
                ? SPSStatus.DISCONTINUED
                : SPSStatus.ARRIVED;
    }

    private void scheduleHL7Msg(HL7PSUTask task) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(task.getAETitle())
                                         .getAEExtension(ArchiveAEExtension.class);
        String hl7PSUSendingApplication = arcAE.hl7PSUSendingApplication();
        String[] hl7PSUReceivingApplications = arcAE.hl7PSUReceivingApplications();
        if (hl7PSUSendingApplication == null || hl7PSUReceivingApplications.length == 0) {
            LOG.info("HL7 Procedure Status Update Sending or Receiving Applications not configured.");
            return;
        }

        Attributes attrs = attributesFrom(arcAE, task);
        if (attrs == null) {
            LOG.info("No attributes available to send out HL7 Procedure Status Update Notifications");
            return;
        }

        HL7Application sender = device.getDeviceExtension(HL7DeviceExtension.class)
                                      .getHL7Application(hl7PSUSendingApplication, true);
        UnparsedHL7Message hl7Msg = null;
        MPPS mpps = task.getMpps();
        String uri = mpps != null ? arcAE.hl7PSUMppsTemplateURI() : arcAE.hl7PSUStudyTemplateURI();
        for (String hl7PSUReceivingApplication : hl7PSUReceivingApplications) {
            try {
                HL7Application receiver = hl7AppCache.findHL7Application(hl7PSUReceivingApplication);
                if (hl7Msg == null)
                    hl7Msg = new UnparsedHL7Message(HL7SenderUtils.data(sender, receiver, attrs, null,
                                                                        arcAE.hl7PSUMessageType().name(), uri,
                                                                        mpps != null ? task.getPPSStatus().name() : null,
                                                                        arcAE));
                else
                    hl7Msg.msh().setReceivingApplicationWithFacility(hl7PSUReceivingApplication);
                hl7Sender.scheduleMessage(null, hl7Msg.data());
            } catch (ConfigurationException e) {
                LOG.info("HL7 Procedure Status Update notification not sent to {}", e.getMessage());
            } catch (TransformerConfigurationException | UnsupportedEncodingException | SAXException e) {
                LOG.info("Failed to transform attributes to HL7 PSU message");
            } catch (Exception e) {
                LOG.info("Failed to schedule HL7 Procedure Status Update to {}:\n", hl7PSUReceivingApplication, e);
            }
        }
    }

    private Attributes attributesFrom(ArchiveAEExtension arcAE, HL7PSUTask task) {
        MWLItem mwl = null;
        if ((arcAE.hl7PSUSendingApplication() != null && arcAE.hl7PSUReceivingApplications().length > 0)
            || arcAE.hl7PSUMWL())
            mwl = updateMWLStatus(arcAE, task);

        Attributes attrs;
        MPPS mpps = task.getMpps();
        if (mpps != null) {
            attrs = mpps.getAttributes();
            attrs.addAll(mpps.getPatient().getAttributes());
            return attrs;
        }

        if (mwl != null) {
            attrs = mwl.getAttributes();
            attrs.addAll(mwl.getPatient().getAttributes());
            return attrs;
        }

        if (arcAE.hl7PSUForRequestedProcedure()) {
            LOG.info("HL7 Procedure Status Update notification restricted to existence of MWL associated to study");
            return null;
        }

        Series series = findSeries(task);
        if (series == null)
            return null;

        Study study = series.getStudy();
        attrs = new Attributes(series.getAttributes());
        attrs.addAll(study.getAttributes());
        attrs.addAll(study.getPatient().getAttributes());
        return attrs;
    }

    private Series findSeries(HL7PSUTask task) {
        try {
            return em.createNamedQuery(Series.FIND_BY_SERIES_IUID, Series.class)
                    .setParameter(1, task.getStudyInstanceUID())
                    .setParameter(2, task.getSeriesInstanceUID())
                    .getSingleResult();
        } catch (NoResultException e) {
            LOG.info("Series referenced in HL7PSUTask {} does not exist", task);
        }
        return null;
    }
}
