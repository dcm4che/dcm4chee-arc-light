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
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.StgCmtResult;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@Stateless
public class StgCmtEJB implements StgCmtManager {

    private final Logger LOG = LoggerFactory.getLogger(StgCmtEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Override
    public void addExternalRetrieveAETs(Attributes eventInfo, Device device) {
        String transactionUID = eventInfo.getString(Tag.TransactionUID);
        StgCmtResult result = getStgCmtResult(transactionUID);
        updateExternalRetrieveAETs(eventInfo, result.getStudyInstanceUID(),
                device.getDeviceExtension(ArchiveDeviceExtension.class).getExporterDescriptorNotNull(result.getExporterID()));
        result.setStgCmtResult(eventInfo);
    }

    private StgCmtResult getStgCmtResult(String transactionUID) throws NoResultException {
        StgCmtResult result;
        try {
            result = em.createNamedQuery(StgCmtResult.FIND_BY_TRANSACTION_UID, StgCmtResult.class)
                    .setParameter(1, transactionUID).getSingleResult();
        } catch (NoResultException e) {
            LOG.warn("No Storage Commitment result found with transaction UID : " + transactionUID);
            throw e;
        }
        return result;
    }

    private void updateExternalRetrieveAETs(Attributes eventInfo, String suid, ExporterDescriptor ed) {
        String[] configRetrieveAETs = ed.getRetrieveAETitles();
        String defRetrieveAET = eventInfo.getString(Tag.RetrieveAETitle, ed.getStgCmtSCPAETitle());
        Sequence sopSeq = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        List<Instance> instances = em.createNamedQuery(Instance.FIND_BY_STUDY_IUID, Instance.class)
                                .setParameter(1, suid).getResultList();
        HashSet<Instance> i = new HashSet<>(instances);
        for (Instance inst : i) {
            Attributes sopRef = sopRefOf(inst.getSopInstanceUID(), sopSeq);
            if (sopRef != null) {
                if (configRetrieveAETs != null && configRetrieveAETs.length > 0) {
                    for (String retrieveAET : configRetrieveAETs) {
                        inst.addExternalRetrieveAET(retrieveAET);
                    }
                } else {
                    inst.addExternalRetrieveAET(sopRef.getString(Tag.RetrieveAETitle, defRetrieveAET));
                }
            }
        }
        Iterator<Instance> iter = instances.iterator();
        Instance inst1 = iter.next();
        HashSet<String> studyExternalAETs = new HashSet<>(inst1.getExternalRetrieveAETs());
        while (iter.hasNext()) {
            studyExternalAETs.retainAll(iter.next().getExternalRetrieveAETs());
        }
        if (!studyExternalAETs.isEmpty()) {
            Study study = inst1.getSeries().getStudy();
            for (String s : studyExternalAETs) {
                study.addExternalRetrieveAET(s);
            }
        }
    }

    private Attributes sopRefOf(String iuid, Sequence seq) {
        for (Attributes item : seq) {
            if (iuid.equals(item.getString(Tag.ReferencedSOPInstanceUID)))
                return item;
        }
        return null;
    }

    @Override
    public void persistStgCmtResult(StgCmtResult result) {
        em.persist(result);
    }

    @Override
    public List<StgCmtResult> listStgCmts(
            StgCmtResult.Status status, String studyUID, String exporterID, int offset, int limit) {
        //TODO
        return Collections.emptyList();
    }

    @Override
    public boolean deleteStgCmt(String transactionUID) {
        try {
            StgCmtResult result = getStgCmtResult(transactionUID);
            em.remove(result);
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }

    @Override
    public int deleteStgCmts(StgCmtResult.Status status, Date updatedBefore) {
        List<StgCmtResult> results = status != null && updatedBefore != null
                                    ? em.createNamedQuery(StgCmtResult.FIND_BY_STATUS_AND_UPDATED_BEFORE, StgCmtResult.class)
                                        .setParameter(1, status).setParameter(2, updatedBefore).getResultList()
                                    : status != null && updatedBefore == null
                                    ? em.createNamedQuery(StgCmtResult.FIND_BY_STATUS, StgCmtResult.class)
                                        .setParameter(1, status).getResultList()
                                    : status == null && updatedBefore != null
                                    ? em.createNamedQuery(StgCmtResult.FIND_BY_UPDATED_BEFORE, StgCmtResult.class)
                                        .setParameter(1, updatedBefore).getResultList()
                                    : em.createNamedQuery(StgCmtResult.FIND_ALL, StgCmtResult.class).getResultList();
        if (results.isEmpty())
            return 0;
        for (StgCmtResult result : results)
            em.remove(result);
        return results.size();
    }
}
