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

package org.dcm4chee.arc.ups;

import org.dcm4che3.net.Association;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.entity.Subscription;
import org.dcm4chee.arc.entity.UPS;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;

import javax.websocket.Session;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */
public interface UPSService {

    UPSContext newUPSContext(Association as);

    UPSContext newUPSContext(HttpServletRequestInfo httpRequestInfo, ArchiveAEExtension arcAE);

    UPS createUPS(UPSContext ctx) throws DicomServiceException;

    UPS updateUPS(UPSContext ctx) throws DicomServiceException;

    UPS changeUPSState(UPSContext ctx) throws DicomServiceException;

    UPS requestUPSCancel(UPSContext ctx) throws DicomServiceException;

    UPS findUPS(UPSContext ctx) throws DicomServiceException;

    void createSubscription(UPSContext ctx) throws DicomServiceException;

    int deleteSubscription(UPSContext ctx) throws DicomServiceException;

    int suspendSubscription(UPSContext ctx) throws DicomServiceException;

    void registerWebsocketChannel(Session session, String aet, String subscriberAET);

    void unregisterWebsocketChannel(Session session);

    List<Session> getWebsocketChannels(String subscriberAET);
}
