/*
 *
 *  * **** BEGIN LICENSE BLOCK *****
 *  * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *  *
 *  * The contents of this file are subject to the Mozilla Public License Version
 *  * 1.1 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  * http://www.mozilla.org/MPL/
 *  *
 *  * Software distributed under the License is distributed on an "AS IS" basis,
 *  * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  * for the specific language governing rights and limitations under the
 *  * License.
 *  *
 *  * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  * Java(TM), hosted at https://github.com/dcm4che.
 *  *
 *  * The Initial Developer of the Original Code is
 *  * J4Care.
 *  * Portions created by the Initial Developer are Copyright (C) 2017
 *  * the Initial Developer. All Rights Reserved.
 *  *
 *  * Contributor(s):
 *  * See @authors listed below
 *  *
 *  * Alternatively, the contents of this file may be used under the terms of
 *  * either the GNU General Public License Version 2 or later (the "GPL"), or
 *  * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  * in which case the provisions of the GPL or the LGPL are applicable instead
 *  * of those above. If you wish to allow use of your version of this file only
 *  * under the terms of either the GPL or the LGPL, and not to allow others to
 *  * use your version of this file under the terms of the MPL, indicate your
 *  * decision by deleting the provisions above and replace them with the notice
 *  * and other provisions required by the GPL or the LGPL. If you do not delete
 *  * the provisions above, a recipient may use your version of this file under
 *  * the terms of any one of the MPL, the GPL or the LGPL.
 *  *
 *  * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.conf;

import javax.jms.Message;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */

public class ExporterDescriptor {

    private String exporterID;
    private String description;
    private URI exportURI;
    private String queueName;
    private int priority = Message.DEFAULT_PRIORITY;
    private String aeTitle;
    private String[] ianDestinations = {};
    private String[] retrieveAETitles = {};
    private String retrieveLocationUID;
    private Availability instanceAvailability = Availability.ONLINE;
    private String stgCmtSCPAETitle;
    private String deleteStudyFromStorageID;
    private boolean rejectForDataRetentionExpiry;
    private ScheduleExpression[] schedules = {};
    private final Map<String, String> properties = new HashMap<>();

    public ExporterDescriptor() {
    }

    public ExporterDescriptor(String exporterID) {
        this.exporterID = exporterID;
    }

    public ExporterDescriptor(String exporterID, URI exportURI) {
        this.exporterID = exporterID;
        this.exportURI = exportURI;
    }

    public String getExporterID() {
        return exporterID;
    }

    public void setExporterID(String exporterID) {
        this.exporterID = exporterID;
    }

    public URI getExportURI() {
        return exportURI;
    }

    public void setExportURI(URI exportURI) {
        this.exportURI = exportURI;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (priority < 0 || priority > 9)
            throw new IllegalArgumentException("JMS Priority Level for processing the Export Task should be between 0 (lowest) to 9 (highest).");

        this.priority = priority;
    }

    public String getAETitle() {
        return aeTitle;
    }

    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getStgCmtSCPAETitle() {
        return stgCmtSCPAETitle;
    }

    public void setStgCmtSCPAETitle(String stgCmtSCPAETitle) {
        this.stgCmtSCPAETitle = stgCmtSCPAETitle;
    }

    public String getDeleteStudyFromStorageID() {
        return deleteStudyFromStorageID;
    }

    public void setDeleteStudyFromStorageID(String deleteStudyFromStorageID) {
        this.deleteStudyFromStorageID = deleteStudyFromStorageID;
    }

    public String[] getIanDestinations() {
        return ianDestinations;
    }

    public void setIanDestinations(String... ianDestinations) {
        this.ianDestinations = ianDestinations;
    }

    public String[] getRetrieveAETitles() {
        return retrieveAETitles;
    }

    public void setRetrieveAETitles(String... retrieveAETitles) {
        this.retrieveAETitles = retrieveAETitles;
    }

    public String getRetrieveLocationUID() {
        return retrieveLocationUID;
    }

    public void setRetrieveLocationUID(String retrieveLocationUID) {
        this.retrieveLocationUID = retrieveLocationUID;
    }

    public Availability getInstanceAvailability() {
        return instanceAvailability;
    }

    public void setInstanceAvailability(Availability instanceAvailability) {
        this.instanceAvailability = instanceAvailability;
    }

    public boolean isRejectForDataRetentionExpiry() {
        return rejectForDataRetentionExpiry;
    }

    public void setRejectForDataRetentionExpiry(boolean rejectForDataRetentionExpiry) {
        this.rejectForDataRetentionExpiry = rejectForDataRetentionExpiry;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression... schedules) {
        this.schedules = schedules;
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public String getProperty(String name, String defValue) {
        String value = properties.get(name);
        return value != null ? value : defValue;
    }

    public Map<String,String> getProperties() {
        return properties;
    }

    public void setProperties(String[] ss) {
        properties.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("Property in incorrect format : " + s);
            setProperty(s.substring(0, index), s.substring(index+1));
        }
    }

    @Override
    public String toString() {
        return "ExporterDescriptor{" +
                "exporterID=" + exporterID +
                ", exportURI=" + exportURI +
                ", priority=" + priority +
                ", queueName=" + queueName +
                ", aeTitle=" + aeTitle +
                ", stgCmtSCPAETitle=" + stgCmtSCPAETitle +
                ", deleteStudyFromStorageID=" + deleteStudyFromStorageID +
                ", ianDests=" + Arrays.toString(ianDestinations) +
                ", retrieveAETs=" + Arrays.toString(retrieveAETitles) +
                ", retrieveLocationUID=" + retrieveLocationUID +
                ", availability=" + instanceAvailability +
                ", schedules=" + Arrays.toString(schedules) +
                ", properties=" + properties +
                '}';
    }
}
