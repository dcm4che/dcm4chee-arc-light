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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.HL7PSUTask;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.procedure.ImportResult;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QIDO;
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
    public ImportResult importMWL(HttpServletRequestInfo request, String mwlscu, String mwlscp, String destAET,
            int priority, Attributes filter, Attributes keys, boolean fuzzymatching, boolean filterbyscu,
            boolean delete, boolean simulate)
            throws Exception {
        ApplicationEntity localAE = device.getApplicationEntity(mwlscu, true);
        if (localAE == null || !localAE.isInstalled())
            throw new ConfigurationNotFoundException("No such Application Entity: " + mwlscu);

        EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
        if (fuzzymatching) queryOptions.add(QueryOption.FUZZY);
        List<Attributes> mwlItems = findSCU.findMWLItems(localAE, mwlscp, queryOptions, priority, keys);
        if (filterbyscu)
            mwlItems.removeIf(item -> !item.matches(filter, false, false));

        Set<MWLItem.IDs> toDelete = delete
                ? ejb.findMWLItemIDs(localAE, destAET, filter, fuzzymatching)
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
            ctx.setSourceMwlScp(mwlscp);
            try {
                ejb.createOrUpdateMWLItem(ctx, simulate);
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
                    if (ctx.getEventActionCode() != null) {
                        procedureEvent.fire(ctx);
                    }
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
    public ImportResult importMWL(MWLImport rule) throws Exception {
        Attributes filter = toFilter(rule);
        return importMWL(null, rule.getAETitle(), rule.getMWLSCP(), rule.getDestinationAE(), Priority.NORMAL,
                filter, toKeys(rule, filter), false, rule.isFilterBySCU(), rule.isDeleteNotFound(), false);
    }

    private static Attributes toFilter(MWLImport rule) {
        Attributes keys = new Attributes();
        DateRange range = toDateRange(rule.getNotOlderThan(), rule.getPrefetchBefore());
        if (range != null) {
            Attributes item = new Attributes();
            item.setDateRange(Tag.ScheduledProcedureStepStartDateAndTime, range);
            keys.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(item);
        }
        AttributesBuilder builder = new AttributesBuilder(keys);
        for (Map.Entry<String, String> entry : rule.getFilter().entrySet()) {
            builder.setString(TagUtils.parseTagPath(entry.getKey()), entry.getValue());
        }
        return keys;
    }

    private static DateRange toDateRange(Duration notOlderThan, Duration prefetchBefore) {
        if (notOlderThan == null && prefetchBefore == null) return null;
        long now = System.currentTimeMillis();
        return new DateRange(
                notOlderThan != null ? new Date(now - notOlderThan.getSeconds() * 1000) : null,
                prefetchBefore != null ? new Date(now + prefetchBefore.getSeconds() * 1000) : null);
    }

    private Attributes toKeys(MWLImport rule, Attributes filter) {
        Attributes keys = new Attributes(filter);
        AttributesBuilder builder = new AttributesBuilder(keys);
        String[] includeFields = rule.getIncludeFields();
        if (StringUtils.contains(includeFields, "all")) {
            ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            AttributesBuilder.setNullIfAbsent(keys,
                    arcdev.getAttributeFilter(Entity.MWL).getSelection(false));
            AttributesBuilder.setNullIfAbsent(keys,
                    arcdev.getAttributeFilter(Entity.Patient).getSelection(false));
        } else {
            for (String tagPath : includeFields) {
                builder.setNullIfAbsent(TagUtils.parseTagPath(tagPath));
            }
            AttributesBuilder.setNullIfAbsent(keys, QIDO.MWL.includetags);
            Attributes spsKeys = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
            if (spsKeys != null && !spsKeys.isEmpty())
                AttributesBuilder.setNullIfAbsent(spsKeys, QIDO.MWL_SPS.includetags);
        }
        return keys;
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
    public List<MWLItem> updateMWLStatus(ArchiveAEExtension arcAE, HL7PSUTask task, SPSStatus status) {
        return ejb.updateMWLStatus(arcAE, task, status);
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
            ProcedureContext pCtx = createProcedureContext().setAssociation(ctx.getAssociation());
            pCtx.setPatient(mergedMPPS.getPatient());
            pCtx.setSpsStatus(mppsStatus.equals("IN PROGRESS") ? SPSStatus.STARTED : SPSStatus.valueOf(mppsStatus));
            pCtx.setMppsUID(mergedMPPS.getSopInstanceUID());
            for (Attributes attrs : mergedMPPS.getAttributes().getSequence(Tag.ScheduledStepAttributesSequence)) {
                pCtx.setSpsID(attrs.getString(Tag.ScheduledProcedureStepID));
                pCtx.setAttributes(attrs);
                if (pCtx.getSpsID() != null)
                    updateSPSStatus(pCtx, ctx);
            }
        }
    }

    private void updateSPSStatus(ProcedureContext pCtx, MPPSContext ctx) {
        try {
            ejb.updateSPSStatus(pCtx);
        } catch (RuntimeException e) {
            pCtx.setException(e);
            LOG.warn(e.getMessage());
        } finally {
            if (pCtx.getEventActionCode() == null) {
                pCtx.setStatus(ctx.getAttributes().getString(Tag.PerformedProcedureStepStatus));
                pCtx.setEventActionCode(ctx.getDimse() == Dimse.N_CREATE_RQ
                        ? AuditMessages.EventActionCode.Create : AuditMessages.EventActionCode.Update);
            }
            procedureEvent.fire(pCtx);
        }
    }
}
