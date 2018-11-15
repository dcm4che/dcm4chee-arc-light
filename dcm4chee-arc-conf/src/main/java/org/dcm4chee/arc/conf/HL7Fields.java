/*
 * **** BEGIN LICENSE BLOCK *****
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.conf;

import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.StringUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2018
 */
public class HL7Fields {
    private final UnparsedHL7Message msg;
    private final String defCharset;
    private volatile HL7Message hl7Message;

    public HL7Fields(UnparsedHL7Message msg, String defCharset) {
        this.msg = msg;
        this.defCharset = defCharset;
    }

    public String get(String field, String defVal) {
        if (field.length() < 5 || field.charAt(3) != '-')
            throw new IllegalArgumentException(field);

        String[] ss = StringUtils.split(field.substring(4), '.');
        if (ss.length > 3)
            throw new IllegalArgumentException(field);

        int[] is = new int[ss.length];
        try {
            for (int i = 0; i < ss.length; i++) {
                if ((is[i] = Integer.parseInt(ss[i]) - 1) < 0)
                    throw new IllegalArgumentException(field);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field);
        }
        HL7Segment seg;
        if (field.startsWith("MSH")) {
            seg = msg.msh();
        } else {
            is[0]++;
            HL7Message hl7Message = this.hl7Message;
            if (hl7Message == null) {
                this.hl7Message = hl7Message = HL7Message.parse(msg.data(), defCharset);
            }
            seg = hl7Message.getSegment(field.substring(0, 3));
        }
        if (seg == null)
            return defVal;

        String val = seg.getField(is[0], "");
        if (is.length > 1) {
            val = StringUtils.cut(val, is[1], seg.getComponentSeparator());
            if (is.length > 2) {
                val = StringUtils.cut(val, is[2], seg.getSubcomponentSeparator());
            }
        }
        return val.isEmpty() ? defVal : val;
    }

}
