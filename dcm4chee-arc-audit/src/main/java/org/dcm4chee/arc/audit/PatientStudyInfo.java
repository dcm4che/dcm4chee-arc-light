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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since March 2016
 */
class PatientStudyInfo {
    static final int CALLING_HOSTNAME = 0;
    static final int CALLING_AET = 1;
    static final int CALLED_AET = 2;
    static final int STUDY_UID = 3;
    static final int ACCESSION_NO = 4;
    static final int PATIENT_ID = 5;
    static final int PATIENT_NAME = 6;
    static final int OUTCOME = 7;
    static final int STUDY_DATE = 8;
    private final String[] fields;

    PatientStudyInfo(StoreContext ctx) {
        fields = new String[] {
                ctx.getStoreSession().getRemoteHostName(),
                AuditServiceUtils.getStoreCallingAE(ctx),
                ctx.getStoreSession().getCalledAET(),
                ctx.getStudyInstanceUID(),
                ctx.getAttributes().getString(Tag.AccessionNumber),
                ctx.getAttributes().getString(Tag.PatientID, AuditServiceUtils.noValue),
                StringUtils.maskEmpty(ctx.getAttributes().getString(Tag.PatientName), null),
                null != ctx.getRejectionNote() ? null != ctx.getException()
                    ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                    : ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning()
                    : null != ctx.getException() ? ctx.getException().getMessage() : null,
                ctx.getAttributes().getString(Tag.StudyDate)
        };
    }

    PatientStudyInfo(RetrieveContext ctx, Attributes attrs) {
        fields = new String[] {
                ctx.getHttpRequest().getRemoteAddr(),
                ctx.getHttpRequest().getAttribute(AuditServiceUtils.keycloakClassName) != null
                    ? AuditServiceUtils.getPreferredUsername(ctx.getHttpRequest())
                    : ctx.getHttpRequest().getRemoteAddr(),
                ctx.getLocalAETitle(),
                ctx.getStudyInstanceUIDs()[0],
                attrs.getString(Tag.AccessionNumber),
                attrs.getString(Tag.PatientID, AuditServiceUtils.noValue),
                StringUtils.maskEmpty(attrs.getString(Tag.PatientName), null),
                null != ctx.getException() ? ctx.getException().getMessage(): null,
                attrs.getString(Tag.StudyDate)
        };
    }

    PatientStudyInfo(StudyDeleteContext ctx) {
        fields = new String[] {
                null,
                null,
                null,
                ctx.getStudy().getStudyInstanceUID(),
                ctx.getStudy().getAccessionNumber() != null ? ctx.getStudy().getAccessionNumber() : null,
                ctx.getPatient().getPatientID().getID(),
                null != ctx.getPatient().getPatientName().toString()
                        ? ctx.getPatient().getPatientName().toString() : null,
                ctx.getException() != null ? ctx.getException().getMessage() : null,
                ctx.getStudy().getStudyDate() != null ? ctx.getStudy().getStudyDate() : null
        };
    }

    PatientStudyInfo(String s) {
        fields = StringUtils.split(s, '\\');
    }

    String getField(int field) {
        return StringUtils.maskEmpty(fields[field], null);
    }

    @Override
    public String toString() {
        return StringUtils.concat(fields, '\\');
    }
}
