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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.util.Base64;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.stow.client.StowTask;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Event;
import javax.json.Json;
import javax.json.JsonException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2021
 */
public class StowTaskImpl implements StowTask {
    private static final Logger LOG = LoggerFactory.getLogger(StowTaskImpl.class);
    private static InstanceLocations NO_MORE_MATCHES = new NoMoreMatches();
    private static final String boundary = UIDUtils.createUID();

    private final Event<RetrieveContext> retrieveStart;
    private final Event<RetrieveContext> retrieveEnd;
    private final RetrieveContext ctx;
    private final AccessTokenRequestor accessTokenRequestor;
    private final Invocation.Builder[] requests;
    private final BlockingQueue<InstanceLocations> matches = new LinkedBlockingQueue<>();
    private final CountDownLatch doneSignal;
    private volatile boolean canceled;

    public StowTaskImpl(RetrieveContext ctx, Event<RetrieveContext> retrieveStart, Event<RetrieveContext> retrieveEnd,
                        AccessTokenRequestor accessTokenRequestor, Invocation.Builder... requests) {
        this.ctx = ctx;
        this.retrieveStart = retrieveStart;
        this.retrieveEnd = retrieveEnd;
        this.requests = requests;
        this.accessTokenRequestor = accessTokenRequestor;
        this.doneSignal = new CountDownLatch(requests.length);
    }

    @Override
    public void cancel() {
        //TODO
    }

    @Override
    public void run() {
        retrieveStart.fire(ctx);
        try {
            if (requests.length > 1) startStoreOperations();
            for (InstanceLocations match : ctx.getMatches()) {
                if (!ctx.copyToRetrieveCache(match)) {
                    matches.offer(match);
                }
            }
            ctx.copyToRetrieveCache(null);
            if (requests.length == 1) {
                matches.offer(NO_MORE_MATCHES);
                runStoreOperations(requests[0]);
            } else {
                InstanceLocations match;
                while ((match = ctx.copiedToRetrieveCache()) != null && !canceled) {
                    matches.offer(match);
                }
                for (int i = 0; i < requests.length; i++) {
                    matches.offer(NO_MORE_MATCHES);
                }
            }
        } finally {
            ctx.getRetrieveService().updateLocations(ctx);
            SafeClose.close(ctx);
        }
        retrieveEnd.fire(ctx);
    }

    private void startStoreOperations() {
        Device device = ctx.getArchiveAEExtension().getApplicationEntity().getDevice();
        for (Invocation.Builder request : requests)
            device.execute(() -> runStoreOperations(request));
    }

