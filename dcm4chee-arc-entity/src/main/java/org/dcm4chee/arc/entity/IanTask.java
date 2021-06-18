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
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2016
 */
@NamedQueries({
        @NamedQuery(name = IanTask.FIND_WITH_MPPS_BY_DEVICE_NAME,
                query = "select o from IanTask o " +
                        "where o.mpps is not null and o.deviceName=?1 and o.pk>?2 " +
                        "order by o.pk"),
        @NamedQuery(name = IanTask.FIND_SCHEDULED_BY_DEVICE_NAME,
                query = "select o from IanTask o where o.deviceName=?1 and o.scheduledTime < current_timestamp " +
                        "order by o.scheduledTime"),
        @NamedQuery(name = IanTask.FIND_BY_STUDY_IUID,
                query = "select o from IanTask o where o.studyInstanceUID=?1"),

})
@Entity
@Table(name = "ian_task",
        uniqueConstraints = @UniqueConstraint(columnNames = "study_iuid"),
        indexes = @Index(columnList = "device_name")
)
public class IanTask {
    public static final String FIND_WITH_MPPS_BY_DEVICE_NAME = "IanTask.findWithMppsByDeviceName";
    public static final String FIND_SCHEDULED_BY_DEVICE_NAME = "IanTask.findScheduledByDeviceName";
    public static final String FIND_BY_STUDY_IUID = "IanTask.findByStudyIUID";

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "device_name", updatable = false)
    private String deviceName;

    @Basic(optional = false)
    @Column(name = "calling_aet", updatable = false)
    private String callingAET;

    @Basic(optional = false)
    @Column(name = "ian_dests", updatable = false)
    private String ianDestinations;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_time")
    private Date scheduledTime;

    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @OneToOne
    @JoinColumn(name = "mpps_fk", updatable = false)
    private MPPS mpps;

    public long getPk() {
        return pk;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getCallingAET() {
        return callingAET;
    }

    public void setCallingAET(String callingAET) {
        this.callingAET = callingAET;
    }

    public String[] getIanDestinations() {
        return StringUtils.split(ianDestinations, '\\');
    }

    public void setIanDestinations(String... ianDestinations) {
        this.ianDestinations = StringUtils.concat(ianDestinations, '\\');
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IanTask[pk=").append(pk).append(", deviceName=").append(deviceName);
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
