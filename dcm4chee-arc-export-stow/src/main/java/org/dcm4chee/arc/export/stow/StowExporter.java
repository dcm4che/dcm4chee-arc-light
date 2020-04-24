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
 * Portions created by the Initial Developer are Copyright (C) 2016
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

package org.dcm4chee.arc.export.stow;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.google.auth.oauth2.OAuth2Credentials;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.QueueMessage.Status;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Optional;

public class StowExporter extends AbstractExporter {

    private static Logger LOG = LoggerFactory.getLogger(StowExporter.class);

    private final RetrieveService retrieveService;
    private final AccessTokenRequestor accessTokenRequestor;
    private final IWebApplicationCache iWebAppCache;
    private final String webApplicationName;

    private Optional<OAuth2Credentials> credentials;


    public StowExporter(ExporterDescriptor descriptor, RetrieveService retrieveService, AccessTokenRequestor accessTokenRequestor, IWebApplicationCache iWebAppCache) {
        super(descriptor);
        this.retrieveService = retrieveService;
        this.webApplicationName = descriptor.getExportURI().getSchemeSpecificPart();
        this.credentials = Optional.empty();
        this.accessTokenRequestor = accessTokenRequestor;
        this.iWebAppCache = iWebAppCache;
        try {
            this.credentials = Optional.of(GoogleCredentials.getApplicationDefault().createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform".split(","))));
        } catch (IOException e) {
            LOG.warn("Credentials not configured");
        }
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        RetrieveContext ctx = this.retrieveService.newRetrieveContextWADO(exportContext.getHttpServletRequestInfo(), exportContext.getAETitle(), exportContext.getStudyInstanceUID(), exportContext.getSeriesInstanceUID(), exportContext.getSopInstanceUID());
        if (!this.retrieveService.calculateMatches(ctx)) {
            return new Outcome(QueueMessage.Status.WARNING, "No matches found.");
        }
        List<InstanceLocations> matches = ctx.getMatches();

        int numMatches = matches.size();
        InstanceLocations inst = matches.get(numMatches >>> 1);

        MultipartRelatedOutput output = new MultipartRelatedOutput();
        StreamingOutput entity = new StowObjectOutput(ctx, inst);
        output.addPart(entity, MediaTypes.APPLICATION_DICOM_TYPE);

        return invoke(this.webApplicationName, output);

    }

    private Outcome invoke(String webApplicationName, Object entity) {
        String token = "";
        WebApplication webApplication = null;
        String urlString = null;
        try {
            webApplication = iWebAppCache.findWebApplication(webApplicationName);
            urlString = webApplication.getServiceURL().toString();
        } catch (ConfigurationException e) {
            return new Outcome(Status.FAILED, e.getMessage());
        }

        if (credentials.isPresent()) {
            try {
                credentials.get().getRequestMetadata();
            } catch (IOException e) {
                LOG.warn(e.getMessage());
            }
            token = credentials.get().getAccessToken().getTokenValue();
        } else {
            try {
                token = accessTokenRequestor.getAccessToken2(webApplication).getToken();
            } catch (Exception e) {
                LOG.warn(e.getMessage());
            }

        }

        ResteasyClient client = null;
        try {
            client = accessTokenRequestor.resteasyClientBuilder(urlString, true, true)
                    .build();
        } catch (Exception e) {
            return new Outcome(Status.FAILED, e.getMessage());
        }
        WebTarget target = client.target(urlString);
        Invocation.Builder request = target.request();
        request.header("Authorization", "Bearer " + token);
        Response response = request.post(Entity.entity(entity, MediaTypes.MULTIPART_RELATED_TYPE));
        int responseCode = response.getStatus();
        if (responseCode != HttpURLConnection.HTTP_OK)
            return new Outcome(Status.FAILED, String.valueOf(responseCode));
        return new Outcome(QueueMessage.Status.COMPLETED, "Uploaded by STOW Exporter ");
    }


    private class StowObjectOutput implements StreamingOutput {

        private final RetrieveContext ctx;
        private final InstanceLocations inst;

        public StowObjectOutput(RetrieveContext ctx, InstanceLocations inst) {
            this.ctx = ctx;
            this.inst = inst;
        }

        @Override
        public void write(final OutputStream out) throws IOException {
            try (Transcoder transcoder = ctx.getRetrieveService().openTranscoder(ctx, inst, Collections.EMPTY_LIST, true)) {
                transcoder.transcode(new Transcoder.Handler() {
                    @Override
                    public OutputStream newOutputStream(Transcoder transcoder, Attributes dataset) throws IOException {
                        ctx.getRetrieveService().getAttributesCoercion(ctx, inst).coerce(dataset, null);
                        return out;
                    }
                });
            }
        }
    }

}
