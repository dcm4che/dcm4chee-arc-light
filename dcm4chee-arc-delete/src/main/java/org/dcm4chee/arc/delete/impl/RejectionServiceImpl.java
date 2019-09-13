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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2019
 */
@ApplicationScoped
public class RejectionServiceImpl implements org.dcm4chee.arc.delete.RejectionService {
    private static final Logger LOG = LoggerFactory.getLogger(RejectionServiceImpl.class);

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private StoreService storeService;

    @Inject
    private DeletionServiceEJB ejb;

    public void onExport(@Observes ExportContext ctx) {
        ExporterDescriptor desc = ctx.getExporter().getExporterDescriptor();
        if (!desc.isRejectForDataRetentionExpiry() || ctx.getOutcome().getStatus() != QueueMessage.Status.COMPLETED)
            return;

        ApplicationEntity ae = getApplicationEntity(desc.getAETitle());
        if (ae == null || !ae.isInstalled()) {
            LOG.warn("No such Application Entity: {}", desc.getAETitle());
            return;
        }

        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        RejectionNote rn = arcDev.getRejectionNote(RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED);
        if (rn == null) {
            LOG.warn("Data Retention Policy Expired Rejection Note not configured.");
            return;
        }

        LOG.info("Export completed, invoke rejection of objects.");
        try {
            if (ejb.claimExpired(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ExpirationState.REJECTED))
                reject(ae, ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), rn,
                        ctx.getHttpServletRequestInfo());
        } catch (Exception e) {
            LOG.warn("Rejection of Expired Study[UID={}], Series[UID={}], SOPInstance[UID={}] failed.\n",
                    ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), e);
            ejb.claimExpired(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ExpirationState.FAILED_TO_REJECT);
        }
    }

    @Override
    public int reject(String aet, String studyIUID, String seriesIUID, String sopIUID, Code code,
                      HttpServletRequestInfo httpRequest) throws Exception {
        return reject(getApplicationEntity(aet), studyIUID, seriesIUID, sopIUID, getRejectionNote(code), httpRequest);
    }

    @Override
    public int reject(ApplicationEntity ae, String studyIUID, String seriesIUID, String sopIUID, RejectionNote rjNote,
                      HttpServletRequestInfo httpRequest) throws Exception {
        StoreSession storeSession = storeService.newStoreSession(httpRequest, ae, null);
        String rejectionNoteObjectStorageID = rejectionNoteObjectStorageID(storeSession);
        storeSession.withObjectStorageID(rejectionNoteObjectStorageID);
        storeService.restoreInstances(storeSession, studyIUID, seriesIUID, null);
        Attributes attrs = queryService.createRejectionNote(ae, studyIUID, seriesIUID, sopIUID, rjNote);
        if (attrs == null)
            return 0;

        int count = countInstances(attrs);
        LOG.info("Start rejection of {} instances of Study[UID={}], Series[UID={}], SOPInstance[UID={}].",
                count, studyIUID, seriesIUID, sopIUID);
        StoreContext storeCtx = storeService.newStoreContext(storeSession);
        storeCtx.setSopClassUID(attrs.getString(Tag.SOPClassUID));
        storeCtx.setSopInstanceUID(attrs.getString(Tag.SOPInstanceUID));
        storeCtx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
        storeService.store(storeCtx, attrs);
        LOG.info("Rejection of {} instances of Study[UID={}], Series[UID={}], SOPInstance[UID={}] completed.",
                count, studyIUID, seriesIUID, sopIUID);
        return count;
    }

    @Override
    public void scheduleReject(String aet, String studyIUID, String seriesIUID, String sopIUID, Code code,
                               HttpServletRequestInfo httpRequest, String batchID)
            throws QueueSizeLimitExceededException {
        ejb.scheduleRejection(aet, studyIUID, seriesIUID, sopIUID, code, httpRequest, batchID);
    }

    private static int countInstances(Attributes attrs) {
        return attrs.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence)
                .getSequence(Tag.ReferencedSeriesSequence).stream()
                .collect(Collectors.summingInt(x -> x.getSequence(Tag.ReferencedSOPSequence).size()));
    }

    private String rejectionNoteObjectStorageID(StoreSession storeSession) {
        String rejectionNoteStorageAET = device.getDeviceExtension(ArchiveDeviceExtension.class)
                .getRejectionNoteStorageAET();
        if (rejectionNoteStorageAET == null)
            return null;

        ApplicationEntity rjAE = getApplicationEntity(rejectionNoteStorageAET);
        ArchiveAEExtension rjArcAE;
        if (rjAE == null || !rjAE.isInstalled() || (rjArcAE = rjAE.getAEExtension(ArchiveAEExtension.class)) == null) {
            LOG.warn("Rejection Note Storage Application Entity with an Archive AE Extension not configured: {}",
                    rejectionNoteStorageAET);
            return null;
        }

        String[] objectStorageIDs;
        if ((objectStorageIDs = rjArcAE.getObjectStorageIDs()).length > 0)
            return objectStorageIDs[0];

        LOG.warn("Object storage for rejection notes shall fall back on those configured for AE: {} since none are " +
                "configured for RejectionNoteStorageAE: {}",
                storeSession.getLocalApplicationEntity().getAETitle(), rejectionNoteStorageAET);
        return null;
    }

    private ApplicationEntity getApplicationEntity(String aet) {
        return device.getApplicationEntity(aet, true);
    }

    private RejectionNote getRejectionNote(Code code) {
        return device.getDeviceExtension(ArchiveDeviceExtension.class).getRejectionNote(code);
    }
}
