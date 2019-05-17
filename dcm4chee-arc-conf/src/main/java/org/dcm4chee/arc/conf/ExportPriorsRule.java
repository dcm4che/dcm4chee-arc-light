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

import org.dcm4che3.data.Attributes;

import java.util.Arrays;
import java.util.Calendar;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class ExportPriorsRule {

    private String commonName;

    private ScheduleExpression[] schedules = {};

    private Conditions conditions = new Conditions();

    private String[] exporterIDs = {};

    private Duration suppressDuplicateExportInterval;

    private EntitySelector[] entitySelectors = {};

    private ExportReoccurredInstances exportReoccurredInstances = ExportReoccurredInstances.REPLACE;

    public ExportPriorsRule() {
    }

    public ExportPriorsRule(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression[] schedules) {
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

    public void setExporterIDs(String[] exporterIDs) {
        this.exporterIDs = exporterIDs;
    }

    public Duration getSuppressDuplicateExportInterval() {
        return suppressDuplicateExportInterval;
    }

    public void setSuppressDuplicateExportInterval(Duration suppressDuplicateExportInterval) {
        this.suppressDuplicateExportInterval = suppressDuplicateExportInterval;
    }

    public EntitySelector[] getEntitySelectors() {
        return entitySelectors;
    }

    public void setEntitySelectors(EntitySelector[] entitySelectors) {
        this.entitySelectors = entitySelectors;
    }

    public boolean match(String hostName, String sendingAET, String receivingAET, Attributes attrs, Calendar cal) {
        return ScheduleExpression.emptyOrAnyContains(cal, schedules)
                && conditions.match(hostName, sendingAET, receivingAET, attrs);
    }

    public ExportReoccurredInstances getExportReoccurredInstances() {
        return exportReoccurredInstances;
    }

    public void setExportReoccurredInstances(ExportReoccurredInstances exportReoccurredInstances) {
        this.exportReoccurredInstances = exportReoccurredInstances;
    }

    @Override
    public String toString() {
        return "ExportPriorsRule{" +
                "cn=" + commonName +
                ", conditions=" + conditions +
                ", schedules=" + Arrays.toString(schedules) +
                ", exporterIDs=" + Arrays.toString(exporterIDs) +
                ", suppressDups=" + suppressDuplicateExportInterval +
                ", entitySelectors=" + Arrays.toString(entitySelectors) +
                ", exportReoccurredInstances=" + exportReoccurredInstances +
                '}';
    }
}
