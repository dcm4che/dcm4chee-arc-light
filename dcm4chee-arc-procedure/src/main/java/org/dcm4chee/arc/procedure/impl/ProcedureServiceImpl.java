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

import org.dcm4che3.data.*;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@ApplicationScoped
public class ProcedureServiceImpl implements ProcedureService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureServiceImpl.class);

    @Inject
    private ProcedureServiceEJB ejb;

    @Inject
    private Event<ProcedureContext> procedureEvent;

    @Override
    public ProcedureContext createProcedureContextHL7(Socket s, UnparsedHL7Message hl7msg) {
        return new ProcedureContextImpl(null, null, s, hl7msg);
    }

    @Override
    public ProcedureContext createProcedureContextWEB(HttpServletRequest httpRequest) {
        return new ProcedureContextImpl(httpRequest, null,  null, null);
    }

    @Override
    public ProcedureContext createProcedureContextAssociation(Association as) {
        return new ProcedureContextImpl(null, as, as.getSocket(), null);
    }

    @Override
    public MWLItem findMWLItem(ProcedureContext ctx) {
        return ejb.findMWLItem(ctx);
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
    public int updateSPSStatusToCompleted(String studyIUID) {
        return ejb.updateSPSStatusToCompleted(studyIUID);
    }

    public void onMPPS(@Observes MPPSContext ctx) {
        Attributes attr = ctx.getAttributes();
        String mppsStatus = attr.getString(Tag.PerformedProcedureStepStatus);
        if (mppsStatus != null) {
            MPPS mergedMPPS = ctx.getMPPS();
            Attributes mergedMppsAttr = mergedMPPS.getAttributes();
            Attributes ssaAttr = mergedMppsAttr.getNestedDataset(Tag.ScheduledStepAttributesSequence);
            ProcedureContext pCtx = createProcedureContextAssociation(ctx.getAssociation());
            mppsStatus = mppsStatus.equals("IN PROGRESS") ? SPSStatus.STARTED.toString() : mppsStatus;
            pCtx.setPatient(mergedMPPS.getPatient());
            pCtx.setStudyInstanceUID(ssaAttr.getString(Tag.StudyInstanceUID));
            if (ssaAttr.getString(Tag.ScheduledProcedureStepID) != null) {
                try {
                    ejb.updateSPSStatus(pCtx, mppsStatus);
                } catch (RuntimeException e) {
                    pCtx.setException(e);
                    LOG.warn(e.getMessage());
                } finally {
                    if (pCtx.getEventActionCode() != null)
                        procedureEvent.fire(pCtx);
                }
            }
        }
    }
}
