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

package org.dcm4chee.arc.ups.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.json.JSONWriter;
import org.dcm4chee.arc.ups.UPSEvent;
import org.dcm4chee.arc.ups.UPSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */
@ApplicationScoped
@ServerEndpoint(value = "/aets/{AETitle}/ws/subscribers/{SubscriberAET}")
public class EventReportSender {
    private static final Logger LOG = LoggerFactory.getLogger(EventReportSender.class);

    private final AtomicInteger messageID = new AtomicInteger(0);

    @Inject
    private UPSService service;

    @OnOpen
    public void open(Session session,
            @PathParam("AETitle") String aet,
            @PathParam("SubscriberAET") String subscriberAET) {
        LOG.info("{} open /aets/{}/ws/subscribers/{} ", session, aet, subscriberAET);
        service.registerWebsocketChannel(session, aet, subscriberAET);
    }

    @OnClose
    public void close(Session session,
            @PathParam("AETitle") String aet,
            @PathParam("SubscriberAET") String subscriberAET) {
        LOG.info("{} close /aets/{}/ws/subscribers/{} ", session, aet, subscriberAET);
        service.unregisterWebsocketChannel(session);
    }

    @OnError
    public void error(Session session, Throwable thr,
            @PathParam("AETitle") String aet,
            @PathParam("SubscriberAET") String subscriberAET) {
        LOG.warn("{} error /aets/{}/ws/subscribers/{}:\n", session, aet, subscriberAET, thr);
    }

    public void onUPSEvent(@Observes UPSEvent event) {
        Optional<String> inprocessStateReport = toInprocessStateReportJson(event);
        String json = toJson(event);
        for (String subscriberAET : event.subscriberAETs) {
            List<Session> sessions = service.getWebsocketChannels(subscriberAET);
            if (sessions.isEmpty()) {
                LOG.info("No Websocket channel to send {} EventReport to {}", event.type, subscriberAET);
            } else {
                try {
                    LOG.info("Send {} EventReport to {}", event.type, subscriberAET);
                    if (inprocessStateReport.isPresent()) {
                        send(inprocessStateReport.get(), sessions);
                    }
                    send(json, sessions);
                } catch (IOException e) {
                    LOG.warn("Failed to send {} EventReport to {}:\n", event.type, subscriberAET, e);
                }
            }
        }
    }

    private Optional<String> toInprocessStateReportJson(UPSEvent event) {
        return event.inprocessStateReport().map(attrs -> toJson(event, attrs));
    }

    private String toJson(UPSEvent event) {
        return toJson(event, new Attributes(event.attrs));
    }

    private String toJson(UPSEvent event, Attributes attrs) {
        StringWriter out = new StringWriter(256);
        try (JsonGenerator gen = Json.createGenerator(out)) {
            new JSONWriter(gen).write(event.withCommandAttributes(attrs, messageID.incrementAndGet()));
        }
        return out.toString();
    }

    private void send(String json, List<Session> sessions) throws IOException {
        IOException e1 = null;
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(json);
                return;
            } catch (IOException e) {
                service.unregisterWebsocketChannel(session);
                LOG.info("{} error:\n", session, e1 = e);
            }
        }
        throw e1;
    }
}
