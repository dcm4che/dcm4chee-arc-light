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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

import javax.persistence.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2016
 */
@Entity
@Table(name = "uidmap")
public class UIDMap {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "uidmap")
    private byte[] encodedMap;

    @Transient
    private Map<String,String> cachedMap;

    public UIDMap() {}

    public UIDMap(long pk, byte[] encodedMap) {
        this.pk = pk;
        this.encodedMap = encodedMap;
    }

    @Override
    public String toString() {
        return "UIDMap[pk=" + pk + "]";
    }

    public long getPk() {
        return pk;
    }

    public Map<String,String> getUIDMap() {
        if (cachedMap == null)
            cachedMap = decodeUIDMap(encodedMap);
        return cachedMap;
    }

    public void setUIDMap(Map<String,String> uidmap) {
        encodedMap = encodeUIDMap(cachedMap = uidmap);
    }

    public byte[] getEncodedMap() {
        return encodedMap;
    }

    public static byte[] encodeUIDMap(Map<String, String> uidmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        try (PrintStream ps = new PrintStream(out)) {
            for (Map.Entry<String, String> entry : uidmap.entrySet())
                ps.append(entry.getKey()).append('=').append(entry.getValue()).println();
        }
        return out.toByteArray();
    }

    public static Map<String,String> decodeUIDMap(byte[] b) {
        Map<String,String> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(b)));
        String line;
        int delimPos;
        try {
            while ((line = reader.readLine()) != null) {
                delimPos = line.indexOf('=');
                result.put(line.substring(0, delimPos), line.substring(delimPos+1));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
