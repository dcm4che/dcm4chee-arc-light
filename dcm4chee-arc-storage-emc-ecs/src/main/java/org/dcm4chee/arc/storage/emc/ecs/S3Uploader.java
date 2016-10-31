/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.storage.emc.ecs;

import com.emc.object.s3.S3Client;
import com.emc.object.s3.S3ObjectMetadata;
import com.emc.object.s3.bean.MultipartPartETag;
import com.emc.object.s3.request.CompleteMultipartUploadRequest;
import com.emc.object.s3.request.InitiateMultipartUploadRequest;
import com.emc.object.s3.request.PutObjectRequest;
import com.emc.object.s3.request.UploadPartRequest;
import org.dcm4chee.arc.storage.CacheInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2016
 */
class S3Uploader extends CacheInputStream implements Uploader {

    @Override
    public void upload(S3Client s3, InputStream in, String container, String storagePath) throws IOException {
        if (fillBuffers(in))
            uploadMultipleParts(s3, in, container, storagePath);
        else
            uploadSinglePart(s3, container, storagePath);
    }

    private void uploadSinglePart(S3Client s3, String container, String storagePath) {
        s3.putObject(new PutObjectRequest(container, storagePath, this)
                .withObjectMetadata(new S3ObjectMetadata().withContentLength(available())));
    }

    private void uploadMultipleParts(S3Client s3, InputStream in, String container, String storagePath)
            throws IOException {
        String uploadId = s3.initiateMultipartUpload(new InitiateMultipartUploadRequest(container, storagePath))
                .getUploadId();
        SortedSet<MultipartPartETag> parts = new TreeSet<>();
        int partNumber = 1;
        do {
            parts.add(s3.uploadPart(new UploadPartRequest(container,  storagePath, uploadId, partNumber, this)
                    .withContentLength(new Long(available()))));
            partNumber++;
        } while (fillBuffers(in));
        if (available() > 0) {
            parts.add(s3.uploadPart(
                    new UploadPartRequest(container,  storagePath, uploadId, partNumber, this)
                            .withContentLength(new Long(available()))));
        }
        s3.completeMultipartUpload(
                new CompleteMultipartUploadRequest(container, storagePath, uploadId).withParts(parts));
    }

}
