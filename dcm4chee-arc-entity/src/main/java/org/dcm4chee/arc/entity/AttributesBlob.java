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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;

import javax.persistence.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
@Entity
@Table(name = "dicomattrs")
public class AttributesBlob {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    
    @Basic(optional = false)
    @Column(name = "attrs")
    private byte[] encodedAttributes;
    
    @Transient
    private Attributes cachedAttributes;

    public AttributesBlob(Attributes attrs) {
        setAttributes(attrs);
    }
    
    protected AttributesBlob() {}

    @Override
    public String toString() {
        return "AttributesBlob[pk=" + pk + "]";
    }

    public long getPk() {
        return pk;
    }

    public Attributes getAttributes() throws BlobCorruptedException {
        if (cachedAttributes == null)
            cachedAttributes = AttributesBlob.decodeAttributes(encodedAttributes, null);
        return cachedAttributes;
    }

    public void setAttributes(Attributes attrs) {
        cachedAttributes = new Attributes(attrs);
        cachedAttributes.removeAllBulkData();
        encodedAttributes = AttributesBlob.encodeAttributes(cachedAttributes);
    }

    public byte[] getEncodedAttributes() {
        return encodedAttributes;
    }

    public static byte[] encodeAttributes(Attributes attrs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        try {
            DicomOutputStream dos = new DicomOutputStream(out, UID.ExplicitVRLittleEndian);
            dos.writeDataset(null, attrs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    public static Attributes decodeAttributes(byte[] b, Attributes result) {
        if (b == null || b.length == 0)
            return result != null ? result : new Attributes(0);

        if (result == null)
            result = new Attributes();
        ByteArrayInputStream is = new ByteArrayInputStream(b);
        try {
            DicomInputStream dis = new DicomInputStream(is);
            dis.readFileMetaInformation();
            dis.readAttributes(result, -1, -1);
            return result;
        } catch (IOException e) {
            throw new BlobCorruptedException(e);
        }
    }
}
