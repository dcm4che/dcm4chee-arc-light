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

package org.dcm4chee.arc.procedure;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.MWLImport;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.HL7PSUTask;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.util.QueryParam;

import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
public interface ProcedureService {
    ProcedureContext createProcedureContext();

    void updateProcedure(ProcedureContext ctx);

    void deleteProcedure(ProcedureContext ctx);

    void updateStudySeriesAttributes(ProcedureContext ctx);

    List<MWLItem> updateMWLStatus(String studyIUID, SPSStatus status);

    List<MWLItem> updateMWLStatus(List<MWLItem> mwlItems, SPSStatus status);

    List<MWLItem> findMWLItems(ArchiveAEExtension arcAE, HL7PSUTask hl7PSUTask);

    List<MWLItem> findMWLItems(ArchiveAEExtension arcAE, Attributes studyAttrs);

    void updateMWLStatus(ProcedureContext ctx);

    void updateMWLStatus(ProcedureContext ctx, SPSStatus from);

    int updateMatchingSPS(SPSStatus spsStatus, Attributes queryKeys, QueryParam queryParam, int mwlFetchSize);

    MWLItem findMWLItem(ProcedureContext ctx);

    Attributes getMWLItemAttrs(String studyInstanceUID, String spsID);

    ImportResult importMWL(HttpServletRequestInfo request, String mwlscu, String mwlscp, String worklistLabel,
            int priority, Attributes filter, Attributes keys, boolean fuzzymatching, boolean filterbyscu,
            boolean delete, boolean simulate)
            throws Exception;

    ImportResult importMWL(MWLImport rule) throws Exception;
}
