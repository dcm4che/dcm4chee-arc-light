/*
 * ** BEGIN LICENSE BLOCK *****
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.impl;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.*;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Dec 2016
 */
@ApplicationScoped
public class ArchiveAssociationHandler extends AssociationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ArchiveAssociationHandler.class);

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private AccessTokenRequestor accessTokenRequestor;

    @Override
    protected AAssociateAC makeAAssociateAC(Association as, AAssociateRQ rq, UserIdentityAC userIdentity)
            throws IOException {
        ArchiveAEExtension arcAE = as.getApplicationEntity().getAEExtension(ArchiveAEExtension.class);
        if (arcAE != null) {
            if (!validateUserIdentity(as, arcAE, rq.getUserIdentityRQ()))
                throw new AAssociateRJ(AAssociateRJ.RESULT_REJECTED_PERMANENT,
                        AAssociateRJ.SOURCE_SERVICE_PROVIDER_ACSE,
                        AAssociateRJ.REASON_NO_REASON_GIVEN);
            if (arcAE.validateCallingAEHostname() && !validateCallingAEHostname(as))
                throw new AAssociateRJ(AAssociateRJ.RESULT_REJECTED_PERMANENT,
                    AAssociateRJ.SOURCE_SERVICE_USER,
                    AAssociateRJ.REASON_CALLING_AET_NOT_RECOGNIZED);
        }
        return super.makeAAssociateAC(as, rq, userIdentity);
    }

    private boolean validateUserIdentity(Association as, ArchiveAEExtension arcAE, UserIdentityRQ userIdentityRQ) {
        switch (arcAE.userIdentityNegotiation()) {
            case SUPPORTS:
                return validateUserIdentity(as, arcAE, userIdentityRQ, true);
            case REQUIRED:
                return validateUserIdentity(as, arcAE, userIdentityRQ, false);
        }
        return true;
    }

    private boolean validateUserIdentity(Association as, ArchiveAEExtension arcAE, UserIdentityRQ userIdentityRQ,
            boolean optional) {
        KeycloakClient kc;
        if (userIdentityRQ != null
                && (userIdentityRQ.getType() == UserIdentityRQ.USERNAME_PASSCODE
                    || userIdentityRQ.getType() == UserIdentityRQ.JWT)
                && (kc = keycloakClient(arcAE)) != null)
            try {
                switch (userIdentityRQ.getType()) {
                    case UserIdentityRQ.USERNAME_PASSCODE:
                        kc.setKeycloakGrantType(KeycloakClient.GrantType.password);
                        kc.setUserID(userIdentityRQ.getUsername());
                        kc.setPassword(new String(userIdentityRQ.getPasscode()));
                        return accessTokenRequestor.verifyUsernamePasscode(kc, arcAE.userIdentityNegotiationRole());
                    case UserIdentityRQ.JWT:
                        return accessTokenRequestor.verifyJWT(
                                userIdentityRQ.getUsername(), kc, arcAE.userIdentityNegotiationRole());
                }
            } catch (Exception e) {
                LOG.info("{}: validation of {} failed:\n", as, userIdentityRQ, e);
                return false;
            }
        return optional;
    }

    private KeycloakClient keycloakClient(ArchiveAEExtension arcAE) {
        String keycloakClientID = arcAE.userIdentityNegotiationKeycloakClientID();
        if (keycloakClientID != null) {
            KeycloakClient kc = arcAE.getApplicationEntity().getDevice().getKeycloakClient(keycloakClientID);
            return kc != null ? kc.clone() : null;
        }
        if (System.getProperty("auth-server-url") != null) {
            KeycloakClient kc = new KeycloakClient();
            kc.setKeycloakServerURL(System.getProperty("auth-server-url"));
            kc.setKeycloakRealm(System.getProperty("realm-name", "dcm4che"));
            kc.setKeycloakClientID(System.getProperty("ui-client-id", "dcm4chee-arc-ui"));
            kc.setTLSDisableTrustManager(
                    Boolean.parseBoolean(System.getProperty("disable-trust-manager", "false")));
            kc.setTLSAllowAnyHostname(
                    Boolean.parseBoolean(System.getProperty("allow-any-hostname", "true")));
            return kc;
        }
        return null;
    }

    private boolean validateCallingAEHostname(Association as) {
        try {
            ApplicationEntity ae = aeCache.get(as.getAAssociateRQ().getCallingAET());
            if (ae != null) {
                InetAddress remote = as.getSocket().getInetAddress();
                for (Connection c : ae.getConnections()) {
                    if (equalsHost(c.getHostname(), remote))
                        return true;
                }
                LOG.info("{}: Host {} of Calling AE does not match configuration", as, remote);
            } else {
                LOG.info("{}: Calling AE not configured", as);
            }
        } catch (ConfigurationException e) {
            LOG.warn("{}: Failed to lookup configuration for Calling AE:\n", as, e);
        }
        return false;
    }

    private boolean equalsHost(String configuredHostname, InetAddress addr) {
        if (StringUtils.isIPAddr(configuredHostname))
            return configuredHostname.equals(addr.getHostAddress());

        String canonicalHostName = addr.getCanonicalHostName();
        return configuredHostname.equalsIgnoreCase(
                configuredHostname.indexOf('.') < 0
                        ? StringUtils.split(canonicalHostName, '.')[0]
                        : canonicalHostName);
    }
}
