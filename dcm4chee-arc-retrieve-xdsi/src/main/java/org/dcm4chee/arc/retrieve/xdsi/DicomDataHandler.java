/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.retrieve.xdsi;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.InstanceLocations;

import javax.activation.DataHandler;
import javax.enterprise.event.Event;
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
    private Event<RetrieveContext> retrieveEnd;

    public DicomDataHandler(RetrieveContext ctx, InstanceLocations inst, Collection<String> tsuids) {
        super(inst, MediaTypes.APPLICATION_DICOM);
        this.ctx = ctx;
        this.inst = inst;
        this.tsuids = tsuids;
    }

    public void setRetrieveEnd(Event<RetrieveContext> retrieveEnd) {
        this.retrieveEnd = retrieveEnd;
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
        if (retrieveEnd != null) {
            ctx.getRetrieveService().updateLocations(ctx);
            retrieveEnd.fire(ctx);
        }
    }

}
