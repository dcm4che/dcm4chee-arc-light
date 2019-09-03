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
 * Portions created by the Initial Developer are Copyright (C) 2019
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

package org.dcm4chee.arc.stgcmt.rs;

import org.dcm4che3.net.service.QueryRetrieveLevel2;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2019
 */
@RequestScoped
@Path("aets/{aet}/stgcmt/{stgcmtscp}")
public class StgCmtSCUMatchingLegacyRS extends StgCmtSCUMatching {

    @PathParam("aet")
    private String aet;

    @PathParam("stgcmtscp")
    private String stgcmtscp;
    
    @POST
    @Path("/studies")
    @Produces("application/json")
    public Response matchingStudyStorageCommit() {
        return storageCommitMatching(aet, stgcmtscp,"matchingStudyStorageCommit",
                QueryRetrieveLevel2.STUDY, null, null);
    }

    @POST
    @Path("/series")
    @Produces("application/json")
    public Response matchingSeriesStorageCommit() {
        return storageCommitMatching(aet, stgcmtscp,"matchingSeriesStorageCommit",
                QueryRetrieveLevel2.SERIES, null, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/json")
    public Response matchingSeriesOfStudyStorageCommit(
            @PathParam("StudyInstanceUID") String studyUID) {
        return storageCommitMatching(aet, stgcmtscp,"matchingSeriesOfStudyStorageCommit",
                QueryRetrieveLevel2.SERIES, studyUID, null);
    }

    @POST
    @Path("/instances")
    @Produces("application/json")
    public Response matchingInstancesStorageCommit() {
        return storageCommitMatching(aet, stgcmtscp,"matchingInstancesStorageCommit",
                QueryRetrieveLevel2.IMAGE, null, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("application/json")
    public Response matchingInstancesOfStudyStorageCommit(
            @PathParam("StudyInstanceUID") String studyUID) {
        return storageCommitMatching(aet, stgcmtscp,"matchingInstancesOfStudyStorageCommit",
                QueryRetrieveLevel2.IMAGE, studyUID, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/json")
    public Response matchingInstancesOfSeriesStorageCommit(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("SeriesInstanceUID") String seriesUID) {
        return storageCommitMatching(aet, stgcmtscp,"matchingInstancesOfSeriesStorageCommit",
                QueryRetrieveLevel2.IMAGE, studyUID, seriesUID);
    }

}
