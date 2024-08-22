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

package org.dcm4chee.arc.keycloak;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
public class HttpServletRequestInfo {

    public final String requesterUserID;
    public final String requesterHost;
    public final int requesterPort;
    public final String requestURI;
    public final String queryString;
    public final String localHost;
    public MediaType contentType;

    private HttpServletRequestInfo(HttpServletRequest request) {
        requesterUserID = KeycloakContext.valueOf(request).getUserName();
        requesterHost = request.getRemoteHost();
        requesterPort = request.getRemotePort();
        requestURI = request.getRequestURL().toString();
        queryString = request.getQueryString();
        localHost = request.getServerName();
    }

    private HttpServletRequestInfo(String requesterUserID, String requesterHost, String requestURI, String queryStr) {
        this.requesterUserID = requesterUserID;
        this.requesterHost = requesterHost;
        this.requesterPort = 0;
        this.requestURI = requestURI;
        this.queryString = queryStr;
        this.localHost = hostOfURI(requestURI);
    }

    public static HttpServletRequestInfo valueOf(HttpServletRequest request) {
        return new HttpServletRequestInfo(request);
    }

    public static HttpServletRequestInfo valueOf(String requesterUserID, String requesterHost, String requestURI) {
        return requestURI == null
                ? null
                : new HttpServletRequestInfo(requesterUserID, requesterHost, requestURI,
                        requestURI.contains("?") ? requestURI.substring(requestURI.indexOf("?") + 1) : null);
    }

    private static String hostOfURI(String requestURI) {
        try {
            int beginIndex = requestURI.indexOf(':') + 1;
            while (requestURI.charAt(++beginIndex) == '/');
            int endIndex = beginIndex;
            char ch;
            while ((ch = requestURI.charAt(endIndex)) != '/' && ch != ':') endIndex++;
            return requestURI.substring(beginIndex, endIndex);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public MediaType getContentType() {
        return contentType;
    }

    public HttpServletRequestInfo setContentType(HttpHeaders headers) {
        this.contentType = headers.getMediaType();
        return this;
    }

    public String requestURIWithQueryStr() {
        return queryString == null
                ? requestURI
                : requestURI + '?' + queryString;
    }

    @Override
    public String toString() {
        return "HttpServletRequestInfo[requesterUserID=" + requesterUserID
                + ", requesterHost=" + requesterHost
                + ", requesterPort=" + requesterPort
                + ", requesterURI=" + requestURIWithQueryStr()
                + ", localHost=" + localHost
                + "]";
    }
}