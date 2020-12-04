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

package org.dcm4chee.arc.issuer.impl;

import org.dcm4che3.data.Issuer;
import org.dcm4chee.arc.entity.IssuerEntity;
import org.dcm4chee.arc.entity.IssuerEntity_;
import org.dcm4chee.arc.issuer.IssuerService;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@Stateless
public class IssuerServiceEJB implements IssuerService {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Override
    public IssuerEntity updateOrCreate(Issuer issuer) {
        try {
            IssuerEntity entity = find(issuer);
            entity.setIssuer(issuer);
            return entity;
        } catch (NoResultException e) {
            return create(issuer);
        }
    }

    @Override
    public IssuerEntity mergeOrCreate(Issuer issuer) {
        try {
            IssuerEntity entity = find(issuer);
            entity.merge(issuer);
            return entity;
        } catch (NoResultException e) {
            return create(issuer);
        }
    }

    private IssuerEntity create(Issuer issuer) {
        IssuerEntity entity = new IssuerEntity(issuer);
        em.persist(entity);
        return entity;
    }

    private IssuerEntity find(Issuer issuer) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<IssuerEntity> q = cb.createQuery(IssuerEntity.class);
        Root<IssuerEntity> issuerEntity = q.from(IssuerEntity.class);
        List<Predicate> predicates = new ArrayList<>();
        if (issuer.getLocalNamespaceEntityID() != null)
            predicates.add(cb.equal(issuerEntity.get(IssuerEntity_.localNamespaceEntityID),
                    issuer.getLocalNamespaceEntityID()));
        if (issuer.getUniversalEntityID() != null) {
            predicates.add(cb.equal(issuerEntity.get(IssuerEntity_.universalEntityID),
                    issuer.getUniversalEntityID()));
            if (issuer.getUniversalEntityIDType() != null)
                predicates.add(cb.equal(issuerEntity.get(IssuerEntity_.universalEntityIDType),
                        issuer.getUniversalEntityIDType()));
        } else
            predicates.add(cb.isNull(issuerEntity.get(IssuerEntity_.universalEntityID)));
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(q).getSingleResult();
    }
}
