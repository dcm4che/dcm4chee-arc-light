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
 *
 * *** END LICENSE BLOCK *****
 */
package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.ParticipantObjectDetail;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.HL7ConnectionEvent;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class HL7AuditUtils {

    static ParticipantObjectDetail[] getParticipantObjectDetail(SpoolFileReader reader) {
        ParticipantObjectDetail[] detail = new ParticipantObjectDetail[2];
        setParticipantObjectDetail(reader.getData(), 0, detail);
        setParticipantObjectDetail(reader.getAck(), 1, detail);
        return detail;
    }

    private static void setParticipantObjectDetail(byte[] val, int index, ParticipantObjectDetail[] detail) {
        if (val.length > 0) {
            detail[index] = new ParticipantObjectDetail();
            detail[index].setType("HL7v2 Message");
            detail[index].setValue(val);
        }
    }

    static HL7Segment getHL7Segment(UnparsedHL7Message hl7Message, String segName) {
        HL7Segment msh = hl7Message.msh();
        String charset = msh.getField(17, "ASCII");
        HL7Message msg = HL7Message.parse(hl7Message.data(), charset);
        return msg.getSegment(segName);
    }

    static boolean isOrderProcessed(HL7ConnectionEvent hl7ConnEvent) {
        String messageType = hl7ConnEvent.getHL7Message().msh().getMessageType();
        return messageType.equals("ORM^O01") || messageType.equals("OMG^O19") || messageType.equals("OMI^O23");
    }

    static boolean isOrderMessage(HL7ConnectionEvent hl7ConnEvent) {
        return getHL7Segment(hl7ConnEvent.getHL7Message(), "ORC") != null;
    }

    static String procRecHL7StudyIUID(UnparsedHL7Message hl7Message, String defVal) {
        HL7Segment zds = HL7AuditUtils.getHL7Segment(hl7Message, "ZDS");
        HL7Segment ipc = HL7AuditUtils.getHL7Segment(hl7Message, "IPC");
        return zds != null
                ? zds.getField(1, null)
                : ipc != null
                    ? ipc.getField(3, null) : defVal;
    }

    static String procRecHL7Acc(UnparsedHL7Message hl7Message) {
        HL7Segment obr = HL7AuditUtils.getHL7Segment(hl7Message, "OBR");
        HL7Segment ipc = HL7AuditUtils.getHL7Segment(hl7Message, "IPC");
        return ipc != null
                ? ipc.getField(1, null)
                : obr != null
                    ? obr.getField(18, null) : null;
    }
}
