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

package org.dcm4chee.arc.impl;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.imageio.ImageReaderExtension;
import org.dcm4che3.net.imageio.ImageWriterExtension;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
public class ArchiveDeviceProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ArchiveDeviceProducer.class);
    private static final String DEF_DEVICE_NAME = "dcm4chee-arc";

    private static String[] JBOSS_PROPERITIES = {
            "jboss.home",
            "jboss.modules",
            "jboss.server.base",
            "jboss.server.config",
            "jboss.server.data",
            "jboss.server.deploy",
            "jboss.server.log",
            "jboss.server.temp",
    };

    @Resource(lookup="java:app/AppName")
    private String appName;

    @Inject
    private DicomConfiguration conf;

    private Device device;

    @PostConstruct
    private void init() {
        addJBossDirURLSystemProperties();
        try {
            device = findDevice();
            initImageReaderFactory();
            initImageWriterFactory();
            extractVendorData();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private void extractVendorData() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String unzipTo = arcDev != null ? arcDev.getUnzipVendorDataToURI() : null;
        if (unzipTo == null)
            return;

        byte[][] vendorData = device.getVendorData();
        if (vendorData.length == 0) {
            LOG.warn("UnzipVendorDataToURI={}, but no Vendor Data", unzipTo);
            return;
        }

        Path basePath = Paths.get(URI.create(StringUtils.replaceSystemProperties(unzipTo)));
        ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(vendorData[0]));
        ZipEntry entry;
        try {
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path filePath = basePath.resolve(entry.getName());
                    Files.createDirectories(filePath.getParent());
                    Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to extract Device Vendor Data", e);
        }
        device.setVendorData();
        TemplatesCache.getDefault().clear();
    }

    @Produces
    public Device getDevice() {
        return device;
    }

    public void reloadConfiguration() throws Exception {
        device.reconfigure(findDevice());
        initImageReaderFactory();
        initImageWriterFactory();
        extractVendorData();
    }

    private Device findDevice() throws ConfigurationException {
        String key = appName + ".DeviceName";
        String name = System.getProperty(key, DEF_DEVICE_NAME);
        Device arcDevice = null;
        try {
            arcDevice = conf.findDevice(name);
        } catch (ConfigurationNotFoundException e) {
            LOG.error("Missing Configuration for Device '{}' - you may change the Device name by System Property '{}'",
                    name, key);
            throw e;
        }
        return arcDevice;
    }

    private void initImageReaderFactory() {
        ImageReaderExtension ext = device.getDeviceExtension(ImageReaderExtension.class);
        if (ext != null)
            ImageReaderFactory.setDefault(ext.getImageReaderFactory());
        else
            ImageReaderFactory.resetDefault();
    }

    private void initImageWriterFactory() {
        ImageWriterExtension ext = device.getDeviceExtension(ImageWriterExtension.class);
        if (ext != null)
            ImageWriterFactory.setDefault(ext.getImageWriterFactory());
        else
            ImageWriterFactory.resetDefault();
        ImageWriterFactory.getImageWriter(ImageWriterFactory.getImageWriterParam(UID.JPEGLSLossless));
    }

    private static void addJBossDirURLSystemProperties() {
        for (String key : JBOSS_PROPERITIES) {
            String url = new File(System.getProperty(key + ".dir")).toURI().toString();
            System.setProperty(key + ".url", url.substring(0, url.length()-1));
        }
    }
}
