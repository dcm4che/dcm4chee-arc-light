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
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.query.scu.CFindSCU;

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

    private static final ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();
    private static final int PCID = 1;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Override
    public List<Attributes> find(ApplicationEntity localAE, String calledAET, int priority, QueryRetrieveLevel2 level,
                           String studyIUID, String seriesIUID, String sopIUID, int... returnKeys)
            throws Exception {
         Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFIND,
                queryOptions(level, studyIUID, seriesIUID));
        try {
            return find(as, priority, level, studyIUID, seriesIUID, sopIUID, returnKeys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    private EnumSet<QueryOption> queryOptions(QueryRetrieveLevel2 level, String studyIUID, String seriesIUID) {
        return level.compareTo(QueryRetrieveLevel2.STUDY) > 0
                && (studyIUID == null || level.compareTo(QueryRetrieveLevel2.SERIES) > 0 && seriesIUID == null)
                        ? EnumSet.of(QueryOption.RELATIONAL)
                        : EnumSet.noneOf(QueryOption.class);

    }

    @Override
    public Association openAssociation(ApplicationEntity localAE, String calledAET,
                                       String cuid, EnumSet<QueryOption> queryOptions)
            throws Exception {
        return localAE.connect(aeCache.findApplicationEntity(calledAET), createAARQ(cuid, queryOptions));
    }

    @Override
    public List<Attributes> find(Association as, int priority, QueryRetrieveLevel2 level,
                                 String studyIUID, String seriesIUID, String sopIUID, int... returnKeys)
            throws Exception {
        List<Attributes> list = new ArrayList<>();
        DimseRSP rsp = query(as, priority, mkQueryKeys(level, studyIUID, seriesIUID, sopIUID, returnKeys), 0);
        rsp.next();
        Attributes match = rsp.getDataset();
        while (rsp.next()) {
            list.add(match);
            match = rsp.getDataset();
        }
        Attributes cmd = rsp.getCommand();
        int status = cmd.getInt(Tag.Status, -1);
        if (status != Status.Success)
            throw new DicomServiceException(status, cmd.getString(Tag.ErrorComment));
        return list;
    }

    @Override
    public DimseRSP query(Association as, int priority, Attributes keys, int autoCancel) throws Exception {
        AAssociateRQ aarq = as.getAAssociateRQ();
        String cuid = aarq.getPresentationContext(PCID).getAbstractSyntax();
        if (QueryOption.toOptions(aarq.getExtNegotiationFor(cuid)).contains(QueryOption.DATETIME)
                && !as.getQueryOptionsFor(cuid).contains(QueryOption.DATETIME))
            keys.accept(nullifyTM, true);
        return as.cfind(cuid, priority, keys, UID.ImplicitVRLittleEndian, autoCancel);
    }

    private static Attributes.Visitor nullifyTM = new Attributes.Visitor(){
        @Override
        public boolean visit(Attributes attrs, int tag, VR vr, Object value) throws Exception {
            if (vr == VR.TM && value != Value.NULL)
                attrs.setNull(tag, VR.TM);

            return true;
        }
    };

    private Attributes mkQueryKeys(QueryRetrieveLevel2 level, String studyIUID, String seriesIUID, String sopIUID,
                                   int... returnKeys) {
        Attributes keys = new Attributes(returnKeys.length + 4);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, level.name());
        for (int tag : returnKeys)
            keys.setNull(tag, DICT.vrOf(tag));
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        if (seriesIUID != null) {
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
            if (sopIUID != null) {
                keys.setString(Tag.SOPInstanceUID, VR.UI, sopIUID);
            }
        }
        return keys;
    }

    private AAssociateRQ createAARQ(String cuid, EnumSet<QueryOption> queryOptions) {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.addPresentationContext(new PresentationContext(PCID, cuid, UID.ImplicitVRLittleEndian));
        if (queryOptions != null)
            aarq.addExtendedNegotiation(new ExtendedNegotiation(cuid,
                    QueryOption.toExtendedNegotiationInformation(queryOptions)));
        return aarq;
    }
}
