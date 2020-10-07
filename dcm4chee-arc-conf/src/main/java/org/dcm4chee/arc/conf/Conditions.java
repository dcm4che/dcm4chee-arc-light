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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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
import org.dcm4che3.util.TagUtils;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
public class Conditions {

    public static final String RECEIVING_APPLICATION_ENTITY_TITLE = "ReceivingApplicationEntityTitle";
    public static final String RECEIVING_HOSTNAME = "ReceivingHostname";
    public static final String SENDING_APPLICATION_ENTITY_TITLE = "SendingApplicationEntityTitle";
    public static final String SENDING_HOSTNAME = "SendingHostname";

    private final Map<String, Pattern> map = new TreeMap<>();

    public Conditions(String... props) {
         for (String s : props) {
            int index = s.indexOf('=');
            if (index == -1)
                throw new IllegalArgumentException("Condition in incorrect format : " + s);
             String tagPath = s.substring(0, index);
             if ((tagPath.endsWith("]") || tagPath.endsWith("]!"))
                     && Integer.parseInt(tagPath.substring(tagPath.indexOf("[") + 1, tagPath.indexOf("]"))) <= 0)
                 throw new IllegalArgumentException("Condition attribute value position not allowed : " + s);
             setCondition(tagPath, s.substring(index+1));
        }
    }

    public void setReceivingAETitle(String value) {
        setCondition(RECEIVING_APPLICATION_ENTITY_TITLE, value);
    }

    public void setNotReceivingAETitle(String value) {
        setCondition(RECEIVING_APPLICATION_ENTITY_TITLE + '!', value);
    }

    public void setReceivingHostname(String value) {
        setCondition(RECEIVING_HOSTNAME, value);
    }

    public void setNotReceivingHostname(String value) {
        setCondition(RECEIVING_HOSTNAME + '!', value);
    }

    public void setSendingAETitle(String value) {
        setCondition(SENDING_APPLICATION_ENTITY_TITLE, value);
    }

    public void setNotSendingAETitle(String value) {
        setCondition(SENDING_APPLICATION_ENTITY_TITLE + '!', value);
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

    public boolean match(String sendingHost, String sendingAET,
            String receivingHost, String receivingAET, Attributes attrs) {
        for (Map.Entry<String, Pattern> entry : map.entrySet()) {
            String tagPath = entry.getKey();
            Pattern pattern = entry.getValue();
            boolean ne = tagPath.endsWith("!");
            if (ne)
                tagPath = tagPath.substring(0, tagPath.length()-1);
            int valPos = 0;
            if (tagPath.endsWith("]")) {
                String[] tagPathWithValPos = tagPath.split("\\[");
                tagPath = tagPathWithValPos[0];
                valPos = Integer.parseInt(tagPathWithValPos[1].substring(0, tagPathWithValPos[1].length() - 1));
            }
            switch (tagPath) {
                case RECEIVING_APPLICATION_ENTITY_TITLE:
                    if (ne ? (receivingAET != null && pattern.matcher(receivingAET).matches())
                           : (receivingAET == null || !pattern.matcher(receivingAET).matches()))
                        return false;
                    break;
                case RECEIVING_HOSTNAME:
                    if (ne ? (receivingHost != null && pattern.matcher(receivingHost).matches())
                            : (receivingHost == null || !pattern.matcher(receivingHost).matches()))
                        return false;
                    break;
                case SENDING_APPLICATION_ENTITY_TITLE:
                    if (ne ? (sendingAET != null && pattern.matcher(sendingAET).matches())
                           : (sendingAET == null || !pattern.matcher(sendingAET).matches()))
                        return false;
                    break;
                case SENDING_HOSTNAME:
                    if (ne ? (sendingHost != null && pattern.matcher(sendingHost).matches())
                           : (sendingHost == null || !pattern.matcher(sendingHost).matches()))
                        return false;
                    break;
                default:
                    if (!match(attrs, TagUtils.parseTagPath(tagPath), pattern, 0, ne, valPos))
                        return false;
            }
        }
        return true;
    }

    private boolean match(Attributes attrs, int[] tagPath, Pattern pattern, int level, boolean ne, int valPos) {
        if (level < tagPath.length-1) {
            Sequence seq = attrs.getSequence(tagPath[level]);
            if (seq != null)
                for (Attributes item : seq)
                    if (match(item, tagPath, pattern, level+1, false, valPos))
                        return !ne;
        } else {
            String[] ss = attrs.getStrings(tagPath[level]);
            if (ss != null && ss.length >= 1) {
                if (valPos > 0) {
                    if (valPos > ss.length || (ss[valPos - 1] != null && pattern.matcher(ss[valPos - 1]).matches()))
                        return !ne;
                } else
                    for (String s : ss)
                        if (s != null && pattern.matcher(s).matches())
                            return !ne;
            }
        }
        return ne;
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Conditions))
            return false;

        return toString().equals(obj.toString());
    }
}
