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

package org.dcm4chee.arc.hl7.psu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.entity.HL7PSUTask;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */

class BuildHL7Message {
    final HL7Message hl7msg;

    static class Builder {
        private HL7Message hl7msg;

        Builder hl7msg(HL7PSUTask hl7psuTask, String dest, ArchiveAEExtension arcAE) {
            hl7msg = buildHL7Mesg(hl7psuTask, dest, arcAE);
            return this;
        }

        BuildHL7Message buildHL7Msg() {
            return new BuildHL7Message(this);
        }
    }

    private BuildHL7Message(Builder builder) {
        hl7msg = builder.hl7msg;
    }

    static HL7Message buildHL7Mesg(HL7PSUTask hl7psuTask, String dest, ArchiveAEExtension arcAE) {
        HL7Message hl7Msg = new HL7Message();
        Attributes mwlAttrs = hl7psuTask.getMwl().getAttributes();
        HL7Segment msh = new HL7Segment(10);
        msh.setField(8, "OMG^O19^OMG_O19");
        msh.setSendingApplicationWithFacility(arcAE.hl7psuSendingApplication());
        msh.setReceivingApplicationWithFacility(dest);
        msh.setField(7, "20170102");
        HL7Segment orc = new HL7Segment(10);
        orc.setField(1, "NW");
        orc.setField(2, mwlAttrs.getString(Tag.PlacerOrderNumberImagingServiceRequest));
        orc.setField(3, mwlAttrs.getString(Tag.FillerOrderNumberImagingServiceRequest));
        orc.setField(5, "SC");
        HL7Segment tq1 = new HL7Segment(10);
        tq1.setField(7, "20170102");
        HL7Segment obr = new HL7Segment(10);
        obr.setField(2, mwlAttrs.getString(Tag.PlacerOrderNumberImagingServiceRequest));
        obr.setField(3, mwlAttrs.getString(Tag.FillerOrderNumberImagingServiceRequest));
        HL7Segment obx = new HL7Segment(10);
        hl7Msg.add(msh);
        hl7Msg.add(orc);
        hl7Msg.add(tq1);
        hl7Msg.add(obr);
        hl7Msg.add(obx);
        return hl7Msg;
    }
}

