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

package org.dcm4chee.arc.stow.client.impl;

import org.dcm4che3.data.UID;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.Base64;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveStart;
import org.dcm4chee.arc.stow.client.StowClient;
import org.dcm4chee.arc.stow.client.StowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2021
 */
@ApplicationScoped
public class StowClientImpl implements StowClient {
    static final Logger LOG = LoggerFactory.getLogger(StowClientImpl.class);

    @Inject
    @RetrieveStart
    private Event<RetrieveContext> retrieveStart;

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    @Inject
    private AccessTokenRequestor accessTokenRequestor;

    @Override
    public StowTask newStowTask(RetrieveContext ctx) throws DicomServiceException {
        try {
            WebApplication webApp = ctx.getDestinationWebApp();
            Map<String, String> props = webApp.getProperties();
            String url = webApp.getServiceURL().append("/studies").toString();
            Client client = accessTokenRequestor.resteasyClientBuilder(
                    url,
                    Boolean.parseBoolean(props.get("allow-any-hostname")),
                    Boolean.parseBoolean(props.get("disable-trust-manager")))
                    .build();
            Invocation.Builder request = client.target(url).request(MediaTypes.APPLICATION_DICOM_JSON);
            if (webApp.getKeycloakClientID() != null) {
                request.header("Authorization", "Bearer " + accessTokenRequestor.getAccessToken2(webApp));
            } else if (props.containsKey("bearer-token")) {
                request.header("Authorization", "Bearer " + props.get("bearer-token"));
            } else if (props.containsKey("basic-auth")) {
                request.header("Authorization", "Basic "
                        + encodeBase64(props.get("basic-auth").getBytes(StandardCharsets.UTF_8)));
            }
            return new StowTaskImpl(ctx, retrieveStart, retrieveEnd, request,
                    uidsOf(props.get("transfer-syntax")),
                    props.containsKey("concurrency")
                            ? Integer.parseInt(props.get("concurrency"))
                            : 1);
        } catch (Exception e) {
            LOG.info("Failed to build STOW request: ", e);
            DicomServiceException dse = new DicomServiceException(Status.UnableToPerformSubOperations, e);
            ctx.setException(dse);
            throw dse;
        }
    }

    private static List<String> uidsOf(String s) {
        String[] uids = StringUtils.split(s, ',');
        for (int i = 0; i < uids.length; i++) {
            if (Character.isLetter((uids[i] = uids[i].trim()).charAt(0)))
                uids[i] = UID.forName(uids[i]);
        }
        return Arrays.asList(uids);
    }

    private static String encodeBase64(byte[] b) {
        int len = (b.length * 4 / 3 + 3) & ~3;
        char[] ch = new char[len];
        Base64.encode(b, 0, b.length, ch, 0);
        return new String(ch);
    }

}
