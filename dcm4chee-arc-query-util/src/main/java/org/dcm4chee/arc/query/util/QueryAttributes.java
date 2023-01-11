/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2019
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.query.util;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.conf.AttributesBuilder;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2017
 */
public class QueryAttributes {
    private static final ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

    private final Attributes keys = new Attributes();
    private final AttributesBuilder builder = new AttributesBuilder(keys);
    private boolean includeAll;

    private List<int[]> modified = new ArrayList(2);

    private final ArrayList<OrderByTag> orderByTags = new ArrayList<>();

    public QueryAttributes(UriInfo info, Map<String, AttributeSet> attributeSetMap) {
        this(splitAndDecode(info.getQueryParameters(false)), attributeSetMap);
    }
    public static MultivaluedMap<String, String> parseQueryString(String queryString) {
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

    private static MultivaluedMap<String, String> splitAndDecode(MultivaluedMap<String, String> queryParameters) {
        MultivaluedHashMap<String, String> map = new MultivaluedHashMap<>();
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet())
            for (String values : entry.getValue())
                for (String value : StringUtils.split(values, ','))
                    map.add(entry.getKey(), decodeURL(value));
        return map;
    }

    private static String decodeURL(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public QueryAttributes(MultivaluedMap<String, String> map, Map<String, AttributeSet> attributeSetMap) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            switch (key) {
                case "includefield":
                    addIncludeTag(entry.getValue(), attributeSetMap);
                    break;
                case "orderby":
                    addOrderByTag(entry.getValue());
                    break;
                case "accept":
                case "access_token":
                case "charset":
                case "comparefield":
                case "count":
                case "deletionlock":
                case "template":
                case "workitem":
                case "different":
                case "missing":
                case "offset":
                case "limit":
                case "priority":
                case "onlyWithStudies":
                case "fuzzymatching":
                case "retrievefailed":
                case "compressionfailed":
                case "delete":
                case "filterbyscu":
                case "patientVerificationStatus":
                case "metadataUpdateFailed":
                case "storageVerificationFailed":
                case "storageVerificationPolicy":
                case "storageVerificationUpdateLocationStatus":
                case "storageVerificationStorageID":
                case "incomplete":
                case "ExternalRetrieveAET":
                case "ExternalRetrieveAET!":
                case "batchID":
                case "scheduledTime":
                case "dicomDeviceName":
                case "queue":
                case "dcmQueueName":
                case "SplitStudyDateRange":
                case "ForceQueryByStudyUID":
                case "includedefaults":
                case "ExpirationDate":
                case "storageID":
                case "storageClustered":
                case "storageExported":
                case "allOfModalitiesInStudy":
                case "StudySizeInKB":
                case "ExpirationState":
                case "upsLabel":
                case "upsScheduledTime":
                case "readPixelData":
                case "updatePolicy":
                case "reasonForModification":
                case "sourceOfPreviousValues":
                case "csvPatientID":
                case "test":
                case "requested":
                case "merged":
                case "ExporterID":
                case "FreezeExpirationDate":
                case "allmodified":
                case "irwf":
                    break;
                case "SendingApplicationEntityTitleOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries, VR.AE,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "ReceivingApplicationEntityTitleOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.ReceivingApplicationEntityTitleOfSeries, VR.AE,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "SendingPresentationAddressOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.SendingPresentationAddressOfSeries, VR.UR,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "ReceivingPresentationAddressOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.ReceivingPresentationAddressOfSeries, VR.UR,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "SendingHL7ApplicationOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.SendingHL7ApplicationOfSeries, VR.LO,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "SendingHL7FacilityOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.SendingHL7FacilityOfSeries, VR.LO,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "ReceivingHL7ApplicationOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.ReceivingHL7ApplicationOfSeries, VR.LO,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "ReceivingHL7FacilityOfSeries":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.ReceivingHL7FacilityOfSeries, VR.LO,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "StudyReceiveDateTime":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.StudyReceiveDateTime, VR.DT,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "StudyAccessDateTime":
                    keys.setString(PrivateTag.PrivateCreator, PrivateTag.StudyAccessDateTime, VR.DT,
                            entry.getValue().toArray(StringUtils.EMPTY_STRING));
                    break;
                case "modified":
                    addModified(entry.getValue());
                    break;
                default:
                    addQueryKey(key, entry.getValue());
                    break;
            }
        }
    }

    private void addModified(List<String> values) {
        for (String s : values) {
            for (String value : StringUtils.split(s, ',')) {
                try {
                    modified.add(TagUtils.parseTagPath(value));
                } catch (IllegalArgumentException e2) {
                    throw new IllegalArgumentException("modified=" + s);
                }
            }
        }
    }

    private void addIncludeTag(List<String> includefields, Map<String, AttributeSet> attributeSetMap) {
        for (String s : includefields) {
            if (s.equals("all")) {
                includeAll = true;
                break;
            }
            for (String field : StringUtils.split(s, ','))
                if (!includeAttributeSet(s, attributeSetMap))
                    try {
                        int[] tagPath = TagUtils.parseTagPath(field);
                        builder.setNullIfAbsent(tagPath);
                    } catch (IllegalArgumentException e2) {
                        throw new IllegalArgumentException("includefield=" + s);
                    }
        }
    }

    private boolean includeAttributeSet(String includefield, Map<String, AttributeSet> attributeSetMap) {
        if (attributeSetMap != null) {
            AttributeSet attributeSet = attributeSetMap.get(includefield);
            if (attributeSet != null) {
                for (int tag : attributeSet.getSelection())
                    builder.setNullIfAbsent(tag);
                return true;
            }
        }
        return false;
    }

    public void addReturnTags(int... tags) {
        for (int tag : tags)
            builder.setNullIfAbsent(tag);
    }

    private void addOrderByTag(List<String> orderby) {
        for (String s : orderby) {
            try {
                for (String field : StringUtils.split(s, ',')) {
                    boolean desc = field.charAt(0) == '-';
                    int tags[] = TagUtils.parseTagPath(desc ? field.substring(1) : field);
                    int tag = tags[tags.length - 1];
                    orderByTags.add(desc ? OrderByTag.desc(tag) : OrderByTag.asc(tag));
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("orderby=" + s);
            }
        }
    }

    public boolean isIncludeAll() {
        return includeAll;
    }

    public boolean isIncludePrivate() {
        return includeAll || keys.contains(0x77770010);
    }

    public Attributes getQueryKeys() {
        return keys;
    }

    public Attributes getReturnKeys(int[] includetags) {
        Attributes returnKeys = new Attributes(keys.size() + 4 + includetags.length);
        returnKeys.addAll(keys);
        returnKeys.setNull(Tag.SpecificCharacterSet, VR.CS);
        returnKeys.setNull(Tag.RetrieveAETitle, VR.AE);
        returnKeys.setNull(Tag.InstanceAvailability, VR.CS);
        returnKeys.setNull(Tag.TimezoneOffsetFromUTC, VR.SH);
        for (int tag : includetags)
            returnKeys.setNull(tag, DICT.vrOf(tag));
        return returnKeys;
    }
    
    public ArrayList<OrderByTag> getOrderByTags() {
        return orderByTags;
    }

    private void addQueryKey(String attrPath, List<String> values) {
        try {
            builder.setString(TagUtils.parseTagPath(attrPath), values.toArray(new String[values.size()]));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(attrPath + "=" + values.get(0));
        }
    }

    public List<int[]> getModified() {
        return modified;
    }

}
