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

package org.dcm4chee.arc.ui2; /**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Feb 2024
 */

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.wildfly.security.http.oidc.IDToken;
import org.wildfly.security.http.oidc.OidcSecurityContext;

import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(urlPatterns = "/index")
public class SelectLangServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        try (PrintWriter out = resp.getWriter()) {
            out.print("<html><head><script>");
            setLang(req, out);
            out.println("window.location.href=`/dcm4chee-arc/ui2/${lang}/index.html`;</script></head></html>");
        }
    }

    private void setLang(HttpServletRequest req, PrintWriter out) {
        OidcSecurityContext ksc = (OidcSecurityContext) req.getAttribute(OidcSecurityContext.class.getName());
        if (ksc != null) {
            IDToken idToken = ksc.getIDToken();
            if (idToken != null) {
                String locale = idToken.getLocale();
                if (locale != null && locale.length() >= 2) {
                    String lang = locale.substring(0, 2);
                    switch (lang) {
                        case "de":
                        case "en":
                        case "es":
                        case "hi":
                        case "it":
                        case "ja":
                        case "mr":
                        case "pt":
                        case "ru":
                        case "zh":
                            out.print("const lang='" + lang + "';");
                            return;
                    }
                }
            }
        }
        out.print("const lang=localStorage.getItem('current_language')||'en';");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
    }
}
