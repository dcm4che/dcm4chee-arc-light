package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.util.TagUtils;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class Conditions {

    public static final String RECEIVING_APPLICATION_ENTITY_TITLE = "ReceivingApplicationEntityTitle";
    public static final String SENDING_APPLICATION_ENTITY_TITLE = "SendingApplicationEntityTitle";
    public static final String SENDING_HOSTNAME = "SendingHostname";

    private final Map<String, Pattern> map = new TreeMap<>();

    public Conditions(String... props) {
         for (String s : props) {
            int index = s.indexOf('=');
            if (index == -1)
                throw new IllegalArgumentException(s);
            setCondition(s.substring(0, index), s.substring(index+1));
        }
    }

    public void setReceivingAETitle(String value) {
        setCondition(RECEIVING_APPLICATION_ENTITY_TITLE, value);
    }

    public void setNotReceivingAETitle(String value) {
        setCondition(RECEIVING_APPLICATION_ENTITY_TITLE + '!', value);
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

    public boolean match(String hostName, String sendingAET, String receivingAET, Attributes attrs) {
        for (Map.Entry<String, Pattern> entry : map.entrySet()) {
            String tagPath = entry.getKey();
            Pattern pattern = entry.getValue();
            boolean ne = tagPath.endsWith("!");
            if (ne)
                tagPath = tagPath.substring(0, tagPath.length()-1);
            switch (tagPath) {
                case RECEIVING_APPLICATION_ENTITY_TITLE:
                    if (ne ? (receivingAET != null && pattern.matcher(receivingAET).matches())
                           : (receivingAET == null || !pattern.matcher(receivingAET).matches()))
                        return false;
                    break;
                case SENDING_APPLICATION_ENTITY_TITLE:
                    if (ne ? (sendingAET != null && pattern.matcher(sendingAET).matches())
                           : (sendingAET == null || !pattern.matcher(sendingAET).matches()))
                        return false;
                    break;
                case SENDING_HOSTNAME:
                    if (ne ? (hostName != null && pattern.matcher(hostName).matches())
                           : (hostName == null || !pattern.matcher(hostName).matches()))
                        return false;
                    break;
                default:
                    if (!match(attrs, TagUtils.parseTagPath(tagPath), pattern, 0, ne))
                        return false;
            }
        }
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
//            if (pattern.matcher(s).matches())
//                        return true;
            if (ss == null && ne)
                return true;
            if (ss == null && !ne)
                return false;
        }
        return false;
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
