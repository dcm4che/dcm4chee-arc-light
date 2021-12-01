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

package org.dcm4chee.arc.mpps.impl;

import org.dcm4che3.data.AttributesCoercion;
import org.dcm4che3.data.NullifyAttributesCoercion;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.io.XSLTAttributesCoercion;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.coerce.CoercionFactory;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion2;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.mima.SupplementAssigningAuthorities;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.mpps.MPPSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@ApplicationScoped
public class MPPSServiceImpl implements MPPSService {

    static final Logger LOG = LoggerFactory.getLogger(MPPSServiceImpl.class);

    @Inject
    private MPPSServiceEJB ejb;

    @Inject
    private CoercionFactory coercionFactory;

    @Override
    public MPPSContext newMPPSContext(Association as) {
        return new MPPSContextImpl(as);
    }

    @Override
    public MPPS createMPPS(MPPSContext ctx) throws DicomServiceException {
        try {
            coerceAttributes(ctx, Dimse.N_CREATE_RQ);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        return ejb.createMPPS(ctx);
    }

    @Override
    public MPPS findMPPS(MPPSContext ctx) throws DicomServiceException {
        return ejb.findMPPS(ctx);
    }

    @Override
    public MPPS updateMPPS(MPPSContext ctx) throws DicomServiceException {
        try {
            coerceAttributes(ctx, Dimse.N_SET_RQ);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        return ejb.updateMPPS(ctx);
    }

    private void coerceAttributes(MPPSContext ctx, Dimse dimse) throws Exception {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        List<ArchiveAttributeCoercion2> coercions = arcAE.attributeCoercions2()
                .filter(descriptor -> descriptor.match(
                        TransferCapability.Role.SCU,
                        dimse,
                        UID.ModalityPerformedProcedureStep,
                        ctx.getRemoteHostName(),
                        ctx.getCallingAET(),
                        ctx.getLocalHostName(),
                        ctx.getCalledAET(),
                        ctx.getAttributes()))
                .collect(Collectors.toList());
        if (coercions.isEmpty()) {
            ArchiveAttributeCoercion rule = arcAE.findAttributeCoercion(
                    dimse,
                    TransferCapability.Role.SCU,
                    UID.ModalityPerformedProcedureStep,
                    ctx.getRemoteHostName(),
                    ctx.getCallingAET(),
                    ctx.getLocalHostName(),
                    ctx.getCalledAET(),
                    ctx.getAttributes());
            if (rule != null)
                coerceLegacy(ctx, rule);
        } else {
            for (ArchiveAttributeCoercion2 coercion : coercions) {
                try {
                    if (coercionFactory.getCoercionProcessor(coercion).coerce(
                            coercion,
                            UID.ModalityPerformedProcedureStep,
                            ctx.getRemoteHostName(),
                            ctx.getCallingAET(),
                            ctx.getLocalHostName(),
                            ctx.getCalledAET(),
                            ctx.getAttributes(),
                            null)
                            && coercion.isCoercionSufficient()) break;
                } catch (Exception e) {
                    LOG.info("Failed to apply {}:\n", coercion, e);
                    switch (coercion.getCoercionOnFailure()) {
                        case RETHROW:
                            throw e;
                        case CONTINUE:
                            continue;
                    }
                    break;
                }
            }
        }
    }

    private void coerceLegacy(MPPSContext ctx, ArchiveAttributeCoercion rule) throws Exception {
        AttributesCoercion coercion = null;
        coercion = coerceAttributesByXSL(ctx, rule, coercion);
        coercion = SupplementAssigningAuthorities.forMPPS(rule.getSupplementFromDevice(), coercion);
        coercion = rule.mergeAttributes(coercion);
        coercion = NullifyAttributesCoercion.valueOf(rule.getNullifyTags(), coercion);
        if (coercion != null)
            coercion.coerce(ctx.getAttributes(), null);
    }

    private AttributesCoercion coerceAttributesByXSL(
            MPPSContext ctx, ArchiveAttributeCoercion rule, AttributesCoercion next) {
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

    private SAXTransformer.SetupTransformer setupTransformer(MPPSContext ctx) {
        return t -> {
            t.setParameter("LocalAET", ctx.getCalledAET());
            if (ctx.getCallingAET() != null)
                t.setParameter("RemoteAET", ctx.getCallingAET());

            t.setParameter("RemoteHost", ctx.getRemoteHostName());
        };
    }
}
