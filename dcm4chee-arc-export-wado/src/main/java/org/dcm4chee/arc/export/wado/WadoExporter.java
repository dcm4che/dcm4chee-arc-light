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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.export.wado;

import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.QueryService;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2016
 */
public class WadoExporter extends AbstractExporter {
    private static final int COPY_BUFFER_SIZE = 8192;
    private final MessageFormat format;
    private final Entity entity;
    private final QueryService queryService;

    public WadoExporter(ExporterDescriptor descriptor, QueryService queryService) {
        super(descriptor);
        this.format = new MessageFormat(
                descriptor.getExportURI().getSchemeSpecificPart().replace('[','{').replace(']','}'));
        this.entity = Entity.values()[format.getFormats().length];
        this.queryService = queryService;
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        for (Object[] params : entity.queryParams(exportContext, queryService)) {
            fetch(params, buffer);
        }
        return new Outcome(QueueMessage.Status.COMPLETED, "");
    }

    private void fetch(Object[] params, byte[] buffer) {
        try {
            URL url = new URL(format.format(params));
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            int responseCode = httpConn.getResponseCode();
            try (InputStream in = httpConn.getInputStream()) {
                StreamUtils.copy(in, null, buffer);
            }
        } catch (Exception e) {

        }
    }

    private enum Entity {
        CONST {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return Collections.singletonList(new Object[]{});
            }
        }, STUDY {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return Collections.singletonList(new Object[]{ctx.getStudyInstanceUID()});
            }
        }, SERIES {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return ctx.getSeriesInstanceUID().equals("*")
                        ? queryService.getSeriesInstanceUIDs(ctx.getStudyInstanceUID())
                        : Collections.singletonList(new Object[]{
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID()});
            }
        }, INSTANCE {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return ctx.getSeriesInstanceUID().equals("*")
                        ? queryService.getSOPInstanceUIDs(ctx.getStudyInstanceUID())
                        : ctx.getSopInstanceUID().equals("*")
                        ? queryService.getSOPInstanceUIDs(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID())
                        : Collections.singletonList(new Object[]{
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID(),
                            ctx.getSopInstanceUID()});
            }
        };

        abstract List<Object[]> queryParams(ExportContext ctx, QueryService queryService);
    }
}
