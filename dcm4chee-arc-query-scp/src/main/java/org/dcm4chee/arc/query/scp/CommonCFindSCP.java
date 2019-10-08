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

import org.dcm4che3.data.*;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.io.XSLTAttributesCoercion;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.net.service.QueryTask;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion;
import org.dcm4chee.arc.conf.UseCallingAETitleAsCoercion;
import org.dcm4chee.arc.mima.SupplementAssigningAuthorities;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.RunInTransaction;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
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
        QueryRetrieveLevel2 qrLevel = validateQueryIdentifier(as, keys, queryOpts.contains(QueryOption.RELATIONAL));
        QueryContext ctx = queryService.newQueryContextFIND(as, sopClassUID, queryOpts);
        ctx.setQueryRetrieveLevel(qrLevel);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null && !idWithIssuer.getID().equals("*"))
            ctx.setPatientIDs(idWithIssuer);
        Sequence sortingOperationSeq = (Sequence) keys.remove(Tag.SortingOperationsSequence);
        if (sortingOperationSeq != null)
            ctx.setOrderByTags(sortingOperationSeq.stream().map(OrderByTag::valueOf).collect(Collectors.toList()));
        ctx.setQueryKeys(keys);
        ctx.setReturnKeys(createReturnKeys(keys));
        coerceAttributes(ctx);
        return new ArchiveQueryTask(as, pc, rq, keys, ctx, runInTx);
    }

    private QueryRetrieveLevel2 validateQueryIdentifier(Association as, Attributes keys, boolean relational)
            throws DicomServiceException {
        QueryRetrieveLevel2 qrLevel;
        try {
            qrLevel = QueryRetrieveLevel2.validateQueryIdentifier(keys, qrLevels, relational);
        } catch (DicomServiceException e) {
            if (relational || !relationalQueryNegotiationLenient(as))
                throw e;

            qrLevel = QueryRetrieveLevel2.validateQueryIdentifier(keys, qrLevels, true);
            LOG.info("{}: {}", as, e.getMessage());
        }
        return qrLevel;
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

    private void coerceAttributes(QueryContext ctx) {
        ArchiveAttributeCoercion rule = ctx.getArchiveAEExtension().findAttributeCoercion(
                ctx.getRemoteHostName(),
                ctx.getCallingAET(),
                TransferCapability.Role.SCU,
                Dimse.C_FIND_RQ,
                ctx.getSOPClassUID());
        if (rule == null)
            return;

        AttributesCoercion coercion = null;
        coercion = coerceAttributesByXSL(ctx, rule, coercion);
        coercion = SupplementAssigningAuthorities.forQuery(rule.getSupplementFromDevice(), coercion);
        coercion = rule.supplementIssuerOfPatientID(coercion);
        coercion = rule.nullifyIssuerOfPatientID(ctx.getQueryKeys(), coercion);
        coercion = NullifyAttributesCoercion.valueOf(rule.getNullifyTags(), coercion);
        if (rule.isTrimISO2022CharacterSet())
            coercion = new TrimISO2020CharacterSetAttributesCoercion(coercion);
        coercion = UseCallingAETitleAsCoercion.of(rule.getUseCallingAETitleAs(), ctx.getCallingAET(), coercion);
        if (coercion != null)
            coercion.coerce(ctx.getQueryKeys(), ctx.getCoercedQueryKeys());
    }

    private AttributesCoercion coerceAttributesByXSL(
            QueryContext ctx, ArchiveAttributeCoercion rule, AttributesCoercion next) {
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
            try {
                Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
                LOG.info("Coerce Attributes from rule: {}", rule);
                return new XSLTAttributesCoercion(tpls, null)
                        .includeKeyword(!rule.isNoKeywords())
                        .setupTransformer(setupTransformer(ctx));
            } catch (TransformerConfigurationException e) {
                LOG.error("{}: Failed to compile XSL: {}", ctx, xsltStylesheetURI, e);
            }
        return next;
    }

    private SAXTransformer.SetupTransformer setupTransformer(QueryContext ctx) {
        return t -> {
            t.setParameter("LocalAET", ctx.getCalledAET());
            if (ctx.getCallingAET() != null)
                t.setParameter("RemoteAET", ctx.getCallingAET());
            if (ctx.getRemoteHostName() != null)
                t.setParameter("RemoteHost", ctx.getRemoteHostName());
        };
    }
}