    private void runStoreOperations(Invocation.Builder request) {
        try {
            InstanceLocations match;
            while (!canceled && (match = matches.take()) != NO_MORE_MATCHES)
                store(match, request);
            while (!canceled && (match = ctx.copiedToRetrieveCache()) != null)
                store(match, request);
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to fetch next match from queue:\n",
                    request, e);
        } finally {
            doneSignal.countDown();
        }
    }

    private void store(InstanceLocations inst, Invocation.Builder request) {
        String iuid = inst.getSopInstanceUID();
        WebApplication destinationWebApp = ctx.getDestinationWebApp();
        String tsuid = selectTransferSyntax(inst);
        if (tsuid == null) {
            LOG.info("Transfer syntax of instance location {} do not match with any transfer syntax configured in " +
                            "destination web application {}",
                    inst.getLocations().get(0).getTransferSyntaxUID(), destinationWebApp);
            ctx.incrementFailed();
            return;
        }

        try {
            authorize(request);
            MultipartRelatedOutput output = new MultipartRelatedOutput();
            output.setBoundary(boundary);
            writeDICOM(output, inst, tsuid);
            output.setBoundary(boundary);
            onStowRsp(toAttributes(request.post(Entity.entity(output, MediaTypes.MULTIPART_RELATED_APPLICATION_DICOM_TYPE))));
            //TODO - metrics
        } catch (Exception e) {
            ctx.incrementFailed();
            ctx.addFailedSOPInstanceUID(iuid);
            LOG.warn("{}: failed to send {} to {}:", request, inst, destinationWebApp, e);
        }
    }

    private void onStowRsp(Attributes rsp) {
        if (rsp == null)
            return;

        if (rsp.contains(Tag.FailedSOPSequence)) {
            ctx.incrementFailed();
            return;
        }
        Attributes refSOP = rsp.getNestedDataset(Tag.ReferencedSOPSequence);
        if (refSOP.contains(Tag.WarningReason))
            ctx.incrementWarning();
        else
            ctx.incrementCompleted();
    }

    private Attributes toAttributes(Response response) {
        Attributes rsp = null;
        try {
            rsp = new JSONReader(Json.createParser(new InputStreamReader(
                    response.readEntity(InputStream.class), StandardCharsets.UTF_8)))
                    .readDataset(null);
        } catch (JsonException e) {
            LOG.info("Invalid JSON payload");
            ctx.incrementFailed();
        } catch (Exception e) {
            ctx.incrementFailed();
        }
        return rsp;
    }

    private void writeDICOM(MultipartRelatedOutput output, InstanceLocations inst, String tsuid)  {
        DicomObjectOutput entity = new DicomObjectOutput(ctx, inst, Collections.singletonList(tsuid));
        output.addPart(entity,
                new MediaType(MediaTypes.APPLICATION_DICOM_TYPE.getType(),
                        MediaTypes.APPLICATION_DICOM_TYPE.getSubtype(),
                        new HashMap<String, String>().put("transfer-syntax", tsuid)));
    }

    private String selectTransferSyntax(InstanceLocations inst) {
        String tsuidsStr = ctx.getDestinationWebApp().getProperties().get("transfer-syntax");
        String instTSUID = inst.getLocations().get(0).getTransferSyntaxUID();
        if (tsuidsStr == null)
            return instTSUID;

        boolean hasExplicitVRLittleEndian = false;
        String[] tsuids = tsuidsStr.split(",");
        for (String tsuid : tsuids) {
            if (!hasExplicitVRLittleEndian)
                hasExplicitVRLittleEndian = tsuid.equals(UID.ExplicitVRLittleEndian) || tsuid.equals("ExplicitVRLittleEndian");

            if (instTSUID.equals(tsuid) || instTSUID.equals(UID.forName(tsuid)))
                return instTSUID;
        }
        return hasExplicitVRLittleEndian ? UID.ExplicitVRLittleEndian : null;
    }

    private void authorize(Invocation.Builder request) throws Exception {
        String user = ctx.getDestinationWebApp().getProperties().get("User");
        String authorization = user != null
                ? basicAuth(user)
                : bearer();
        if (authorization == null)
            return;

        request.header("Authorization", authorization);
    }

    private String basicAuth(String user) {
        byte[] userPswdBytes = user.getBytes();
        int len = (userPswdBytes.length * 4 / 3 + 3) & ~3;
        char[] ch = new char[len];
        Base64.encode(userPswdBytes, 0, userPswdBytes.length, ch, 0);
        return "Basic " + new String(ch);
    }

    private String bearer() throws Exception {
        String token = ctx.getDestinationWebApp().getProperties().get("Token");
        if (token == null && ctx.getDestinationWebApp().getKeycloakClient() == null)
            return null;

        return "Bearer "
                + (token != null ? token : accessTokenRequestor.getAccessToken2(ctx.getDestinationWebApp()).getToken());
    }

    private static final class NoMoreMatches implements InstanceLocations {
        @Override
        public Long getInstancePk() {
            return null;
        }

        @Override
        public void setInstancePk(Long pk) {
        }

        @Override
        public String getSopInstanceUID() {
            return null;
        }

        @Override
        public String getSopClassUID() {
            return null;
        }

        @Override
        public List<Location> getLocations() {
            return null;
        }

        @Override
        public Attributes getAttributes() {
            return null;
        }

        @Override
        public String getRetrieveAETs() {
            return null;
        }

        @Override
        public String getExternalRetrieveAET() {
            return null;
        }

        @Override
        public Availability getAvailability() {
            return null;
        }

        @Override
        public Date getCreatedTime() {
            return null;
        }

        @Override
        public Date getUpdatedTime() {
            return null;
        }

        @Override
        public Attributes getRejectionCode() {
            return null;
        }

        @Override
        public boolean isContainsMetadata() {
            return false;
        }

        @Override
        public boolean isImage() {
            return false;
        }

        @Override
        public boolean isVideo() {
            return false;
        }

        @Override
        public boolean isMultiframe() {
            return false;
        }
    }
}
