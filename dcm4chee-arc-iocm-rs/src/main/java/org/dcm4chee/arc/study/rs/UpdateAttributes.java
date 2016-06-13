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

package org.dcm4chee.arc.study.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.query.util.AttributesBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class UpdateAttributes {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateAttributes.class);

    @Inject
    private Device device;

    @Inject
    private PatientService patientService;

    @Inject
    private StudyService iocmService;

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @PUT
    @Path("/patients")
    @Consumes("application/json")
    public void updatePatient(InputStream in) throws Exception {
        logRequest();
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request, getApplicationEntity());
        ctx.setPreviousAttributes(queryAttributes(uriInfo));
        IDWithIssuer previousPatientID = ctx.getPreviousPatientID();
        if (previousPatientID == null)
            throw new WebApplicationException("missing query parameter: PatientID", Response.Status.BAD_REQUEST);
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        ctx.setAttributes(reader.readDataset(null));
        IDWithIssuer patientID = ctx.getPatientID();
        if (patientID == null)
            throw new WebApplicationException("missing Patient ID in message body", Response.Status.BAD_REQUEST);
        if (previousPatientID.equals(patientID)) {
            ctx.setPreviousAttributes(null);
            patientService.updatePatient(ctx);
        } else {
            patientService.changePatientID(ctx);
        }
    }

    @PUT
    @Path("/studies/{StudyUID}")
    @Consumes("application/json")
    public void updateStudy(@PathParam("StudyUID") String studyUID, InputStream in) throws Exception {
        logRequest();
        StudyMgtContext ctx = iocmService.createIOCMContextWEB(request, getApplicationEntity());
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        ctx.setAttributes(reader.readDataset(null));
        String studyUIDBody = ctx.getStudyInstanceUID();
        if (ctx.getPatientID() == null)
            throw new WebApplicationException("missing Patient ID in message body", Response.Status.BAD_REQUEST);
        if (studyUIDBody == null)
            throw new WebApplicationException("missing Study Instance UID in message body", Response.Status.BAD_REQUEST);
        if (!studyUIDBody.equals(studyUID))
            throw new WebApplicationException("Study Instance UID[" + studyUIDBody +
                    "] in message body does not match Study Instance UID[" + studyUID + "] in path",
                    Response.Status.BAD_REQUEST);

        iocmService.updateStudy(ctx);
    }

    private void logRequest() {
        LOG.info("Process {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

    private static Attributes queryAttributes(UriInfo info) {
        Attributes attrs = new Attributes();
        AttributesBuilder builder = new AttributesBuilder(attrs);
        MultivaluedMap<String, String> map = info.getQueryParameters();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String attrPath = entry.getKey();
            List<String> values = entry.getValue();
            try {
                builder.setString(attrPath, values.toArray(new String[values.size()]));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(attrPath + "=" + values.get(0));
            }
        }
        return attrs;
    }
}
