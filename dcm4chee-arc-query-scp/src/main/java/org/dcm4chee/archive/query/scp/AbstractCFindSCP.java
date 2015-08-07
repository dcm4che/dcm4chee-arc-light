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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryTask;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
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
class AbstractCFindSCP extends BasicCFindSCP {

    private static ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

    enum Level {
        PATIENT(Tag.PatientID),
        STUDY(Tag.StudyInstanceUID),
        SERIES(Tag.SeriesInstanceUID),
        IMAGE(Tag.SOPInstanceUID);

        final int uniqueKey;

        Level(int uniqueKey) {
            this.uniqueKey = uniqueKey;
        }

    }

    private final Level[] qrLevels;

    @Inject
    private QueryService queryService;

    protected AbstractCFindSCP(String sopClass, Level... qrLevels) {
        super(sopClass);
        this.qrLevels = qrLevels;
    }

    @Override
    protected QueryTask calculateMatches(Association as, PresentationContext pc, Attributes rq, Attributes keys)
            throws DicomServiceException {

        Level qrLevel = levelOf(keys.getString(Tag.QueryRetrieveLevel));
        EnumSet<QueryOption> queryOpts = queryOpts(as, rq);
        validateIdentifier(keys, qrLevel, queryOpts);
        QueryContext ctx = queryService.newQueryContext(as, queryOpts);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null && !idWithIssuer.getID().equals("*"))
            ctx.setPatientIDs(Collections.singleton(idWithIssuer));
        ctx.setQueryKeys(keys);
        return new ArchiveQueryTask(as, pc, rq, keys, createQuery(qrLevel, ctx));
    }

    private Query createQuery(Level qrLevel, QueryContext ctx) {
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

    private void validateIdentifier(Attributes keys, Level qrLevel, EnumSet<QueryOption> queryOpts)
            throws DicomServiceException {
        boolean relational = queryOpts.contains(QueryOption.RELATIONAL);
        if (qrLevel == Level.PATIENT)
            checkUniqueKey(keys, qrLevel.uniqueKey, true);

        for (int i = 0; qrLevels[i] != qrLevel; i++)
            checkUniqueKey(keys, qrLevels[i].uniqueKey, relational);
    }

    private void checkUniqueKey(Attributes keys, int uniqueKey, boolean relational)
            throws DicomServiceException {
        String[] ids = keys.getStrings(uniqueKey);
        if (ids != null && ids.length > 1)
            throw invalidAttributeValue(uniqueKey, StringUtils.concat(ids, '\\'));
        if (!relational)
            if (ids == null || ids.length == 0)
                throw missingAttribute(uniqueKey);
    }

    private static EnumSet<QueryOption> queryOpts(Association as, Attributes rq) {
        return QueryOption.toOptions(as.getAAssociateAC().getExtNegotiationFor(rq.getString(Tag.AffectedSOPClassUID)));
    }

    private static Level levelOf(String value) throws DicomServiceException {
        try {
            return Level.valueOf(value);
        } catch (NullPointerException e) {
            throw missingAttribute(Tag.QueryRetrieveLevel);
        } catch (IllegalArgumentException e) {
            throw invalidAttributeValue(Tag.QueryRetrieveLevel, value);
        }
    }

    private static DicomServiceException missingAttribute(int tag) {
        return identifierDoesNotMatchSOPClass(
                "Missing " + DICT.keywordOf(tag) + " " + TagUtils.toString(tag), tag);
    }

    private static DicomServiceException invalidAttributeValue(int tag, String value) {
        return identifierDoesNotMatchSOPClass(
                "Invalid " + DICT.keywordOf(tag) + " " + TagUtils.toString(tag) + " - " + value,
                Tag.QueryRetrieveLevel);
    }

    private static DicomServiceException identifierDoesNotMatchSOPClass(String comment, int tag) {
        return new DicomServiceException(Status.IdentifierDoesNotMatchSOPClass, comment)
                .setOffendingElements(tag);
    }

}
