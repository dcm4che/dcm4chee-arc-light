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
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.InstanceLocations;

import javax.ws.rs.core.MediaType;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2016
 */
enum ObjectType {
    UncompressedSingleFrameImage(MediaTypes.IMAGE_JPEG_TYPE, true, false) {
        @Override
        public Optional<MediaType> getCompatibleMimeType(MediaType other) {
            return findCompatibleSingleFrameMimeType(other);
        }

        @Override
        public MediaType[] getRenderedContentTypes() {
            return ObjectType.renderedSingleFrameMediaTypes();
        }

        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return octetStreamMediaType();
        }
    },
    CompressedSingleFrameImage(MediaTypes.IMAGE_JPEG_TYPE, true, false) {
        @Override
        public Optional<MediaType> getCompatibleMimeType(MediaType other) {
            return findCompatibleSingleFrameMimeType(other);
        }

        @Override
        public MediaType[] getRenderedContentTypes() {
            return renderedSingleFrameMediaTypes();
        }

        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return calcPixelDataContentTypes(inst);
        }
    },
    UncompressedMultiFrameImage(MediaTypes.APPLICATION_DICOM_TYPE, true, false) {
        @Override
        public Optional<MediaType> getCompatibleMimeType(MediaType other) {
            return findCompatibleMultiFrameMimeType(other);
        }

        @Override
        public MediaType[] getRenderedContentTypes() {
            return renderedMultiFrameMediaTypes();
        }

        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return octetStreamMediaType();
        }
    },
    CompressedMultiFrameImage(MediaTypes.APPLICATION_DICOM_TYPE, true, false) {
        @Override
        public Optional<MediaType> getCompatibleMimeType(MediaType other) {
            return findCompatibleMultiFrameMimeType(other);
        }

        @Override
        public MediaType[] getRenderedContentTypes() {
            return renderedMultiFrameMediaTypes();
        }

        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return calcPixelDataContentTypes(inst);
        }
    },
    MPEG2Video(MediaTypes.VIDEO_MPEG_TYPE, false, true),
    MPEG4Video(MediaTypes.VIDEO_MP4_TYPE, false, true),
    SRDocument(MediaType.TEXT_HTML_TYPE, false, false) {
        @Override
        public Optional<MediaType> getCompatibleMimeType(MediaType other) {
            return findCompatibleSRMimeType(other);
        }

        @Override
        public MediaType[] getRenderedContentTypes() {
            return renderedSRMediaTypes();
        }

        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return null;
        }
    },
    EncapsulatedPDF(MediaTypes.APPLICATION_PDF_TYPE, false, false),
    EncapsulatedCDA(MediaType.TEXT_XML_TYPE, false, false){
        @Override
        public MediaType[] getRenderedContentTypes() {
            return null;
        }
    },
    EncapsulatedSTL(MediaTypes.MODEL_STL_TYPE, false, false){
        @Override
        public MediaType[] getRenderedContentTypes() {
            return null;
        }
    },
    Other(MediaTypes.APPLICATION_DICOM_TYPE, false, false){
        @Override
        public MediaType[] getRenderedContentTypes() {
            return null;
        }

        @Override
        public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
            return null;
        }
    };

    private final MediaType defaultMimeType;
    private final boolean image;
    private final boolean video;

    ObjectType(MediaType defaultMimeType, boolean image, boolean video) {
        this.defaultMimeType = defaultMimeType;
        this.image = image;
        this.video = video;
    }

    public static ObjectType objectTypeOf(RetrieveContext ctx, InstanceLocations inst, int frame) {
        if (inst.isImage()) {
            switch (inst.getLocations().get(0).getTransferSyntaxUID()) {
                case UID.MPEG2:
                case UID.MPEG2MainProfileHighLevel:
                    return MPEG2Video;
                case UID.MPEG4AVCH264HighProfileLevel41:
                case UID.MPEG4AVCH264BDCompatibleHighProfileLevel41:
                case UID.MPEG4AVCH264HighProfileLevel42For2DVideo:
                case UID.MPEG4AVCH264HighProfileLevel42For3DVideo:
                case UID.MPEG4AVCH264StereoHighProfileLevel42:
                case UID.HEVCH265MainProfileLevel51:
                case UID.HEVCH265Main10ProfileLevel51:
                    return MPEG4Video;
                case UID.ImplicitVRLittleEndian:
                case UID.ExplicitVRLittleEndian:
                    return frame <= 0 && inst.isMultiframe()
                            ? UncompressedMultiFrameImage
                            : UncompressedSingleFrameImage;
                default:
                    return frame <= 0 && inst.isMultiframe()
                            ? CompressedMultiFrameImage
                            : CompressedSingleFrameImage;
            }
        }
        switch (inst.getSopClassUID()) {
            case UID.EncapsulatedPDFStorage:
                return EncapsulatedPDF;
            case UID.EncapsulatedCDAStorage:
                return EncapsulatedCDA;
            case UID.EncapsulatedSTLStorage:
                return EncapsulatedSTL;
        }
        ArchiveDeviceExtension arcDev = ctx.getArchiveAEExtension().getArchiveDeviceExtension();
        return arcDev.isWadoSupportedSRClass(inst.getSopClassUID())
                ? SRDocument
                : Other;
    }

    public MediaType getDefaultMimeType() {
        return defaultMimeType;
    }

    public Optional<MediaType> getCompatibleMimeType(MediaType other) {
        return findCompatibleMimeType(other, defaultMimeType, MediaTypes.APPLICATION_DICOM_TYPE);
    }

    private static MediaType[] octetStreamMediaType() {
        return new MediaType[]{MediaType.APPLICATION_OCTET_STREAM_TYPE};
    }

    private static MediaType[] renderedSingleFrameMediaTypes() {
        return new MediaType[]{MediaTypes.IMAGE_JPEG_TYPE, MediaTypes.IMAGE_GIF_TYPE, MediaTypes.IMAGE_PNG_TYPE};
    }

    private static MediaType[] renderedMultiFrameMediaTypes() {
        return new MediaType[]{MediaTypes.IMAGE_GIF_TYPE};
    }

    private static MediaType[] renderedSRMediaTypes() {
        return new MediaType[]{MediaType.TEXT_HTML_TYPE, MediaType.TEXT_PLAIN_TYPE};
    }

    private static Optional<MediaType> findCompatibleSingleFrameMimeType(MediaType other) {
        return findCompatibleMimeType(other,
                MediaTypes.IMAGE_JPEG_TYPE,
                MediaTypes.APPLICATION_DICOM_TYPE,
                MediaTypes.IMAGE_GIF_TYPE,
                MediaTypes.IMAGE_PNG_TYPE);
    }

    private static Optional<MediaType> findCompatibleMultiFrameMimeType(MediaType other) {
        return findCompatibleMimeType(other,
                MediaTypes.APPLICATION_DICOM_TYPE,
                MediaTypes.IMAGE_GIF_TYPE);
    }

    private static Optional<MediaType> findCompatibleSRMimeType(MediaType other) {
        return findCompatibleMimeType(other,
                MediaType.TEXT_HTML_TYPE,
                MediaType.TEXT_PLAIN_TYPE,
                MediaTypes.APPLICATION_DICOM_TYPE);
    }

    private static Optional<MediaType> findCompatibleMimeType(MediaType other, MediaType... mimeTypes) {
        return Stream.of(mimeTypes).filter(other::isCompatible).findFirst();
    }

    public MediaType[] getRenderedContentTypes() {
        return new MediaType[]{defaultMimeType};
    }

    public MediaType[] getBulkdataContentTypes(InstanceLocations inst) {
        return new MediaType[]{defaultMimeType};
    }

    public boolean isImage() {
        return image;
    }

    public boolean isVideo() {
        return video;
    }

    private static MediaType[] calcPixelDataContentTypes(InstanceLocations inst) {
        String tsuid = inst.getLocations().get(0).getTransferSyntaxUID();
        MediaType mediaType = MediaTypes.forTransferSyntax(tsuid);
        return new MediaType[] {mediaType, MediaType.APPLICATION_OCTET_STREAM_TYPE};
    }
}
