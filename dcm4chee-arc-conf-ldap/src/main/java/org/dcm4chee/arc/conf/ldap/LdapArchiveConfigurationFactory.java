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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
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
 */

package org.dcm4chee.arc.conf.ldap;

import org.dcm4che3.conf.api.ApplicationEntityCache;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che3.conf.api.hl7.HL7Configuration;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.conf.ldap.LdapDicomConfiguration;
import org.dcm4che3.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che3.conf.ldap.audit.LdapAuditLoggerConfiguration;
import org.dcm4che3.conf.ldap.audit.LdapAuditRecordRepositoryConfiguration;
import org.dcm4che3.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che3.conf.ldap.imageio.LdapImageReaderConfiguration;
import org.dcm4che3.conf.ldap.imageio.LdapImageWriterConfiguration;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Properties;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
public class LdapArchiveConfigurationFactory {

    private static final String LDAP_PROPERTIES = "ldap.properties";
    private static final String JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";

    private static final LdapDicomConfigurationExtension[] configExts() {
        return new LdapDicomConfigurationExtension[]{
                new LdapAuditLoggerConfiguration(),
                new LdapAuditRecordRepositoryConfiguration(),
                new LdapImageReaderConfiguration(),
                new LdapImageWriterConfiguration(),
                new LdapArchiveConfiguration(),
                newLdapHL7Configuration()
        };
    };

    @Resource(lookup="java:app/AppName")
    String appName;

    private static LdapDicomConfigurationExtension newLdapHL7Configuration() {
        LdapHL7Configuration hl7Config = new LdapHL7Configuration();
        hl7Config.addHL7ConfigurationExtension(new LdapArchiveHL7Configuration());
        return hl7Config;
    }

    public static DicomConfiguration newLdapDicomConfiguration(URL envURL)
            throws ConfigurationException {
        try {
            return newLdapDicomConfiguration(loadProperties(envURL));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load LDAP properties from " + envURL, e);
        }
    }

    public static LdapDicomConfiguration newLdapDicomConfiguration(Hashtable<?, ?> env)
            throws ConfigurationException {
        LdapDicomConfiguration config = new LdapDicomConfiguration(env);
        for (LdapDicomConfigurationExtension ext : configExts()) {
            config.addDicomConfigurationExtension(ext);
        }
        return config;
    }

    @Produces
    @ApplicationScoped
    public DicomConfiguration newLdapDicomConfiguration() throws ConfigurationException {
        return newLdapDicomConfiguration(envURL());
    }

    @Produces
    @ApplicationScoped
    public IApplicationEntityCache newApplicationEntityCache(DicomConfiguration conf) {
        return new ApplicationEntityCache(conf);
    }

    @Produces
    @ApplicationScoped
    public IHL7ApplicationCache newHL7ApplicationCache(DicomConfiguration conf) {
        return new HL7ApplicationCache(conf.getDicomConfigurationExtension(HL7Configuration.class));
    }

    private static Properties loadProperties(URL url) throws IOException {
        Properties p = new Properties();
        try (InputStream stream = url.openStream()) {
            p.load(stream);
        }
        return p;
    }

    private URL envURL() {
        String configDir = System.getProperty(JBOSS_SERVER_CONFIG_DIR);
        Path path = Paths.get(configDir, appName, LDAP_PROPERTIES);
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }
}
