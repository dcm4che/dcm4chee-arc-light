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

package org.dcm4chee.arc.rs.util;

import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.jboss.resteasy.util.MediaTypeHelper;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jboss.resteasy.util.MediaTypeHelper.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Oct 2019
 */

public class MediaTypeUtils {

    private static final MediaTypeComparator COMPARATOR = new MediaTypeComparator();

    public static List<MediaType> acceptableMediaTypesOf(HttpHeaders headers, List<String> acceptQueryParam) {
        List<MediaType> list = acceptQueryParam.stream()
                .flatMap(s -> Stream.of(StringUtils.split(s, ',')))
                .map(String::trim)
                .map(MediaType::valueOf)
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            List<String> vals = headers.getRequestHeader(HttpHeaders.ACCEPT);
            if (vals == null || vals.isEmpty()) {
                return Collections.singletonList(MediaType.WILDCARD_TYPE);
            }
            for (String v : vals) {
                StringTokenizer tokenizer = new StringTokenizer(v, ",");
                while (tokenizer.hasMoreElements()) {
                    String item = tokenizer.nextToken().trim();
                    list.add(MediaType.valueOf(item));
                }
            }
        }
        if (list.size() > 1)
            Collections.sort(list, COMPARATOR);
        return list;
    }

    public static String selectTransferSyntax(Collection<String> acceptable, String tsuid) {
        return acceptable.isEmpty() || acceptable.contains(tsuid)
                ? tsuid
                : acceptable.contains(UID.ExplicitVRLittleEndian)
                ? UID.ExplicitVRLittleEndian
                : UID.ImplicitVRLittleEndian;
    }

    private static class MediaTypeComparator implements Comparator<MediaType>
    {
        public int compare(MediaType mediaType2, MediaType mediaType)
        {
            float q = getQWithParamInfo(mediaType);
            boolean wasQ = q != 2.0f;
            if (q == 2.0f) q = 1.0f;

            float q2 = getQWithParamInfo(mediaType2);
            boolean wasQ2 = q2 != 2.0f;
            if (q2 == 2.0f) q2 = 1.0f;


            if (q < q2) return -1;
            if (q > q2) return 1;

            int cmp = compareWildcardType(mediaType2, mediaType);
            if (cmp != 0) return cmp;

            MediaType type2 = MediaTypes.getMultiPartRelatedType(mediaType2);
            MediaType type = MediaTypes.getMultiPartRelatedType(mediaType);
            if (type2 != null && type != null) {
                cmp = compareWildcardType(type2, type);
                if (cmp != 0) return cmp;
                cmp = disfavor(MediaType.APPLICATION_OCTET_STREAM_TYPE, type2, type);
            } else {
                cmp = disfavor(MediaType.APPLICATION_OCTET_STREAM_TYPE, mediaType2, mediaType);
            }
            if (cmp != 0) return cmp;

            Map<String, String> parameters = mediaType.getParameters();
            Map<String, String> parameters2 = mediaType2.getParameters();
            String transferSyntax = parameters != null ? parameters.get("transfer-syntax") : null;
            String transferSyntax2 = parameters2 != null ? parameters2.get("transfer-syntax") : null;
            if (transferSyntax != null && transferSyntax2 != null) {
                cmp = disfavor(TransferSyntaxType.NATIVE,
                        TransferSyntaxType.forUID(transferSyntax2),
                        TransferSyntaxType.forUID(transferSyntax));
                if (cmp != 0) return cmp;
            }

            int numNonQ = 0;
            if (parameters != null) {
                numNonQ = parameters.size();
                if (wasQ) numNonQ--;
            }

            int numNonQ2 = 0;
            if (parameters2 != null) {
                numNonQ2 = parameters2.size();
                if (wasQ2) numNonQ2--;
            }

            if (numNonQ < numNonQ2) return -1;
            if (numNonQ > numNonQ2) return 1;

            return 0;
        }

    }

    private static int disfavor(MediaType disfavorType, MediaType mediaType2, MediaType mediaType) {
        if (MediaTypes.equalsIgnoreParameters(mediaType, disfavorType)
            && !MediaTypes.equalsIgnoreParameters(mediaType2, disfavorType))
            return -1;
        if (!MediaTypes.equalsIgnoreParameters(mediaType, disfavorType)
            && MediaTypes.equalsIgnoreParameters(mediaType2, disfavorType))
            return 1;
        return 0;
    }

    private static int disfavor(TransferSyntaxType disfavorType, TransferSyntaxType type2, TransferSyntaxType type) {
        if (type == disfavorType && type2 != disfavorType) return -1;
        if (type != disfavorType && type2 == disfavorType) return 1;
        return 0;
    }

    private static int compareWildcardType(MediaType mediaType2, MediaType mediaType) {
        if (mediaType.isWildcardType() && !mediaType2.isWildcardType()) return -1;
        if (!mediaType.isWildcardType() && mediaType2.isWildcardType()) return 1;
        if (mediaType.isWildcardSubtype() && !mediaType2.isWildcardSubtype()) return -1;
        if (!mediaType.isWildcardSubtype() && mediaType2.isWildcardSubtype()) return 1;
        if (isComposite(mediaType.getSubtype()) && !isComposite(mediaType2.getSubtype()))
            return -1;
        if (!isComposite(mediaType.getSubtype()) && isComposite(mediaType2.getSubtype()))
            return 1;
        if (isCompositeWildcardSubtype(mediaType.getSubtype()) && !isCompositeWildcardSubtype(mediaType2.getSubtype()))
            return -1;
        if (!isCompositeWildcardSubtype(mediaType.getSubtype()) && isCompositeWildcardSubtype(mediaType2.getSubtype()))
            return 1;
        if (isWildcardCompositeSubtype(mediaType.getSubtype()) && !isWildcardCompositeSubtype(mediaType2.getSubtype()))
            return -1;
        if (!isWildcardCompositeSubtype(mediaType.getSubtype()) && isWildcardCompositeSubtype(mediaType2.getSubtype()))
            return 1;
        return 0;
    }
}
