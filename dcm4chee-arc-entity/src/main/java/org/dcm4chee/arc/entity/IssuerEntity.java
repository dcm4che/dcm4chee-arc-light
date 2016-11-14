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

import org.dcm4che3.data.Issuer;

import javax.persistence.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@NamedQueries({
@NamedQuery(
    name=IssuerEntity.FIND_BY_ENTITY_ID,
    query="select entity from IssuerEntity entity where entity.issuer.localNamespaceEntityID = ?1"),
@NamedQuery(
    name=IssuerEntity.FIND_BY_ENTITY_UID,
    query="select entity from IssuerEntity entity " +
            "where entity.issuer.universalEntityID = ?1 and entity.issuer.universalEntityIDType = ?2"),
@NamedQuery(
    name=IssuerEntity.FIND_BY_ENTITY_ID_OR_UID,
    query="select entity from IssuerEntity entity where entity.issuer.localNamespaceEntityID = ?1 " +
            "or (entity.issuer.universalEntityID = ?2 and entity.issuer.universalEntityIDType = ?3)")
})
@Entity
@Table(name = "issuer", uniqueConstraints = {
    @UniqueConstraint(columnNames = "entity_id"),
    @UniqueConstraint(columnNames = { "entity_uid", "entity_uid_type" })
})
public class IssuerEntity {

    public static final String FIND_BY_ENTITY_ID = "IssuerEntity.findByEntityID";
    public static final String FIND_BY_ENTITY_UID = "IssuerEntity.findByEntityUID";
    public static final String FIND_BY_ENTITY_ID_OR_UID = "IssuerEntity.findByEntityIDorUID";

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Embedded
    private Issuer issuer;

    protected IssuerEntity() {} // for JPA

    public IssuerEntity(Issuer issuer) {
        this.issuer = issuer;
    }

    public long getPk() {
        return pk;
    }

    public Issuer getIssuer() {
        return issuer;
    }

    public void setIssuer(Issuer issuer) {
        this.issuer = issuer;
    }

    @Override
    public String toString() {
        return issuer.toString();
    }
}
