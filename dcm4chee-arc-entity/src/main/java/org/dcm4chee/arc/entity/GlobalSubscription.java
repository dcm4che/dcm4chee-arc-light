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
        name= GlobalSubscription.GLOBAL_AETS,
        query="select sub.subscriberAET from GlobalSubscription sub where sub.matchKeysBlob is null")
@NamedQuery(
        name= GlobalSubscription.FILTERED_AETS,
        query="select sub.subscriberAET from GlobalSubscription sub where sub.matchKeysBlob is not null")
@NamedQuery(
        name= GlobalSubscription.FIND_BY_AET,
        query="select sub from GlobalSubscription sub where sub.subscriberAET = ?1")
@NamedQuery(
        name= GlobalSubscription.FIND_ALL_EAGER,
        query="select sub from GlobalSubscription sub left join fetch sub.matchKeysBlob")
@Entity
@Table(name = "global_subscription",
        uniqueConstraints = @UniqueConstraint(columnNames = "subscriber_aet"))
public class GlobalSubscription {

    public static final String GLOBAL_AETS = "GlobalSubscription.globalAETs";
    public static final String FILTERED_AETS = "GlobalSubscription.filteredAETs";
    public static final String FIND_BY_AET = "GlobalSubscription.findByAET";
    public static final String FIND_ALL_EAGER = "GlobalSubscription.findAllEager";

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

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
        return matchKeysBlob != null ? matchKeysBlob.getAttributes() : null;
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
        return "GlobalSubscription[pk=" + pk
                + ", aet=" + subscriberAET
                + ", deletionLock=" + deletionLock
                + ", matchingKeys=" + (matchKeysBlob != null)
                + "]";
    }
}
