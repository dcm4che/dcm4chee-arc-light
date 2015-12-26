package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class Conditions {

    private static final String ReceivingApplicationEntityTitle = "ReceivingApplicationEntityTitle";
    private static final String SendingApplicationEntityTitle = "SendingApplicationEntityTitle";
    private static final String SendingHostname = "SendingHostname";

    private Pattern receivingAETPattern;
    private Pattern sendingAETPattern;
    private Pattern sendingHostnamePattern;
    private final Map<String, Pattern> map = new HashMap<>();

    public Conditions(String... props) {
         for (String s : props) {
            int index = s.indexOf('=');
            if (index == -1)
                throw new IllegalArgumentException(s);
            setCondition(s.substring(0, index), s.substring(index+1));
        }
    }

    public void setReceivingAETitle(String value) {
        setCondition(ReceivingApplicationEntityTitle, value);
    }

    public void setSendingAETitle(String value) {
        setCondition(SendingApplicationEntityTitle, value);
    }

    public void setSendingHostname(String value) {
        setCondition(SendingHostname, value);
    }

    public void setCondition(String tagPath, String value) {
        Pattern pattern = Pattern.compile(value);
        if (tagPath.equals(SendingHostname))
            sendingHostnamePattern = pattern;
        else if (tagPath.equals(SendingApplicationEntityTitle))
            sendingAETPattern = pattern;
        else if (tagPath.equals(ReceivingApplicationEntityTitle))
            receivingAETPattern = pattern;
        map.put(tagPath, pattern);
    }

    public Map<String,Pattern> getMap() {
        return map;
    }

    public boolean match(String hostName, String sendingAET, String receivingAET, Attributes attrs) {
        if (receivingAET != null && receivingAETPattern != null && !receivingAETPattern.matcher(receivingAET).matches())
            return false;

        if (sendingAET != null && sendingAETPattern != null && !sendingAETPattern.matcher(sendingAET).matches())
            return false;

        if (sendingAET != null && sendingAETPattern != null && !sendingAETPattern.matcher(sendingAET).matches())
            return false;

        if (hostName != null && sendingHostnamePattern != null && !sendingHostnamePattern.matcher(hostName).matches())
            return false;

        for (Map.Entry<String, Pattern> entry : map.entrySet()) {
            String tagPath = entry.getKey();
            Pattern pattern = entry.getValue();
            if (!tagPath.equals(ReceivingApplicationEntityTitle) &&
                    !tagPath.equals(SendingApplicationEntityTitle) &&
                    !tagPath.equals(SendingHostname)
                    && !match(attrs, TagUtils.parseTagPath(tagPath), pattern, 0))
                return false;
        }
        return true;
    }

    private boolean match(Attributes attrs, int[] tagPath, Pattern pattern, int level) {
        if (level < tagPath.length-1) {
            Sequence seq = attrs.getSequence(tagPath[level]);
            if (seq != null)
                for (Attributes item : seq)
                    if (match(item, tagPath, pattern, level+1))
                        return true;
        } else {
            String[] ss = attrs.getStrings(tagPath[level]);
            if (ss != null)
                for (String s : ss)
                    if (pattern.matcher(s).matches())
                        return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
