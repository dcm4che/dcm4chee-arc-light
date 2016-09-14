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

package org.dcm4chee.arc.retrieve.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.io.XSLTAttributesCoercion;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.LeadingCFindSCPQueryCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.scu.CFindSCUAttributeCoercion;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
@ApplicationScoped
public class RetrieveServiceImpl implements RetrieveService {

    private static Logger LOG = LoggerFactory.getLogger(RetrieveServiceImpl.class);

    private static final int MAX_FAILED_IUIDS_LEN = 4000;

    private static final Expression<?>[] SELECT = {
            QLocation.location.pk,
            QLocation.location.storageID,
            QLocation.location.storagePath,
            QLocation.location.objectType,
            QLocation.location.transferSyntaxUID,
            QLocation.location.digest,
            QLocation.location.size,
            QLocation.location.status,
            QSeries.series.pk,
            QInstance.instance.pk,
            QInstance.instance.sopClassUID,
            QInstance.instance.sopInstanceUID,
            QInstance.instance.retrieveAETs,
            QInstance.instance.availability,
            QUIDMap.uIDMap,
            QueryBuilder.instanceAttributesBlob.encodedAttributes
    };

    static final Expression<?>[] PATIENT_STUDY_SERIES_ATTRS = {
            QStudy.study.pk,
            QStudy.study.studyInstanceUID,
            QStudy.study.accessTime,
            QStudy.study.failedRetrieves,
            QStudy.study.failedSOPInstanceUIDList,
            QSeries.series.seriesInstanceUID,
            QSeries.series.failedRetrieves,
            QSeries.series.failedSOPInstanceUIDList,
            QueryBuilder.seriesAttributesBlob.encodedAttributes,
            QueryBuilder.studyAttributesBlob.encodedAttributes,
            QueryBuilder.patientAttributesBlob.encodedAttributes
    };

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private Device device;

    @Inject
    private CodeCache codeCache;

    @Inject
    private RetrieveServiceEJB ejb;

    @Inject
    private CFindSCU cfindscu;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private LeadingCFindSCPQueryCache leadingCFindSCPQueryCache;

    StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    @Override
    public RetrieveContext newRetrieveContextGET(
            Association as, Attributes rqCmd, QueryRetrieveLevel2 qrLevel, Attributes keys) {
        RetrieveContext ctx = newRetrieveContext(as, qrLevel, keys);
        ctx.setPriority(rqCmd.getInt(Tag.Priority, 0));
        ctx.setDestinationAETitle(as.getRemoteAET());
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextMOVE(
            Association as, Attributes rqCmd, QueryRetrieveLevel2 qrLevel, Attributes keys)
            throws ConfigurationException {
        RetrieveContext ctx = newRetrieveContext(as, qrLevel, keys);
        ctx.setPriority(rqCmd.getInt(Tag.Priority, 0));
        ctx.setDestinationAETitle(rqCmd.getString(Tag.MoveDestination));
        ctx.setMoveOriginatorMessageID(rqCmd.getInt(Tag.MessageID, 0));
        ctx.setMoveOriginatorAETitle(as.getRemoteAET());
        ctx.setDestinationAE(aeCache.findApplicationEntity(ctx.getDestinationAETitle()));
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextWADO(
            HttpServletRequest request, String localAET, String studyUID, String seriesUID, String objectUID) {
        RetrieveContext ctx = newRetrieveContext(localAET, studyUID, seriesUID, objectUID);
        ctx.setHttpRequest(request);
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextSTORE(
            String localAET, String studyUID, String seriesUID, String objectUID, String destAET)
            throws ConfigurationException {
        RetrieveContext ctx = newRetrieveContext(localAET, studyUID, seriesUID, objectUID);
        ctx.setDestinationAETitle(destAET);
        ctx.setDestinationAE(aeCache.findApplicationEntity(destAET));
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextIOCM(
            HttpServletRequest request, String localAET, String studyUID, String... seriesUIDs) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(localAET, true).getAEExtension(ArchiveAEExtension.class);
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, localAET, null);
        ctx.setHttpRequest(request);
        ctx.setStudyInstanceUIDs(studyUID);
        ctx.setSeriesInstanceUIDs(seriesUIDs);
        return ctx;
    }

    private RetrieveContext newRetrieveContext(String localAET, String studyUID, String seriesUID, String objectUID) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(localAET, true).getAEExtension(ArchiveAEExtension.class);
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, localAET, arcAE.getQueryRetrieveView());
        initCodes(ctx);
        if (studyUID != null)
            ctx.setStudyInstanceUIDs(studyUID);
        if (seriesUID != null)
            ctx.setSeriesInstanceUIDs(seriesUID);
        if (objectUID != null)
            ctx.setSopInstanceUIDs(objectUID);
        return ctx;
    }

