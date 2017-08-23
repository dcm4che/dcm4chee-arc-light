package org.dcm4chee.arc.hl7.rs;

import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * @since Aug 2017
 */
public class HL7SenderTest {

    private final String msgType = "ADT^A28^ADT_A05";
    private final String sender = "*|*";
    private final String receiver = "HL7RCV|DCM4CHEE";
    private final String patientID = "P1^Issuer";
    private final String patientName = "Name^Surname";
    private final String patientMotherBirthName = "Mother^Name";
    private final String patientBirthDate = "19671212";
    private final String patientSex = "F";

    @Test
    public void testHL7Send() throws Exception {
        HL7Segment msh = HL7Segment.makeMSH();
        msh.setSendingApplicationWithFacility(sender);
        msh.setReceivingApplicationWithFacility(receiver);
        msh.setField(8, msgType);

        HL7Segment pid = new HL7Segment(8);
        pid.setField(0, "PID");
        pid.setField(3, patientID);
        pid.setField(5, patientName);
        pid.setField(6, patientMotherBirthName);
        pid.setField(7, patientBirthDate);
        pid.setField(8, patientSex);

        HL7Message msg = new HL7Message(2);
        msg.add(msh);
        msg.add(pid);
        validate(msg);
    }

    private void validate(HL7Message msg) {
        HL7Segment msh = msg.getSegment("MSH");
        HL7Segment pid = msg.getSegment("PID");
        assertEquals(msgType, msh.getField(8, ""));
        assertEquals(sender, msh.getSendingApplicationWithFacility());
        assertEquals(receiver, msh.getReceivingApplicationWithFacility());
        assertEquals(patientID, pid.getField(3, ""));
        assertEquals(patientName, pid.getField(5, ""));
        assertEquals(patientMotherBirthName, pid.getField(6, ""));
        assertEquals(patientBirthDate, pid.getField(7, ""));
        assertEquals(patientSex, pid.getField(8, ""));
    }
}
