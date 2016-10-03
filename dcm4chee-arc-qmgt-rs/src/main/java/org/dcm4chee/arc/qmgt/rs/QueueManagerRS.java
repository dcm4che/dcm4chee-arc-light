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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.arc.qmgt.rs;

import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@RequestScoped
@Path("queue/{queueName}")
public class QueueManagerRS {

    @Inject
    private QueueManager mgr;

    @PathParam("queueName")
    private String queueName;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("updatedBefore")
    @Pattern(regexp = "(19|20)\\d{2}\\-\\d{2}\\-\\d{2}")
    private String updatedBefore;

    @GET
    @NoCache
    @Produces("application/json")
    public Response search() throws Exception {
        return Response.ok(toEntity(mgr.search(queueName, parseStatus(status), parseInt(offset), parseInt(limit))))
                .build();
    }

    @POST
    @Path("{msgId}/cancel")
    public void cancelProcessing(@PathParam("msgId") String msgId) throws Exception {
        mgr.cancelProcessing(msgId);
    }

    @POST
    @Path("{msgId}/reschedule")
    public void rescheduleMessage(@PathParam("msgId") String msgId) throws Exception {
        mgr.rescheduleMessage(msgId);
    }

    @DELETE
    @Path("{msgId}")
    public void deleteMessage(@PathParam("msgId") String msgId) throws Exception {
        mgr.deleteMessage(msgId);
    }

    @DELETE
    @Produces("application/json")
    public String deleteMessages() {
        return "{\"deleted\":"
                + mgr.deleteMessages(queueName, parseStatus(status), parseDate(updatedBefore))
                + '}';
    }

    private Object toEntity(final List<QueueMessage> msgs) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                Writer w = new OutputStreamWriter(out, "UTF-8");
                int count = 0;
                w.write('[');
                for (QueueMessage msg : msgs) {
                    if (count++ > 0)
                        w.write(',');
                    msg.writeAsJSON(w);
                }
                w.write(']');
                w.flush();
            }
        };
    }

    private static QueueMessage.Status parseStatus(String s) {
        return s != null
                ? s.equals("IN PROCESS")
                    ? QueueMessage.Status.IN_PROCESS
                    : QueueMessage.Status.valueOf(s)
                : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private Date parseDate(String s) {
        try {
            return s != null
                    ? new SimpleDateFormat("yyyy-MM-dd").parse(s)
                    : null;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
