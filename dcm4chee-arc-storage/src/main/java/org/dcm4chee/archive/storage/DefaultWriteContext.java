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

package org.dcm4chee.archive.storage;

import org.dcm4che3.data.Attributes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public class DefaultWriteContext implements WriteContext {
    private final Storage storage;
    private final Attributes attrs;
    private String storagePath;
    private long size;
    private MessageDigest messageDigest;

    public DefaultWriteContext(Storage storage, Attributes attrs) {
        this.storage = storage;
        this.attrs = attrs;
    }

    @Override
    public Storage getStorage() {
        return storage;
    }

    @Override
    public Attributes getAttributes() {
        return attrs;
    }

    @Override
    public String getStoragePath() {
        return storagePath;
    }

    @Override
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public void incrementSize(long size) {
        this.size += size;
    }

    @Override
    public MessageDigest getMessageDigest() {
        return messageDigest;
    }

    @Override
    public void setMessageDigest(MessageDigest messageDigest) {
        this.messageDigest = messageDigest;
    }

    @Override
    public void setMessageDigest(String algorithm) {
        try {
            setMessageDigest(algorithm != null ? MessageDigest.getInstance(algorithm) : null);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm: " + algorithm);
        }
    }

    @Override
    public byte[] getDigest() {
        return messageDigest != null ? messageDigest.digest() : null;
    }
}
