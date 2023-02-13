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

package org.dcm4chee.arc.storage.cloud;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.dcm4chee.arc.storage.CacheInputStream;
import org.jclouds.azureblob.blobstore.AzureBlobStore;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.MultipartPart;
import org.jclouds.blobstore.domain.MultipartUpload;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.InputStreamPayload;

/**
 * @author Daniil Trishkin <kernel.pryanic@protonmail.com>
 * @since Feb 2023
 */
class AzureBlobUploader extends CacheInputStream implements Uploader {
    @Override
    public void upload(BlobStoreContext context, InputStream in, long length, BlobStore blobStore,
            String container, String storagePath) throws IOException {
        if (fillBuffers(in))
            uploadMultipleParts(context, in, container, storagePath);
        else
            uploadSinglePart(blobStore, container, storagePath);
    }

    private void uploadSinglePart(BlobStore blobStore, String container, String storagePath) {
        Blob blob = blobStore.blobBuilder(storagePath).payload(createPayload()).build();
        blobStore.putBlob(container, blob);
    }

    private Payload createPayload() {
        Payload payload = new InputStreamPayload(this);
        payload.getContentMetadata().setContentLength(Long.valueOf(available()));
        return payload;
    }

    private void uploadMultipleParts(BlobStoreContext context, InputStream in, String container, String storagePath)
            throws IOException {
        AzureBlobStore blobStore = (AzureBlobStore) context.getBlobStore();
        Payload payload = createPayload();
        Blob blob = blobStore.blobBuilder(storagePath).payload(payload).build();
        MultipartUpload mpu = blobStore.initiateMultipartUpload(container, blob.getMetadata(), null);
        List<MultipartPart> parts = new ArrayList<MultipartPart>();
        int partNumber = 1;
        do {
            parts.add(blobStore.uploadMultipartPart(mpu, partNumber, payload));
            partNumber++;
        } while (fillBuffers(in));
        if (available() > 0)
            parts.add(blobStore.uploadMultipartPart(mpu, partNumber, payload));
        blobStore.completeMultipartUpload(mpu, parts);
    }
}
