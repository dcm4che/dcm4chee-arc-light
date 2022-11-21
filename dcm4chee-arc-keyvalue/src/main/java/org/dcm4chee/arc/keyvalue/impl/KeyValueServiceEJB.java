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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.arc.keyvalue.impl;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.dcm4chee.arc.entity.KeyValue;
import org.dcm4chee.arc.keyvalue.ContentTypeMismatchException;
import org.dcm4chee.arc.keyvalue.KeyValueService;
import org.dcm4chee.arc.keyvalue.UserMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2022
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class KeyValueServiceEJB implements KeyValueService {
    private static final Logger LOG = LoggerFactory.getLogger(KeyValueServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Override
    public KeyValue getKeyValue(String key, String user) {
        try {
            return findByKeyAndUser(key, user);
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public void setKeyValue(String key, String user, boolean share, String value, String contentType)
            throws UserMismatchException, ContentTypeMismatchException {
        try {
            KeyValue keyValue = findByKey(key);
            if (!keyValue.getContentType().equals(contentType))
                throw new ContentTypeMismatchException("There is already a Value set for the specified Key with a different content type.");

            if (!share && keyValue.getUsername() != null && !keyValue.getUsername().equals(user))
                throw new UserMismatchException("There is already a Value set for the specified Key by a different user.");

            updateKeyValue(keyValue, share ? null : user, value, contentType);
        } catch (NoResultException e) {
            createKeyValue(key, share ? null : user, value, contentType);
        }
    }

    @Override
    public KeyValue deleteKeyValue(String key, String user) throws UserMismatchException {
        try {
            KeyValue keyValue = findByKey(key);
            if (keyValue.getUsername() != null && !keyValue.getUsername().equals(user))
                throw new UserMismatchException("The Value for the specified Key was set by a different user.");
            em.remove(keyValue);
            return keyValue;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public List<Long> keyValuePKs(Date before, int fetchSize) {
        return em.createNamedQuery(KeyValue.PK_UPDATED_BEFORE, Long.class)
                .setParameter(1, before)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    @Override
    public int deleteKeyValues(List<Long> pks) {
        return em.createNamedQuery(KeyValue.DELETE_BY_PKS)
                .setParameter(1, pks)
                .executeUpdate();
    }

    private KeyValue findByKey(String key) {
        return em.createNamedQuery(KeyValue.FIND_BY_KEY, KeyValue.class)
                .setParameter(1, key)
                .getSingleResult();
    }

    private KeyValue findByKeyAndUser(String key, String user) {
        return em.createNamedQuery(KeyValue.FIND_BY_KEY_AND_USER, KeyValue.class)
                .setParameter(1, key)
                .setParameter(2, user)
                .getSingleResult();
    }

    private void updateKeyValue(KeyValue keyValue, String user, String value, String contentType) {
        keyValue.setKeyAsUser(keyValue.getKey(), user);
        keyValue.setValueWithContentType(value, contentType);
        LOG.info("Update {}", keyValue);
    }

    private void createKeyValue(String key, String user, String value, String contentType) {
        KeyValue kv = new KeyValue();
        kv.setKeyAsUser(key, user);
        kv.setValueWithContentType(value, contentType);
        em.persist(kv);
        LOG.info("Create {}", kv);
    }
}

