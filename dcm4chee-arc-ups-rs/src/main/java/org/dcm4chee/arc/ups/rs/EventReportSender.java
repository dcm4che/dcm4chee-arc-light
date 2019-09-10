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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */
@ApplicationScoped
@ServerEndpoint(value = "/aets/{AETitle}/ws/subscribers/{SubscriberAET}")
public class EventReportSender {
    private static final Logger LOG = LoggerFactory.getLogger(EventReportSender.class);
    private final ConcurrentHashMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    @OnOpen
    public void open(Session session,
            @PathParam("AETitle") String aet,
            @PathParam("SubscriberAET") String subscriberAET) {
        subscribers.put(session.getId(), new Subscriber(session, aet, subscriberAET));
    }

    @OnClose
    public void close(Session session,
            @PathParam("AETitle") String aet,
            @PathParam("SubscriberAET") String subscriberAET) {
        subscribers.remove(session.getId());
    }

    @OnError
    public void error(Session session, Throwable thr,
            @PathParam("AETitle") String aet,
            @PathParam("SubscriberAET") String subscriberAET) {
        LOG.warn("{} error:\n", session, thr);
    }

    private class Subscriber {
        final Session session;
        final String aet;
        final String subscriberAET;

        Subscriber(Session session, String aet, String subscriberAET) {
            this.session = session;
            this.aet = aet;
            this.subscriberAET = subscriberAET;
        }
    }
}
