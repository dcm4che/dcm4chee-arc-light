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

package org.dcm4chee.arc.wado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.SAXWriter;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public class DicomXSLTOutput implements StreamingOutput {
    private final RetrieveContext ctx;
    private final InstanceLocations inst;
    private final MediaType mediaType;
    private final String wadoURL;

    public DicomXSLTOutput(RetrieveContext ctx, InstanceLocations inst, MediaType mediaType, String wadoURL) {
        this.ctx = ctx;
        this.inst = inst;
        this.mediaType = mediaType;
        this.wadoURL = wadoURL;
    }

    @Override
    public void write(OutputStream output) throws IOException, WebApplicationException {
        try {
            SAXWriter saxWriter = SAXTransformer.getSAXWriter(templates(), new StreamResult(output),
                    transformer -> transformer.setParameter("wadoURL", wadoURL));
            saxWriter.setIncludeKeyword(false);
            saxWriter.write(readAttributes());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    private Attributes readAttributes() throws IOException {
        RetrieveService service = ctx.getRetrieveService();
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)){
            Attributes attrs = dis.readDataset(-1, -1);
            service.getAttributesCoercion(ctx, inst).coerce(attrs, null);
            return attrs;
        }
    }

    private Templates templates() throws TransformerConfigurationException {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        String uri = StringUtils.replaceSystemProperties(
                mediaType.isCompatible(MediaType.TEXT_HTML_TYPE)
                        ? arcAE.wadoSR2HtmlTemplateURI()
                        : arcAE.wadoSR2TextTemplateURI());
        return TemplatesCache.getDefault().get(uri);
    }

}
