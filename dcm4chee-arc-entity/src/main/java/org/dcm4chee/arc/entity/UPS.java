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

package org.dcm4chee.arc.entity;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4chee.arc.conf.*;

import javax.persistence.*;
import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

@NamedQuery(
        name = UPS.FIND_BY_PATIENT,
        query = "select ups from UPS ups where ups.patient = ?1")
@NamedQuery(
        name= UPS.FIND_BY_IUID,
        query="select ups from UPS ups where ups.upsInstanceUID = ?1")
@NamedQuery(
        name= UPS.FIND_BY_IUID_EAGER,
        query="select ups from UPS ups " +
                "join fetch ups.patient p " +
                "join fetch ups.attributesBlob " +
                "join fetch p.attributesBlob " +
                "where ups.upsInstanceUID = ?1")
@NamedQuery(
        name= UPS.FIND_WO_DELETION_LOCK,
        query="select ups from UPS ups " +
                "where ((ups.procedureStepState = ?1 and ups.updatedTime < ?2) " +
                "or (ups.procedureStepState = ?3 and ups.updatedTime < ?4)) " +
                "and not exists (select sub from Subscription sub where sub.ups = ups and sub.deletionLock = ?5) " +
                "order by ups.updatedTime")
@Entity
@Table(name = "ups",
        uniqueConstraints = @UniqueConstraint(columnNames = "ups_iuid" ),
        indexes = {
                @Index(columnList = "updated_time"),
                @Index(columnList = "ups_priority"),
                @Index(columnList = "ups_label"),
                @Index(columnList = "worklist_label"),
                @Index(columnList = "start_date_time"),
                @Index(columnList = "expiration_date_time"),
                @Index(columnList = "expected_end_date_time"),
                @Index(columnList = "input_readiness_state"),
                @Index(columnList = "admission_id"),
                @Index(columnList = "replaced_iuid"),
                @Index(columnList = "ups_state")
        })
public class UPS {

    public static final String FIND_BY_PATIENT = "UPS.findByPatient";
    public static final String FIND_BY_IUID = "UPS.findByIUID";
    public static final String FIND_BY_IUID_EAGER = "UPS.findByIUIDEager";
    public static final String FIND_WO_DELETION_LOCK = "UPS.findWithoutDeletionLock";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Version
    @Column(name = "version")
    private long version;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_time")
    private Date updatedTime;

    @Basic(optional = false)
    @Column(name = "ups_iuid", updatable = false)
    private String upsInstanceUID;

    @Basic(optional = false)
    @Column(name = "ups_priority")
    private UPSPriority upsPriority;

    @Basic(optional = false)
    @Column(name = "ups_label")
    private String upsLabel;

    @Basic(optional = false)
    @Column(name = "worklist_label")
    private String worklistLabel;

    @Basic(optional = false)
    @Column(name = "start_date_time")
    private String scheduledStartDateAndTime;

    @Basic(optional = false)
    @Column(name = "expiration_date_time")
    private String scheduledProcedureStepExpirationDateTime;

    @Basic(optional = false)
    @Column(name = "expected_end_date_time")
    private String expectedCompletionDateAndTime;

    @Basic(optional = false)
    @Column(name = "input_readiness_state")
    private InputReadinessState inputReadinessState;

    @Basic(optional = false)
    @Column(name = "admission_id")
    private String admissionID;

    @Basic(optional = false)
    @Column(name = "replaced_iuid")
    private String replacedSOPInstanceUID;

    @Basic(optional = false)
    @Column(name = "ups_state")
    private UPSState procedureStepState;

    @Column(name = "transaction_iuid")
    private String transactionUID;

    @Column(name = "performer_aet")
    private String performerAET;

    @ManyToOne
    @JoinColumn(name = "ups_code_fk")
    private CodeEntity scheduledWorkitemCode;

    @ManyToOne
    @JoinColumn(name = "station_name_fk")
    private CodeEntity scheduledStationNameCode;

    @ManyToOne
    @JoinColumn(name = "station_class_fk")
    private CodeEntity scheduledStationClassCode;

    @ManyToOne
    @JoinColumn(name = "station_location_fk")
    private CodeEntity scheduledStationGeographicLocationCode;

