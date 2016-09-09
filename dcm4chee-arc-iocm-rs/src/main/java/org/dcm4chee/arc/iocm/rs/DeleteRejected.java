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

package org.dcm4chee.arc.iocm.rs;

import org.dcm4che3.data.Code;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.DeletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@Path("reject")
@RequestScoped
public class DeleteRejected {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteRejected.class);

    @Inject
    private Device device;

    @Inject
    private DeletionService service;

    @Context
    private HttpServletRequest request;

    @QueryParam("rejectedBefore")
    @Pattern(regexp = "(19|20)\\d{2}\\-\\d{2}\\-\\d{2}")
    private String rejectedBefore;

    @QueryParam("keepRejectionNote")
    @Pattern(regexp = "true|false")
    private String keepRejectionNote;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
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

    @DELETE
    @Path("{CodeValue}^{CodingSchemeDesignator}")
    @Produces("application/json")
    public String delete(
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator)
            throws Exception {
        LOG.info("Process DELETE {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        Code code = new Code(codeValue, designator, null, "?");
        RejectionNote rjNote = arcDev.getRejectionNote(code);
        if (rjNote == null)
            throw new WebApplicationException(
                    getResponse("Unknown Rejection Note Code: " + code, Response.Status.NOT_FOUND));

        boolean keep = Boolean.parseBoolean(keepRejectionNote);
        Date before = parseDate(rejectedBefore);
        int fetchSize = arcDev.getDeleteRejectedFetchSize();
        int deleted = service.deleteRejectedInstancesBefore(rjNote.getRejectionNoteCode(), before, fetchSize);
        if (!Boolean.parseBoolean(keepRejectionNote))
            deleted += service.deleteRejectionNotesBefore(rjNote.getRejectionNoteCode(), before, fetchSize);

        LOG.info("Deleted {} instances permanently", deleted);
        return "{\"deleted\":" + deleted + '}';
    }

    private Response getResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }
}
