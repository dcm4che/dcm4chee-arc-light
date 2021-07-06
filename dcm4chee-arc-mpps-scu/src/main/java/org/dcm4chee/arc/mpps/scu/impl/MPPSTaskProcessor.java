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

package org.dcm4chee.arc.mpps.scu.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Dimse;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.mpps.scu.MPPSSCU;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskProcessor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@ApplicationScoped
@Named("MPPS")
public class MPPSTaskProcessor implements TaskProcessor {

    @Inject
    private MPPSSCU mppsSCU;

    @Override
    public Outcome process(Task task) throws Exception {
            return mppsSCU.forwardMPPS(
                    task.getLocalAET(),
                    task.getRemoteAET(),
                    Dimse.valueOf(task.getDIMSE()),
                    task.getSOPInstanceUID(),
                    task.getPayload(Attributes.class),
                    procAttrs(task));
    }

    private Attributes procAttrs(Task task) {
        Attributes procAttrs = new Attributes(4);
        procAttrs.setString(Tag.AccessionNumber, VR.SH, task.getAccessionNumber());
        procAttrs.setString(Tag.StudyInstanceUID, VR.UI, task.getStudyInstanceUID());
        procAttrs.setString(Tag.PatientName, VR.PN, task.getPatientName());
        procAttrs.setString(Tag.PatientID, VR.LO, task.getPatientID());
        return procAttrs;
    }
}
