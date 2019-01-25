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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */
public class StudyRetentionPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(StudyRetentionPolicy.class);

    private String commonName;
    private int priority;
    private Conditions conditions = new Conditions();
    private Period retentionPeriod;
    private boolean expireSeriesIndividually;
    private boolean startRetentionPeriodOnStudyDate;
    private String exporterID;
    private boolean freezeExpirationDate;
    private boolean revokeExpiration;

    public StudyRetentionPolicy() {
    }

    public StudyRetentionPolicy(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Conditions getConditions() {
        return conditions;
    }

    public void setConditions(Conditions conditions) {
        this.conditions = conditions;
    }

    public Period getRetentionPeriod() {
        return retentionPeriod;
    }

    public void setRetentionPeriod(Period retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    public boolean isExpireSeriesIndividually() {
        return expireSeriesIndividually;
    }

    public void setExpireSeriesIndividually(boolean expireSeriesIndividually) {
        this.expireSeriesIndividually = expireSeriesIndividually;
    }

    public boolean match(String hostname, String sendingAET, String receivingAET, Attributes attrs) {
        return conditions.match(hostname, sendingAET, receivingAET, attrs);
    }

    public boolean isStartRetentionPeriodOnStudyDate() {
        return startRetentionPeriodOnStudyDate;
    }

    public void setStartRetentionPeriodOnStudyDate(boolean startRetentionPeriodOnStudyDate) {
        this.startRetentionPeriodOnStudyDate = startRetentionPeriodOnStudyDate;
    }

    public String getExporterID() {
        return exporterID;
    }

    public void setExporterID(String exporterID) {
        this.exporterID = exporterID;
    }

    public boolean isFreezeExpirationDate() {
        return freezeExpirationDate;
    }

    public void setFreezeExpirationDate(boolean freezeExpirationDate) {
        this.freezeExpirationDate = freezeExpirationDate;
    }

    public boolean isRevokeExpiration() {
        return revokeExpiration;
    }

    public void setRevokeExpiration(boolean revokeExpiration) {
        this.revokeExpiration = revokeExpiration;
    }

    public LocalDate expirationDate(Attributes attrs) {
        LocalDate date = LocalDate.now();
        String s;
        if (startRetentionPeriodOnStudyDate && (s = attrs.getString(Tag.StudyDate)) != null) {
            try {
                date = LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE);
            } catch (Exception e) {
                LOG.warn("Failed parsing study date to get retention start date." + e.getMessage());
            }
        }
        return date.plus(retentionPeriod);
    }

    public boolean protectStudy() {
        return revokeExpiration && freezeExpirationDate;
    }

    @Override
    public String toString() {
        return "StudyRetentionPolicy{" +
                "cn=" + commonName +
                ", priority=" + priority +
                ", conditions=" + conditions +
                ", retentionPeriod=" + retentionPeriod +
                ", expireSeriesIndividually=" + expireSeriesIndividually +
                ", startRetentionPeriodOnStudyDate=" + startRetentionPeriodOnStudyDate +
                ", exporterID=" + exporterID +
                ", freezeExpirationDate=" + freezeExpirationDate +
                ", revokeExpiration=" + revokeExpiration +
                '}';
    }
}


