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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

import jakarta.servlet.http.HttpServletRequest;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;

import java.net.URI;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2023
 */
class RedirectUtils {
    private RedirectUtils() {}
    public static URI redirectURI(HttpServletRequest request, String path, Device device, IWebApplicationCache iWebAppCache,
                                  String webAppName, WebApplication.ServiceClass serviceClass)
            throws ConfigurationException {
        WebApplication webApp = iWebAppCache.findWebApplication(webAppName);
        if (!webApp.containsServiceClass(serviceClass)) {
            throw new ConfigurationException("WebApplication: " + webAppName
                    + " does not provide " + serviceClass + " service");
        }
        if (webApp.getDevice().getDeviceName().equals(device.getDeviceName())) {
            throw new ConfigurationException("WebApplication: " + webAppName
                    + " is provided by this Device: " + device.getDeviceName() + " - prevent redirect to itself");
        }
        boolean https = "https:".equalsIgnoreCase(request.getRequestURL().substring(0,6));
        StringBuilder serviceURL = webApp.getServiceURL(selectConnection(webApp, https));
        if (path != null) serviceURL.append(path);
        String queryString = request.getQueryString();
        if (queryString != null) serviceURL.append('?').append(queryString);
        return URI.create(serviceURL.toString());
    }

    private static Connection selectConnection(WebApplication webApp, boolean https) throws ConfigurationException {
        Connection altConn = null;
        for (Connection conn : webApp.getConnections()) {
            if (conn.isInstalled() && (altConn = conn).isTls() == https) {
                return conn;
            }
        }
        if (altConn == null) {
            throw new ConfigurationException(
                    "No installed Network Connection for WebApplication: " + webApp.getApplicationName());
        }
        return altConn;
    }

}
