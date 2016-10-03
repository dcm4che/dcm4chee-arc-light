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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.monitor.rs;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2016
 */

import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Path("/monitor")
@RequestScoped
public class ArchiveMonitor {

    @Inject
    private Device device;

    @GET
    @NoCache
    @Path("associations")
    @Produces("application/json")
    public StreamingOutput listOpenAssociations() throws Exception {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                Writer w = new OutputStreamWriter(out, "UTF-8");
                int count = 0;
                w.write('[');
                for (Association as : device.listOpenAssociations()) {
                    if (count++ > 0)
                        w.write(',');
                    w.write("{\"serialNo\":");
                    w.write(String.valueOf(as.getSerialNo()));
                    w.write(",\"connectTime\":\"");
                    w.write(df.format(new Date(as.getConnectTimeInMillis())));
                    w.write("\",\"initiated\":");
                    w.write(String.valueOf(as.isRequestor()));
                    w.write(",\"localAETitle\":\"");
                    w.write(as.getLocalAET());
                    w.write("\",\"remoteAETitle\":\"");
                    w.write(as.getRemoteAET());
                    w.write("\",\"performedOps\":{");
                    writePerformed(w, as);
                    w.write("},\"invokedOps\":{");
                    writeInvoked(w, as);
                    w.write('}');
                    writeOtherProperties(w, as);
                    w.write('}');
                }
                w.write(']');
                w.flush();
            }
        };
    }

    @DELETE
    @Path("associations/{serialNo}")
    public void abortAssociation(@PathParam("serialNo") int serialNo) {
        for (Association as : device.listOpenAssociations()) {
            if (as.getSerialNo() == serialNo) {
                as.abort();
                return;
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    private void writeOtherProperties(Writer w, Association as) throws IOException {
        for (String key : as.getPropertyNames()) {
            Object value = as.getProperty(key);
            if (value instanceof String) {
                w.write(",\"");
                w.write(key);
                w.write("\":\"");
                w.write((String) value);
                w.write('\"');
            }
        }
    }

    private void writePerformed(Writer w, Association as) throws IOException {
        boolean previous = ArchiveMonitor.write(w, false, "C-STORE",
                as.getNumberOfReceived(Dimse.C_STORE_RQ),
                as.getNumberOfSent(Dimse.C_STORE_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-GET",
                as.getNumberOfReceived(Dimse.C_GET_RQ),
                as.getNumberOfSent(Dimse.C_GET_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-FIND",
                as.getNumberOfReceived(Dimse.C_FIND_RQ),
                as.getNumberOfSent(Dimse.C_FIND_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-MOVE",
                as.getNumberOfReceived(Dimse.C_MOVE_RQ),
                as.getNumberOfSent(Dimse.C_MOVE_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-ECHO",
                as.getNumberOfReceived(Dimse.C_ECHO_RQ),
                as.getNumberOfSent(Dimse.C_ECHO_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-EVENT-REPORT",
                as.getNumberOfReceived(Dimse.N_EVENT_REPORT_RQ),
                as.getNumberOfSent(Dimse.N_EVENT_REPORT_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-GET",
                as.getNumberOfReceived(Dimse.N_GET_RQ),
                as.getNumberOfSent(Dimse.N_GET_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-SET",
                as.getNumberOfReceived(Dimse.N_SET_RQ),
                as.getNumberOfSent(Dimse.N_SET_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-ACTION",
                as.getNumberOfReceived(Dimse.N_ACTION_RQ),
                as.getNumberOfSent(Dimse.N_ACTION_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-CREATE",
                as.getNumberOfReceived(Dimse.N_CREATE_RQ),
                as.getNumberOfSent(Dimse.N_CREATE_RSP));
        ArchiveMonitor.write(w, previous , "N-DELETE",
                as.getNumberOfReceived(Dimse.N_DELETE_RQ),
                as.getNumberOfSent(Dimse.N_DELETE_RSP));
    }

    private void writeInvoked(Writer w, Association as) throws IOException {
        boolean previous = ArchiveMonitor.write(w, false, "C-STORE",
                as.getNumberOfSent(Dimse.C_STORE_RQ),
                as.getNumberOfReceived(Dimse.C_STORE_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-GET",
                as.getNumberOfSent(Dimse.C_GET_RQ),
                as.getNumberOfReceived(Dimse.C_GET_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-FIND",
                as.getNumberOfSent(Dimse.C_FIND_RQ),
                as.getNumberOfReceived(Dimse.C_FIND_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-MOVE",
                as.getNumberOfSent(Dimse.C_MOVE_RQ),
                as.getNumberOfReceived(Dimse.C_MOVE_RSP));
        previous = ArchiveMonitor.write(w, previous , "C-ECHO",
                as.getNumberOfSent(Dimse.C_ECHO_RQ),
                as.getNumberOfReceived(Dimse.C_ECHO_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-EVENT-REPORT",
                as.getNumberOfSent(Dimse.N_EVENT_REPORT_RQ),
                as.getNumberOfReceived(Dimse.N_EVENT_REPORT_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-GET",
                as.getNumberOfSent(Dimse.N_GET_RQ),
                as.getNumberOfReceived(Dimse.N_GET_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-SET",
                as.getNumberOfSent(Dimse.N_SET_RQ),
                as.getNumberOfReceived(Dimse.N_SET_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-ACTION",
                as.getNumberOfSent(Dimse.N_ACTION_RQ),
                as.getNumberOfReceived(Dimse.N_ACTION_RSP));
        previous = ArchiveMonitor.write(w, previous , "N-CREATE",
                as.getNumberOfSent(Dimse.N_CREATE_RQ),
                as.getNumberOfReceived(Dimse.N_CREATE_RSP));
        ArchiveMonitor.write(w, previous , "N-DELETE",
                as.getNumberOfSent(Dimse.N_DELETE_RQ),
                as.getNumberOfReceived(Dimse.N_DELETE_RSP));
    }

    private static boolean write(Writer w, boolean previous, String command, int rq, int rsp) throws IOException {
        if (rq == 0)
            return previous;

        if (previous)
            w.write(',');
        w.write('\"');
        w.write(command);
        w.write("\":{\"RQ\":");
        w.write(String.valueOf(rq));
        w.write(",\"RSP\":");
        w.write(String.valueOf(rsp));
        w.write('}');
        return true;
    }
}
