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

package org.dcm4chee.arc.entity;

import org.dcm4che3.util.StringUtils;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */
@NamedQueries({
        @NamedQuery(name = HL7PSUTask.FIND_WITH_MPPS_BY_DEVICE_NAME,
                query = "select o from HL7PSUTask o " +
                        "join fetch o.mwl mwl " +
                        "join fetch mwl.attributesBlob " +
                        "where o.mpps is not null and o.deviceName=?1 and o.pk>?2 " +
                        "order by o.pk"),
        @NamedQuery(name = HL7PSUTask.FIND_SCHEDULED_BY_DEVICE_NAME,
                query = "select o from HL7PSUTask o " +
                        "join fetch o.mwl mwl " +
                        "join fetch mwl.attributesBlob " +
                        "where o.deviceName=?1 and o.scheduledTime < current_timestamp"),
        @NamedQuery(name = HL7PSUTask.FIND_BY_STUDY_IUID,
                query = "select o from HL7PSUTask o where o.studyInstanceUID=?1 and o.mwl=?2"),

})
@Entity
@Table(name = "hl7psu_task",
        uniqueConstraints = @UniqueConstraint(columnNames = "study_iuid"),
        indexes = @Index(columnList = "device_name")
)
public class HL7PSUTask {
    public static final String FIND_WITH_MPPS_BY_DEVICE_NAME = "HL7PSUTask.findWithMppsByDeviceName";
    public static final String FIND_SCHEDULED_BY_DEVICE_NAME = "HL7PSUTask.findScheduledByDeviceName";
    public static final String FIND_BY_STUDY_IUID = "HL7PSUTask.findByStudyIUID";

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "device_name", updatable = false)
    private String deviceName;

    @Basic(optional = false)
    @Column(name = "called_aet", updatable = false)
    private String calledAET;

    @Basic(optional = false)
    @Column(name = "hl7psu_dests", updatable = false)
    private String hl7psuDestinations;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_time")
    private Date scheduledTime;

    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @OneToOne
    @JoinColumn(name = "mpps_fk", updatable = false)
    private MPPS mpps;

    @OneToOne
    @JoinColumn(name = "mwl_fk", updatable = false)
    private MWLItem mwl;

    public long getPk() {
        return pk;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getCalledAET() {
        return calledAET;
    }

    public void setCalledAET(String calledAET) {
        this.calledAET = calledAET;
    }

    public String[] getHl7psuDestinations() {
        return StringUtils.split(hl7psuDestinations, '\\');
    }

    public void setHl7psuDestinations(String... hl7psuDestinations) {
        this.hl7psuDestinations = StringUtils.concat(hl7psuDestinations, '\\');
    }

    public Date getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Date scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public void setStudyInstanceUID(String studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    public MPPS getMpps() {
        return mpps;
    }

    public void setMpps(MPPS mpps) {
        this.mpps = mpps;
    }

    public MWLItem getMwl() {
        return mwl;
    }

    public void setMwl(MWLItem mwl) {
        this.mwl = mwl;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("HL7PSUTask[pk=").append(pk).append(", deviceName=").append(deviceName);
        if (mpps == null)
            sb.append(", studyInstanceUID=").append(studyInstanceUID);
        else
            sb.append(", mppsInstanceUID=").append(mpps.getSopInstanceUID());
        if (scheduledTime != null)
            sb.append(", scheduledTime=").append(scheduledTime);
        sb.append(']');
        return sb.toString();
    }
}
