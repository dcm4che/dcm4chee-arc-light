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

import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.servlet.http.HttpServletRequest;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
public class HttpServletRequestInfo {

    public final String requesterUserID;
    public final String requesterHost;
    public final String requestURI;

    private HttpServletRequestInfo(HttpServletRequest request) {
        requesterUserID = KeycloakContext.valueOf(request).getUserName();
        requesterHost = request.getRemoteHost();
        requestURI = request.getRequestURI();
    }

    private HttpServletRequestInfo(String requesterUserID, String requesterHost, String requestURI) {
        this.requesterUserID = requesterUserID;
        this.requesterHost = requesterHost;
        this.requestURI = requestURI;
    }

    public static HttpServletRequestInfo valueOf(HttpServletRequest request) {
        return new HttpServletRequestInfo(request);
    }

    public static HttpServletRequestInfo valueOf(Message msg) {
        try {
            return msg.propertyExists("RequestURI")
                    ? new HttpServletRequestInfo(
                        msg.getStringProperty("RequesterUserID"),
                        msg.getStringProperty("RequesterHostName"),
                        msg.getStringProperty("RequestURI"))
                    : null;
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }

    public void copyTo(Message msg) {
        try {
            msg.setStringProperty("RequesterUserID", requesterUserID);
            msg.setStringProperty( "RequesterHostName", requesterHost);
            msg.setStringProperty( "RequestURI", requestURI);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }

    public static void copyTo(HttpServletRequestInfo requestInfo, Message msg) {
        if (requestInfo != null)
            requestInfo.copyTo(msg);
    }
}