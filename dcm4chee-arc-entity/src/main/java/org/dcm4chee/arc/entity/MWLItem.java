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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.DateUtils;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.SPSStatus;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2016
 */
@NamedQueries({
@NamedQuery(
        name = MWLItem.FIND_BY_STUDY_IUID,
        query = "select mwl from MWLItem mwl " +
                "where mwl.studyInstanceUID = ?1"),
        @NamedQuery(
                name = MWLItem.FIND_BY_STUDY_IUID_AND_SPS_ID,
                query = "select mwl from MWLItem mwl " +
                        "where mwl.studyInstanceUID = ?1 and mwl.scheduledProcedureStepID = ?2")
})
@Entity
@Table(name = "mwl_item",
        uniqueConstraints = @UniqueConstraint(columnNames = { "study_iuid", "sps_id" }),
        indexes = {
                @Index(columnList = "updated_time"),
                @Index(columnList = "sps_id"),
                @Index(columnList = "req_proc_id"),
                @Index(columnList = "study_iuid"),
                @Index(columnList = "accession_no"),
                @Index(columnList = "modality"),
                @Index(columnList = "sps_start_date"),
                @Index(columnList = "sps_start_time"),
                @Index(columnList = "sps_status")
        })
public class MWLItem {

    public static final String FIND_BY_STUDY_IUID = "MWLItem.findByStudyIUID";
    public static final String FIND_BY_STUDY_IUID_AND_SPS_ID = "MWLItem.findByStudyIUIDAndSPSID";

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Version
    @Column(name = "version")
    private long version;

    @Basic(optional = false)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "updated_time")
    private Date updatedTime;

    @Basic(optional = false)
    @Column(name = "sps_id", updatable = false)
    private String scheduledProcedureStepID;

    @Basic(optional = false)
    @Column(name = "req_proc_id")
    private String requestedProcedureID;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "accession_no")
    private String accessionNumber;

    @Basic(optional = false)
    @Column(name = "modality")
    private String modality;

    @Basic(optional = false)
    @Column(name = "sps_start_date")
    private String scheduledStartDate;

    @Basic(optional = false)
    @Column(name = "sps_start_time")
    private String scheduledStartTime;

    @Basic(optional = false)
    @Column(name = "sps_status")
    private SPSStatus status;

    @OneToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @Transient
    private Attributes cachedAttributes;

    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "perf_phys_name_fk")
    private PersonName scheduledPerformingPhysicianName;

    @ManyToOne
    @JoinColumn(name = "accno_issuer_fk")
    private IssuerEntity issuerOfAccessionNumber;

    @OneToMany(mappedBy = "mwlItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private Collection<ScheduledStationAETitle> scheduledStationAETs;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_fk")
    private Patient patient;

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public String getScheduledProcedureStepID() {
        return scheduledProcedureStepID;
    }

    public String getRequestedProcedureID() {
        return requestedProcedureID;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public IssuerEntity getIssuerOfAccessionNumber() {
        return issuerOfAccessionNumber;
    }

    public void setIssuerOfAccessionNumber(IssuerEntity issuerOfAccessionNumber) {
        this.issuerOfAccessionNumber = issuerOfAccessionNumber;
    }

    public String getModality() {
        return modality;
    }

    public String getScheduledStartDate() {
        return scheduledStartDate;
    }

    public String getScheduledStartTime() {
        return scheduledStartTime;
    }

    public PersonName getScheduledPerformingPhysicianName() {
        return scheduledPerformingPhysicianName;
    }

    public SPSStatus getStatus() {
        return status;
    }

    public Collection<ScheduledStationAETitle> getScheduledStationAETs() {
        if (scheduledStationAETs == null)
            scheduledStationAETs = new ArrayList<>();

        return scheduledStationAETs;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Patient getPatient() {
        return patient;
    }

    @Override
    public String toString() {
        return "MWLItem[pk=" + pk
                + ", spsid=" + scheduledProcedureStepID
                + ", rpid=" + requestedProcedureID
                + ", suid=" + studyInstanceUID
                + ", accno=" + accessionNumber
                + ", modality=" + modality
                + ", performer=" + scheduledPerformingPhysicianName
                + ", start=" + scheduledStartDate + scheduledStartTime
                + ", status=" + status
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedTime = new Date();
    }

    public AttributesBlob getAttributesBlob() {
        return attributesBlob;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr) {
        Attributes spsItem = attrs
                .getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (spsItem == null) {
            throw new IllegalArgumentException(
                    "Missing Scheduled Procedure Step Sequence (0040,0100) Item");
        }
        scheduledProcedureStepID = spsItem.getString(Tag.ScheduledProcedureStepID);
        modality = spsItem.getString(Tag.Modality, "*").toUpperCase();
        Date dt = spsItem.getDate(Tag.ScheduledProcedureStepStartDateAndTime);
        if (dt != null) {
            scheduledStartDate = DateUtils.formatDA(null, dt);
            scheduledStartTime = spsItem.containsValue(Tag.ScheduledProcedureStepStartTime)
                    ? DateUtils.formatTM(null, dt)
                    : "*";
        } else {
            scheduledStartDate = "*";
            scheduledStartTime = "*";
        }
        scheduledPerformingPhysicianName = PersonName.valueOf(
                attrs.getString(Tag.ScheduledPerformingPhysicianName), fuzzyStr, scheduledPerformingPhysicianName);
        String cs = spsItem.getString(Tag.ScheduledProcedureStepStatus);
        status = SPSStatus.valueOf(cs);
        requestedProcedureID = attrs.getString(Tag.RequestedProcedureID);
        studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        accessionNumber = attrs.getString(Tag.AccessionNumber, "*");

        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(new Attributes(attrs, filter.getSelection()));
        else
            attributesBlob.setAttributes(new Attributes(attrs, filter.getSelection()));
    }
}
