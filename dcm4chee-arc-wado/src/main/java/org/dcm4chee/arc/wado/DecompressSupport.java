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
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.imageio.codec.jpeg.PatchJPEGLSImageInputStream;
import org.dcm4che3.imageio.stream.EncapsulatedPixelDataImageInputStream;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2016
 */
class DecompressSupport implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DecompressSupport.class);

    private final RetrieveContext ctx;
    private final InstanceLocations inst;
    private ImageReaderFactory.ImageReaderParam decompressorParam;
    private ImageReader decompressor;
    private ImageReadParam decompressParam;
    private BufferedImage bi;
    private DicomInputStream dis;
    protected EncapsulatedPixelDataImageInputStream encapsulatedPixelData;

    DecompressSupport(RetrieveContext ctx, InstanceLocations inst) {
        this.ctx = ctx;
        this.inst = inst;
    }

    protected void initEncapsulatedPixelData() throws IOException {
        RetrieveService service = ctx.getRetrieveService();
        dis = service.openDicomInputStream(ctx, inst);
        Attributes attrs = dis.readDataset(-1, Tag.PixelData);
        if (dis.tag() != Tag.PixelData || dis.length() != -1)
            throw new IOException("No or incorrect encapsulated compressed pixel data in requested object");

        ImageDescriptor imageDescriptor = new ImageDescriptor(attrs);
        String tsuid = dis.getTransferSyntax();
        TransferSyntaxType tsType = TransferSyntaxType.forUID(tsuid);
        encapsulatedPixelData = new EncapsulatedPixelDataImageInputStream(dis, imageDescriptor, tsType);
        initDecompressor(tsuid, tsType, imageDescriptor);
        if (tsType == TransferSyntaxType.RLE)
            initBufferedImage(imageDescriptor);
    }

    @Override
    public void close() {
        SafeClose.close(encapsulatedPixelData);
        encapsulatedPixelData = null;
        SafeClose.close(dis);
        dis = null;
        if (decompressor != null) {
            decompressor.dispose();
            decompressor = null;
        }
    }

    private void initDecompressor(String tsuid, TransferSyntaxType tsType, ImageDescriptor imageDescriptor) {
        decompressorParam = ImageReaderFactory.getImageReaderParam(tsuid);
        if (decompressorParam == null)
            throw new UnsupportedOperationException("Unsupported Transfer Syntax: " + tsuid);

        this.decompressor = ImageReaderFactory.getImageReader(decompressorParam);
        this.decompressParam = decompressor.getDefaultReadParam();
    }

    private void initBufferedImage(ImageDescriptor imageDescriptor) {
        int rows = imageDescriptor.getRows();
        int cols = imageDescriptor.getColumns();
        int samples = imageDescriptor.getSamples();
        int bitsAllocated = imageDescriptor.getBitsAllocated();
        int bitsStored = imageDescriptor.getBitsStored();
        boolean signed = imageDescriptor.isSigned();
        int dataType = bitsAllocated > 8
                ? (signed ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT)
                : DataBuffer.TYPE_BYTE;
        ComponentColorModel cm = samples == 1
                ? new ComponentColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_GRAY),
                    new int[] { bitsStored },
                    false, // hasAlpha
                    false, // isAlphaPremultiplied,
                    Transparency.OPAQUE,
                    dataType)
                :  new ComponentColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_sRGB),
                    new int[] { bitsStored, bitsStored, bitsStored },
                    false, // hasAlpha
                    false, // isAlphaPremultiplied,
                    Transparency.OPAQUE,
                    dataType);

        SampleModel sm = new BandedSampleModel(dataType, cols, rows, samples);
        WritableRaster raster = Raster.createWritableRaster(sm, null);
        bi = new BufferedImage(cm, raster, false, null);
    }

    protected BufferedImage decompressFrame(int frameIndex) throws IOException {
        if (encapsulatedPixelData.isEndOfStream())
            throw new IOException("Number of data fragments not sufficient for number of frames in requested object");

        decompressor.setInput(decompressorParam.patchJPEGLS != null
                ? new PatchJPEGLSImageInputStream(encapsulatedPixelData, decompressorParam.patchJPEGLS)
                : encapsulatedPixelData);
        decompressParam.setDestination(bi);
        long start = System.currentTimeMillis();
        bi = decompressor.read(0, decompressParam);
        long end = System.currentTimeMillis();
        if (LOG.isDebugEnabled())
            LOG.debug("Decompressed frame #{} 1:{} in {} ms",
                    frameIndex + 1, (float) sizeOf(bi) / encapsulatedPixelData.getStreamPosition(), end - start);
        encapsulatedPixelData.seekNextFrame();
        return bi;
    }

    protected void writeFrameTo(OutputStream out) throws IOException {
        WritableRaster raster = bi.getRaster();
        SampleModel sm = raster.getSampleModel();
        DataBuffer db = raster.getDataBuffer();
        switch (db.getDataType()) {
            case DataBuffer.TYPE_BYTE:
                writeTo(sm, ((DataBufferByte) db).getBankData(), out);
                break;
            case DataBuffer.TYPE_USHORT:
                writeTo(sm, ((DataBufferUShort) db).getData(), out);
                break;
            case DataBuffer.TYPE_SHORT:
                writeTo(sm, ((DataBufferShort) db).getData(), out);
                break;
            case DataBuffer.TYPE_INT:
                writeTo(sm, ((DataBufferInt) db).getData(), out);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported Datatype: " + db.getDataType());
        }
    }

    private static int sizeOf(BufferedImage bi) {
        DataBuffer db = bi.getData().getDataBuffer();
        return db.getSize() * db.getNumBanks() * (DataBuffer.getDataTypeSize(db.getDataType()) / 8);
    }

    private void writeTo(SampleModel sm, byte[][] bankData, OutputStream out) throws IOException {
        int h = sm.getHeight();
        int w = sm.getWidth();
        ComponentSampleModel csm = (ComponentSampleModel) sm;
        int len = w * csm.getPixelStride();
        int stride = csm.getScanlineStride();
        if (csm.getBandOffsets()[0] != 0)
            bgr2rgb(bankData[0]);
        for (byte[] b : bankData)
            for (int y = 0, off = 0; y < h; ++y, off += stride)
                out.write(b, off, len);
    }

    private static void bgr2rgb(byte[] bs) {
        for (int i = 0, j = 2; j < bs.length; i += 3, j += 3) {
            byte b = bs[i];
            bs[i] = bs[j];
            bs[j] = b;
        }
    }

    private static void writeTo(SampleModel sm, short[] data, OutputStream out) throws IOException {
        int h = sm.getHeight();
        int w = sm.getWidth();
        int stride = ((ComponentSampleModel) sm).getScanlineStride();
        byte[] b = new byte[w * 2];
        for (int y = 0; y < h; ++y) {
            for (int i = 0, j = y * stride; i < b.length;) {
                short s = data[j++];
                b[i++] = (byte) s;
                b[i++] = (byte) (s >> 8);
            }
            out.write(b);
        }
    }

    private static void writeTo(SampleModel sm, int[] data, OutputStream out) throws IOException {
        int h = sm.getHeight();
        int w = sm.getWidth();
        int stride = ((SinglePixelPackedSampleModel) sm).getScanlineStride();
        byte[] b = new byte[w * 3];
        for (int y = 0; y < h; ++y) {
            for (int i = 0, j = y * stride; i < b.length;) {
                int s = data[j++];
                b[i++] = (byte) (s >> 16);
                b[i++] = (byte) (s >> 8);
                b[i++] = (byte) s;
            }
            out.write(b);
        }
    }
}