    @ManyToOne
    @JoinColumn(name = "admission_issuer_fk")
    private IssuerEntity issuerOfAdmissionID;

    @ManyToMany
    @JoinTable(name = "rel_ups_perf_code",
            joinColumns = @JoinColumn(name = "ups_fk", referencedColumnName = "pk"),
            inverseJoinColumns = @JoinColumn(name = "perf_code_fk", referencedColumnName = "pk"))
    private Collection<CodeEntity> humanPerformerCodes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ups_fk")
    private Collection<UPSRequest> referencedRequests;

    @OneToMany(mappedBy = "ups")
    private Collection<Subscription> subscriptions;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_fk")
    private Patient patient;

    @OneToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @Override
    public String toString() {
        return "UPS[pk=" + pk
                + ", uid=" + upsInstanceUID
                + ", state=" + procedureStepState
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
        Date now = new Date();
        updatedTime = now;
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public String getUpsInstanceUID() {
        return upsInstanceUID;
    }

    public void setUpsInstanceUID(String upsInstanceUID) {
        this.upsInstanceUID = upsInstanceUID;
    }

    public UPSState getProcedureStepState() {
        return procedureStepState;
    }

    public String getTransactionUID() {
        return transactionUID;
    }

    public void setTransactionUID(String transactionUID) {
        this.transactionUID = transactionUID;
    }

    public String getPerformerAET() {
        return performerAET;
    }

    public void setPerformerAET(String performerAET) {
        this.performerAET = performerAET;
    }

    public AttributesBlob getAttributesBlob() {
        return attributesBlob;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter) {
        upsPriority = UPSPriority.valueOf(attrs.getString(Tag.ScheduledProcedureStepPriority));
        upsLabel = attrs.getString(Tag.ProcedureStepLabel);
        worklistLabel = attrs.getString(Tag.WorklistLabel);
        scheduledStartDateAndTime = attrs.getString(Tag.ScheduledProcedureStepStartDateTime);
        scheduledProcedureStepExpirationDateTime = attrs.getString(Tag.ScheduledProcedureStepExpirationDateTime, "*");
        expectedCompletionDateAndTime = attrs.getString(Tag.ExpectedCompletionDateTime, "*");
        inputReadinessState = InputReadinessState.valueOf(attrs.getString(Tag.InputReadinessState));
        admissionID = attrs.getString(Tag.AdmissionID, "*");
        replacedSOPInstanceUID = getString(attrs.getNestedDataset(Tag.ReplacedProcedureStepSequence),
                Tag.ReferencedSOPInstanceUID, "*");
        procedureStepState = UPSState.fromString(attrs.getString(Tag.ProcedureStepState));
        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(new Attributes(attrs, filter.getSelection()));
        else
            attributesBlob.setAttributes(new Attributes(attrs, filter.getSelection()));

        updatedTime = new Date();
    }

    private static String getString(Attributes item, int tag, String defVal) {
        return item != null ? item.getString(tag, defVal) : defVal;
    }

    public void setScheduledWorkitemCode(CodeEntity scheduledWorkitemCode) {
        this.scheduledWorkitemCode = scheduledWorkitemCode;
    }

    public void setScheduledStationNameCode(CodeEntity scheduledStationNameCode) {
        this.scheduledStationNameCode = scheduledStationNameCode;
    }

    public void setScheduledStationClassCode(CodeEntity scheduledStationClassCode) {
        this.scheduledStationClassCode = scheduledStationClassCode;
    }

    public void setScheduledStationGeographicLocationCode(CodeEntity scheduledStationGeographicLocationCode) {
        this.scheduledStationGeographicLocationCode = scheduledStationGeographicLocationCode;
    }

    public void setIssuerOfAdmissionID(IssuerEntity issuerOfAdmissionID) {
        this.issuerOfAdmissionID = issuerOfAdmissionID;
    }

    public Collection<CodeEntity> getHumanPerformerCodes() {
        if (humanPerformerCodes == null) {
            humanPerformerCodes = new ArrayList<>();
        }
        return humanPerformerCodes;
    }

    public Collection<UPSRequest> getReferencedRequests() {
        if (referencedRequests == null) {
            referencedRequests = new ArrayList<>();
        }
        return referencedRequests;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

}
