/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.entity;

import org.dcm4che3.util.TagUtils;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2016
 */
@Entity
@Table(name = "metadata", indexes = @Index(columnList = "storage_id,status"))

@NamedQueries({
        @NamedQuery(name = Metadata.FIND_BY_STORAGE_ID_AND_STATUS,
                query = "select m from Metadata m where m.storageID=?1 and m.status=?2"),
        @NamedQuery(name=Metadata.FIND_BY_SERIES_IUID_AND_STORAGE_ID,
                query="select se.metadata from Series se " +
                        "where se.seriesInstanceUID = ?1 and se.metadata.storageID = ?2"),
        @NamedQuery(name = Metadata.UPDATE_STATUS_FROM,
                query = "update Metadata m set m.status = ?3 where m.pk = ?1 and m.status = ?2"),
        @NamedQuery(name = Metadata.DELETE_BY_PK,
                query = "delete from Metadata m where m.pk = ?1")
})

public class Metadata {

    public static final String FIND_BY_STORAGE_ID_AND_STATUS = "Metadata.FindByStorageIDAndStatus";
    public static final String FIND_BY_SERIES_IUID_AND_STORAGE_ID = "Metadata.FindBySeriesIUIDAndStorageID";
    public static final String UPDATE_STATUS_FROM = "Metadata.UpdateStatusFrom";
    public static final String DELETE_BY_PK = "Metadata.DeleteByPk";

    public enum Status {
        OK,                         // 0
        TO_DELETE,                  // 1
        FAILED_TO_DELETE,           // 2
        MISSING_OBJECT,             // 3
        FAILED_TO_FETCH_METADATA,   // 4
        FAILED_TO_FETCH_OBJECT,     // 5
        DIFFERING_OBJECT_SIZE,      // 6
        DIFFERING_OBJECT_CHECKSUM,  // 7
        DIFFERING_S3_MD5SUM,        // 8
        FAILED_TO_DELETE2           // 9
    }

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Column(name = "storage_id", updatable = false)
    private String storageID;

    @Basic(optional = false)
    @Column(name = "storage_path", updatable = false)
    private String storagePath;

    @Basic(optional = false)
    @Column(name = "object_size", updatable = false)
    private long size;

    @Basic(optional = true)
    @Column(name = "digest", updatable = false)
    private String digest;

    @Basic(optional = false)
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", updatable = true)
    private Status status = Status.OK;

    public long getPk() {
        return pk;
    }

    public String getStorageID() {
        return storageID;
    }

    public void setStorageID(String storageID) {
        this.storageID = storageID;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public byte[] getDigest() {
        return digest != null ? TagUtils.fromHexString(digest) : null;
    }

    public void setDigest(byte[] digest) {
        this.digest = digest != null ? TagUtils.toHexString(digest) : null;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Metadata[pk=" + pk
                + ", storageID=" + storageID
                + ", path=" + storagePath
                + ", size=" + size
                + ", status=" + status
                + "]";
    }

    @PrePersist
    public void onPrePersist() {
        createdTime = new Date();
    }
}
