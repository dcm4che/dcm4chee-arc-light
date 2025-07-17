/*
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
 */

package org.dcm4chee.arc.retrieve.xdsi;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreContext;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Jul 2025
 */
class CMoveRSPHandler extends DimseRSPHandler {
    final RetrieveContext ctx;
    final Set<String> missing;
    final Association as;

    public CMoveRSPHandler(RetrieveContext ctx, Set<String> missing, Association as) {
        super(ThreadLocalRandom.current().nextInt(0xFFFF) + 1);
        this.ctx = ctx;
        this.missing = missing;
        this.as = as;
    }

    public void onStore(StoreContext storeCtx) {
        if (missing.remove(storeCtx.getSopInstanceUID())) {
            ctx.getMatches().add(createInstanceLocations(storeCtx));
        }
    }

    @Override
    public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
        super.onDimseRSP(as, cmd, data);
    }

    @Override
    public void onClose(Association as) {
        super.onClose(as);
    }

    private InstanceLocations createInstanceLocations(StoreContext storeCtx) {
        Instance inst = storeCtx.getStoredInstance();
        Series series = inst.getSeries();
        Study study = series.getStudy();
        Patient patient = study.getPatient();
        Attributes instAttrs = inst.getAttributes();
        Attributes seriesAttrs = series.getAttributes();
        Attributes studyAttrs = study.getAttributes();
        Attributes patAttrs = patient.getAttributes();
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs, instAttrs);
        instAttrs.addAll(seriesAttrs, true);
        instAttrs.addAll(studyAttrs, true);
        instAttrs.addAll(patAttrs, true);
        RetrieveService service = ctx.getRetrieveService();
        InstanceLocations instanceLocations = service.newInstanceLocations(instAttrs);
        instanceLocations.setInstancePk(inst.getPk());
        instanceLocations.getLocations().addAll(locations(storeCtx));
        return instanceLocations;
    }

    private Collection<Location> locations(StoreContext storeCtx) {
        Collection<Location> locations = storeCtx.getLocations();
        return locations.isEmpty() ? storeCtx.getStoredInstance().getLocations() : locations;
    }
}
