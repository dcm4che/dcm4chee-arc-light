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

import org.dcm4che3.net.Status;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveStart;
import org.dcm4chee.arc.stow.client.StowClient;
import org.dcm4chee.arc.stow.client.StowTask;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
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
        return new StowTaskImpl(ctx, retrieveStart, retrieveEnd, accessTokenRequestor, openRequest(ctx));
    }

    private void openMultipleRequests(RetrieveContext ctx) throws DicomServiceException {

    }

    private Invocation.Builder openRequest(RetrieveContext ctx) throws DicomServiceException {
        try {
            WebApplication destinationWebApp = ctx.getDestinationWebApp();
            String uri = destinationWebApp.getServiceURL().toString();
            Map<String, String> properties = destinationWebApp.getProperties();
            ResteasyClient client = accessTokenRequestor.resteasyClientBuilder(
                    uri,
                    properties.containsKey("allowAnyHost") && Boolean.parseBoolean(properties.get("allowAnyHost")),
                    properties.containsKey("disableTM") && Boolean.parseBoolean(properties.get("disableTM")))
                    .build();
            return client.target(uri).request();
        } catch (Exception e) {
            LOG.info("Failed to build STOW request: ", e);
            DicomServiceException dse = new DicomServiceException(Status.UnableToPerformSubOperations, e);
            ctx.setException(dse);
            retrieveStart.fire(ctx);
            throw dse;
        }
    }
}
