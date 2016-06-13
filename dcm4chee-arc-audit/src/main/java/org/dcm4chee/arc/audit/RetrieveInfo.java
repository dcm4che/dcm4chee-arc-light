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

package org.dcm4chee.arc.audit;

import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.retrieve.RetrieveContext;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since March 2016
 */
public class RetrieveInfo {
    static final int LOCALAET = 0;
    static final int DESTAET = 1;
    static final int DESTNAPID = 2;
    static final int REQUESTORHOST = 3;
    static final int MOVEAET = 4;
    static final int FAILURE_DESC = 5;
    static final int WARNING_DESC = 6;
    static final int FAILED_IUIDS_SHOW_FLAG = 7;
    private final String[] fields;

    RetrieveInfo(RetrieveContext ctx, String etFile) {
        fields = new String[] {
                ctx.getHttpRequest() != null ? ctx.getHttpRequest().getRequestURI() : ctx.getLocalAETitle(),
                ctx.getHttpRequest() != null
                    ? ctx.getHttpRequest().getAttribute(AuditServiceUtils.keycloakClassName) != null
                    ? AuditServiceUtils.getPreferredUsername(ctx.getHttpRequest())
                    : ctx.getHttpRequest().getRemoteAddr()
                    : ctx.getDestinationAETitle(),
                null != ctx.getHttpRequest() ? ctx.getHttpRequest().getRemoteAddr() : ctx.getDestinationHostName(),
                ctx.getRequestorHostName(),
                ctx.getMoveOriginatorAETitle(),
                (etFile.substring(0,8).equals("RTRV_BGN") && ctx.getException() != null) || etFile.substring(9,10).equals("E")
                    ? getFailOutcomeDesc(ctx) : null,
                etFile.substring(9,10).equals("P") && ctx.warning() != 0
                    ? ctx.warning() == ctx.getMatches().size() ? "Warnings on retrieve of all instances"
                    : "Warnings on retrieve of " + ctx.warning() + " instances" : null,
                etFile.substring(9,10).equals("E") && ctx.failedSOPInstanceUIDs().length > 0
                    ? Boolean.toString(true) : Boolean.toString(false)
        };
    }

    RetrieveInfo(String s) {
        fields = StringUtils.split(s, '\\');
    }

    String getField(int field) {
        return StringUtils.maskEmpty(fields[field], null);
    }

    @Override
    public String toString() {
        return StringUtils.concat(fields, '\\');
    }

    private String getFailOutcomeDesc(RetrieveContext ctx) {
        return null != ctx.getException()
            ? ctx.getException().getMessage() != null ? ctx.getException().getMessage() : ctx.getException().toString()
            : (ctx.failedSOPInstanceUIDs().length > 0 && (ctx.completed() == 0 && ctx.warning() == 0))
            ? "Unable to perform sub-operations on all instances"
            : (ctx.failedSOPInstanceUIDs().length > 0 && !(ctx.completed() == 0 && ctx.warning() == 0))
            ? "Retrieve of " + ctx.failed() + " objects failed" : null;
    }

}
