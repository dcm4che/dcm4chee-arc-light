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

package org.dcm4chee.arc.retrieve.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.StudyInfo;
import org.dcm4chee.arc.store.InstanceLocations;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
class LocationQuery {

    private final EntityManager em;
    private final CriteriaBuilder cb;
    private final RetrieveContext ctx;
    private final CriteriaQuery<Tuple> q;
    private final Root<Instance> instance;
    private final Join<Instance, Series> series;
    private final Join<Series, Study> study;
    private final CollectionJoin<Instance, Location> locationPath;
    private final Join<Location, UIDMap> uidMap;
    private final Path<byte[]> instanceAttrBlob;
    private final List<Predicate> predicates = new ArrayList<>();
    private Predicate[] iuidPredicates;

    public LocationQuery(EntityManager em, CriteriaBuilder cb, RetrieveContext ctx, CodeCache codeCache) {
        this.em = em;
        this.cb = cb;
        this.ctx = ctx;
        this.q = cb.createTupleQuery();
        this.instance = q.from(Instance.class);
        this.series = instance.join(Instance_.series);
        this.study = series.join(Series_.study);
        this.locationPath = instance.join(Instance_.locations, JoinType.LEFT);
        if (ctx.getObjectType() != null) {
            locationPath.on(cb.equal(locationPath.get(Location_.objectType), ctx.getObjectType()));
        }
        this.uidMap = locationPath.join(Location_.uidMap, JoinType.LEFT);
        this.instanceAttrBlob = instance.join(Instance_.attributesBlob).get(AttributesBlob_.encodedAttributes);

        if (ctx.getSeriesMetadataUpdate() != null) {
            predicates.add((cb.equal(series.get(Series_.pk), ctx.getSeriesMetadataUpdate().seriesPk)));
            q.orderBy(cb.asc(instance.get(Instance_.instanceNumber)));
        } else {
            QueryBuilder builder = new QueryBuilder(cb);
            if (!QueryBuilder.isUniversalMatching(ctx.getPatientIDs())) {
                builder.patientIDPredicate(predicates, study.join(Study_.patient), ctx.getPatientIDs());
            }
            builder.accessControl(predicates, study, ctx.getAccessControlIDs());
            builder.uidsPredicate(predicates, study.get(Study_.studyInstanceUID), ctx.getStudyInstanceUIDs());
            builder.uidsPredicate(predicates, series.get(Series_.seriesInstanceUID), ctx.getSeriesInstanceUIDs());
            QueryRetrieveView qrView = ctx.getQueryRetrieveView();
            if (qrView != null) {
                builder.hideRejectedInstance(predicates, q, study, series, instance,
                        codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()),
                        qrView.isHideNotRejectedInstances());
                builder.hideRejectionNote(predicates, instance,
                        codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes()));
            }
            String[] sopInstanceUIDs = ctx.getSopInstanceUIDs();
            if (!QueryBuilder.isUniversalMatching(sopInstanceUIDs)) {
                // SQL Server actually does support lesser parameters than its specified limit (2100)
                int limit = getInExpressionCountLimit() - 10;
                if (limit > 0 && sopInstanceUIDs.length > limit) {
                    iuidPredicates =
                            builder.splitUIDPredicates(instance.get(Instance_.sopInstanceUID), sopInstanceUIDs, limit);
                } else {
                    builder.uidsPredicate(predicates, instance.get(Instance_.sopInstanceUID), sopInstanceUIDs);
                }
            }
        }
        q.multiselect(
                locationPath.get(Location_.pk),
                locationPath.get(Location_.storageID),
                locationPath.get(Location_.storagePath),
                locationPath.get(Location_.objectType),
                locationPath.get(Location_.transferSyntaxUID),
                locationPath.get(Location_.digest),
                locationPath.get(Location_.size),
                locationPath.get(Location_.status),
                series.get(Series_.pk),
                instance.get(Instance_.pk),
                instance.get(Instance_.retrieveAETs),
                instance.get(Instance_.externalRetrieveAET),
                instance.get(Instance_.availability),
                instance.get(Instance_.createdTime),
                instance.get(Instance_.updatedTime),
                uidMap.get(UIDMap_.pk),
                uidMap.get(UIDMap_.encodedMap),
                instanceAttrBlob
        );
    }

    private int getInExpressionCountLimit() {
        return ((SessionFactoryImplementor) em.unwrap(Session.class).getSessionFactory())
                .getServiceRegistry().getService(JdbcServices.class)
                .getDialect().getInExpressionCountLimit();
    }

    public void execute(Map<Long, StudyInfo> studyInfoMap) {
        if (iuidPredicates == null)
            execute(studyInfoMap, predicates.toArray(new Predicate[0]));
        else {
            Predicate[] a = new Predicate[predicates.size() + 1];
            predicates.toArray(a);
            for (Predicate uidPredicate : iuidPredicates) {
                a[a.length - 1] = uidPredicate;
                execute(studyInfoMap, a);
            }
        }
    }

    private void execute(Map<Long, StudyInfo> studyInfoMap, Predicate[] predicates) {
        HashMap<Long,InstanceLocations> instMap = new HashMap<>();
        HashMap<Long,Attributes> seriesAttrsMap = new HashMap<>();
        HashMap<Long,Map<String, CodeEntity>> rejectedInstancesOfSeriesMap = new HashMap<>();
        for (Tuple tuple : em.createQuery(q.where(predicates)).getResultList()) {
            Long instPk = tuple.get(instance.get(Instance_.pk));
            InstanceLocations match = instMap.get(instPk);
            if (match == null) {
                Long seriesPk = tuple.get(series.get(Series_.pk));
                Attributes seriesAttrs = seriesAttrsMap.get(seriesPk);
                Map<String, CodeEntity> rejectedInstancesOfSeries = rejectedInstancesOfSeriesMap.get(seriesPk);
                if (seriesAttrs == null) {
                    SeriesAttributes seriesAttributes = new SeriesAttributes(em, cb, seriesPk);
                    studyInfoMap.put(seriesAttributes.studyInfo.getStudyPk(), seriesAttributes.studyInfo);
                    ctx.getSeriesInfos().add(seriesAttributes.seriesInfo);
                    ctx.setPatientUpdatedTime(seriesAttributes.patientUpdatedTime);
                    seriesAttrsMap.put(seriesPk, seriesAttrs = seriesAttributes.attrs);
                    if (ctx.getSeriesMetadataUpdate() != null)
                        rejectedInstancesOfSeriesMap.put(
                                seriesPk, rejectedInstancesOfSeries = getRejectedInstancesOfSeries(seriesAttrs));
                }
                Attributes instAttrs = AttributesBlob.decodeAttributes(tuple.get(instanceAttrBlob), null);
                Attributes.unifyCharacterSets(seriesAttrs, instAttrs);
                instAttrs.addAll(seriesAttrs, true);
                match = instanceLocationsFromDB(tuple, instAttrs, rejectedInstancesOfSeries != null
                        ? rejectedInstancesOfSeries.get(instAttrs.getString(Tag.SOPInstanceUID))
                        : null);
                ctx.getMatches().add(match);
                instMap.put(instPk, match);
            }
            addLocation(match, tuple);
        }
    }

    private InstanceLocations instanceLocationsFromDB(Tuple tuple, Attributes instAttrs, CodeEntity rejectionCode) {
        InstanceLocationsImpl inst = new InstanceLocationsImpl(instAttrs);
        inst.setInstancePk(tuple.get(instance.get(Instance_.pk)));
        inst.setRetrieveAETs(tuple.get(instance.get(Instance_.retrieveAETs)));
        inst.setExternalRetrieveAET(tuple.get(instance.get(Instance_.externalRetrieveAET)));
        inst.setAvailability(tuple.get(instance.get(Instance_.availability)));
        inst.setCreatedTime(tuple.get(instance.get(Instance_.createdTime)));
        inst.setUpdatedTime(tuple.get(instance.get(Instance_.updatedTime)));
        if (rejectionCode != null)
            inst.setRejectionCode(rejectionCode.getCode().toItem());
        return inst;
    }

    private Map<String, CodeEntity> getRejectedInstancesOfSeries(Attributes seriesAttrs) {
        return em.createNamedQuery(RejectedInstance.FIND_BY_SERIES_UID, RejectedInstance.class)
                .setParameter(1, seriesAttrs.getString(Tag.StudyInstanceUID))
                .setParameter(2, seriesAttrs.getString(Tag.SeriesInstanceUID))
                .getResultStream()
                .collect(Collectors.toMap(RejectedInstance::getSopInstanceUID, RejectedInstance::getRejectionNoteCode));
    }

    private void addLocation(InstanceLocations match, Tuple tuple) {
        Long pk = tuple.get(locationPath.get(Location_.pk));
        if (pk == null)
            return;

        Location location = new Location.Builder()
                .pk(pk)
                .storageID(tuple.get(locationPath.get(Location_.storageID)))
                .storagePath(tuple.get(locationPath.get(Location_.storagePath)))
                .objectType(tuple.get(locationPath.get(Location_.objectType)))
                .transferSyntaxUID(tuple.get(locationPath.get(Location_.transferSyntaxUID)))
                .digest(tuple.get(locationPath.get(Location_.digest)))
                .size(tuple.get(locationPath.get(Location_.size)))
                .status(tuple.get(locationPath.get(Location_.status)))
                .build();
        Long uidMapPk = tuple.get(uidMap.get(UIDMap_.pk));
        if (uidMapPk != null) {
            location.setUidMap(new UIDMap(uidMapPk, tuple.get(uidMap.get(UIDMap_.encodedMap))));
        }
        match.getLocations().add(location);
    }
}
