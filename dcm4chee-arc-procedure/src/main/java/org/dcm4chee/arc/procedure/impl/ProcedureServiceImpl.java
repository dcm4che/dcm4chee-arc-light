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

package org.dcm4chee.arc.procedure.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.data.*;
import org.dcm4che3.net.*;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.procedure.ImportResult;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QIDO;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.query.util.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@ApplicationScoped
public class ProcedureServiceImpl implements ProcedureService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureServiceImpl.class);

    @Inject
    private Device device;

    @Inject
    private CFindSCU findSCU;

    @Inject
    private ProcedureServiceEJB ejb;

    @Inject
    private Event<PatientMgtContext> patientEvent;

    @Inject
    private Event<ProcedureContext> procedureEvent;

    @Override
    public ProcedureContext createProcedureContext() {
        return new ProcedureContextImpl();
    }

    @Override
    public MWLItem findMWLItem(ProcedureContext ctx) {
        return ejb.findMWLItem(ctx);
    }

    @Override
    public ImportResult importMWL(HttpServletRequestInfo request, String mwlscu, String mwlscp, String destAET, int priority,
            QueryAttributes queryAttributes, boolean fuzzymatching, boolean filterbyscu, boolean delete,
            boolean simulate)
            throws Exception {
        ApplicationEntity localAE = device.getApplicationEntity(mwlscu, true);
        if (localAE == null || !localAE.isInstalled())
            throw new ConfigurationNotFoundException("No such Application Entity: " + mwlscu);

        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        AttributeFilter patAttrFilter = arcdev.getAttributeFilter(Entity.Patient);
        AttributeFilter mwlAttrFilter = arcdev.getAttributeFilter(Entity.MWL);
        Attributes matchingKeys = new Attributes(queryAttributes.getQueryKeys());
        QIDO.MWL.addReturnTags(queryAttributes);
        if (queryAttributes.isIncludeAll()) {
            queryAttributes.addReturnTags(mwlAttrFilter.getSelection(false));
            queryAttributes.addReturnTags(patAttrFilter.getSelection(false));
        }
        EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
        if (fuzzymatching) queryOptions.add(QueryOption.FUZZY);
        List<Attributes> mwlItems = findSCU.findMWLItems(localAE, mwlscp, queryOptions, priority,
                queryAttributes.getQueryKeys());
        if (filterbyscu)
            mwlItems.removeIf(item -> !item.matches(matchingKeys, false, false));

        Set<MWLItem.IDs> toDelete = delete
                ? ejb.findMWLItemIDs(localAE, destAET, matchingKeys, fuzzymatching)
                : Collections.emptySet();

        int created = 0;
        int updated = 0;
        List<Exception> exceptions = new ArrayList<>();
        for (Attributes mwlItem : mwlItems) {
            MWLItem.IDs spsID = new MWLItem.IDs(
                    mwlItem.getNestedDataset(Tag.ScheduledProcedureStepSequence)
                            .getString(Tag.ScheduledProcedureStepID),
                    mwlItem.getString(Tag.StudyInstanceUID));
            toDelete.remove(spsID);
            ProcedureContext ctx = createProcedureContext().setHttpServletRequest(request);
            ctx.setAttributes(mwlItem);
            ctx.setSpsID(spsID.scheduledProcedureStepID);
            ctx.setLocalAET(destAET);
            PatientMgtContext patCtx = null;
            try {
                patCtx = ejb.createOrUpdateMWLItem(ctx, simulate);
                String eventActionCode = ctx.getEventActionCode();
                if (eventActionCode != null) {
                    if (eventActionCode.equals(AuditMessages.EventActionCode.Create)) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
                ctx.setException(e);

            } finally {
                if (!simulate) {
                    if (patCtx != null && patCtx.getEventActionCode() != null) {
                        patientEvent.fire(patCtx);
                    }
                    procedureEvent.fire(ctx);
                }
            }
        }
        if (!simulate) {
            Iterator<MWLItem.IDs> iter = toDelete.iterator();
            while (iter.hasNext()) {
                try {
                    MWLItem.IDs next = iter.next();
                    ProcedureContext ctx = createProcedureContext().setHttpServletRequest(request);
                    ctx.setStudyInstanceUID(next.studyInstanceUID);
                    ctx.setSpsID(next.scheduledProcedureStepID);
                    deleteProcedure(ctx);
                } catch (Exception e) {
                    exceptions.add(e);
                    iter.remove();
                }
            }
        }
        return new ImportResult(mwlItems.size(), created, updated, toDelete.size(), exceptions);
    }

    @Override
    public void updateProcedure(ProcedureContext ctx) {
        try {
            ejb.updateProcedure(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                procedureEvent.fire(ctx);
        }
    }

    @Override
    public void deleteProcedure(ProcedureContext ctx) {
        ejb.deleteProcedure(ctx);
        if (ctx.getEventActionCode() != null) {
            LOG.info("Successfully deleted MWLItem {} from database." + ctx.getSpsID());
            procedureEvent.fire(ctx);
        }
    }

    @Override
    public void updateStudySeriesAttributes(ProcedureContext ctx) {
        try {
            ejb.updateStudySeriesAttributes(ctx);
        } finally {
            if (ctx.getEventActionCode() != null)
                procedureEvent.fire(ctx);
        }
    }

    @Override
    public List<MWLItem> updateMWLStatus(String studyIUID, SPSStatus status) {
        return ejb.updateMWLStatus(studyIUID, status);
    }

    @Override
    public void updateMWLStatus(ProcedureContext ctx) {
        try {
            ejb.updateMWLStatus(ctx);
        } catch (Exception e) {
          ctx.setException(e);
        } finally {
            if (ctx.getEventActionCode() != null)
                procedureEvent.fire(ctx);
        }
    }

    @Override
    public void updateMWLStatus(ProcedureContext ctx, SPSStatus from) {
        List<MWLItem.IDs> spsIDs = ejb.spsOfPatientWithStatus(ctx.getPatient(), from);
        for (MWLItem.IDs spsID : spsIDs) {
            ctx.setSpsID(spsID.scheduledProcedureStepID);
            ctx.setStudyInstanceUID(spsID.studyInstanceUID);
            ctx.setEventActionCode(null);
            ctx.setException(null);
            updateMWLStatus(ctx);
        }
    }

    @Override
    public int updateMatchingSPS(SPSStatus spsStatus, Attributes queryKeys, QueryParam queryParam,
                                  int mwlFetchSize) {
        return ejb.updateMatchingSPS(spsStatus, queryKeys, queryParam, mwlFetchSize);
    }

    public void onMPPS(@Observes MPPSContext ctx) {
        if (ctx.getException() != null)
            return;

        String mppsStatus = ctx.getAttributes().getString(Tag.PerformedProcedureStepStatus);
        if (mppsStatus != null) {
            MPPS mergedMPPS = ctx.getMPPS();
            Attributes mergedMppsAttr = mergedMPPS.getAttributes();
            Attributes ssaAttr = mergedMppsAttr.getNestedDataset(Tag.ScheduledStepAttributesSequence);
            ProcedureContext pCtx = createProcedureContext().setAssociation(ctx.getAssociation());
            pCtx.setPatient(mergedMPPS.getPatient());
            pCtx.setAttributes(ssaAttr);
            pCtx.setSpsStatus(mppsStatus.equals("IN PROGRESS") ? SPSStatus.STARTED : SPSStatus.valueOf(mppsStatus));
            pCtx.setMppsUID(mergedMPPS.getSopInstanceUID());
            try {
                if (ssaAttr.getString(Tag.ScheduledProcedureStepID) != null)
                    ejb.updateSPSStatus(pCtx);
            } catch (RuntimeException e) {
                pCtx.setException(e);
                LOG.warn(e.getMessage());
            } finally {
                if (pCtx.getEventActionCode() == null) {
                    pCtx.setStatus(mppsStatus);
                    pCtx.setEventActionCode(ctx.getDimse() == Dimse.N_CREATE_RQ
                            ? AuditMessages.EventActionCode.Create : AuditMessages.EventActionCode.Update);
                }
                procedureEvent.fire(pCtx);
            }

        }
    }
}
