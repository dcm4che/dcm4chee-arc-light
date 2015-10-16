package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.util.TagUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class Conditions {

    private static final String SendingApplicationEntityTitle = "SendingApplicationEntityTitle";

    private final Map<String, Pattern> map = new HashMap<>();

    public Conditions(String... props) {
         for (String s : props) {
            int index = s.indexOf('=');
            if (index == -1)
                throw new IllegalArgumentException(s);
            setCondition(s.substring(0, index), s.substring(index+1));
        }
    }

    public void setSendingAETitle(String value) {
        setCondition(SendingApplicationEntityTitle, value);
    }

    public void setCondition(String tagPath, String value) {
        TagUtils.parseTagPath(tagPath);
        map.put(tagPath, Pattern.compile(value));
    }

    public Map<String,Pattern> getMap() {
        return map;
    }

    public boolean match(String sendingAET, Attributes attrs) {
        for (Map.Entry<String, Pattern> entry : map.entrySet()) {
            String tagPath = entry.getKey();
            Pattern pattern = entry.getValue();
            if (tagPath.equals(SendingApplicationEntityTitle)
                    ? !pattern.matcher(sendingAET).matches()
                    : !match(attrs, TagUtils.parseTagPath(tagPath), pattern, 0))
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
