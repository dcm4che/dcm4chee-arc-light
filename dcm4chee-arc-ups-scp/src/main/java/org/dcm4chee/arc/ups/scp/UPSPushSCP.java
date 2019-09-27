/*
 * **** BEGIN LICENSE BLOCK *****
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.ups.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.DicomService;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
@ApplicationScoped
@Typed(DicomService.class)
public class UPSPushSCP extends AbstractDicomService {

    @Inject
    private UPSService service;

    public UPSPushSCP() {
        super(UID.UnifiedProcedureStepPushSOPClass);
    }

    @Override
    public void onDimseRQ(Association as, PresentationContext pc, Dimse dimse, Attributes rq, Attributes rqAttrs)
            throws IOException {
        switch (as.getAbstractSyntax(pc.getPCID())) {
            case UID.UnifiedProcedureStepPushSOPClass:
                switch (dimse) {
                    case N_CREATE_RQ:
                        onNCreateRQ(as, pc, rq, rqAttrs);
                        return;
                    case N_GET_RQ:
                        onNGetRQ(as, pc, rq, rqAttrs);
                        return;
                    case N_ACTION_RQ:
                        onNActionRQ(as, pc, rq, rqAttrs, 2);
                        return;
                }
            case UID.UnifiedProcedureStepPullSOPClass:
                switch (dimse) {
                    case N_SET_RQ:
                        onNSetRQ(as, pc, rq, rqAttrs);
                        return;
                    case N_GET_RQ:
                        onNGetRQ(as, pc, rq, rqAttrs);
                        return;
                    case N_ACTION_RQ:
                        onNActionRQ(as, pc, rq, rqAttrs, 1);
                        return;
                }
            case UID.UnifiedProcedureStepWatchSOPClass:
                switch (dimse) {
                    case N_GET_RQ:
                        onNGetRQ(as, pc, rq, rqAttrs);
                        return;
                    case N_ACTION_RQ:
                        onNActionRQ(as, pc, rq, rqAttrs, 254);
                        return;
                }
        }
        throw new DicomServiceException(Status.UnrecognizedOperation);
    }

    private void onNCreateRQ(Association as, PresentationContext pc, Attributes rq, Attributes rqAttrs)
            throws IOException {
        Attributes rsp = Commands.mkNCreateRSP(rq, Status.Success);
        Attributes rspAttrs = create(as, rq, rqAttrs, rsp);
        as.tryWriteDimseRSP(pc, rsp, rspAttrs);
    }

    private void onNSetRQ(Association as, PresentationContext pc, Attributes rq, Attributes rqAttrs)
            throws IOException {
        Attributes rsp = Commands.mkNSetRSP(rq, Status.Success);
        Attributes rspAttrs = set(as, rq, rqAttrs, rsp);
        as.tryWriteDimseRSP(pc, rsp, rspAttrs);
    }

    private void onNGetRQ(Association as, PresentationContext pc, Attributes rq, Attributes rqAttrs)
            throws IOException {
        Attributes rsp = Commands.mkNGetRSP(rq, Status.Success);
        Attributes rspAttrs = get(as, rq, rqAttrs, rsp);
        as.tryWriteDimseRSP(pc, rsp, rspAttrs);
    }

    private void onNActionRQ(Association as, PresentationContext pc, Attributes rq, Attributes rqAttrs,
            int validActionTypeIDs) throws IOException {
        Attributes rsp = Commands.mkNActionRSP(rq, Status.Success);
        Attributes rspAttrs = action(as, rq, rqAttrs, rsp, validActionTypeIDs);
        as.tryWriteDimseRSP(pc, rsp, rspAttrs);
    }

    private Attributes create(Association as, Attributes rq, Attributes rqAttrs, Attributes rsp)
            throws DicomServiceException {
        UPSContext ctx = service.newUPSContext(as);
        ctx.setUpsInstanceUID(rq.getString(Tag.AffectedSOPInstanceUID));
        ctx.setAttributes(rqAttrs);
        service.createUPS(ctx);
        return null;
    }

    private Attributes set(Association as, Attributes rq, Attributes rqAttrs, Attributes rsp)
            throws DicomServiceException {
        UPSContext ctx = service.newUPSContext(as);
        ctx.setUpsInstanceUID(rq.getString(Tag.RequestedSOPInstanceUID));
        ctx.setAttributes(rqAttrs);
        service.updateUPS(ctx);
        return null;
    }

    private Attributes get(Association as, Attributes rq, Attributes rqAttrs, Attributes rsp)
            throws DicomServiceException {
        UPSContext ctx = service.newUPSContext(as);
        ctx.setUpsInstanceUID(rq.getString(Tag.RequestedSOPInstanceUID));
        service.findUPS(ctx);
        return filter(ctx.getAttributes(), rq.getInts(Tag.AttributeIdentifierList));
    }

    private Attributes filter(Attributes attrs, int[] tags) {
        if (tags == null) {
            return attrs;
        }
        Arrays.sort(tags);
        return new Attributes(attrs, tags);
    }

    private Attributes action(Association as, Attributes rq, Attributes rqAttrs, Attributes rsp, int validActionTypeIDs)
            throws DicomServiceException {
        int actionTypeID = validateActionTypeID(rq.getInt(Tag.ActionTypeID, 0), validActionTypeIDs);
        UPSContext ctx = service.newUPSContext(as);
        ctx.setUpsInstanceUID(rq.getString(Tag.RequestedSOPInstanceUID));
        switch(actionTypeID) {
            case 1:
                ctx.setAttributes(rqAttrs);
                service.changeUPSState(ctx);
                break;
            case 2:
                ctx.setAttributes(rqAttrs);
                service.requestUPSCancel(ctx);
                break;
            case 3:
                ctx.setSubscriberAET(requireNonNull(rqAttrs, Tag.ReceivingAE));
                ctx.setDeletionLock(validateDeletionLock(rqAttrs));
                if (ctx.getUpsInstanceUID().equals(UID.UPSFilteredGlobalSubscriptionSOPInstance)) {
                    rqAttrs.removeSelected(Tag.DeletionLock, Tag.ReceivingAE);
                    ctx.setAttributes(rqAttrs);
                }
                service.createSubscription(ctx);
                break;
            case 4:
                ctx.setSubscriberAET(requireNonNull(rqAttrs, Tag.ReceivingAE));
                service.deleteSubscription(ctx);
                break;
            case 5:
                ctx.setSubscriberAET(requireNonNull(rqAttrs, Tag.ReceivingAE));
                service.suspendSubscription(ctx);
                break;
        }
        return null;
    }

    private boolean validateDeletionLock(Attributes rqAttrs) throws DicomServiceException {
        switch (requireNonNull(rqAttrs, Tag.DeletionLock)) {
            case "TRUE":
                return true;
            case "FALSE":
                return false;
        }
        throw new DicomServiceException(Status.InvalidArgumentValue);
    }

    private static String requireNonNull(Attributes rqAttrs, int tag) throws DicomServiceException {
        String value = rqAttrs.getString(tag);
        if (value == null) {
            throw new DicomServiceException(Status.InvalidArgumentValue);
        }
        return value;
    }

    private static int validateActionTypeID(int actionTypeID, int validActionTypeIDs) throws DicomServiceException {
        if (actionTypeID <= 0 || ((1 << (actionTypeID - 1)) & validActionTypeIDs) == 0) {
            throw new DicomServiceException(Status.NoSuchActionType).setActionTypeID(actionTypeID);
        }
        return actionTypeID;
    }
}
