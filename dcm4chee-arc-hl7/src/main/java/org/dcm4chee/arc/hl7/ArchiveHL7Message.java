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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.hl7;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2018
 */
public class ArchiveHL7Message extends UnparsedHL7Message {

    private String patRecEventActionCode;
    private String procRecEventActionCode;
    private HttpServletRequestInfo httpServletRequestInfo;
    private Attributes studyAttrs;

    public ArchiveHL7Message(byte[] data) {
        super(data);
    }

    public String getPatRecEventActionCode() {
        return patRecEventActionCode;
    }

    public void setPatRecEventActionCode(String patRecEventActionCode) {
        this.patRecEventActionCode = patRecEventActionCode;
    }

    public String getProcRecEventActionCode() {
        return procRecEventActionCode;
    }

    public void setProcRecEventActionCode(String procRecEventActionCode) {
        this.procRecEventActionCode = procRecEventActionCode;
    }

    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    public void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
    }

    public Attributes getStudyAttrs() {
        return studyAttrs;
    }

    public void setStudyAttrs(Attributes studyAttrs) {
        this.studyAttrs = studyAttrs;
    }
}
