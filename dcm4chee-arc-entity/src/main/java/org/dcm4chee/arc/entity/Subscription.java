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

import org.dcm4che3.data.Attributes;

import javax.persistence.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
@NamedQuery(
        name=Subscription.FIND_BY_UPS_IUID_AND_SUBSCRIBER_AET,
        query="select sub from Subscription sub " +
                "where sub.upsInstanceUID = ?1 and sub.subscriberAET = ?2")
@NamedQuery(
        name=Subscription.FIND_BY_UPS_IUID,
        query="select sub from Subscription sub " +
                "where sub.upsInstanceUID = ?1")
@NamedQuery(
        name=Subscription.FIND_BY_UPS_IUID_EAGER,
        query="select sub from Subscription sub " +
                "join fetch sub.matchKeysBlob " +
                "where sub.upsInstanceUID = ?1")
@NamedQuery(
        name=Subscription.DELETE_BY_UPS_IUID_AND_SUBSCRIBER_AET,
        query="delete from Subscription sub " +
                "where sub.upsInstanceUID = ?1 and sub.subscriberAET = ?2")
@NamedQuery(
        name=Subscription.DELETE_BY_SUBSCRIBER_AET,
        query="delete from Subscription sub " +
                "where sub.subscriberAET = ?1")
@Entity
@Table(name = "subscription",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ups_iuid", "subscriber_aet"}),
        indexes = {
                @Index(columnList = "ups_iuid"),
                @Index(columnList = "subscriber_aet")
        })
public class Subscription {

    public static final String FIND_BY_UPS_IUID_AND_SUBSCRIBER_AET = "Subscription.findByUPSIUIDAndSubscriberAET";
    public static final String FIND_BY_UPS_IUID = "Subscription.findByUPSIUID";
    public static final String FIND_BY_UPS_IUID_EAGER = "Subscription.findByUPSIUIDEager";
    public static final String DELETE_BY_UPS_IUID_AND_SUBSCRIBER_AET = "Subscription.deleteByUPSIUIDAndSubscriberAET";
    public static final String DELETE_BY_SUBSCRIBER_AET = "Subscription.deleteBySubscriberAET";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "ups_iuid", updatable = false)
    private String upsInstanceUID;

    @Basic(optional = false)
    @Column(name = "subscriber_aet", updatable = false)
    private String subscriberAET;

    @Basic(optional = false)
    @Column(name = "deletion_lock")
    private boolean deletionLock;

    @OneToOne(fetch=FetchType.LAZY, cascade=CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "matchkeys_fk")
    private AttributesBlob matchKeysBlob;

    public long getPk() {
        return pk;
    }

    public String getUpsInstanceUID() {
        return upsInstanceUID;
    }

    public void setUpsInstanceUID(String upsInstanceUID) {
        this.upsInstanceUID = upsInstanceUID;
    }

    public String getSubscriberAET() {
        return subscriberAET;
    }

    public void setSubscriberAET(String subscriberAET) {
        this.subscriberAET = subscriberAET;
    }

    public boolean isDeletionLock() {
        return deletionLock;
    }

    public void setDeletionLock(boolean deletionLock) {
        this.deletionLock = deletionLock;
    }

    public Attributes getMatchKeys() throws BlobCorruptedException {
        return matchKeysBlob.getAttributes();
    }

    public void setMatchKeys(Attributes matchKeys) {
        if (matchKeys == null)
            matchKeysBlob = null;
        else if (matchKeysBlob == null)
            matchKeysBlob = new AttributesBlob(matchKeys);
        else
            matchKeysBlob.setAttributes(matchKeys);
    }

    @Override
    public String toString() {
        return "Subscription[pk=" + pk
                + ", uid=" + upsInstanceUID
                + ", aet=" + subscriberAET
                + ", deletionLock=" + deletionLock
                + "]";
    }
}
