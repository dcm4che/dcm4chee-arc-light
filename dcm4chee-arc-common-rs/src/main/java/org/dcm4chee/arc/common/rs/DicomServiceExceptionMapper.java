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

package org.dcm4chee.arc.common.rs;

import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.store.StoreService;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2016
 */

@Provider
public class DicomServiceExceptionMapper implements ExceptionMapper<DicomServiceException> {

    public Response toResponse(DicomServiceException e) {
        return Response.status(httpStatusOf(e.getStatus()))
                .entity(BuildHTTPResponseStatus.getStatus(e.getMessage())).build();
    }

    private static Response.Status httpStatusOf(int status) {
        switch (status) {
            case StoreService.DUPLICATE_REJECTION_NOTE:
            case StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE:
            case StoreService.REJECTION_FAILED_CLASS_INSTANCE_CONFLICT:
            case StoreService.REJECTION_FAILED_ALREADY_REJECTED:
                return Response.Status.CONFLICT;
            case StoreService.REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_AUTHORIZED:
            case StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED:
                return Response.Status.FORBIDDEN;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

}