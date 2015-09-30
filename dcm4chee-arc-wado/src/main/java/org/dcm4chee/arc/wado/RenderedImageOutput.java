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

package org.dcm4chee.arc.wado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.image.BufferedImageUtils;
import org.dcm4che3.image.PixelAspectRatio;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public class RenderedImageOutput implements StreamingOutput {
    private static final float DEF_FRAME_TIME = 1000.f;
    private static final byte[] LOOP_FOREVER = { 1, 0, 0 };

    private final ImageReader reader;
    private final DicomImageReadParam readParam;
    private final int rows;
    private final int columns;
    private final int imageIndex;
    private final ImageWriter writer;
    private final ImageWriteParam writeParam;

    public RenderedImageOutput(ImageReader reader, DicomImageReadParam readParam, int rows, int columns,
                               int imageIndex, ImageWriter writer, ImageWriteParam writeParam) {
        this.reader = reader;
        this.readParam = readParam;
        this.rows = rows;
        this.columns = columns;
        this.imageIndex = imageIndex;
        this.writer = writer;
        this.writeParam = writeParam;
    }

    @Override
    public void write(OutputStream out) throws IOException, WebApplicationException {
        ImageOutputStream imageOut = new MemoryCacheImageOutputStream(out);
        try {
            writer.setOutput(imageOut);
            BufferedImage bi = null;
            if (imageIndex < 0) {
                IIOMetadata metadata = null;
                int numImages = reader.getNumImages(false);
                writer.prepareWriteSequence(null);
                for (int i = 0; i < numImages; i++) {
                    readParam.setDestination(bi);
                    bi = reader.read(i, readParam);
                    BufferedImage bi2 = adjust(bi);
                    if (metadata == null)
                        metadata = createAnimatedGIFMetadata(bi2, writeParam, frameTime());
                    writer.writeToSequence(
                            new IIOImage(bi2, null, metadata),
                            writeParam);
                    imageOut.flush();
                }
                writer.endWriteSequence();
            } else {
                bi = reader.read(imageIndex, readParam);
                writer.write(null, new IIOImage(adjust(bi), null, null), writeParam);
            }
        } finally {
            writer.dispose();
            reader.dispose();
            imageOut.close();
        }
    }

    private float frameTime() throws IOException {
        DicomMetaData metaData  = (DicomMetaData) reader.getStreamMetadata();
        Attributes attrs = metaData.getAttributes();
        return attrs.getFloat(Tag.FrameTime, DEF_FRAME_TIME);
    }

    private IIOMetadata createAnimatedGIFMetadata(BufferedImage bi, ImageWriteParam param, float frameTime)
            throws IOException {
        ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromRenderedImage(bi);
        IIOMetadata metadata = writer.getDefaultImageMetadata(imageType, param);
        String formatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);
        IIOMetadataNode graphicControlExt =
                (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
        graphicControlExt.setAttribute("delayTime", Integer.toString(Math.round(frameTime() / 10)));
        IIOMetadataNode appExts = new IIOMetadataNode("ApplicationExtensions");
        IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
        appExt.setAttribute("applicationID", "NETSCAPE");
        appExt.setAttribute("authenticationCode", "2.0");
        appExt.setUserObject(LOOP_FOREVER);
        appExts.appendChild(appExt);
        root.appendChild(appExts);
        metadata.setFromTree(formatName, root);
        return metadata;
    }

    private BufferedImage adjust(BufferedImage bi) throws IOException {
        if (bi.getColorModel().getNumComponents() == 3)
            bi = BufferedImageUtils.convertToIntRGB(bi);
        return rescale(bi);
    }

    private BufferedImage rescale(BufferedImage bi) throws IOException {
        int r = rows;
        int c = columns;
        float sy = getPixelAspectRatio();
        if (r == 0 && c == 0 && sy == 1f)
            return bi;

        float sx = 1f;
        if (r != 0 || c != 0) {
            if (r != 0 && c != 0)
                if (r * bi.getWidth() > c * bi.getHeight() * sy)
                    r = 0;
                else
                    c = 0;
            sx = r != 0 ? r / (bi.getHeight() * sy) : c / (float)bi.getWidth();
            sy *= sx;
        }
        AffineTransformOp op = new AffineTransformOp(
                AffineTransform.getScaleInstance(sx, sy),
                AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(bi, null);
    }

    private float getPixelAspectRatio() throws IOException {
        Attributes prAttrs = readParam.getPresentationState();
        return prAttrs != null ? PixelAspectRatio.forPresentationState(prAttrs)
                               : PixelAspectRatio.forImage(getAttributes());
    }

    private Attributes getAttributes() throws IOException {
        return ((DicomMetaData) reader.getStreamMetadata()).getAttributes();
    }
}
