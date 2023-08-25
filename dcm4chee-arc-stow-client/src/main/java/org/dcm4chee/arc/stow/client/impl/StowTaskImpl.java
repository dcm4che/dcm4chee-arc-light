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

import jakarta.enterprise.event.Event;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.InvocationCallback;
import jakarta.ws.rs.core.Response;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.stream.DicomObjectOutput;
import org.dcm4chee.arc.rs.util.MediaTypeUtils;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.stow.client.StowTask;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2021
 */
public class StowTaskImpl implements StowTask {
    private static final Logger LOG = LoggerFactory.getLogger(StowTaskImpl.class);

    private final Event<RetrieveContext> retrieveStart;
    private final Event<RetrieveContext> retrieveEnd;
    private final RetrieveContext ctx;
    private final BlockingQueue<WrappedInstanceLocations> matches = new LinkedBlockingQueue<>();
    private final ResteasyWebTarget target;
    private final String authorization;
    private final Collection<String> acceptableTransferSyntaxes;
    private final int concurrency;
    private final Semaphore semaphore;
    private volatile boolean canceled;

    public StowTaskImpl(RetrieveContext ctx, Event<RetrieveContext> retrieveStart, Event<RetrieveContext> retrieveEnd,
            ResteasyWebTarget target, String authorization, Collection<String> acceptableTransferSyntaxes,
            int concurrency) {
        this.ctx = ctx;
        this.retrieveStart = retrieveStart;
        this.retrieveEnd = retrieveEnd;
        this.target = target;
        this.authorization = authorization;
        this.acceptableTransferSyntaxes = acceptableTransferSyntaxes;
        this.concurrency = concurrency;
        this.semaphore = concurrency > 1 ? new Semaphore(concurrency) : null;
    }

    @Override
    public void cancel() {
        canceled = true;
    }

    @Override
    public void run() {
        retrieveStart.fire(ctx);
        try {
            for (InstanceLocations match : ctx.getMatches()) {
                if (!ctx.copyToRetrieveCache(match)) {
                    matches.offer(new WrappedInstanceLocations(match));
                }
            }
            ctx.copyToRetrieveCache(null);
            matches.offer(new WrappedInstanceLocations(null));
            runStoreOperations();
        } finally {
            if (semaphore != null) {
                try {
                    semaphore.acquire(concurrency);
                } catch (InterruptedException e) {
                    LOG.warn("{}: failed to wait for pending responses:\n", target, e);
                }
            }
            target.getResteasyClient().close();
            ctx.getRetrieveService().updateLocations(ctx);
            SafeClose.close(ctx);
        }
        retrieveEnd.fire(ctx);
    }

    private void runStoreOperations() {
        try {
            InstanceLocations match;
            while (!canceled && (match = matches.take().instanceLocations) != null)
                store(match);
            while (!canceled && (match = ctx.copiedToRetrieveCache()) != null)
                store(match);
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to fetch next match from queue:\n", target, e);
        }
    }

    private void store(InstanceLocations inst) {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        output.addPart(new DicomObjectOutput(ctx, inst, acceptableTransferSyntaxes),
                MediaTypes.applicationDicomWithTransferSyntax(MediaTypeUtils.selectTransferSyntax(
                        acceptableTransferSyntaxes, inst.getLocations().get(0).getTransferSyntaxUID())));
        Entity<MultipartRelatedOutput> entity = Entity.entity(output,
                MediaTypes.MULTIPART_RELATED_APPLICATION_DICOM_TYPE);
        Invocation.Builder request = target.request(MediaTypes.APPLICATION_DICOM_JSON);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        InvocationCallback<Response> callback = new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {
                onStowRsp(toAttributes(response));
                response.close();
                if (semaphore != null) semaphore.release();
            }

            @Override
            public void failed(Throwable e) {
                ctx.incrementFailed();
                ctx.addFailedMatch(inst);
                LOG.warn("{}: failed to send {} to {}:\n", target, inst, ctx.getDestinationWebApp(), e);
                if (semaphore != null) semaphore.release();
            }
        };
        if (async()) {
            request.async().post(entity, callback);
        } else {
            try {
                callback.completed(request.post(entity));
            } catch (Throwable e) {
                callback.failed(e);
            }
        }
    }

    private boolean async() {
        if (semaphore != null) {
            try {
                semaphore.acquire();
                return true;
            } catch (InterruptedException e) {
                LOG.info("{}: failed to wait for pending responses:\n", target, e);
            }
        }
        return false;
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

    private static class WrappedInstanceLocations {
        final InstanceLocations instanceLocations;

        private WrappedInstanceLocations(InstanceLocations instanceLocations) {
            this.instanceLocations = instanceLocations;
        }
    }
}
