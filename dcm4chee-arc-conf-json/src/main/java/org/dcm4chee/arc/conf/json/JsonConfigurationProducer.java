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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf.json;

import org.dcm4che3.conf.json.JsonConfiguration;
import org.dcm4che3.conf.json.JsonConfigurationExtension;
import org.dcm4che3.conf.json.audit.JsonAuditLoggerConfiguration;
import org.dcm4che3.conf.json.audit.JsonAuditRecordRepositoryConfiguration;
import org.dcm4che3.conf.json.hl7.JsonHL7Configuration;
import org.dcm4che3.conf.json.imageio.JsonImageReaderConfiguration;
import org.dcm4che3.conf.json.imageio.JsonImageWriterConfiguration;
import org.dcm4chee.arc.conf.ui.json.JsonArchiveUIConfiguration;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2015
 */
@ApplicationScoped
public class JsonConfigurationProducer {

    @Produces
    @ApplicationScoped
    private static JsonAuditLoggerConfiguration jsonAuditLoggerConfiguration =
            new JsonAuditLoggerConfiguration();

    @Produces
    @ApplicationScoped
    private static JsonAuditRecordRepositoryConfiguration jsonAuditRecordRepositoryConfiguration =
            new JsonAuditRecordRepositoryConfiguration();

    @Produces
    @ApplicationScoped
    private static JsonImageReaderConfiguration jsonImageReaderConfiguration =
            new JsonImageReaderConfiguration();

    @Produces
    @ApplicationScoped
    private static JsonImageWriterConfiguration jsonImageWriterConfiguration =
            new JsonImageWriterConfiguration();

    @Produces
    @ApplicationScoped
    private static JsonHL7Configuration newJsonHL7Configuration() {
        JsonHL7Configuration hl7Config = new JsonHL7Configuration();
        hl7Config.addHL7ConfigurationExtension(new JsonArchiveHL7Configuration());
        return hl7Config;
    }

    public static JsonConfiguration newJsonConfiguration() {
        return newJsonConfiguration(Stream.of(
                jsonAuditLoggerConfiguration,
                jsonAuditRecordRepositoryConfiguration,
                jsonImageReaderConfiguration,
                jsonImageWriterConfiguration,
                new JsonArchiveConfiguration(),
                new JsonArchiveUIConfiguration(),
                newJsonHL7Configuration()));
    }

    private static JsonConfiguration newJsonConfiguration(Stream<JsonConfigurationExtension> stream) {
        JsonConfiguration config = new JsonConfiguration();
        stream.forEach(config::addJsonConfigurationExtension);
        return config;
    }

    @Produces
    @ApplicationScoped
    public static JsonConfiguration newJsonConfiguration(Instance<JsonConfigurationExtension> configExts) {
        return newJsonConfiguration(configExts.stream());
    }
}
