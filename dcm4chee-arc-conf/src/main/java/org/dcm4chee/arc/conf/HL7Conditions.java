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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.util.TagUtils;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2016
 */
public class HL7Conditions {
    public static final String SENDING_HOSTNAME = "SendingHostname";

    private final Map<String, Pattern> map = new TreeMap<>();

    public HL7Conditions(String... props) {
        for (String s : props) {
            int index = s.indexOf('=');
            if (index == -1)
                throw new IllegalArgumentException(s);
            setCondition(s.substring(0, index), s.substring(index+1));
        }
    }

    public void setSendingHostname(String value) {
        setCondition(SENDING_HOSTNAME, value);
    }

    public void setNotSendingHostname(String value) {
        setCondition(SENDING_HOSTNAME + '!', value);
    }

    public void setCondition(String tagPath, String value) {
        Pattern pattern = Pattern.compile(value);
        map.put(tagPath, pattern);
    }

    public Map<String,Pattern> getMap() {
        return map;
    }

    public boolean match(String hostName, HL7Segment msh, Attributes attrs) {
        for (Map.Entry<String, Pattern> entry : map.entrySet()) {
            String hl7Field = entry.getKey();
            Pattern pattern = entry.getValue();
            boolean ne = hl7Field.endsWith("!");
            if (ne)
                hl7Field = hl7Field.substring(0, hl7Field.length()-1);
            if (hl7Field.equals(SENDING_HOSTNAME)) {
                if (ne ? (hostName != null && pattern.matcher(hostName).matches())
                        : (hostName == null || !pattern.matcher(hostName).matches()))
                    return false;
            } else if (hl7Field.startsWith("MSH-")) {
                if (!match(msh, hl7Field, pattern, ne))
                    return false;
            } else if (attrs != null) {
                if (!match(attrs, TagUtils.parseTagPath(hl7Field), pattern, 0, ne))
                    return false;
            }
        }
        return true;
    }

    private boolean match(HL7Segment msh, String hl7Field, Pattern pattern, boolean ne) {
        if (hl7Field.startsWith("MSH-"))
            try {
                int index = Integer.parseInt(hl7Field.substring(4)) - 1;
                if (index >= 0) {
                    String value = msh.getField(index, null);
                    return ne ? (value != null && pattern.matcher(value).matches())
                              : (value == null || pattern.matcher(value).matches());
                }
            } catch (NumberFormatException ignore) {}
        return true;
    }

    private boolean match(Attributes attrs, int[] tagPath, Pattern pattern, int level, boolean ne) {
        if (level < tagPath.length-1) {
            Sequence seq = attrs.getSequence(tagPath[level]);
            if (seq != null)
                for (Attributes item : seq)
                    if (match(item, tagPath, pattern, level+1, false))
                        return true;
        } else {
            String[] ss = attrs.getStrings(tagPath[level]);
            if (ss != null)
                for (String s : ss)
                    if (pattern != null && s != null && pattern.matcher(s).matches() && !ne)
                        return true;
                    else if (pattern == null || s == null)
                        return false;
                    else if (ne && !pattern.matcher(s).matches())
                        return true;
            if (ss == null && ne)
                return true;
            if (ss == null && !ne)
                return false;
        }
        return false;
    }
}
