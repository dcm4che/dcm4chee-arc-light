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

package org.dcm4chee.arc.query.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.QueryContext;
import org.hibernate.StatelessSession;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
class InstanceQuery extends AbstractQuery {

    private static final Expression<?>[] SELECT = {
            QSeries.series.pk,
            QInstance.instance.retrieveAETs,
            QInstance.instance.externalRetrieveAET,
            QInstance.instance.availability,
            QInstance.instance.createdTime,
            QInstance.instance.updatedTime,
            QCodeEntity.codeEntity.codeValue,
            QCodeEntity.codeEntity.codingSchemeDesignator,
            QCodeEntity.codeEntity.codeMeaning,
            QLocation.location.storageID,
            QLocation.location.storagePath,
            QLocation.location.transferSyntaxUID,
            QLocation.location.digest,
            QLocation.location.size,
            QInstance.instance.attributesBlob.encodedAttributes
    };

    private Long seriesPk;
    private Attributes seriesAttrs;

    public InstanceQuery(QueryContext context, StatelessSession session) {
        super(context, session);
    }

    @Override
    protected HibernateQuery<Tuple> newHibernateQuery() {
        HibernateQuery<Tuple> q = new HibernateQuery<Void>(session).select(SELECT).from(QInstance.instance);
        q = QueryBuilder.applyInstanceLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = q.leftJoin(QInstance.instance.locations, QLocation.location)
                .on(QLocation.location.objectType.eq(Location.ObjectType.DICOM_FILE));
        q = QueryBuilder.applySeriesLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applyStudyLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applyPatientLevelJoins(q,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam(),
                context.isOrderByPatientName());
        BooleanBuilder predicates = new BooleanBuilder();
        QueryBuilder.addPatientLevelPredicates(predicates,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        QueryBuilder.addStudyLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam(), QueryRetrieveLevel2.IMAGE);
        QueryBuilder.addSeriesLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam(), QueryRetrieveLevel2.IMAGE);
        QueryBuilder.addInstanceLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam());
        return q.where(predicates);
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Long seriesPk = results.get(QSeries.series.pk);
        String retrieveAETs = results.get(QInstance.instance.retrieveAETs);
        Availability availability = results.get(QInstance.instance.availability);
        if (!seriesPk.equals(this.seriesPk)) {
            this.seriesAttrs = context.getQueryService()
                    .getSeriesAttributes(seriesPk, context.getQueryParam());
            this.seriesPk = seriesPk;
        }
        Attributes instAtts = AttributesBlob.decodeAttributes(
                results.get(QInstance.instance.attributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(seriesAttrs, instAtts);
        Attributes attrs = new Attributes(seriesAttrs.size() + instAtts.size() + 2);
        attrs.addAll(seriesAttrs);
        attrs.addAll(instAtts);
        String externalRetrieveAET = results.get(QInstance.instance.externalRetrieveAET);
        if (externalRetrieveAET != null)
            attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs, externalRetrieveAET);
        else
            attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs);
        attrs.setString(Tag.InstanceAvailability, VR.CS, availability.toString());
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceReceiveDateTime, VR.DT,
                results.get(QInstance.instance.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceUpdateDateTime, VR.DT,
                results.get(QInstance.instance.updatedTime));
        if (results.get(QCodeEntity.codeEntity.codeValue) != null) {
            Sequence rejectionCodeSeq = attrs.newSequence(ArchiveTag.PrivateCreator, ArchiveTag.RejectionCodeSequence, 1);
            Attributes item = new Attributes();
            item.setString(Tag.CodeValue, VR.SH, results.get(QCodeEntity.codeEntity.codeValue));
            item.setString(Tag.CodeMeaning, VR.LO, results.get(QCodeEntity.codeEntity.codeMeaning));
            item.setString(Tag.CodingSchemeDesignator, VR.SH, results.get(QCodeEntity.codeEntity.codingSchemeDesignator));
            rejectionCodeSeq.add(item);
        }
        if (results.get(QLocation.location.storageID) != null) {
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageID, VR.LO,
                    results.get(QLocation.location.storageID));
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StoragePath, VR.LO,
                    StringUtils.split(results.get(QLocation.location.storagePath), '/'));
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageTransferSyntax, VR.UI,
                    results.get(QLocation.location.transferSyntaxUID));
            attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectSize, VR.UL,
                    results.get(QLocation.location.size).intValue());
            if (results.get(QLocation.location.digest) != null)
                attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectDigest, VR.LO,
                        results.get(QLocation.location.digest));
        }
        return attrs;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }
}
