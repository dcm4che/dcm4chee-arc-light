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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

package org.dcm4chee.arc.rs.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.util.ByteUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.RSOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2017
 */

@ApplicationScoped
public class RSForward {

    private static final Logger LOG = LoggerFactory.getLogger(RSForward.class);
    private static final String LOG_APPLY_RS_FWD_RULE = "Apply {0} to RSOperation {1}";

    @Inject
    private RSClient rsClient;

    public void forward(RSOperation rsOp, ArchiveAEExtension arcAE, HttpServletRequest request) {
        arcAE.rsForwardRules()
                .filter(rule -> rule.containsRSOperations(rsOp) && rule.matchesRequest(request))
                .forEach(rule -> {
                    LOG.info(MessageFormat.format(LOG_APPLY_RS_FWD_RULE, rule, rsOp));
                    rsClient.scheduleRequest(rsOp, request, rule.getWebAppName(), null, ByteUtils.EMPTY_BYTES);
                });
    }

    public void forward(RSOperation rsOp, ArchiveAEExtension arcAE, Attributes attrs, HttpServletRequest request) {
        byte[] content = toContent(attrs, arcAE);
        String pids = pidInURL(rsOp, attrs);
        arcAE.rsForwardRules()
                .filter(rule -> rule.containsRSOperations(rsOp) && rule.matchesRequest(request))
                .forEach(rule -> {
                    LOG.info(MessageFormat.format(LOG_APPLY_RS_FWD_RULE, rule, rsOp));
                    rsClient.scheduleRequest(rsOp, request, rule.getWebAppName(), pids, content);
                });
    }

    public void forward(
            RSOperation rsOp, ArchiveAEExtension arcAE, Attributes attrs, Attributes prev, HttpServletRequest request) {
        byte[] content = toContent(attrs, arcAE);
        String pids = pidInURL(rsOp, prev);
        arcAE.rsForwardRules()
                .filter(rule -> rule.containsRSOperations(rsOp) && rule.matchesRequest(request))
                .forEach(rule -> {
                    LOG.info(MessageFormat.format(LOG_APPLY_RS_FWD_RULE, rule, rsOp));
                    rsClient.scheduleRequest(rsOp, request, rule.getWebAppName(), pids, content);
                });
    }

    private String pidInURL(RSOperation rsOp, Attributes attrs) {
        switch (rsOp) {
            case CreatePatient:
                return IDWithIssuer.pidOf(attrs).toString();
            case UpdatePatientByPID:
            case DeletePatientByPID:
            case ChangePatientIDByPID:
            case MergePatientByPID:
            case UnmergePatientByPID:
                Set<IDWithIssuer> pids = IDWithIssuer.pidsOf(attrs);
                return URLEncoder.encode(
                        pids.stream().map(IDWithIssuer::toString).collect(Collectors.joining("~")),
                        StandardCharsets.UTF_8);
            default:
                return null;
        }
    }

    public void forward(RSOperation rsOp, ArchiveAEExtension arcAE, HttpServletRequest request, byte[] content) {
        arcAE.rsForwardRules()
                .filter(rule -> rule.containsRSOperations(rsOp) && rule.matchesRequest(request))
                .forEach(rule -> {
                    LOG.info(MessageFormat.format(LOG_APPLY_RS_FWD_RULE, rule, rsOp));
                    rsClient.scheduleRequest(rsOp, request, rule.getWebAppName(), null, content);
                });
    }

    private static byte[] toContent(Attributes attrs, ArchiveAEExtension arcAE) {
        if (attrs == null)
            return ByteUtils.EMPTY_BYTES;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator gen = Json.createGenerator(out)) {
            arcAE.encodeAsJSONNumber(new JSONWriter(gen)).write(attrs);
        }
        return out.toByteArray();
    }

}
