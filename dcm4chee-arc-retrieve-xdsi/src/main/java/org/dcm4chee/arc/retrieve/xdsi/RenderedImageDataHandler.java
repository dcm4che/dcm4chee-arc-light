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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.retrieve.xdsi;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.image.BufferedImageUtils;
import org.dcm4che3.image.PixelAspectRatio;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4che3.xdsi.RetrieveRenderedImagingDocumentSetRequestType.StudyRequest.SeriesRequest.RenderedDocumentRequest;

import javax.activation.DataHandler;
import javax.enterprise.event.Event;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2017
 */
public class RenderedImageDataHandler extends DataHandler {
    private final RetrieveContext ctx;
    private final InstanceLocations inst;
    private final RenderedDocumentRequest docReq;
    private final ImageReader imageReader;
    private final ImageWriter imageWriter;
    private Event<RetrieveContext> retrieveEnd;

    public RenderedImageDataHandler(RetrieveContext ctx, InstanceLocations inst, RenderedDocumentRequest docReq,
                                    ImageReader imageReader, ImageWriter imageWriter) {
        super(inst, MediaTypes.IMAGE_JPEG);
        this.ctx = ctx;
        this.inst = inst;
        this.docReq = docReq;
        this.imageReader = imageReader;
        this.imageWriter = imageWriter;
    }

    public void setRetrieveEnd(Event<RetrieveContext> retrieveEnd) {
        this.retrieveEnd = retrieveEnd;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        try (DicomInputStream dis = ctx.getRetrieveService().openDicomInputStream(ctx, inst)) {
            imageReader.setInput(dis);
            ImageOutputStream imageOut = new MemoryCacheImageOutputStream(out);
            imageWriter.setOutput(imageOut);
            BufferedImage bi = imageReader.read(parseInt(docReq.getFrameNumber(), 1) - 1, readParam());
            imageWriter.write(null, new IIOImage(adjust(bi), null, null), writeParam());
            imageOut.close();   // does not close out,
                                // marks imageOut as closed to prevent finalizer thread to invoke out.flush()
        }
        if (retrieveEnd != null) {
            imageWriter.dispose();
            imageReader.dispose();
            retrieveEnd.fire(ctx);
        }
    }

    private ImageWriteParam writeParam() {
        ImageWriteParam writeParam = imageWriter.getDefaultWriteParam();
        if (docReq.getImageQuality() != null) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(Integer.parseInt(docReq.getImageQuality()) / 100.f);
        }
        return writeParam;
    }

    private ImageReadParam readParam() {
        DicomImageReadParam readParam = new DicomImageReadParam();
        if (docReq.getWindowCenter() != null && docReq.getWindowWidth() != null) {
            readParam.setWindowCenter(Float.parseFloat(docReq.getWindowCenter()));
            readParam.setWindowWidth(Float.parseFloat(docReq.getWindowWidth()));
        }
        return readParam;
    }

    private BufferedImage adjust(BufferedImage bi) throws IOException {
        if (bi.getColorModel().getNumComponents() == 3)
            bi = BufferedImageUtils.convertToIntRGB(bi);
        return rescale(bi);
    }

    private BufferedImage rescale(BufferedImage bi) throws IOException {
        int r = parseInt(docReq.getRows(), 0);
        int c = parseInt(docReq.getColumns(), 0);
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

    private static int parseInt(String s, int defVal) {
        return s != null ? Integer.parseInt(s) : defVal;
    }

    private float getPixelAspectRatio() throws IOException {
        return PixelAspectRatio.forImage(getAttributes());
    }

    private Attributes getAttributes() throws IOException {
        return ((DicomMetaData) imageReader.getStreamMetadata()).getAttributes();
    }
}
