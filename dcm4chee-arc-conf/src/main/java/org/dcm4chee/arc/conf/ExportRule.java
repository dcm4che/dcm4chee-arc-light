package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.util.TagUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class ExportRule {

    private static final String SendingApplicationEntityTitle = "SendingApplicationEntityTitle";

    private final String commonName;

    private ScheduleExpression[] schedules = {};

    private final Map<String, Pattern> conditions = new HashMap<>();

    private String[] exporterIDs = {};

    private Entity entity;

    private Duration exportDelay;


    public ExportRule(String commonName) {
        this.commonName = commonName;
    }

    public final String getCommonName() {
        return commonName;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression... schedules) {
        this.schedules = schedules;
    }

    public void setSendingAETitle(String value) {
        setCondition(SendingApplicationEntityTitle, value);
    }

    public void setCondition(String tagPath, String value) {
        TagUtils.parseTagPath(tagPath);
        conditions.put(tagPath, Pattern.compile(value));
    }

    public Map<String,Pattern> getConditions() {
        return conditions;
    }

    public String[] getExporterIDs() {
        return exporterIDs;
    }

    public void setExporterIDs(String... exporterIDs) {
        this.exporterIDs = exporterIDs;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        switch (entity) {
            case Study:
            case Series:
            case Instance:
                this.entity = entity;
                break;
            default:
                throw new IllegalArgumentException("entity: " + entity);
        }
    }

    public Duration getExportDelay() {
        return exportDelay;
    }

    public void setExportDelay(Duration exportDelay) {
        this.exportDelay = exportDelay;
    }

    public boolean match(String aet, Attributes attrs, Calendar cal) {
        if (!match(cal))
            return false;

        for (Map.Entry<String, Pattern> entry : conditions.entrySet()) {
            String tagPath = entry.getKey();
            Pattern pattern = entry.getValue();
            if (tagPath.equals(SendingApplicationEntityTitle)
                ? !pattern.matcher(aet).matches()
                : !match(attrs, TagUtils.parseTagPath(tagPath), pattern, 0))
                    return false;
        }

        return true;
    }

    private boolean match(Calendar cal) {
        if (schedules.length == 0)
            return true;

        for (ScheduleExpression schedule : this.schedules)
            if (schedule.contains(cal))
                return true;

        return false;
    }

    private boolean match(Attributes attrs, int[] tagPath, Pattern pattern, int level) {
        if (level < tagPath.length-1) {
            Sequence seq = attrs.getSequence(tagPath[level]);
            if (seq != null)
                for (Attributes item : seq)
                    if (match(attrs, tagPath, pattern, level+1))
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
        return "ExportRule{" +
                "cn=" + commonName +
                ", conditions=" + conditions +
                ", schedules=" + Arrays.toString(schedules) +
                ", exporterIDs=" + Arrays.toString(exporterIDs) +
                ", entity=" + entity +
                ", exporterDelay=" + exportDelay +
                '}';
    }
}
