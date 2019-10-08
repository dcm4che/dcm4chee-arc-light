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

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.ups.UPSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Oct 2019
 */
@ApplicationScoped
public class UPSEventSCP {
    private static final Logger LOG = LoggerFactory.getLogger(UPSEventSCP.class);

    private final ConcurrentHashMap<FromTo, Association> reuseAssociations = new ConcurrentHashMap<>();

    @Inject
    private IApplicationEntityCache aeCache;

    public void onUPSEvent(@Observes UPSEvent event) {
        Optional<Attributes> inprocessStateReport = event.inprocessStateReport();
        int keepAliveTimeout = event.arcAE.upsEventSCUKeepAlive();
        boolean keepAlive = keepAliveTimeout > 0;
        for (String subscriberAET : event.subscriberAETs) {
            if (event.arcAE.isUPSEventSCU(subscriberAET)) {
                try {
                    ApplicationEntity localAE = event.arcAE.getApplicationEntity();
                    Association as = keepAlive
                            ? getAssociation(localAE, subscriberAET, keepAliveTimeout)
                            : localAE.connect(
                                    aeCache.findApplicationEntity(subscriberAET),
                                    mkAAssociateRQ(localAE, subscriberAET));
                    try {
                        if (inprocessStateReport.isPresent()) {
                            sendNEventReport(as, event, inprocessStateReport.get());
                        }
                        sendNEventReport(as, event, event.attrs);
                    } finally {
                        if (!keepAlive) {
                            as.release();
                        }
                    }
                } catch (Exception e) {
                    LOG.info("Failed to send {} EventReport to {} - {}", event.type, subscriberAET, e.getMessage());
                }
            }
        }
    }

    private Association getAssociation(ApplicationEntity localAE, String subscriberAET, int keepAlive)
            throws Exception {
        FromTo fromTo = new FromTo(localAE.getAETitle(), subscriberAET);
        Association as = reuseAssociations.get(fromTo);
        if (!as.isReadyForDataTransfer()) {
            ApplicationEntity remote = aeCache.get(subscriberAET);
            CompatibleConnection cc = localAE.findCompatibleConnection(remote);
            Connection localConnection = new Connection(cc.getLocalConnection());
            localConnection.setIdleTimeout(keepAlive);
            as = localAE.connect(localConnection, cc.getRemoteConnection(), mkAAssociateRQ(localAE, subscriberAET));
            reuseAssociations.put(fromTo, as);
        }
        return as;
    }

    private void sendNEventReport(Association as, UPSEvent event, Attributes attrs) throws Exception {
        DimseRSP dimseRSP = as.neventReport(
                UID.UnifiedProcedureStepEventSOPClass,
                UID.UnifiedProcedureStepPushSOPClass,
                event.upsIUID,
                event.type.eventTypeID(),
                attrs,
                null);
        dimseRSP.next();
        int status = dimseRSP.getCommand().getInt(Tag.Status, -1);
        if (status != 0) {
            throw new DicomServiceException(status);
        }
    }

    private static AAssociateRQ mkAAssociateRQ(ApplicationEntity localAE, String subscriberAET) {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.setCalledAET(subscriberAET);
        TransferCapability tc = localAE.getTransferCapabilityFor(UID.UnifiedProcedureStepEventSOPClass,
                TransferCapability.Role.SCP);
        aarq.addPresentationContext(new PresentationContext(1, UID.UnifiedProcedureStepEventSOPClass,
                tc != null ? tc.getTransferSyntaxes() : new String[] { UID.ImplicitVRLittleEndian }));
        aarq.addRoleSelection(new RoleSelection(UID.UnifiedProcedureStepEventSOPClass, false, true));
        return aarq;
    }

    private static class FromTo {
        final String callingAET;
        final String calledAET;

        FromTo(String callingAET, String calledAET) {
            this.callingAET = callingAET;
            this.calledAET = calledAET;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FromTo fromTo = (FromTo) o;
            return callingAET.equals(fromTo.callingAET) &&
                    calledAET.equals(fromTo.calledAET);
        }

        @Override
        public int hashCode() {
            return Objects.hash(callingAET, calledAET);
        }
    }
}
