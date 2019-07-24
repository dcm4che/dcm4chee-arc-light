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

import javax.persistence.*;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
@Entity
@Table(name = "rejected_instance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"study_iuid", "series_iuid", "sop_iuid"}),
        indexes = @Index(columnList = "created_time"))
@NamedQuery(
        name = RejectedInstance.FIND_BY_UIDS,
        query = "select ri from RejectedInstance ri " +
                "where ri.studyInstanceUID = ?1 and ri.seriesInstanceUID = ?2 and ri.sopInstanceUID = ?3")
@NamedQuery(
        name = RejectedInstance.FIND_BY_SERIES_UID,
        query = "select ri from RejectedInstance ri " +
                "where ri.studyInstanceUID = ?1 and ri.seriesInstanceUID = ?2")
@NamedQuery(
        name = RejectedInstance.DELETE_BY_UIDS,
        query = "delete from RejectedInstance ri " +
                "where ri.studyInstanceUID = ?1 and ri.seriesInstanceUID = ?2 and ri.sopInstanceUID = ?3")
public class RejectedInstance {
    public static final String FIND_BY_UIDS = "RejectedInstance.findByUIDs";
    public static final String FIND_BY_SERIES_UID = "RejectedInstance.findBySeriesUID";
    public static final String DELETE_BY_UIDS = "RejectedInstance.deleteByUIDs";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "series_iuid", updatable = false)
    private String seriesInstanceUID;

    @Basic(optional = false)
    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Basic(optional = false)
    @Column(name = "sop_cuid", updatable = false)
    private String sopClassUID;

    @ManyToOne
    @JoinColumn(name = "reject_code_fk")
    private CodeEntity rejectionNoteCode;

    protected RejectedInstance() {} // for JPA

    public RejectedInstance(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID,
            String sopClassUID, CodeEntity rejectionNoteCode) {
        this.studyInstanceUID = studyInstanceUID;
        this.seriesInstanceUID = seriesInstanceUID;
        this.sopInstanceUID = sopInstanceUID;
        this.sopClassUID = sopClassUID;
        this.rejectionNoteCode = rejectionNoteCode;
    }

    @PrePersist
    public void onPrePersist() {
        createdTime = new Date();
    }

    @Override
    public String toString() {
        return "RejectedInstance[pk=" + pk + ", time=" + createdTime + ", code=" + rejectionNoteCode +
                "] of Instance[uid=" + sopInstanceUID + ", class=" + sopClassUID +
                "] of Series[uid=" + seriesInstanceUID +
                "] of Study[uid=" + studyInstanceUID +
                "]";

    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public long getPk() {
        return pk;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public String getSopClassUID() {
        return sopClassUID;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public CodeEntity getRejectionNoteCode() {
        return rejectionNoteCode;
    }

    public void setRejectionNoteCode(CodeEntity rejectionNoteCode) {
        this.rejectionNoteCode = rejectionNoteCode;
    }
}
