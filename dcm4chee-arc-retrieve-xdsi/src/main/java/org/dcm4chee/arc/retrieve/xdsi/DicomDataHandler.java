package org.dcm4chee.arc.retrieve.xdsi;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;

import javax.activation.DataHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2017
 */
public class DicomDataHandler extends DataHandler {
    private final RetrieveContext ctx;
    private final InstanceLocations inst;
    private final Collection<String> tsuids;

    public DicomDataHandler(RetrieveContext ctx, InstanceLocations inst, Collection<String> tsuids) {
        super(inst, MediaTypes.APPLICATION_DICOM);
        this.ctx = ctx;
        this.inst = inst;
        this.tsuids = tsuids;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try (Transcoder transcoder = ctx.getRetrieveService().openTranscoder(ctx, inst, tsuids, true)) {
            transcoder.transcode(new Transcoder.Handler() {
                @Override
                public OutputStream newOutputStream(Transcoder transcoder, Attributes dataset) throws IOException {
                    ctx.getRetrieveService().getAttributesCoercion(ctx, inst).coerce(dataset, null);
                    return os;
                }
            });
        }
    }
}
