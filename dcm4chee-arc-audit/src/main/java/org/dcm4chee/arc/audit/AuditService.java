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

package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.ActiveParticipant;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.EventIdentification;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Principal;

import java.util.Calendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    @Inject
    private Device device;

    private AuditLogger log() {
        return device.getDeviceExtension(AuditLogger.class);
    }

    public void onArchiveServiceEvent(@Observes ArchiveServiceEvent event, Principal user) {
        Calendar timestamp = log().timeStamp();
        ArchiveServiceEvent.Type type = event.getType();
        HttpServletRequest request = event.getRequest();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.ApplicationActivity);
        if (type.equals(type.STARTED)) {
            ei.getEventTypeCode().add(AuditMessages.EventTypeCode.ApplicationStart);
            ei.setEventActionCode("E");
            ei.setEventOutcomeIndicator("0");
        }
        if (type.equals(type.STOPPED)) {
            ei.getEventTypeCode().add(AuditMessages.EventTypeCode.ApplicationStop);
            ei.setEventActionCode("E");
            ei.setEventOutcomeIndicator("0");
        }
        ei.setEventDateTime(timestamp);
        msg.setEventIdentification(ei);
        ActiveParticipant apApplication = new ActiveParticipant();
        apApplication.getRoleIDCode().add(AuditMessages.RoleIDCode.Application);
        apApplication.setUserID(device.getDeviceName());
        String aeTitle = "";
        for (ApplicationEntity ae : device.getApplicationEntities()) {
            aeTitle += ae.getAETitle() + ";";
        }
        apApplication.setAlternativeUserID(aeTitle);
        apApplication.setUserIsRequestor(false);
        msg.getActiveParticipant().add(apApplication);

        if (request != null) {
            ActiveParticipant apUser = new ActiveParticipant();
            apUser.getRoleIDCode().add(AuditMessages.RoleIDCode.ApplicationLauncher);
            if (null == request.getRemoteUser()) {
                apUser.setUserID(request.getRemoteAddr());
                apUser.setNetworkAccessPointTypeCode("2");
                apUser.setNetworkAccessPointID(request.getRemoteAddr());
            }
            if (null != request.getRemoteUser()) {
                apUser.setUserID(request.getRemoteUser());
            }
            apUser.setUserIsRequestor(true);
            msg.getActiveParticipant().add(apUser);
        }

        try {
            log().write(timestamp, msg);
        } catch (IncompatibleConnectionException icc) {
            LOG.info("Incompatible Connection Exception : " + icc.getMessage());
        }
        catch (GeneralSecurityException gse) {
            LOG.info("General Security Exception : " + gse.getMessage());
        }
        catch (IOException io) {
            LOG.info("IO Exception : " + io.getMessage());
        }
    }

    public void onStore(@Observes StoreContext ctx) {
        StoreSession session = ctx.getStoreSession();
        String hostname = session.getRemoteHostName();
        String sendingAET = session.getCallingAET();
        String receivingAET = session.getCalledAET();
        Attributes attrs = ctx.getAttributes();
        //TODO
    }
}
