/*
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 */

package org.dcm4chee.arc.fhir.client.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4chee.arc.fhir.client.FHIRClient;
import org.dcm4chee.arc.fhir.client.ImagingStudy;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.query.QueryService;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Oct 2025
 */
@ApplicationScoped
public class FHIRClientImpl implements FHIRClient {

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private AccessTokenRequestor accessTokenRequestor;

    @Override
    public Response create(ApplicationEntity ae, String studyUID, WebApplication webApp) {
        ImagingStudy imagingStudy = imagingStudy(webApp);
        String url = webApp.getServiceURL().append("/ImagingStudy").toString();
        Map<String, Attributes> seriesAttrs = new HashMap<>();
        Attributes info = queryService.getImagingStudyInfo(studyUID, ae, seriesAttrs);
        try (ResteasyClient client = accessTokenRequestor.resteasyClientBuilder(url, webApp).build()) {
            WebTarget target = client.target(url);
            Invocation.Builder request = target.request();
            String token = accessTokenRequestor.authorizationHeader(webApp);
            if (token != null)
                request.header("Authorization", token);

            Response response = request.post(imagingStudy.create(device, info, seriesAttrs));
            if (response.getEntity() instanceof InputStream is) {
                // to prevent java.net.SocketException: Socket closed caused by ResteasyClient.close()
                response = Response.fromResponse(response)
                        .entity(is.readAllBytes())
                        .build();
            }
            return response;
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_GATEWAY).entity(e).build();
        }
    }

    private static ImagingStudy imagingStudy(WebApplication webApp) {
        String imagingStudy = webApp.getProperty("ImagingStudy", null);
        return imagingStudy != null ? ImagingStudy.valueOf(imagingStudy) : ImagingStudy.FHIR_R5_JSON;
    }

}
