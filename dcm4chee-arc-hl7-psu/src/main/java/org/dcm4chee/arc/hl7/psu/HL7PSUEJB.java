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
 * Portions created by the Initial Developer are Copyright (C) 2017-2025
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
import org.dcm4chee.arc.conf.HL7PSUAction;
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
    private static final String HL7PSU_MSG_ABORT = "Send HL7 Procedure Status Update notification aborted. ";

    //HL7v2.5 Ch4 OBR-25 ResultStatus
    private static final String HL7PSU_RESULTS_STORED = "R";
    private static final String HL7PSU_PARTIAL_REJECT = "A";
    private static final String HL7PSU_COMPLETE_REJECT = "X";

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
        try {
            HL7PSUTask task = em.createNamedQuery(HL7PSUTask.FIND_BY_MPPS, HL7PSUTask.class)
                    .setParameter(1, ctx.getMPPS().getPk())
                    .getSingleResult();
            task.setScheduledTime(scheduledTime(arcAE.hl7PSUTimeout()));
            task.setStudyInstanceUID(ctx.getMPPS().getStudyInstanceUID());
            task.setAccessionNumber(ctx.getMPPS().getAccessionNumber());
            task.setMpps(ctx.getMPPS());
            LOG.info("{}: Updated {}", ctx.getAssociation(), task);
        } catch (NoResultException e) {
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

    public void scheduleHL7Msg(ArchiveAEExtension arcAE, Attributes dicomCompositeObjAttrs) {
        List<MWLItem> mwlItems = procedureService.findMWLItems(arcAE, dicomCompositeObjAttrs);
        for (HL7PSUAction hl7PSUAction : arcAE.hl7PSUAction()) {
            if (hl7PSUAction == HL7PSUAction.SEND_NOTIFICATION) {
                sendHL7PSUNotification(arcAE, dicomCompositeObjAttrs, mwlItems);
                continue;
            }

            if ((procedureService.updateMWLStatus(mwlItems, SPSStatus.COMPLETED)).size() > 0) {
                LOG.info("{} MWL Items status updated to {} by associated Study[IUID={}, AccessionNumber={}].",
                        mwlItems.size(),
                        SPSStatus.COMPLETED,
                        dicomCompositeObjAttrs.getString(Tag.StudyInstanceUID),
                        dicomCompositeObjAttrs.getString(Tag.AccessionNumber));
                continue;
            }

            LOG.info("Study[IUID={}, AccessionNumber={}] does not have any associated MWL items.",
                    dicomCompositeObjAttrs.getString(Tag.StudyInstanceUID),
                    dicomCompositeObjAttrs.getString(Tag.AccessionNumber));
        }
    }

    private void scheduleHL7Msg(HL7PSUTask task) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(task.getAETitle())
                .getAEExtension(ArchiveAEExtension.class);
        List<MWLItem> mwlItems = procedureService.findMWLItems(arcAE, task);
        for (HL7PSUAction hl7PSUAction : arcAE.hl7PSUAction()) {
            if (hl7PSUAction == HL7PSUAction.SEND_NOTIFICATION) {
                sendHL7PSUNotification(arcAE, task, mwlItems);
                continue;
            }

            if ((procedureService.updateMWLStatus(mwlItems, SPSStatus.COMPLETED)).size() > 0) {
                LOG.info("{} MWL Items status updated to {} by {}.", mwlItems.size(), SPSStatus.COMPLETED, task);
                continue;
            }

            LOG.info("Study referenced in the {} does not have any associated MWL items.", task);
        }
    }

    private void sendHL7PSUNotification(
            ArchiveAEExtension arcAE, Attributes dicomCompositeObjAttrs, List<MWLItem> mwlItems) {
        String hl7PSUSendingApplication = arcAE.hl7PSUSendingApplication();
        String[] hl7PSUReceivingApplications = arcAE.hl7PSUReceivingApplications();
        HL7Application hl7SenderApplication;
        if (hl7PSUSendingApplication == null
                || ((hl7SenderApplication = hl7Sender(hl7PSUSendingApplication)) == null)
                || hl7PSUReceivingApplications.length == 0) {
            LOG.info(HL7PSU_MSG_ABORT + "HL7 Procedure Status Update Sending or Receiving Application not configured.");
            return;
        }

        Series series = findSeries(dicomCompositeObjAttrs.getString(Tag.StudyInstanceUID),
                                    dicomCompositeObjAttrs.getString(Tag.SeriesInstanceUID));
        Attributes attrs = attrsForHL7PSUNotification(arcAE, series, mwlItems);
        if (attrs == null) {
            if (series == null)
                LOG.info("No Series[IUID={}] of Study[IUID={}] found.",
                        dicomCompositeObjAttrs.getString(Tag.SeriesInstanceUID),
                        dicomCompositeObjAttrs.getString(Tag.StudyInstanceUID));

            LOG.info(HL7PSU_MSG_ABORT + "No attributes available to populate the message.");
            return;
        }

        String uri = arcAE.hl7PSUStudyTemplateURI();
        UnparsedHL7Message hl7Msg = null;
        byte[] hl7PSUData;
        for (String hl7PSUReceivingApplication : hl7PSUReceivingApplications) {
            HL7Application hl7Receiver = hl7Receiver(hl7PSUReceivingApplication);
            if (hl7Receiver == null) continue;

            if (hl7Msg != null) {
                hl7Msg.msh().setReceivingApplicationWithFacility(hl7PSUReceivingApplication);
                hl7Sender.scheduleMessage(hl7Msg.data());
                continue;
            }

            hl7PSUData = hl7PSUData(hl7SenderApplication, hl7Receiver, attrs, uri, null,
                                    resultStatus(series), arcAE);
            if (hl7PSUData == null) break;

            hl7Msg = new UnparsedHL7Message(hl7PSUData);
            hl7Sender.scheduleMessage(hl7Msg.data());
        }
    }

    private String resultStatus(Series series) {
        if (series == null)
            return HL7PSU_RESULTS_STORED;

        RejectionState rejectionState = series.getStudy().getRejectionState();
        switch (rejectionState) {
            case PARTIAL:
                return HL7PSU_PARTIAL_REJECT;
            case COMPLETE:
                return HL7PSU_COMPLETE_REJECT;
            default:
                return HL7PSU_RESULTS_STORED;
        }
    }

    private void sendHL7PSUNotification(ArchiveAEExtension arcAE, HL7PSUTask task, List<MWLItem> mwlItems) {
        String hl7PSUSendingApplication = arcAE.hl7PSUSendingApplication();
        String[] hl7PSUReceivingApplications = arcAE.hl7PSUReceivingApplications();
        HL7Application hl7SenderApplication;
        if (hl7PSUSendingApplication == null
                || ((hl7SenderApplication = hl7Sender(hl7PSUSendingApplication)) == null)
                || hl7PSUReceivingApplications.length == 0) {
            LOG.info(HL7PSU_MSG_ABORT + "HL7 Procedure Status Update Sending or Receiving Application not configured.");
            return;
        }

        Attributes attrs = attrsForHL7PSUNotification(arcAE, task, mwlItems);
        if (attrs == null) {
            LOG.info(HL7PSU_MSG_ABORT + "No attributes available to populate the message.");
            return;
        }

        String uri = arcAE.hl7PSUStudyTemplateURI();
        UnparsedHL7Message hl7Msg = null;
        String ppsStatus = null;
        MPPS mpps = task.getMpps();
        if (mpps != null) {
            uri = arcAE.hl7PSUMppsTemplateURI();
            ppsStatus = task.getPPSStatus().name();
        }

        byte[] hl7PSUData;
        for (String hl7PSUReceivingApplication : hl7PSUReceivingApplications) {
            HL7Application hl7Receiver = hl7Receiver(hl7PSUReceivingApplication);
            if (hl7Receiver == null) continue;

            if (hl7Msg != null) {
                hl7Msg.msh().setReceivingApplicationWithFacility(hl7PSUReceivingApplication);
                hl7Sender.scheduleMessage(hl7Msg.data());
                continue;
            }

            hl7PSUData = hl7PSUData(hl7SenderApplication, hl7Receiver, attrs, uri, ppsStatus, HL7PSU_RESULTS_STORED, arcAE);
            if (hl7PSUData == null) break;

            hl7Msg = new UnparsedHL7Message(hl7PSUData);
            hl7Sender.scheduleMessage(hl7Msg.data());
        }
    }

    private byte[] hl7PSUData(
            HL7Application hl7SenderApplication, HL7Application hl7Receiver, Attributes attrs, String uri,
            String ppsStatus, String resultStatus, ArchiveAEExtension arcAE) {
        try {
            return HL7SenderUtils.hl7PSUData(hl7SenderApplication, hl7Receiver, attrs, uri, ppsStatus, resultStatus, arcAE);
        } catch (TransformerConfigurationException | UnsupportedEncodingException | SAXException e) {
            LOG.info(HL7PSU_MSG_ABORT + "Failed to transform attributes to HL7 message.\n", e);
        }
        return null;
    }

    private HL7Application hl7Sender(String hl7PSUSendingApplication) {
        return device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(hl7PSUSendingApplication, true);
    }

    private HL7Application hl7Receiver(String hl7PSUReceivingApplication) {
        try {
            return hl7AppCache.findHL7Application(hl7PSUReceivingApplication);
        } catch (ConfigurationException e) {
            LOG.info("HL7 Procedure Status Update notification not sent to {} \n", hl7PSUReceivingApplication, e);
        }
        return null;
    }

    private Attributes attrsForHL7PSUNotification(
            ArchiveAEExtension arcAE, Series series, List<MWLItem> mwlItems) {
        if (!mwlItems.isEmpty()) return mwlAttrs(mwlItems.get(0));

        if (arcAE.hl7PSUForRequestedProcedure()) {
            LOG.info("HL7 Procedure Status Update notification restricted to existence of MWL associated to study");
            return null;
        }

        return series == null ? null : studySeriesAttrs(series);
    }

    private Attributes attrsForHL7PSUNotification(ArchiveAEExtension arcAE, HL7PSUTask task, List<MWLItem> mwlItems) {
        if (task.getMpps() != null) return mppsAttrs(task.getMpps());
        if (!mwlItems.isEmpty()) return mwlAttrs(mwlItems.get(0));

        if (arcAE.hl7PSUForRequestedProcedure()) {
            LOG.info("HL7 Procedure Status Update notification restricted to existence of MWL associated to study");
            return null;
        }

        Series series = findSeries(task.getStudyInstanceUID(), task.getSeriesInstanceUID());
        if (series == null) {
            LOG.info(HL7PSU_MSG_ABORT + "No Series[IUID={}] of Study[IUID={}] found.",
                    task.getSeriesInstanceUID(), task.getStudyInstanceUID());
            return null;
        }

        return studySeriesAttrs(series);
    }

    private Attributes mppsAttrs(MPPS mpps) {
        Attributes attrs = mpps.getAttributes();
        Attributes.unifyCharacterSets(attrs, mpps.getPatient().getAttributes());
        attrs.addAll(mpps.getPatient().getAttributes());
        return attrs;
    }

    private Attributes mwlAttrs(MWLItem mwlItem) {
        Attributes attrs = mwlItem.getAttributes();
        Attributes.unifyCharacterSets(attrs, mwlItem.getPatient().getAttributes());
        attrs.addAll(mwlItem.getPatient().getAttributes());
        return attrs;
    }

    private Attributes studySeriesAttrs(Series series) {
        Study study = series.getStudy();
        Attributes attrs = new Attributes(series.getAttributes());
        Attributes.unifyCharacterSets(attrs, study.getAttributes(), study.getPatient().getAttributes());
        attrs.addAll(study.getAttributes());
        attrs.addAll(study.getPatient().getAttributes());
        return attrs;
    }

    private Series findSeries(String studyIUID, String seriesIUID) {
        try {
            return em.createNamedQuery(Series.FIND_BY_SERIES_IUID, Series.class)
                    .setParameter(1, studyIUID)
                    .setParameter(2, seriesIUID)
                    .getSingleResult();
        } catch (NoResultException e) {}
        return null;
    }

}