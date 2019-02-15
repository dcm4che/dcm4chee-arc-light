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

import org.dcm4che3.data.*;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.ShowPatientInfo;

import javax.persistence.*;
import java.util.*;

/**
 * @author Damien Evans <damien.daddy@gmail.com>
 * @author Justin Falk <jfalkmu@gmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@NamedQueries({
@NamedQuery(
    name=Patient.FIND_BY_PATIENT_ID,
    query="select p from Patient p " +
            "where p.patientID.id = ?1"),
@NamedQuery(
    name=Patient.FIND_BY_PATIENT_ID_EAGER,
    query="select p from Patient p " +
            "left join fetch p.patientName " +
            "join fetch p.attributesBlob " +
            "where p.patientID.id = ?1"),
@NamedQuery(
    name=Patient.FIND_BY_PATIENT_FAMILY_NAME,
    query="select p from Patient p " +
            "where p.patientName.familyName = ?1"),
@NamedQuery(
    name=Patient.FIND_BY_PATIENT_FAMILY_NAME_EAGER,
    query="select p from Patient p " +
            "left join fetch p.patientName " +
            "join fetch p.attributesBlob " +
            "where p.patientName.familyName = ?1"),
@NamedQuery(
    name=Patient.FIND_BY_MERGED_WITH,
    query="select p from Patient p " +
            "where p.mergedWith = ?1"),
@NamedQuery(
    name=Patient.COUNT_BY_MERGED_WITH,
    query="select count(p) from Patient p " +
            "where p.mergedWith = ?1"),
@NamedQuery(
    name=Patient.FIND_BY_VERIFICATION_STATUS,
    query="select new org.dcm4chee.arc.entity.Patient$IDWithPkAndVerificationStatus(p.patientID, p.pk, p.verificationStatus) " +
            "from Patient p " +
            "where p.verificationStatus = ?1 " +
            "order by p.pk"),
@NamedQuery(
    name=Patient.FIND_BY_VERIFICATION_STATUS_AND_TIME,
    query="select new org.dcm4chee.arc.entity.Patient$IDWithPkAndVerificationStatus(p.patientID, p.pk, p.verificationStatus) " +
            "from Patient p " +
            "where p.verificationStatus = ?1 " +
            "and p.verificationTime < ?2 " +
            "order by p.verificationTime"),
@NamedQuery(
    name=Patient.FIND_BY_VERIFICATION_STATUS_AND_TIME_AND_MAX_RETRIES,
    query="select new org.dcm4chee.arc.entity.Patient$IDWithPkAndVerificationStatus(p.patientID, p.pk, p.verificationStatus) " +
            "from Patient p " +
            "where p.verificationStatus = ?1 " +
            "and p.verificationTime < ?2 " +
            "and p.failedVerifications <= ?3 " +
            "order by p.verificationTime"),
@NamedQuery(
    name=Patient.CLAIM_PATIENT_VERIFICATION,
    query="update Patient p set p.verificationStatus = ?3 " +
            "where p.pk = ?1 and p.verificationStatus = ?2")
})
@Entity
@Table(name = "patient",
    uniqueConstraints = @UniqueConstraint(columnNames = "patient_id_fk"),
    indexes = {
        @Index(columnList = "verification_status"),
        @Index(columnList = "verification_time"),
        @Index(columnList = "num_studies"),
        @Index(columnList = "pat_birthdate"),
        @Index(columnList = "pat_sex"),
        @Index(columnList = "pat_custom1"),
        @Index(columnList = "pat_custom2"),
        @Index(columnList = "pat_custom3")
})
public class Patient {

    public static final String FIND_BY_PATIENT_ID = "Patient.findByPatientID";
    public static final String FIND_BY_PATIENT_ID_EAGER = "Patient.findByPatientIDEager";
    public static final String FIND_BY_PATIENT_FAMILY_NAME = "Patient.findByPatientFamilyName";
    public static final String FIND_BY_PATIENT_FAMILY_NAME_EAGER = "Patient.findByPatientFamilyNameEager";
    public static final String FIND_BY_MERGED_WITH = "Patient.findByMergedWith";
    public static final String COUNT_BY_MERGED_WITH = "Patient.CountByMergedWith";
    public static final String FIND_BY_VERIFICATION_STATUS = "Patient.findByVerificationStatus";
    public static final String FIND_BY_VERIFICATION_STATUS_AND_TIME = "Patient.findByVerificationStatusAndTime";
    public static final String FIND_BY_VERIFICATION_STATUS_AND_TIME_AND_MAX_RETRIES =
            "Patient.findByVerificationStatusAndTimeAndMaxRetries";
    public static final String CLAIM_PATIENT_VERIFICATION = "Patient.ClaimPatientVerification";

    public enum VerificationStatus {
        UNVERIFIED,
        VERIFIED,
        NOT_FOUND,
        VERIFICATION_FAILED,
        IN_PROCESS
    }

    public static class IDWithPkAndVerificationStatus {
        public final IDWithIssuer idWithIssuer;
        public final Long pk;
        public final VerificationStatus verificationStatus;

        public IDWithPkAndVerificationStatus(PatientID patientID, Long pk, VerificationStatus verificationStatus) {
            this.idWithIssuer = patientID.getIDWithIssuer();
            this.pk = pk;
            this.verificationStatus = verificationStatus;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(32);
            sb.append("Patient[pk=").append(pk);
            if (showPatientInfo != ShowPatientInfo.HASH_NAME_AND_ID)
                sb.append(", id=").append(idWithIssuer);
            sb.append(']');
            return sb.toString();
        }
    }

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

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "verification_time")
    private Date verificationTime;

    @Basic(optional = false)
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Basic(optional = false)
    @Column(name = "failed_verifications")
    private int failedVerifications;

    @Basic(optional = false)
    @Column(name = "pat_birthdate")
    private String patientBirthDate;

    @Basic(optional = false)
    @Column(name = "pat_sex")
    private String patientSex;

    @Basic(optional = false)
    @Column(name = "pat_custom1")
    private String patientCustomAttribute1;

    @Basic(optional = false)
    @Column(name = "pat_custom2")
    private String patientCustomAttribute2;

    @Basic(optional = false)
    @Column(name = "pat_custom3")
    private String patientCustomAttribute3;

    @Basic(optional = false)
    @Column(name = "num_studies")
    private int numberOfStudies;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "dicomattrs_fk")
    private AttributesBlob attributesBlob;
    
    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pat_name_fk")
    private PersonName patientName;

    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "resp_person_fk")
    private PersonName responsiblePerson;

    @ManyToOne
    @JoinColumn(name = "merge_fk")
    private Patient mergedWith;

    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "patient_id_fk")
    private PatientID patientID;

    private static ShowPatientInfo showPatientInfo = ShowPatientInfo.PLAIN_TEXT;

    public static ShowPatientInfo getShowPatientInfo() {
        return showPatientInfo;
    }

    public static void setShowPatientInfo(ShowPatientInfo showPatientInfo) {
        Patient.showPatientInfo = showPatientInfo;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Patient[pk=").append(pk);
        if (showPatientInfo == ShowPatientInfo.HASH_NAME_AND_ID && patientID != null)
            sb.append(", id=#").append(patientID.toString().hashCode());
        else
            sb.append(", id=").append(patientID);
        if (showPatientInfo != ShowPatientInfo.PLAIN_TEXT && patientName != null)
            sb.append(", name=#").append(patientName.toString().hashCode());
        else
            sb.append(", name=").append(patientName);
        sb.append(']');
        return sb.toString();
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

    public long getVersion() {
        return version;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public Date getVerificationTime() {
        return verificationTime;
    }

    public void setVerificationTime(Date verificationTime) {
        this.verificationTime = verificationTime;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public int getFailedVerifications() {
        return failedVerifications;
    }

    public void setFailedVerifications(int failedVerifications) {
        this.failedVerifications = failedVerifications;
    }

    public void resetFailedVerifications() {
        this.failedVerifications = 0;
    }

    public void incrementFailedVerifications() {
        this.failedVerifications++;
    }

    public String getPatientBirthDate() {
        return patientBirthDate;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public String getPatientCustomAttribute1() {
        return patientCustomAttribute1;
    }

    public String getPatientCustomAttribute2() {
        return patientCustomAttribute2;
    }

    public String getPatientCustomAttribute3() {
        return patientCustomAttribute3;
    }

    public int getNumberOfStudies() {
        return numberOfStudies;
    }

    public void setNumberOfStudies(int numberOfStudies) {
        this.numberOfStudies = numberOfStudies;
    }

    public void incrementNumberOfStudies() {
        numberOfStudies++;
    }

    public void decrementNumberOfStudies() {
        numberOfStudies = Math.max(numberOfStudies-1, 0);
    }

    public Patient getMergedWith() {
        return mergedWith;
    }

    public void setMergedWith(Patient mergedWith) {
        this.mergedWith = mergedWith;
    }

    public PersonName getPatientName() {
        return patientName;
    }

    public PatientID getPatientID() {
        return patientID;
    }

    public void setPatientID(PatientID patientID) {
        this.patientID = patientID;
    }

    public PersonName getResponsiblePerson() {
        return responsiblePerson;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        return attributesBlob.getAttributes();
    }

    public void setAttributes(Attributes attrs, AttributeFilter filter, FuzzyStr fuzzyStr) {
        patientName = PersonName.valueOf(
                attrs.getString(Tag.PatientName), fuzzyStr, patientName);
        patientBirthDate = attrs.getString(Tag.PatientBirthDate, "*");
        patientSex = attrs.getString(Tag.PatientSex, "*").toUpperCase();

        patientCustomAttribute1 = 
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute1(), "*");
        patientCustomAttribute2 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute2(), "*");
        patientCustomAttribute3 =
            AttributeFilter.selectStringValue(attrs, filter.getCustomAttribute3(), "*");

        Attributes blobAttrs = new Attributes(attrs, filter.getSelection(true));
        if (attributesBlob == null)
            attributesBlob = new AttributesBlob(blobAttrs);
        else
            attributesBlob.setAttributes(blobAttrs);

        responsiblePerson = PersonName.valueOf(
                attrs.getString(Tag.ResponsiblePerson), fuzzyStr, responsiblePerson);

        updatedTime = new Date();
    }
}
