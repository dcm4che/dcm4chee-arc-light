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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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

package org.dcm4chee.arc.ui2;

import org.jboss.resteasy.annotations.cache.NoCache;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.StringTokenizer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
@RequestScoped
@Path("")
public class Resource {
    @GET
    @NoCache
    @Path("keycloak.json")
    @Produces("application/json")
    public String keycloakJSON() {
        String authServerURL = System.getProperty("ui-auth-server-url", System.getProperty("auth-server-url"));
        return authServerURL == null ? "{}" :
                "{\"realm\":\"" + System.getProperty("realm-name", "dcm4che") +
                "\",\"resource\":\"" + System.getProperty("ui-client-id","dcm4chee-arc-ui") +
                "\",\"auth-server-url\":\"" + authServerURL +
                "\",\"ssl-required\":\"" + System.getProperty("ssl-required","external") +
                "\",\"public-client\":true,\"confidential-port\":0}";
    }

    @GET
    @NoCache
    @Path("dcm4chee-arc")
    @Produces("application/json")
    public String dcm4cheeArc() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        String arcURLs = System.getProperty("dcm4chee-arc-urls");
        if (arcURLs != null) {
            StringTokenizer urls = new StringTokenizer(arcURLs, " ");
            sb.append("\"dcm4chee-arc-urls\":[\"").append(urls.nextToken());
            while (urls.hasMoreTokens()) sb.append("\",\"").append(urls.nextToken());
                sb.append("\"],");
            }
        sb.append("\"dicomDeviceName\":\"").append(System.getProperty("dcm4chee-arc.DeviceName", "dcm4chee-arc"));
        sb.append("\",\"super-user-role\":\"").append(System.getProperty("super-user-role", "root"));
        sb.append("\",\"management-http-port\":").append(intSystemProperty("jboss.management.http.port", 9990));
        sb.append(",\"management-https-port\":").append(intSystemProperty("jboss.management.https.port", 9993));
        sb.append("}");
        return sb.toString();
    }

    private static int intSystemProperty(String key, int defVal) {
        try {
            return Integer.parseInt(System.getProperty(key));
        } catch (Exception e) {
            return defVal;
        }
    }
}
