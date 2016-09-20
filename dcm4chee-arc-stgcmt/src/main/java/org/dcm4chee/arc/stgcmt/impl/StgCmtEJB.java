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
 * Portions created by the Initial Developer are Copyright (C) 2016
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

package org.dcm4chee.arc.stgcmt.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4chee.arc.entity.ExternalRetrieveAETitle;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.StgCmtResult;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@Stateless
public class StgCmtEJB {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    public void addExternalRetrieveAETs(Attributes eventInfo) {
        //TODO
        String retrieveAET = eventInfo.getString(Tag.RetrieveAETitle);
        Sequence seq = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        for (Attributes item : seq) {
            if (retrieveAET == null)
                retrieveAET = item.getString(Tag.RetrieveAETitle);
//            Instance instance = em.createNamedQuery(Instance.FIND_INSTANCE_STUDY_BY_SOP_IUID_EAGER, Instance.class)
//                                .setParameter(1, item.getString(Tag.ReferencedSOPInstanceUID)).getSingleResult();
//            Collection<ExternalRetrieveAETitle> externalRetrieveAETitles = instance.getExternalRetrieveAETitles();
//            List<String> extRetrieveAETsList = new ArrayList<>();
//            for (ExternalRetrieveAETitle extRetrieveAET : externalRetrieveAETitles)
//                extRetrieveAETsList.add(extRetrieveAET.getRetrieveAET());
//            if (!extRetrieveAETsList.isEmpty() && !extRetrieveAETsList.contains(retrieveAET))
//                externalRetrieveAETitles.add(new ExternalRetrieveAETitle(retrieveAET));
        }
    }

    //TODO fallback remaining - exporterDescriptor required

    public void persistStgCmtResult(
            String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID, String exporterID,
            Attributes actionInfo, String deviceName) {
        StgCmtResult result = new StgCmtResult();
        result.setStgCmtRequest(actionInfo);
        result.setStudyInstanceUID(studyInstanceUID);
        result.setSeriesInstanceUID(seriesInstanceUID);
        result.setSopInstanceUID(sopInstanceUID);
        result.setExporterID(exporterID);
        result.setDeviceName(deviceName);
        em.persist(result);
    }
}
