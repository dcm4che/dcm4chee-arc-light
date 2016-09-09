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

package org.dcm4chee.arc.wado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;

import javax.ws.rs.core.MediaType;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2016
 */
enum ObjectType {
    UncompressedSingleFrameImage(
            new MediaType[] {
                MediaTypes.IMAGE_JPEG_TYPE,
                MediaTypes.APPLICATION_DICOM_TYPE,
                MediaTypes.IMAGE_GIF_TYPE,
                MediaTypes.IMAGE_PNG_TYPE
            },
            new MediaType[] { MediaType.APPLICATION_OCTET_STREAM_TYPE }) {
        @Override
        public MediaType[] getPixelDataContentTypes(InstanceLocations inst) {
            return super.getBulkdataContentTypes(inst);
        }
    },
    CompressedSingleFrameImage(
            new MediaType[] {
                    MediaTypes.IMAGE_JPEG_TYPE,
                    MediaTypes.APPLICATION_DICOM_TYPE,
                    MediaTypes.IMAGE_GIF_TYPE,
                    MediaTypes.IMAGE_PNG_TYPE
            },
            null) {
        @Override
        public MediaType[] getPixelDataContentTypes(InstanceLocations inst) {
            return super.calcPixelDataContentTypes(inst);
        }
        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return super.calcPixelDataContentTypes(inst);
        }
    },
    UncompressedMultiFrameImage(
            new MediaType[] { MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.IMAGE_GIF_TYPE },
            new MediaType[] { MediaType.APPLICATION_OCTET_STREAM_TYPE }) {
        @Override
        public MediaType[] getPixelDataContentTypes(InstanceLocations inst) {
            return super.getBulkdataContentTypes(inst);
        }
    },
    CompressedMultiFrameImage(
            new MediaType[] { MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.IMAGE_GIF_TYPE },
            null) {
        @Override
        public MediaType[] getPixelDataContentTypes(InstanceLocations inst) {
            return super.calcPixelDataContentTypes(inst);
        }
        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return super.calcPixelDataContentTypes(inst);
        }
    },
    MPEG2Video(
            new MediaType[] { MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.VIDEO_MPEG_TYPE },
            new MediaType[] { MediaTypes.VIDEO_MPEG_TYPE }) {
        @Override
        public MediaType[] getPixelDataContentTypes(InstanceLocations inst) {
            return super.getBulkdataContentTypes(inst);
        }
    },
    MPEG4Video(
            new MediaType[] { MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.VIDEO_MP4_TYPE },
            new MediaType[] { MediaTypes.VIDEO_MP4_TYPE }) {
        @Override
        public MediaType[] getPixelDataContentTypes(InstanceLocations inst) {
            return super.getBulkdataContentTypes(inst);
        }
    },
    SRDocument(
            new MediaType[] {
                    MediaType.TEXT_HTML_TYPE,
                    MediaType.TEXT_PLAIN_TYPE,
                    MediaTypes.APPLICATION_DICOM_TYPE
            },
            null),
    EncapsulatedPDF(
            new MediaType[] { MediaTypes.APPLICATION_PDF_TYPE },
            new MediaType[] { MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.APPLICATION_PDF_TYPE }),
    EncapsulatedCDA(
            new MediaType[] { MediaType.TEXT_XML_TYPE },
            new MediaType[] { MediaTypes.APPLICATION_DICOM_TYPE, MediaType.TEXT_XML_TYPE }),
    Other(new MediaType[] { MediaTypes.APPLICATION_DICOM_TYPE }, null);

    private final MediaType[] mimeTypes;
    private final MediaType[] bulkdataContentTypes;

    ObjectType(MediaType[] mimeTypes, MediaType[] bulkdataContentTypes) {
        this.mimeTypes = mimeTypes;
        this.bulkdataContentTypes = bulkdataContentTypes;
    }

    public static ObjectType objectTypeOf(RetrieveContext ctx, InstanceLocations inst, String frameNumber) {
        ArchiveDeviceExtension arcDev = ctx.getArchiveAEExtension().getArchiveDeviceExtension();
        return arcDev.isWadoSupportedSRClass(inst.getSopClassUID())
                ? SRDocument
                : objectTypeOf(inst, frameNumber);
    }

    public static ObjectType objectTypeOf(InstanceLocations inst, String frameNumber) {
        String cuid = inst.getSopClassUID();
        String tsuid = inst.getLocations().get(0).getTransferSyntaxUID();
        Attributes attrs = inst.getAttributes();
        if (cuid.equals(UID.EncapsulatedPDFStorage))
            return EncapsulatedPDF;
        if (cuid.equals(UID.EncapsulatedCDAStorage))
            return EncapsulatedCDA;
        if (!attrs.contains(Tag.BitsAllocated) || cuid.equals(UID.RTDoseStorage))
            return Other;

        boolean multiframe = frameNumber == null && attrs.getInt(Tag.NumberOfFrames, 1) > 1;
        switch (tsuid) {
            case UID.MPEG2:
            case UID.MPEG2MainProfileHighLevel:
                return MPEG2Video;
            case UID.MPEG4AVCH264HighProfileLevel41:
            case UID.MPEG4AVCH264BDCompatibleHighProfileLevel41:
                return MPEG4Video;
            case UID.ImplicitVRLittleEndian:
            case UID.ExplicitVRLittleEndian:
                return multiframe ? UncompressedMultiFrameImage : UncompressedSingleFrameImage;
            default:
                return multiframe ? CompressedMultiFrameImage : CompressedSingleFrameImage;
        }
     }

    public MediaType getDefaultMimeType() {
        return mimeTypes[0];
    }

    public boolean isCompatibleMimeType(MediaType other) {
        for (MediaType type : mimeTypes) {
            if (type.isCompatible(other))
                return true;
        }
        return false;
    }

    public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
        return bulkdataContentTypes;
    }

    public MediaType[] getPixelDataContentTypes(InstanceLocations inst) {
        return null;
    }

    protected MediaType[] calcPixelDataContentTypes(InstanceLocations inst) {
        String tsuid = inst.getLocations().get(0).getTransferSyntaxUID();
        MediaType mediaType = MediaTypes.forTransferSyntax(tsuid);
        return new MediaType[] { mediaType, MediaType.APPLICATION_OCTET_STREAM_TYPE };
    }
}
