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

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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

    @Override
    public List<Attributes> findPatient(ApplicationEntity localAE, String calledAET, int priority, IDWithIssuer pid,
                                        int... returnKeys) throws Exception {
        Association as = openAssociation(localAE, calledAET, UID.PatientRootQueryRetrieveInformationModelFIND,
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
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFIND,
                queryOptions);
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
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFIND,
                queryOptions(false));
        try {
            return findStudy(as, priority, studyIUID, returnKeys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
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
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFIND,
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
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFIND,
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
        AAssociateRQ aarq = as.getAAssociateRQ();
        String cuid = aarq.getPresentationContext(PCID).getAbstractSyntax();
        if (QueryOption.toOptions(aarq.getExtNegotiationFor(cuid)).contains(QueryOption.DATETIME)
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

    private List<Attributes> find(Association as, int priority, Attributes keys) throws Exception {
        List<Attributes> list = new ArrayList<>();
        DimseRSP rsp = query(as, priority, keys, 0, 1, null);
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
            list.add(match);
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
}
