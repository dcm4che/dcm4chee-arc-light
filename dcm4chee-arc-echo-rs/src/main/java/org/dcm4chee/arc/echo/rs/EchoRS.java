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

package org.dcm4chee.arc.echo.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAbort;
import org.dcm4che3.net.pdu.AAssociateRJ;
import org.dcm4che3.net.pdu.AAssociateRQ;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@RequestScoped
@Path("aets/{AETitle}/echo/{RemoteAET}")
public class EchoRS {

    @Inject
    private DicomConfiguration conf;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("RemoteAET")
    private String remoteAET;

    @Context
    private HttpServletRequest request;

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

    private ApplicationEntity getRemoteApplicationEntity() throws ConfigurationException {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        try {
            return conf.findApplicationEntity(remoteAET);
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(
                    "No such Application Entity configured: " + remoteAET,
                    Response.Status.NOT_FOUND);
        }
    }

    private AAssociateRQ createAARQ() {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.addPresentationContextFor(UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        return aarq;
    }

    @POST
    @Produces("application/json")
    public StreamingOutput echo() throws Exception {
        ApplicationEntity ae = getApplicationEntity();
        ApplicationEntity remote = getRemoteApplicationEntity();
        Association as = null;
        long t1, t2;
        Result result = new Result();
        try {
            t1 = System.currentTimeMillis();
            as = ae.connect(remote, createAARQ());
            t2 = System.currentTimeMillis();
            result.connectionTime = Long.toString(t2-t1);
            try {
                DimseRSP rsp = as.cecho();
                try {
                    rsp.next();
                    t1 = System.currentTimeMillis();
                    result.echoTime = Long.toString(t1-t2);
                } catch (IOException e) {
                    result.error(Result.Code.FailedToSendCEchoRQ, e);
                }
            } catch (IOException e) {
                result.error(Result.Code.FailedToReceiveCEchoRSP, e);
            }
        } catch (IncompatibleConnectionException e) {
            result.error(Result.Code.IncompatibleConnection, e);
        } catch (AAssociateRJ e) {
            result.error(Result.Code.AssociationRejected, e);
        } catch (IOException e) {
            result.error(Result.Code.FailedToConnect, e);
        } finally {
            if (as != null) {
                try {
                    t1 = System.currentTimeMillis();
                    as.release();
                    t2 = System.currentTimeMillis();
                    result.releaseTime = Long.toString(t2-t1);
                } catch (IOException e) {
                    result.error(Result.Code.FailedToRelease, e);
                }
            }
        }
        return result;
    }

    private static class Result implements StreamingOutput {
        enum Code {
            Success(null),
            IncompatibleConnection("Incompatible Connection: "),
            FailedToConnect("Failed to connect: "),
            AssociationRejected("Association rejected: "),
            FailedToSendCEchoRQ("Failed to send C-ECHO-RSP: "),
            FailedToReceiveCEchoRSP("Failed to receive C-ECHO-RSP: "),
            FailedToRelease("Failed to release association: ");

            final String prefix;

            Code(String prefix) {
                this.prefix = prefix;
            }

            String errorMessage(Exception ex) {
                return prefix + (
                        (ex instanceof IncompatibleConnectionException
                                || ex instanceof AAssociateRJ
                                || ex instanceof AAbort
                        )
                                ? ex.getMessage()
                                : ex
                );
            }
        }

        Code code = Code.Success;
        Exception exception;
        String connectionTime;
        String echoTime;
        String releaseTime;

        void error(Code code, Exception e) {
            if (exception == null) {
                this.code = code;
                this.exception = e;
            }
        }

        @Override
        public void write(OutputStream out) throws IOException {
            Writer w = new OutputStreamWriter(out, "UTF-8");
            w.write("{\"result\":");
            w.write(Integer.toString(code.ordinal()));
            if (exception != null) {
                w.write(",\"errorMessage\":\"");
                w.write(code.errorMessage(exception));
                w.write('"');
            }
            if (connectionTime != null) {
                w.write(",\"connectionTime\":");
                w.write(connectionTime);
            }
            if (echoTime != null) {
                w.write(",\"echoTime\":");
                w.write(echoTime);
            }
            if (releaseTime != null) {
                w.write(",\"releaseTime\":");
                w.write(releaseTime);
            }
            w.write('}');
            w.flush();
        }
    }
}
