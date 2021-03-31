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

package org.dcm4chee.arc.query.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.entity.MPPS_;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.query.QueryContext;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Mar 2021
 */
public class MPPSQuery extends AbstractQuery {

    private Join<MPPS, Patient> patient;
    private Root<MPPS> mpps;
    public MPPSQuery(QueryContext context, EntityManager em) {
        super(context, em);
    }

    @Override
    protected CriteriaQuery<Tuple> multiselect() {
        CriteriaQuery<javax.persistence.Tuple> q = cb.createTupleQuery();
        this.mpps = q.from(MPPS.class);
        this.patient = mpps.join(MPPS_.patient);
        String viewID = context.getQueryParam().getViewID();
        return null;
    }

    @Override
    protected CriteriaQuery<Long> count() {
        //TODO
        return null;
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        //TODO
        return null;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }

    private CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null)
            q.orderBy(builder.orderMPPS(patient, mpps, context.getOrderByTags()));
        return q;
    }
}
