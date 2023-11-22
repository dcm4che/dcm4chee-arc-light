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

package org.dcm4chee.arc.query.scu.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.io.XSLTAttributesCoercion;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.MergeMWLQueryParam;
import org.dcm4chee.arc.coerce.CoercionFactory;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.mima.SupplementAssigningAuthorities;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2016
 */
@ApplicationScoped
public class CFindSCUImpl implements CFindSCU {

    private static final Logger LOG = LoggerFactory.getLogger(CFindSCUImpl.class);
    private static final ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();
    private static final int PCID = 1;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private CoercionFactory coercionFactory;

    @Override
    public List<Attributes> findPatient(ApplicationEntity localAE, String calledAET, int priority, IDWithIssuer pid,
                                        int... returnKeys) throws Exception {
        Association as = openAssociation(localAE, calledAET, UID.PatientRootQueryRetrieveInformationModelFind,
                queryOptions(false));
        try {
            return findPatient(as, priority, pid, returnKeys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    @Override
    public List<Attributes> findPatient(Association as, int priority, IDWithIssuer pid, int... returnKeys)
            throws Exception {
        return find(as, priority,
                pid.exportPatientIDWithIssuer(
                        withQueryLevelAndReturnKeys("PATIENT", returnKeys,
                                new Attributes(3 + returnKeys.length))));
    }

    @Override
    public List<Attributes> findStudiesOfPatient(
            ApplicationEntity localAE, String calledAET, int priority, IDWithIssuer pid, int... returnKeys)
            throws Exception {
        return find(localAE, calledAET, queryOptions(false), priority,
                pid.exportPatientIDWithIssuer(withQueryLevelAndReturnKeys("STUDY", returnKeys,
                        new Attributes(3 + returnKeys.length))));
    }

    @Override
    public List<Attributes> find(ApplicationEntity localAE, String calledAET, EnumSet<QueryOption> queryOptions,
            int priority, Attributes keys) throws Exception {
        return find(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFind, queryOptions, priority, keys);
    }

    @Override
    public List<Attributes> findMWLItems(ApplicationEntity localAE, MergeMWLQueryParam queryParam, int priority)
            throws Exception {
        Attributes keys = new Attributes();
        AttributesBuilder.setNullIfAbsent(keys,
                Tag.AccessionNumber,
                Tag.IssuerOfAccessionNumberSequence,
                Tag.InstitutionName,
                Tag.ReferencedStudySequence,
                Tag.ReferringPhysicianName,
                Tag.PatientName,
                Tag.PatientID,
                Tag.IssuerOfPatientID,
                Tag.IssuerOfPatientIDQualifiersSequence,
                Tag.PatientBirthDate,
                Tag.PatientSex,
                Tag.PatientSize,
                Tag.PatientWeight,
                Tag.PatientAddress,
                Tag.PatientSexNeutered,
                Tag.StudyInstanceUID,
                Tag.RequestingPhysicianIdentificationSequence,
                Tag.RequestingPhysician,
                Tag.RequestingService,
                Tag.RequestingServiceCodeSequence,
                Tag.RequestedProcedureDescription,
                Tag.RequestedProcedureCodeSequence,
                Tag.AdmissionID,
                Tag.IssuerOfAdmissionIDSequence,
                Tag.ScheduledProcedureStepSequence,
                Tag.RequestedProcedureID,
                Tag.ReasonForTheRequestedProcedure,
                Tag.ReasonForRequestedProcedureCodeSequence);
        queryParam.setMatchingKeys(keys);
        return findMWLItems(localAE, queryParam.mwlSCP, EnumSet.noneOf(QueryOption.class), priority, keys);
    }

    @Override
    public List<Attributes> findMWLItems(ApplicationEntity localAE, String calledAET, EnumSet<QueryOption> queryOptions,
            int priority, Attributes keys) throws Exception {
        return find(localAE, calledAET, UID.ModalityWorklistInformationModelFind, queryOptions, priority, keys);
    }

    private List<Attributes> find(ApplicationEntity localAE, String calledAET, String cuid,
            EnumSet<QueryOption> queryOptions, int priority, Attributes keys) throws Exception {
        Association as = openAssociation(localAE, calledAET, cuid, queryOptions);
        try {
            return find(as, priority, keys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    @Override
    public List<Attributes> findStudy(ApplicationEntity localAE, String calledAET, int priority, String studyIUID,
                                      int... returnKeys) throws Exception {
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFind,
                queryOptions(false));
        try {
            return findStudy(as, priority, studyIUID, returnKeys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    @Override
    public List<Attributes> findStudiesByAccessionNumber(
            ApplicationEntity localAE, String calledAET, int priority, String accNo, int... returnKeys)
            throws Exception {
        Attributes keys = withQueryLevelAndReturnKeys("STUDY", returnKeys,
                new Attributes(2 + returnKeys.length));
        keys.setString(Tag.AccessionNumber, VR.SH, accNo);
        return find(localAE, calledAET, queryOptions(false), priority, keys);
    }

    @Override
    public List<Attributes> findStudy(Association as, int priority, String studyIUID, int... returnKeys)
            throws Exception {
        return find(as, priority,
                withUID(Tag.StudyInstanceUID, studyIUID,
                        withQueryLevelAndReturnKeys("STUDY", returnKeys,
                                new Attributes(2 + returnKeys.length))));
    }

    @Override
    public List<Attributes> findSeries(ApplicationEntity localAE, String calledAET, int priority, String studyIUID,
                                       String seriesIUID, int... returnKeys) throws Exception {
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFind,
                queryOptions(studyIUID == null));
        try {
            return findSeries(as, priority, studyIUID, seriesIUID, returnKeys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    @Override
    public List<Attributes> findSeries(Association as, int priority, String studyIUID, String seriesIUID,
                                       int... returnKeys) throws Exception {
        return find(as, priority,
                withUID(Tag.SeriesInstanceUID, seriesIUID,
                        withUID(Tag.StudyInstanceUID, studyIUID,
                                withQueryLevelAndReturnKeys("SERIES", returnKeys,
                                        new Attributes(3 + returnKeys.length)))));
    }

    @Override
    public List<Attributes> findInstance(ApplicationEntity localAE, String calledAET, int priority, String studyIUID,
                                         String seriesIUID, String sopIUID, int... returnKeys) throws Exception {
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFind,
                queryOptions(studyIUID == null || seriesIUID == null));
        try {
            return findInstance(as, priority, studyIUID, seriesIUID, sopIUID, returnKeys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    @Override
    public List<Attributes> findInstance(Association as, int priority, String studyIUID, String seriesIUID,
                                         String sopIUID, int... returnKeys) throws Exception {
        return find(as, priority,
                withUID(Tag.SOPInstanceUID, sopIUID,
                        withUID(Tag.SeriesInstanceUID, seriesIUID,
                                withUID(Tag.StudyInstanceUID, studyIUID,
                                        withQueryLevelAndReturnKeys("IMAGE", returnKeys,
                                                new Attributes(4 + returnKeys.length))))));
    }

    @Override
    public Association openAssociation(ApplicationEntity localAE, String calledAET,
                                       String cuid, EnumSet<QueryOption> queryOptions)
            throws Exception {
        return localAE.connect(aeCache.findApplicationEntity(calledAET), createAARQ(cuid, queryOptions));
    }

    @Override
    public DimseRSP query(Association as, int priority, Attributes keys, int autoCancel, int capacity,
                          Duration splitStudyDateRange) throws Exception {
        String cuid = getAbstractSyntax(as);
        if (as.getRequestedQueryOptionsFor(cuid).contains(QueryOption.DATETIME)
                && !as.getQueryOptionsFor(cuid).contains(QueryOption.DATETIME))
            keys.accept(nullifyTM, true);
        DateRange dateRange;
        if (splitStudyDateRange != null
                && !keys.containsValue(Tag.StudyInstanceUID)
                && !keys.containsValue(Tag.StudyTime)
                && (dateRange = keys.getDateRange(Tag.StudyDate)) != null
                && dateRange.getStartDate() != null) {
            long startDate = dateRange.getStartDate().getTime();
            long endDate = dateRange.getEndDate() != null
                    ? dateRange.getEndDate().getTime()
                    : System.currentTimeMillis();
            if (endDate - startDate > splitStudyDateRange.getSeconds() * 1000)
                return new SplitQuery(as, cuid, priority, keys, autoCancel, capacity,
                        startDate, endDate, splitStudyDateRange);
        }
        return as.cfind(cuid, priority, keys, UID.ImplicitVRLittleEndian, autoCancel, capacity);
    }

    private static String getAbstractSyntax(Association as) {
        return as.getAAssociateRQ().getPresentationContext(PCID).getAbstractSyntax();
    }

    private List<Attributes> find(Association as, int priority, Attributes keys) throws Exception {
        List<Attributes> list = new ArrayList<>();
        DimseRSP rsp = query(as, priority, coerceCFindRQ(as, keys), 0, 1, null);
        rsp.next();
        Attributes match = rsp.getDataset();
        String defaultCharacterSet = as.getApplicationEntity().getAEExtensionNotNull(ArchiveAEExtension.class)
                .defaultCharacterSet();
        while (rsp.next()) {
            if (defaultCharacterSet != null && !match.containsValue(Tag.SpecificCharacterSet)) {
                LOG.info("{}: No Specific Character Set (0008,0005) in received C-FIND RSP - " +
                        "supplement configured Default Character Set: {}", as, defaultCharacterSet);
                match.setString(Tag.SpecificCharacterSet, VR.CS, defaultCharacterSet);
            }
            list.add(coerceCFindRSP(as, match));
            match = rsp.getDataset();
        }
        Attributes cmd = rsp.getCommand();
        int status = cmd.getInt(Tag.Status, -1);
        if (status != Status.Success)
            throw new DicomServiceException(status, cmd.getString(Tag.ErrorComment));
        return list;
    }

    private static Attributes.Visitor nullifyTM = new Attributes.Visitor(){
        @Override
        public boolean visit(Attributes attrs, int tag, VR vr, Object value) throws Exception {
            if (vr == VR.TM && value != Value.NULL)
                attrs.setNull(tag, VR.TM);

            return true;
        }
    };

    private static EnumSet<QueryOption> queryOptions(boolean relational) {
        EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
        if (relational)
            queryOptions.add(QueryOption.RELATIONAL);
        return queryOptions;
    }

    private static Attributes withQueryLevelAndReturnKeys(String level, int[] returnKeys, Attributes keys) {
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, level);
        for (int tag : returnKeys)
            keys.setNull(tag, DICT.vrOf(tag));
        return keys;
    }

    private static Attributes withUID(int tag, String value, Attributes keys) {
        if (value != null)
            keys.setString(tag, VR.UI, value);
        return keys;
    }

    private static AAssociateRQ createAARQ(String cuid, EnumSet<QueryOption> queryOptions) {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.addPresentationContext(new PresentationContext(PCID, cuid, UID.ImplicitVRLittleEndian));
        if (queryOptions != null)
            aarq.addExtendedNegotiation(new ExtendedNegotiation(cuid,
                    QueryOption.toExtendedNegotiationInformation(queryOptions)));
        return aarq;
    }

    @Override
    public Attributes coerceCFindRQ(Association as, Attributes keys) throws Exception {
        return coerce(Dimse.C_FIND_RQ, as, keys);
    }

    @Override
    public Attributes coerceCFindRSP(Association as, Attributes keys) throws Exception {
        return coerce(Dimse.C_FIND_RSP, as, keys);
    }

    private Attributes coerce(Dimse dimse, Association as, final Attributes keys) throws Exception {
        ArchiveAEExtension arcAE = as.getApplicationEntity().getAEExtension(ArchiveAEExtension.class);
        List<ArchiveAttributeCoercion2> coercions = arcAE.attributeCoercions2()
                .filter(descriptor -> descriptor.match(
                        TransferCapability.Role.SCP,
                        dimse,
                        getAbstractSyntax(as),
                        as.getConnection().getHostname(),
                        as.getCallingAET(),
                        as.getRemoteHostName(),
                        as.getCalledAET(),
                        keys))
                .collect(Collectors.toList());
        if (coercions.isEmpty()) {
            ArchiveAttributeCoercion rule = arcAE.findAttributeCoercion(
                    dimse,
                    TransferCapability.Role.SCP,
                    getAbstractSyntax(as),
                    as.getConnection().getHostname(),
                    as.getCallingAET(),
                    as.getRemoteHostName(),
                    as.getCalledAET(),
                    keys);
            if (rule != null)
                return coerceLegacy(as, rule, keys);
        } else {
            for (ArchiveAttributeCoercion2 coercion : coercions) {
                try {
                    if (coercionFactory.getCoercionProcessor(coercion).coerce(
                            coercion,
                            getAbstractSyntax(as),
                            as.getLocalHostName(),
                            as.getCallingAET(),
                            as.getRemoteHostName(),
                            as.getCalledAET(),
                            keys,
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
        return keys;
    }

    private Attributes coerceLegacy(Association as, ArchiveAttributeCoercion rule, Attributes keys) throws Exception {
        AttributesCoercion coercion = null;
        coercion = coerceAttributesByXSL(as, rule, coercion);
        coercion = SupplementAssigningAuthorities.forQuery(rule.getSupplementFromDevice(), coercion);
        coercion = rule.supplementIssuerOfPatientID(coercion);
        coercion = rule.nullifyIssuerOfPatientID(keys, coercion);
        coercion = rule.mergeAttributes(coercion);
        coercion = NullifyAttributesCoercion.valueOf(rule.getNullifyTags(), coercion);
        if (rule.isTrimISO2022CharacterSet())
            coercion = new TrimISO2020CharacterSetAttributesCoercion(coercion);
        coercion = UseCallingAETitleAsCoercion.of(rule.getUseCallingAETitleAs(), as.getCallingAET(), coercion);
        if (coercion != null) {
            LOG.info("{}: Coerce Attributes from rule: {}", as, rule);
            coercion.coerce(keys = new Attributes(keys), null);
        }
        return keys;
    }

    private AttributesCoercion coerceAttributesByXSL(
            Association as, ArchiveAttributeCoercion rule, AttributesCoercion next) {
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
            try {
                Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
                return new XSLTAttributesCoercion(tpls, null)
                        .includeKeyword(!rule.isNoKeywords())
                        .setupTransformer(setupTransformer(as));
            } catch (TransformerConfigurationException e) {
                LOG.error("{}: Failed to compile XSL: {}", as, xsltStylesheetURI, e);
            }
        return next;
    }

    private SAXTransformer.SetupTransformer setupTransformer(Association as) {
        return t -> {
            t.setParameter("LocalAET", as.getCallingAET());
            t.setParameter("RemoteAET", as.getCalledAET());
            t.setParameter("RemoteHost", as.getRemoteHostName());
        };
    }
}
