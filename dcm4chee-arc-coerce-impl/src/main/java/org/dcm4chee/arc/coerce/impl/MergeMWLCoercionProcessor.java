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

package org.dcm4chee.arc.coerce.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.Cache;
import org.dcm4chee.arc.MergeMWLCache;
import org.dcm4chee.arc.MergeMWLQueryParam;
import org.dcm4chee.arc.coerce.CoercionProcessor;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion2;
import org.dcm4chee.arc.conf.MergeMWLMatchingKey;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.Templates;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2021
 */
@ApplicationScoped
@Named("merge-mwl")
public class MergeMWLCoercionProcessor implements CoercionProcessor {

    static final Logger LOG = LoggerFactory.getLogger(MergeMWLCoercionProcessor.class);

    @Inject
    private Device device;

    @Inject
    private MergeMWLCache mergeMWLCache;

    @Inject
    private QueryService queryService;

    @Inject
    private CFindSCU cfindscu;

    @Override
    public boolean coerce(ArchiveAttributeCoercion2 coercion,
                          String sopClassUID, String sendingHost, String sendingAET,
                          String receivingHost, String receivingAET,
                          Attributes attrs, Attributes modified)
            throws Exception {
        Attributes newAttrs = queryMWL(
                coercion.getRole() == TransferCapability.Role.SCU ? receivingAET : sendingAET,
                mergeMWLQueryParam(coercion, attrs),
                coercion.parseBooleanCoercionParam("filter-by-scu"),
                coercion.getSchemeSpecificPart(),
                coercion.parseBooleanCoercionParam("xsl-no-keyword"));
        if (newAttrs == null) {
            return false;
        }
        LOG.info("Coerce Request Attributes from matching MWL item coerced using {}", coercion);
        Attributes.unifyCharacterSets(attrs, newAttrs);
        if (modified != null) {
            attrs.update(Attributes.UpdatePolicy.OVERWRITE, newAttrs, modified);
        } else {
            attrs.addAll(newAttrs);
        }
        return true;
    }

    private static MergeMWLQueryParam mergeMWLQueryParam(ArchiveAttributeCoercion2 coercion, Attributes attrs) {
        MergeMWLMatchingKey mergeMWLMatchingKey = MergeMWLMatchingKey.valueOf(
                coercion.getCoercionParam("match-by", MergeMWLMatchingKey.ScheduledProcedureStepID.name()));
        String mwlSCP = coercion.getCoercionParam("mwl-scp", null);
        String localMwlWorklistLabels = coercion.getCoercionParam("local-mwl-worklist-label", null);
        String localMwlStatus = coercion.getCoercionParam("local-mwl-status", null);
        return MergeMWLQueryParam.valueOf(mwlSCP,
                StringUtils.split(localMwlWorklistLabels, '|'),
                SPSStatus.valuesOf(StringUtils.split(localMwlStatus, '|')),
                mergeMWLMatchingKey, attrs, coercion.getSchemeSpecificPart());
    }

    private Attributes queryMWL(String localAET, MergeMWLQueryParam queryParam, boolean filterbyscu,
                                String tplURI, boolean xslnokeyword)
            throws Exception {
        Cache.Entry<Attributes> entry = mergeMWLCache.getEntry(queryParam);
        if (entry != null)
            return entry.value();

        List<Attributes> mwlItems;
        LOG.info("Query for MWL Items with {}", queryParam);
        if (queryParam.mwlSCP == null) {
            mwlItems = queryService.queryMWL(queryParam);
            LOG.info("Found {} matching MWL Items in {}", mwlItems.size(), localAET);
        } else {
            mwlItems = cfindscu.findMWLItems(
                    device.getApplicationEntity(localAET, true), queryParam, Priority.NORMAL);
            if (filterbyscu) {
                mwlItems.removeIf(item -> !item.matches(
                        queryParam.setMatchingKeys(new Attributes()), false, false));
            }
            LOG.info("Found {} matching MWL Items in {}", mwlItems.size(), queryParam.mwlSCP);
        }
        if (mwlItems.isEmpty()) {
            mergeMWLCache.put(queryParam, null);
            return null;
        }
        Attributes result = null;
        Sequence reqAttrsSeq = null;
        Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(tplURI));
        Collections.sort(mwlItems, Comparator.comparing(MergeMWLCoercionProcessor::startDateTime).reversed());
        for (Attributes mwlItem : mwlItems) {
            Attributes attrs = SAXTransformer.transform(mwlItem, tpls, false, !xslnokeyword);
            if (reqAttrsSeq == null) {
                result = attrs;
                reqAttrsSeq = attrs.getSequence(Tag.RequestAttributesSequence);
            } else {
                reqAttrsSeq.add(new Attributes(attrs.getNestedDataset(Tag.RequestAttributesSequence)));
            }
        }
        mergeMWLCache.put(queryParam, result);
        return result;
    }

    private static Date startDateTime(Attributes mwlItem) {
        Attributes item;
        Date date;
        return (item = mwlItem.getNestedDataset(Tag.ScheduledProcedureStepSequence)) != null
                && (date = item.getDate(Tag.ScheduledProcedureStepStartDateAndTime)) != null
                ? date : new Date(0);
    }
}

