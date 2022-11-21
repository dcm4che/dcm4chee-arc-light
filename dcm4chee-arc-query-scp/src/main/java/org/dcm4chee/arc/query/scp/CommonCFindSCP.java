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

package org.dcm4chee.arc.query.scp;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import org.dcm4che3.data.*;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.net.service.QueryTask;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.RunInTransaction;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class CommonCFindSCP extends BasicCFindSCP {

    private static final Logger LOG = LoggerFactory.getLogger(CommonCFindSCP.class);

    private final EnumSet<QueryRetrieveLevel2> qrLevels;

    @Inject
    private QueryService queryService;

    @EJB
    private RunInTransaction runInTx;

    protected CommonCFindSCP(String sopClass, EnumSet<QueryRetrieveLevel2> qrLevels) {
        super(sopClass);
        this.qrLevels = qrLevels;
    }

    @Override
    protected QueryTask calculateMatches(Association as, PresentationContext pc, Attributes rq, Attributes keys)
            throws DicomServiceException {
        LOG.info("{}: Process C-FIND RQ:\n{}", as, keys);
        String sopClassUID = rq.getString(Tag.AffectedSOPClassUID);
        EnumSet<QueryOption> queryOpts = as.getQueryOptionsFor(sopClassUID);
        QueryRetrieveLevel2 qrLevel = QueryRetrieveLevel2.validateQueryIdentifier(keys, qrLevels,
                queryOpts.contains(QueryOption.RELATIONAL),
                relationalQueryNegotiationLenient(as));
        QueryContext ctx = queryService.newQueryContextFIND(as, sopClassUID, queryOpts);
        ctx.setQueryRetrieveLevel(qrLevel);
        ctx.setQueryKeys(keys);
        ctx.setReturnKeys(createReturnKeys(keys));
        try {
            queryService.coerceAttributes(ctx);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure);
        }
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null && !idWithIssuer.getID().equals("*"))
            ctx.setPatientIDs(idWithIssuer);
        else if (ctx.getArchiveAEExtension().filterByIssuerOfPatientID())
            ctx.setIssuerOfPatientID(Issuer.fromIssuerOfPatientID(keys));
        Sequence sortingOperationSeq = (Sequence) keys.remove(Tag.SortingOperationsSequence);
        if (sortingOperationSeq != null)
            ctx.setOrderByTags(sortingOperationSeq.stream().map(OrderByTag::valueOf).collect(Collectors.toList()));
        return new ArchiveQueryTask(as, pc, rq, keys, ctx, runInTx);
    }

    private static boolean relationalQueryNegotiationLenient(Association as) {
        ArchiveAEExtension arcAE = as.getApplicationEntity().getAEExtension(ArchiveAEExtension.class);
        return arcAE != null && arcAE.relationalQueryNegotiationLenient();
    }

    private Attributes createReturnKeys(Attributes keys) {
        Attributes returnKeys = new Attributes(keys.size() + 3);
        returnKeys.addAll(keys);
        returnKeys.removePrivateAttributes(PrivateTag.PrivateCreator, 0x7777);
        returnKeys.setNull(Tag.SpecificCharacterSet, VR.CS);
        returnKeys.setNull(Tag.RetrieveAETitle, VR.AE);
        returnKeys.setNull(Tag.InstanceAvailability, VR.CS);
        returnKeys.setNull(Tag.TimezoneOffsetFromUTC, VR.SH);
        return returnKeys;
    }
}
