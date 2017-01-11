package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;

import java.util.Arrays;
import java.util.Calendar;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
public class ExportRule {

    private String commonName;

    private ScheduleExpression[] schedules = {};

    private Conditions conditions = new Conditions();

    private String[] exporterIDs = {};

    private Entity entity;

    private Duration exportDelay;

    private boolean exportPreviousEntity;

    public ExportRule() {
    }

    public ExportRule(String commonName) {
        setCommonName(commonName);
    }

    public final String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression... schedules) {
        this.schedules = schedules;
    }

    public Conditions getConditions() {
        return conditions;
    }

    public void setConditions(Conditions conditions) {
        this.conditions = conditions;
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

    public boolean match(String hostName, String sendingAET, String receivingAET, Attributes attrs, Calendar cal) {
        return match(cal) && conditions.match(hostName, sendingAET, receivingAET, attrs);
    }

    public boolean isExportPreviousEntity() {
        return exportPreviousEntity;
    }

    public void setExportPreviousEntity(boolean exportPreviousEntity) {
        this.exportPreviousEntity = exportPreviousEntity;
    }

    private boolean match(Calendar cal) {
        if (schedules.length == 0)
            return true;

        for (ScheduleExpression schedule : this.schedules)
            if (schedule.contains(cal))
                return true;

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
