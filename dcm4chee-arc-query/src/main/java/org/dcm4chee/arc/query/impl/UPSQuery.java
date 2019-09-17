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
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;

import javax.naming.OperationNotSupportedException;
import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
public class UPSQuery extends AbstractQuery {
    private Root<Workitem> workitem;
    private Join<Workitem, Patient> patient;
    private Path<byte[]> patientAttrBlob;
    private Path<byte[]> upsAttrBlob;

    UPSQuery(QueryContext context, EntityManager em) {
        super(context, em);
    }

    @Override
    protected CriteriaQuery<Tuple> multiselect() {
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        this.workitem = q.from(Workitem.class);
        this.patient = workitem.join(Workitem_.patient);
        return order(restrict(q, patient, workitem)).multiselect(
                workitem.get(Workitem_.sopInstanceUID),
                workitem.get(Workitem_.updatedTime),
                patientAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes),
                upsAttrBlob = workitem.join(Workitem_.attributesBlob).get(AttributesBlob_.encodedAttributes));
    }

    @Override
    protected CriteriaQuery<Long> count() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Workitem> workitem = q.from(Workitem.class);
        Join<Workitem, Patient> patient = workitem.join(Workitem_.patient);
        return restrict(q, patient, workitem).select(cb.count(workitem));
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Attributes upsAttrs = AttributesBlob.decodeAttributes(results.get(upsAttrBlob), null);
        Attributes patAttrs = AttributesBlob.decodeAttributes(results.get(patientAttrBlob), null);
        Attributes.unifyCharacterSets(patAttrs, upsAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + upsAttrs.size() + 3);
        attrs.addAll(patAttrs);
        attrs.addAll(upsAttrs);
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.UnifiedProcedureStepPushSOPClass);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, results.get(workitem.get(Workitem_.sopInstanceUID)));
        attrs.setDate(Tag.ScheduledProcedureStepModificationDateTime, VR.DT,
                results.get(workitem.get(Workitem_.updatedTime)));
        return attrs;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }

    private CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null)
            q.orderBy(builder.orderWorkitems(patient, workitem, context.getOrderByTags()));
        return q;
    }

    private <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Join<Workitem, Patient> patient, Root<Workitem> workitem) {
        List<Predicate> predicates = builder.workitemPredicates(q, patient, workitem,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }
}