    private RetrieveContext newRetrieveContext(Association as, QueryRetrieveLevel2 qrLevel, Attributes keys) {
        ArchiveAEExtension arcAE = as.getApplicationEntity().getAEExtension(ArchiveAEExtension.class);
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, as.getLocalAET(), arcAE.getQueryRetrieveView());
        ctx.setRequestAssociation(as);
        ctx.setQueryRetrieveLevel(qrLevel);
        initCodes(ctx);
        IDWithIssuer pid = IDWithIssuer.pidOf(keys);
        if (pid != null)
            ctx.setPatientIDs(pid);
        switch (qrLevel) {
            case IMAGE:
                ctx.setSopInstanceUIDs(keys.getStrings(Tag.SOPInstanceUID));
            case SERIES:
                ctx.setSeriesInstanceUIDs(keys.getStrings(Tag.SeriesInstanceUID));
            case STUDY:
                ctx.setStudyInstanceUIDs(keys.getStrings(Tag.StudyInstanceUID));
        }
        return ctx;
    }

    private void initCodes(RetrieveContext ctx) {
        QueryRetrieveView qrView = ctx.getQueryRetrieveView();
        ctx.setHideRejectionNotesWithCode(
                codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes()));
        ctx.setShowInstancesRejectedByCode(
                codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()));
    }

    @Override
    public boolean calculateMatches(RetrieveContext ctx)  {
        StatelessSession session = openStatelessSession();
        Collection<InstanceLocations> matches = ctx.getMatches();
        matches.clear();
        try {
            HashMap<Long,InstanceLocations> instMap = new HashMap<>();
            HashMap<Long,Attributes> seriesAttrsMap = new HashMap<>();
            HashMap<Long,StudyInfo> studyInfoMap = new HashMap<>();
            for (Tuple tuple : createQuery(ctx, session).fetch()) {
                Long instPk = tuple.get(QInstance.instance.pk);
                InstanceLocations match = instMap.get(instPk);
                if (match == null) {
                    Long seriesPk = tuple.get(QSeries.series.pk);
                    Attributes seriesAttrs = seriesAttrsMap.get(seriesPk);
                    if (seriesAttrs == null) {
                        SeriesAttributes seriesAttributes = getSeriesAttributes(session, seriesPk);
                        studyInfoMap.put(seriesAttributes.studyInfo.getStudyPk(), seriesAttributes.studyInfo);
                        ctx.getSeriesInfos().add(seriesAttributes.seriesInfo);
                        seriesAttrsMap.put(seriesPk, seriesAttrs = seriesAttributes.attrs);
                    }
                    Attributes instAttrs = AttributesBlob.decodeAttributes(
                            tuple.get(QueryBuilder.instanceAttributesBlob.encodedAttributes), null);
                    Attributes.unifyCharacterSets(seriesAttrs, instAttrs);
                    instAttrs.addAll(seriesAttrs);
                    match = newInstanceLocations(
                            tuple.get(QInstance.instance.sopClassUID),
                            tuple.get(QInstance.instance.sopInstanceUID),
                            tuple.get(QInstance.instance.retrieveAETs),
                            tuple.get(QInstance.instance.availability),
                            instAttrs);
                    matches.add(match);
                    instMap.put(instPk, match);
                }
                match.getLocations().add(loadLocation(tuple));
            }
            ctx.setNumberOfMatches(matches.size());
            ctx.getStudyInfos().addAll(studyInfoMap.values());
            updateStudyAccessTime(ctx);
            return !matches.isEmpty();
        } finally {
            session.close();
        }
    }

    @Override
    public InstanceLocationsImpl newInstanceLocations(
            String sopClassUID, String sopInstanceUID, String retrieveAETs, Availability availability, Attributes attrs) {
        return new InstanceLocationsImpl(sopClassUID, sopInstanceUID, retrieveAETs, availability, attrs);
    }

    private void updateStudyAccessTime(RetrieveContext ctx) {
        Duration maxAccessTimeStaleness = ctx.getArchiveAEExtension().getArchiveDeviceExtension()
                .getMaxAccessTimeStaleness();
        if (maxAccessTimeStaleness == null)
            return;

        long now = System.currentTimeMillis();
        long minAccessTime = now - maxAccessTimeStaleness.getSeconds() * 1000;
        for (StudyInfo study : ctx.getStudyInfos()) {
            if (study.getAccessTime().getTime() < minAccessTime)
                ejb.updateStudyAccessTime(study.getStudyPk());
        }
    }

    private Location loadLocation(Tuple next) {
        Location location = new Location.Builder()
                .pk(next.get(QLocation.location.pk))
                .storageID(next.get(QLocation.location.storageID))
                .storagePath(next.get(QLocation.location.storagePath))
                .objectType(next.get(QLocation.location.objectType))
                .transferSyntaxUID(next.get(QLocation.location.transferSyntaxUID))
                .digest(next.get(QLocation.location.digest))
                .size(next.get(QLocation.location.size))
                .status(next.get(QLocation.location.status))
                .build();
        location.setUidMap(next.get(QUIDMap.uIDMap));
        return location;
    }

    private static class SeriesAttributes {
        final Attributes attrs;
        final StudyInfo studyInfo;
        final SeriesInfo seriesInfo;

        SeriesAttributes(Attributes attrs, StudyInfo studyInfo, SeriesInfo seriesInfo) {
            this.attrs = attrs;
            this.studyInfo = studyInfo;
            this.seriesInfo = seriesInfo;
        }

    }

    private SeriesAttributes getSeriesAttributes(StatelessSession session, Long seriesPk) {
        Tuple tuple = new HibernateQuery<Void>(session).select(PATIENT_STUDY_SERIES_ATTRS)
                .from(QSeries.series)
                .join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob)
                .join(QSeries.series.study, QStudy.study)
                .join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob)
                .join(QStudy.study.patient, QPatient.patient)
                .join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob)
                .where(QSeries.series.pk.eq(seriesPk))
                .fetchOne();
        StudyInfo studyInfo = new StudyInfoImpl(
                tuple.get(QStudy.study.pk),
                tuple.get(QStudy.study.studyInstanceUID),
                tuple.get(QStudy.study.accessTime),
                tuple.get(QStudy.study.failedRetrieves),
                tuple.get(QStudy.study.failedSOPInstanceUIDList));
        SeriesInfo seriesInfo = new SeriesInfoImpl(
                studyInfo.getStudyInstanceUID(),
                tuple.get(QSeries.series.seriesInstanceUID),
                tuple.get(QSeries.series.failedRetrieves),
                tuple.get(QSeries.series.failedSOPInstanceUIDList));
        Attributes patAttrs = AttributesBlob.decodeAttributes(
                tuple.get(QueryBuilder.patientAttributesBlob.encodedAttributes), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(
                tuple.get(QueryBuilder.studyAttributesBlob.encodedAttributes), null);
        Attributes seriesAttrs = AttributesBlob.decodeAttributes(
                tuple.get(QueryBuilder.seriesAttributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + seriesAttrs.size() + 5);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        attrs.addAll(seriesAttrs);
        return new SeriesAttributes(attrs, studyInfo, seriesInfo);
}

    private HibernateQuery<Tuple> createQuery(RetrieveContext ctx, StatelessSession session) {
        HibernateQuery<Tuple> query = new HibernateQuery<Void>(session).select(SELECT)
                .from(QLocation.location)
                .join(QLocation.location.instance, QInstance.instance)
                .join(QInstance.instance.attributesBlob, QueryBuilder.instanceAttributesBlob)
                .join(QInstance.instance.series, QSeries.series)
                .join(QSeries.series.study, QStudy.study)
                .leftJoin(QLocation.location.uidMap, QUIDMap.uIDMap);

        IDWithIssuer[] pids = ctx.getPatientIDs();
        if (pids.length > 0) {
            query = query.join(QStudy.study.patient, QPatient.patient);
            query = QueryBuilder.applyPatientIDJoins(query, pids);
        }

        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QueryBuilder.patientIDPredicate(pids));
        predicate.and(QueryBuilder.accessControl(ctx.getAccessControlIDs()));
        predicate.and(QueryBuilder.uidsPredicate(QStudy.study.studyInstanceUID, ctx.getStudyInstanceUIDs()));
        predicate.and(QueryBuilder.uidsPredicate(QSeries.series.seriesInstanceUID, ctx.getSeriesInstanceUIDs()));
        predicate.and(QueryBuilder.uidsPredicate(QInstance.instance.sopInstanceUID, ctx.getSopInstanceUIDs()));
        if (ctx.getQueryRetrieveView() != null) {
            predicate.and(QueryBuilder.hideRejectedInstance(ctx.getShowInstancesRejectedByCode(),
                    ctx.isHideNotRejectedInstances()));
            predicate.and(QueryBuilder.hideRejectionNote(ctx.getHideRejectionNotesWithCode()));
        }
        Location.ObjectType objectType = ctx.getObjectType();
        if (objectType != null)
            predicate.and(QLocation.location.objectType.eq(objectType));
        return query.where(predicate);
    }

    @Override
    public Transcoder openTranscoder(RetrieveContext ctx, InstanceLocations inst,
                                     Collection<String> tsuids, boolean fmi) throws IOException {
        LocationDicomInputStream locationInputStream = openLocationInputStream(ctx, inst);
        String tsuid = locationInputStream.getLocation().getTransferSyntaxUID();
        if (!tsuids.isEmpty() && !tsuids.contains(tsuid)) {
            tsuid = fmi || tsuids.contains(UID.ExplicitVRLittleEndian)
                    ? UID.ExplicitVRLittleEndian
                    : UID.ImplicitVRLittleEndian;
        }
        Transcoder transcoder = new Transcoder(locationInputStream.getDicomInputStream());
        transcoder.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
        transcoder.setConcatenateBulkDataFiles(true);
        transcoder.setBulkDataDirectory(ctx.getArchiveAEExtension().getBulkDataSpoolDirectoryFile());
        transcoder.setDestinationTransferSyntax(tsuid);
        transcoder.setCloseOutputStream(false);
        transcoder.setIncludeFileMetaInformation(fmi);
        return transcoder;
    }

    @Override
    public DicomInputStream openDicomInputStream(RetrieveContext ctx, InstanceLocations inst) throws IOException {
        return openLocationInputStream(ctx, inst).getDicomInputStream();
    }

    @Override
    public Attributes loadMetadata(RetrieveContext ctx, InstanceLocations inst) throws IOException {
        IOException ex = null;
        String studyInstanceUID = inst.getAttributes().getString(Tag.StudyInstanceUID);
        for (Location location : inst.getLocations()) {
            if (location.getObjectType() == Location.ObjectType.METADATA)
                try (JsonParser parser = Json.createParser(
                        new InputStreamReader(openInputStream(ctx, location, studyInstanceUID), "UTF-8"))) {
                    return new JSONReader(parser).readDataset(null);
                } catch (IOException e) {
                    ex = e;
                }
        }
        if (ex != null) throw ex;
        return null;
    }

    @Override
    public Collection<InstanceLocations> removeNotAccessableMatches(RetrieveContext ctx) {
        ArchiveDeviceExtension arcDev = ctx.getArchiveAEExtension().getArchiveDeviceExtension();
        Collection<InstanceLocations> matches = ctx.getMatches();
        Collection<InstanceLocations> notAccessable = new ArrayList<>(matches.size());
        Iterator<InstanceLocations> iter = matches.iterator();
        while (iter.hasNext()) {
            InstanceLocations match = iter.next();
            if (!isAccessable(arcDev, match)) {
                iter.remove();
                notAccessable.add(match);
            }
        }
        return notAccessable;
    }

    @Override
    public AttributesCoercion getAttributesCoercion(RetrieveContext ctx, InstanceLocations inst) {
        return uidRemap(inst, new MergeAttributesCoercion(inst.getAttributes(), coercion(ctx, inst)));
    }

    @Override
    public void waitForPendingCStoreForward(RetrieveContext ctx) {
        Association fwdas = ctx.getFallbackAssociation();
        if (fwdas == null)
            return;

        Association rqas = ctx.getRequestAssociation();
        String fallbackMoveSCP = fwdas.getCalledAET();
        String suids = Arrays.toString(ctx.getStudyInstanceUIDs());
        String destAET = ctx.getDestinationAETitle();
        try {
            LOG.info("{}: wait for pending forward of objects of study{} from {} to {}",
                    rqas, suids, fallbackMoveSCP, destAET);
            ctx.waitForPendingCStoreForward();
            LOG.info("{}: complete forward of objects of study{} from {} to {} - remaining={}, completed={}, failed={}, warning={}",
                    rqas, suids, fallbackMoveSCP, destAET, ctx.remaining(), ctx.completed(), ctx.failed(), ctx.warning());
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for pending forward of  objects of study{} from {} to {}:\n",
                    rqas, suids, fallbackMoveSCP, destAET, e);
        }
    }

    @Override
    public void waitForPendingCMoveForward(RetrieveContext ctx) {
        waitForPendingCMoveForward(ctx, ctx.getForwardAssociation());
        waitForPendingCMoveForward(ctx, ctx.getFallbackAssociation());
    }

    private void waitForPendingCMoveForward(RetrieveContext ctx, Association fwdas) {
        if (fwdas == null)
            return;

        Association rqas = ctx.getRequestAssociation();
        String moveSCP = fwdas.getRemoteAET();
        LOG.info("{}: wait for outstanding C-MOVE RSP(s) for C-MOVE RQ(s) forwarded to {}",
                rqas, moveSCP);
        try {
            fwdas.waitForOutstandingRSP();
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for outstanding C-MOVE RSP(s) for C-MOVE RQ(s) forwarded to {}",
                    rqas, moveSCP, e);
        }
        try {
            fwdas.release();
        } catch (IOException e) {
            LOG.warn("{}: failed to release association to {}:\n", rqas, moveSCP, e);
        }
    }

    @Override
    public void updateFailedSOPInstanceUIDList(RetrieveContext ctx) {
        int retrieved = ctx.getNumberOfCStoreForwards();
        if (retrieved > 0)
            ejb.updateFailedSOPInstanceUIDList(ctx, failedIUIDList(ctx));
    }

    private String failedIUIDList(RetrieveContext ctx) {
        int failed = ctx.getFallbackMoveRSPFailed();
        if (failed == 0)
            return null;

        Association as = ctx.getRequestAssociation();
        Association fwdas = ctx.getFallbackAssociation();
        LOG.warn("{}: Failed to retrieve {} from {} objects of study{} from {}",
                as, failed, ctx.getNumberOfMatches(),
                Arrays.toString(ctx.getStudyInstanceUIDs()), fwdas.getCalledAET());

        String[] failedIUIDs = ctx.getFallbackMoveRSPFailedIUIDs();
        if (failedIUIDs.length == 0) {
            LOG.warn("{}: Missing Failed SOP Instance UID List in C-MOVE-RSP from {}", as, fwdas.getCalledAET());
            return "*";
        }
        if (failed != failedIUIDs.length) {
            LOG.warn("{}: Number Of Failed Suboperations [{}] does not match " +
                            "size of Failed SOP Instance UID List [{}] in C-MOVE-RSP from {}",
                    as, failed, failedIUIDs.length, fwdas.getCalledAET());
        }
        String concat = StringUtils.concat(failedIUIDs, '\\');
        if (concat.length() > MAX_FAILED_IUIDS_LEN) {
            LOG.warn("{}: Failed SOP Instance UID List [{}] in C-MOVE-RSP from {} too large to persist in DB",
                    as, failed, fwdas.getCalledAET());
            return "*";
        }
        return concat;
    }

    private AttributesCoercion uidRemap(InstanceLocations inst, AttributesCoercion next) {
        UIDMap uidMap = inst.getLocations().get(0).getUidMap();
        return uidMap != null ? new RemapUIDsAttributesCoercion(uidMap.getUIDMap(), next) : next;
    }

    private AttributesCoercion coercion(RetrieveContext ctx, InstanceLocations inst) {
        ArchiveAEExtension aeExt = ctx.getArchiveAEExtension();
        ArchiveAttributeCoercion rule = aeExt.findAttributeCoercion(
                ctx.getRequestorHostName(), ctx.getRequestorAET(), TransferCapability.Role.SCP, Dimse.C_STORE_RQ,
                inst.getSopClassUID());
        if (rule == null)
            return null;

        AttributesCoercion coercion = null;
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
        try {
            Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
            coercion = new XSLTAttributesCoercion(tpls, null).includeKeyword(!rule.isNoKeywords());
        } catch (TransformerConfigurationException e) {
            LOG.error("{}: Failed to compile XSL: {}", ctx.getLocalAETitle(), xsltStylesheetURI, e);
        }
        String leadingCFindSCP = rule.getLeadingCFindSCP();
        if (leadingCFindSCP != null) {
            coercion = new CFindSCUAttributeCoercion(ctx.getLocalApplicationEntity(),
                    leadingCFindSCP, rule.attributeUpdatePolicy(), cfindscu, leadingCFindSCPQueryCache, coercion);
        }
        return coercion;
    }

    private boolean isAccessable(ArchiveDeviceExtension arcDev, InstanceLocations match) {
        for (Location location : match.getLocations()) {
            if (arcDev.getStorageDescriptor(location.getStorageID()) != null)
                return true;
        }
        return false;
    }

    private LocationDicomInputStream openLocationInputStream(RetrieveContext ctx, InstanceLocations inst)
            throws IOException {
        IOException ex = null;
        String studyInstanceUID = inst.getAttributes().getString(Tag.StudyInstanceUID);
        for (Location location : inst.getLocations()) {
            if (location.getObjectType() == Location.ObjectType.DICOM_FILE)
                try {
                    return openLocationInputStream(ctx, location, studyInstanceUID);
                } catch (IOException e) {
                    ex = e;
                }
        }
        if (ex != null) throw ex;
        return null;
    }

    private LocationDicomInputStream openLocationInputStream(
            RetrieveContext ctx, Location location, String studyInstanceUID)
            throws IOException {
        Storage storage = getStorage(ctx, location.getStorageID());
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(location.getStoragePath());
        readContext.setStudyInstanceUID(studyInstanceUID);
        InputStream stream = storage.openInputStream(readContext);
        try {
            return new LocationDicomInputStream(new DicomInputStream(stream), readContext, location);
        } catch (IOException e) {
            SafeClose.close(stream);
            throw e;
        }
    }

    private InputStream openInputStream(RetrieveContext ctx, Location location, String studyInstanceUID)
            throws IOException {
        Storage storage = getStorage(ctx, location.getStorageID());
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(location.getStoragePath());
        readContext.setStudyInstanceUID(studyInstanceUID);
        return storage.openInputStream(readContext);
    }

    private Storage getStorage(RetrieveContext ctx, String storageID) {
        Storage storage = ctx.getStorage(storageID);
        if (storage == null) {
            ArchiveDeviceExtension arcDev = ctx.getArchiveAEExtension().getArchiveDeviceExtension();
            storage = storageFactory.getStorage(arcDev.getStorageDescriptorNotNull(storageID));
            ctx.putStorage(storageID, storage);
        }
        return storage;
    }
}
