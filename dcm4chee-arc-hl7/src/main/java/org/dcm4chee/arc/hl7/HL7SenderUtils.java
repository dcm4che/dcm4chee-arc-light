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
 * Portions created by the Initial Developer are Copyright (C) 2015-2021
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL", sendingApplication); or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL", sendingApplication);
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

package org.dcm4chee.arc.hl7;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerConfigurationException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2021
 */
public class HL7SenderUtils {

    public static byte[] data(
            HL7Application sender, String sendingAppWithFacility, HL7Application receiver,
            Attributes attrs, Attributes prev, String msgType, String uri, String ppsStatus, ArchiveAEExtension arcAE)
            throws TransformerConfigurationException, UnsupportedEncodingException, SAXException {
        return SAXTransformer.transform(attrs, sender.getHL7SendingCharacterSet(), uri, tr -> {
            tr.setParameter("sender", sendingAppWithFacility);
            tr.setParameter("receiver", receiver.getApplicationName());
            tr.setParameter("dateTime", HL7Segment.timeStamp(new Date()));
            tr.setParameter("msgControlID", HL7Segment.nextMessageControlID());
            if (!sender.getHL7SendingCharacterSet().equals("ASCII"))
                tr.setParameter("charset", sender.getHL7SendingCharacterSet());
            tr.setParameter("msgType", msgType);
            tr.setParameter("patientIdentifiers", IDWithIssuer.pidsOf(attrs).stream()
                                                            .map(IDWithIssuer::toString)
                                                            .collect(Collectors.joining("~")));
            if (prev != null) {
                tr.setParameter("priorPatientIdentifiers", IDWithIssuer.pidsOf(prev).stream()
                                                                    .map(IDWithIssuer::toString)
                                                                    .collect(Collectors.joining("~")));
                String prevPatName = prev.getString(Tag.PatientName);
                if (msgType.equals("ADT^A40^ADT_A39") && prevPatName != null)
                    tr.setParameter("priorPatientName", prevPatName);
            }
            if (sender.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class).hl7UseNullValue())
                tr.setParameter("includeNullValues", "\"\"");
            if (ppsStatus != null)
                tr.setParameter("ppsStatus", ppsStatus);
            if (arcAE != null) {
                tr.setParameter("isPIDPV1", arcAE.hl7PSUPIDPV1());
                arcAE.hl7PSUTemplateParams().forEach(
                        (k,v) -> tr.setParameter(k, new AttributesFormat(v).format(attrs)));
            }
        });
    }
}
