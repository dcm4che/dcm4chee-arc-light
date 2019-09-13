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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.diff;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.util.QIDO;
import org.dcm4chee.arc.query.util.QueryAttributes;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2018
 */
public class DiffContext {

    private ApplicationEntity localAE;
    private ApplicationEntity primaryAE;
    private ApplicationEntity secondaryAE;
    private String queryString;
    private QueryAttributes queryAttributes;
    private Duration splitStudyDateRange;
    private int priority;
    private boolean forceQueryByStudyUID;
    private boolean fuzzymatching;
    private boolean checkMissing;
    private boolean checkDifferent;
    private int[] compareKeys;
    private int[] returnKeys;
    private String compareFields;
    private String batchID;
    private HttpServletRequestInfo httpServletRequestInfo;

    public ApplicationEntity getLocalAE() {
        return localAE;
    }

    public DiffContext setLocalAE(ApplicationEntity localAE) {
        this.localAE = localAE;
        return this;
    }

    public ApplicationEntity getPrimaryAE() {
        return primaryAE;
    }

    public DiffContext setPrimaryAE(ApplicationEntity primaryAE) {
        this.primaryAE = primaryAE;
        return this;
    }

    public ApplicationEntity getSecondaryAE() {
        return secondaryAE;
    }

    public DiffContext setSecondaryAE(ApplicationEntity secondaryAE) {
        this.secondaryAE = secondaryAE;
        return this;
    }

    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    public DiffContext setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
        return this;
    }

    public String getQueryString() {
        return queryString;
    }

    public DiffContext setQueryString(String queryString) {
        return setQueryString(queryString, parseQueryString(queryString));
    }

    public DiffContext setQueryString(String queryString, MultivaluedMap<String, String> queryParameters) {
        this.queryString = queryString;
        this.queryAttributes = new QueryAttributes(queryParameters, null);
        this.forceQueryByStudyUID = parseBoolean(queryParameters.getFirst("ForceQueryByStudyUID"), false);
        this.splitStudyDateRange = parseDuration(queryParameters.getFirst("SplitStudyDateRange"));
        this.compareKeys = parseComparefields(queryParameters.get("comparefield"));
        this.priority = parseInt(queryParameters.getFirst("priority"), 0);
        this.fuzzymatching = parseBoolean(queryParameters.getFirst("fuzzymatching"), false);
        this.checkDifferent = parseBoolean(queryParameters.getFirst("different"), true);
        this.checkMissing = parseBoolean(queryParameters.getFirst("missing"), false);
        this.batchID = queryParameters.getFirst("batchID");
        return this;
    }

    public Attributes getQueryKeys() {
        return queryAttributes.getQueryKeys();
    }

    public boolean isForceQueryByStudyUID() {
        return forceQueryByStudyUID;
    }

    public Duration getSplitStudyDateRange() {
        return splitStudyDateRange;
    }

    public int[] getCompareKeys() {
        return compareKeys;
    }

    public int[] getReturnKeys() {
        return returnKeys;
    }

    public boolean isFuzzymatching() {
        return fuzzymatching;
    }

    public int priority() {
        return priority;
    }

    public boolean isCheckDifferent() {
        return checkDifferent;
    }

    public boolean isCheckMissing() {
        return checkMissing;
    }

    public String getCompareFields() {
        return compareFields;
    }

    public String getBatchID() {
        return batchID;
    }

    public boolean supportSorting() {
        if (compareKeys == null)
            throw new IllegalStateException("compareKeys not initialized");

        queryAttributes.addReturnTags(QIDO.STUDY.includetags);
        if (compareKeys != QIDO.STUDY.includetags)
            queryAttributes.addReturnTags(compareKeys);
        if (queryAttributes.isIncludeAll()) {
            ArchiveDeviceExtension arcdev = arcdev();
            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Patient).getSelection(false));
            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Study).getSelection(false));
        }
        Attributes keys = queryAttributes.getQueryKeys();
        returnKeys = keys.tags();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        if (hasArchiveAEExtension(primaryAE) && hasArchiveAEExtension(secondaryAE)) {
            Attributes item = new Attributes(1);
            item.setInt(Tag.SelectorAttribute,  VR.AT, Tag.StudyInstanceUID);
            keys.newSequence(Tag.SortingOperationsSequence, 1).add(item);
            return true;
        }
        return false;
    }

    private static boolean parseBoolean(String s, boolean defval) {
        return s != null ? Boolean.parseBoolean(s) : defval;
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private static Duration parseDuration(String s) {
        return s != null ? Duration.valueOf(s) : null;
    }

    private static boolean hasArchiveAEExtension(ApplicationEntity ae) {
        return ae.getAEExtension(ArchiveAEExtension.class) != null;
    }

    private int[] parseComparefields(List<String> comparefields) {
        if (comparefields == null || comparefields.isEmpty())
            return QIDO.STUDY.includetags;

        int size = comparefields.size();
        if (size == 1) {
            Map<String, AttributeSet> attributeSetMap = arcdev().getAttributeSet(AttributeSet.Type.DIFF_RS);
            AttributeSet attributeSet = attributeSetMap.get(comparefields.get(0));
            if (attributeSet != null) {
                compareFields = attributeSet.getID();
                return attributeSet.getSelection();
            }
        }
        int[] compareKeys = new int[size];
        for (int i = 0; i < size; i++) {
            try {
                compareKeys[i] = TagUtils.forName(comparefields.get(i));
            } catch (IllegalArgumentException e2) {
                throw new IllegalArgumentException("comparefield=" + comparefields.get(i));
            }
        }
        return compareKeys;
    }

    private ArchiveDeviceExtension arcdev() {
        return localAE.getDevice().getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    private static MultivaluedMap<String, String> parseQueryString(String queryString) {
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
        String[] params = queryString.split("&");
        for (String param : params) {
            if (param.indexOf('=') >= 0) {
                String[] nv = param.split("=", 2);
                try {
                    String name = URLDecoder.decode(nv[0], "UTF-8");
                    queryParameters.add(name, nv.length > 1 ? URLDecoder.decode(nv[1], "UTF-8") : "");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    String name = URLDecoder.decode(param, "UTF-8");
                    queryParameters.add(name, "");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return queryParameters;
    }
}
