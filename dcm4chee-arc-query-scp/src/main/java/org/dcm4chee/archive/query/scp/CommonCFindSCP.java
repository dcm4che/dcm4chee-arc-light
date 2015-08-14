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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.archive.query.scp;

import org.dcm4che3.data.*;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.net.service.QueryTask;
import org.dcm4chee.archive.query.Query;
import org.dcm4chee.archive.query.QueryContext;
import org.dcm4chee.archive.query.QueryService;

import javax.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class CommonCFindSCP extends BasicCFindSCP {

    private final EnumSet<QueryRetrieveLevel2> qrLevels;

    @Inject
    private QueryService queryService;

    protected CommonCFindSCP(String sopClass, EnumSet<QueryRetrieveLevel2> qrLevels) {
        super(sopClass);
        this.qrLevels = qrLevels;
    }

    @Override
    protected QueryTask calculateMatches(Association as, PresentationContext pc, Attributes rq, Attributes keys)
            throws DicomServiceException {
        EnumSet<QueryOption> queryOpts = as.getQueryOptionsFor(rq.getString(Tag.AffectedSOPClassUID));
        QueryRetrieveLevel2 qrLevel = QueryRetrieveLevel2.validateQueryIdentifier(
                keys, qrLevels, queryOpts.contains(QueryOption.RELATIONAL));
        QueryContext ctx = queryService.newQueryContext(as, queryOpts);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null && !idWithIssuer.getID().equals("*"))
            ctx.setPatientIDs(idWithIssuer);
        ctx.setQueryKeys(keys);
        ctx.setReturnKeys(createReturnKeys(keys));
        return new ArchiveQueryTask(as, pc, rq, keys, createQuery(qrLevel, ctx));
    }

    private Attributes createReturnKeys(Attributes keys) {
        Attributes returnKeys = new Attributes(keys.size() + 8);
        returnKeys.addAll(keys);
        if (!returnKeys.contains(Tag.SpecificCharacterSet))
            returnKeys.setNull(Tag.SpecificCharacterSet, VR.CS);
        returnKeys.setNull(Tag.RetrieveAETitle, VR.AE);
        returnKeys.setNull(Tag.InstanceAvailability, VR.CS);
        returnKeys.setNull(Tag.ModalitiesInStudy, VR.CS);
        returnKeys.setNull(Tag.SOPClassesInStudy, VR.UI);
        returnKeys.setNull(Tag.NumberOfStudyRelatedSeries, VR.IS);
        returnKeys.setNull(Tag.NumberOfStudyRelatedInstances, VR.IS);
        returnKeys.setNull(Tag.NumberOfSeriesRelatedInstances, VR.IS);
        return returnKeys;
    }

    private Query createQuery(QueryRetrieveLevel2 qrLevel, QueryContext ctx) {
        switch (qrLevel) {
            case PATIENT:
                return queryService.createPatientQuery(ctx);
            case STUDY:
                return queryService.createStudyQuery(ctx);
            case SERIES:
                return queryService.createSeriesQuery(ctx);
            default: // case IMAGE
                return queryService.createInstanceQuery(ctx);
        }
    }

 }
