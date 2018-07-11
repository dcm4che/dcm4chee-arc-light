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
 * Portions created by the Initial Developer are Copyright (C) 2015
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
import org.dcm4chee.arc.conf.AttributeFilter;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@NamedQueries({
@NamedQuery(
    name=MPPS.FIND_BY_PATIENT,
    query="select mpps from MPPS mpps " +
            "where mpps.patient = ?1"),
@NamedQuery(
        name=MPPS.DELETE_BY_PATIENT,
        query="delete from MPPS mpps " +
                "where mpps.patient = ?1"),
@NamedQuery(
    name=MPPS.FIND_BY_SOP_INSTANCE_UID,
    query="select mpps from MPPS mpps " +
            "where mpps.sopInstanceUID = ?1"),
@NamedQuery(
    name=MPPS.FIND_BY_SOP_INSTANCE_UID_EAGER,
    query="select mpps from MPPS mpps " +
            "join fetch mpps.patient p " +
            "left join fetch p.patientName " +
            "join fetch mpps.attributesBlob " +
            "join fetch p.attributesBlob " +
            "where mpps.sopInstanceUID = ?1")
})
@Entity
@Table(name = "mpps", uniqueConstraints = @UniqueConstraint(columnNames = "sop_iuid"))
public class MPPS {

    public enum Status {
        IN_PROGRESS, COMPLETED, DISCONTINUED;
    }

    public static final String FIND_BY_PATIENT = "MPPS.findByPatient";
    public static final String DELETE_BY_PATIENT = "MPPS.deleteByPatient";
    public static final String FIND_BY_SOP_INSTANCE_UID =  "MPPS.findBySOPInstanceUID";
    public static final String FIND_BY_SOP_INSTANCE_UID_EAGER =  "MPPS.findBySOPInstanceUID";

    public static final String IN_PROGRESS = "IN PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String DISCONTINUED = "DISCONTINUED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Basic(optional = false)
    @Column(name = "pps_start_date")
    private String performedProcedureStepStartDate;

    @Basic(optional = false)
    @Column(name = "pps_start_time")
    private String performedProcedureStepStartTime;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "accession_no")
    private String accessionNumber;

    @Basic(optional = false)
    @Column(name = "pps_status")
    private Status status;

    @ManyToOne
    @JoinColumn(name = "accno_issuer_fk")
    private IssuerEntity issuerOfAccessionNumber;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;

    @ManyToOne
    @JoinColumn(name = "discreason_code_fk")
    private CodeEntity discontinuationReasonCode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_fk")
    private Patient patient;

    @Override
    public String toString() {
        return "MPPS[pk=" + pk
                + ", uid=" + sopInstanceUID
                + ", studyInstanceUID=" + studyInstanceUID
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

    public long getPk() {
        return pk;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public String getPerformedProcedureStepStartDate() {
        return performedProcedureStepStartDate;
    }

    public String getPerformedProcedureStepStartTime() {
        return performedProcedureStepStartTime;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    public IssuerEntity getIssuerOfAccessionNumber() {
        return issuerOfAccessionNumber;
    }

    public void setIssuerOfAccessionNumber(IssuerEntity issuerOfAccessionNumber) {
        this.issuerOfAccessionNumber = issuerOfAccessionNumber;
    }

    public CodeEntity getDiscontinuationReasonCode() {
        return discontinuationReasonCode;
    }

    public void setDiscontinuationReasonCode(CodeEntity discontinuationReasonCode) {
        this.discontinuationReasonCode = discontinuationReasonCode;
    }

    public AttributesBlob getAttributesBlob() {
        return attributesBlob;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter) {
        Attributes ssa = attrs.getNestedDataset(Tag.ScheduledStepAttributesSequence);
        String cs = attrs.getString(Tag.PerformedProcedureStepStatus);
        status = IN_PROGRESS.equals(cs) ? Status.IN_PROGRESS : Status.valueOf(cs);
        performedProcedureStepStartDate = attrs.getString(Tag.PerformedProcedureStepStartDate);
        performedProcedureStepStartTime = attrs.getString(Tag.PerformedProcedureStepStartTime);
        studyInstanceUID = ssa.getString(Tag.StudyInstanceUID);
        accessionNumber = ssa.getString(Tag.AccessionNumber, "*");
        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(new Attributes(attrs, filter.getSelection()));
        else
            attributesBlob.setAttributes(new Attributes(attrs, filter.getSelection()));
    }
}
