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

package org.dcm4chee.arc.store.scu.impl;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.AssociationListener;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2016
 */
class CStoreForward {

    static final Logger LOG = LoggerFactory.getLogger(CStoreForward.class);

    private final RetrieveContext retrieveCtx;
    private final IdentityHashMap<Association,CStoreForwardTask> forwardTasks = new IdentityHashMap<>();

    public CStoreForward(RetrieveContext retrieveCtx) {
        this.retrieveCtx = retrieveCtx;
    }

    public void onStore(StoreContext storeCtx) {
            Association as = storeCtx.getStoreSession().getAssociation();
            CStoreForwardTask task = forwardTasks.get(as);
            if (task == null)
                task = createTask(as);
            task.onStore(storeCtx);
    }

    private CStoreForwardTask createTask(final Association as) {
        ApplicationEntity localAE = retrieveCtx.getLocalApplicationEntity();
        Association storeas = openAssociation(as, localAE);
        final CStoreForwardTask task = new CStoreForwardTask(retrieveCtx, storeas);
        forwardTasks.put(as, task);
        as.addAssociationListener(new AssociationListener() {
            @Override
            public void onClose(Association association) {
                task.onStore(null);
                forwardTasks.remove(as);
            }
        });
        if (storeas != null) {
            retrieveCtx.incrementPendingCStoreForward();
            localAE.getDevice().execute(task);
        }
        return task;
    }

    private Association openAssociation(Association as, ApplicationEntity localAE) {
        try {
            LOG.info("{}: open association to {} for forwarding C-STORE-RQ received in association {}",
                    retrieveCtx.getRequestAssociation(),
                    retrieveCtx.getDestinationAETitle(),
                    as);
            return localAE.connect(retrieveCtx.getDestinationAE(), createAARQ(as));
        } catch (Exception e) {
            LOG.warn("{}: failed to open association to {} for forwarding C-STORE-RQ received in association {}:\n",
                    retrieveCtx.getRequestAssociation(),
                    retrieveCtx.getDestinationAETitle(),
                    as, e);
            return null;
        }
    }

    private AAssociateRQ createAARQ(Association as) {
        AAssociateRQ aarq = new AAssociateRQ();
        for (PresentationContext pc : as.getAAssociateRQ().getPresentationContexts())
            aarq.addPresentationContext(pc);
        return aarq;
    }

    boolean match(String studyIUID, String seriesIUID, String sopIUID) {
        switch (retrieveCtx.getQueryRetrieveLevel()) {
            case STUDY:
                for (String uid : retrieveCtx.getStudyInstanceUIDs()) {
                    if (studyIUID.equals(uid))
                        return true;
                }
                break;
            case SERIES:
                if (studyIUID.equals(retrieveCtx.getStudyInstanceUID())) {
                    for (String uid : retrieveCtx.getSeriesInstanceUIDs()) {
                        if (seriesIUID.equals(uid))
                            return true;
                    }
                }
                break;
            case IMAGE:
                if (studyIUID.equals(retrieveCtx.getStudyInstanceUID())
                        && seriesIUID.equals(retrieveCtx.getSeriesInstanceUID())) {
                    for (String uid : retrieveCtx.getSopInstanceUIDs()) {
                        if (sopIUID.equals(uid))
                            return true;
                    }
                }
                break;
        }
        return false;
    }
}
