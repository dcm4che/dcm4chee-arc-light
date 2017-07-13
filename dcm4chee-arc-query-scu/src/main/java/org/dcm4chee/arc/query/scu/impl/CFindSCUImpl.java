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
import org.dcm4che3.net.pdu.AAssociateRQAC;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4chee.arc.Cache;
import org.dcm4chee.arc.query.scu.CFindSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.EnumSet;

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
    public Attributes queryStudy(ApplicationEntity localAE, String calledAET,
                                 int priority, String studyIUID, int[] returnKeys)
            throws Exception {
        Association as = openAssociation(localAE, calledAET, UID.StudyRootQueryRetrieveInformationModelFIND,
                EnumSet.noneOf(QueryOption.class));
        try {
            return queryStudy(as, priority, studyIUID, returnKeys);
        } finally {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    @Override
    public Association openAssociation(ApplicationEntity localAE, String calledAET,
                                       String cuid, EnumSet<QueryOption> queryOptions)
            throws Exception {
        return localAE.connect(aeCache.get(calledAET), createAARQ(cuid, queryOptions));
    }

    @Override
    public Attributes queryStudy(Association as, int priority, String studyIUID, int[] returnKeys) throws Exception {
        DimseRSP rsp = query(as, priority, mkQueryStudyKeys(studyIUID, returnKeys), 0);
        rsp.next();
        return rsp.getDataset();
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

    @Override
    public Attributes queryStudy(ApplicationEntity localAE, String calledAET,
                                 int priority, String studyIUID, int[] returnKeys,
                                 Cache<String, Attributes> cache) {
        Cache.Entry<Attributes> entry = cache.getEntry(studyIUID);
        Attributes newAttrs;
        if (entry != null) {
            newAttrs = entry.value();
        } else {
            try {
                newAttrs = queryStudy(localAE, calledAET, priority, studyIUID, returnKeys);
            } catch (Exception e) {
                newAttrs = null;
            }
            cache.put(studyIUID, newAttrs);
        }
        return newAttrs;
    }

    private Attributes mkQueryStudyKeys(String studyIUID, int[] returnKeys) {
        Attributes keys = new Attributes(returnKeys.length + 2);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        for (int tag : returnKeys)
            keys.setNull(tag, DICT.vrOf(tag));
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
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
