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

package org.dcm4chee.arc.impl;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.imageio.ImageReaderExtension;
import org.dcm4che3.net.imageio.ImageWriterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
public class ArchiveDeviceProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ArchiveDeviceProducer.class);
    private static final String DEF_DEVICE_NAME = "dcm4chee-arc";

    @Resource(lookup="java:app/AppName")
    private String appName;

    @Inject
    private DicomConfiguration conf;

    private Device device;

    @PostConstruct
    private void init() {
        try {
            device = findDevice();
            initImageReaderFactory();
            initImageWriterFactory();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Produces
    public Device getDevice() {
        return device;
    }

    public void reloadConfiguration() throws Exception {
        device.reconfigure(findDevice());
        initImageReaderFactory();
        initImageWriterFactory();
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
}
