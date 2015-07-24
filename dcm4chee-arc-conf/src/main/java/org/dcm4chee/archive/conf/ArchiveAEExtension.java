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

package org.dcm4chee.archive.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.codec.CompressionRule;
import org.dcm4che3.imageio.codec.CompressionRules;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.util.StringUtils;

import java.io.File;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public class ArchiveAEExtension extends AEExtension {
    private String storageID;
    private String bulkDataSpoolDirectory;
    private String remotePIXManagerApplication;
    private String localPIXConsumerApplication;
    private boolean pixQuery;
    private CompressionRules compressionRules = new CompressionRules();


    public String getStorageID() {
        return storageID;
    }

    public void setStorageID(String storageID) {
        this.storageID = storageID;
    }

    public String getBulkDataSpoolDirectory() {
        return bulkDataSpoolDirectory;
    }

    public void setBulkDataSpoolDirectory(String bulkDataSpoolDirectory) {
        this.bulkDataSpoolDirectory = bulkDataSpoolDirectory;
    }

    public CompressionRules getCompressionRules() {
        return compressionRules;
    }

    public void addCompressionRule(CompressionRule rule) {
        compressionRules.add(rule);
    }

    public void setCompressionRules(CompressionRules rules) {
        compressionRules.clear();
        compressionRules.add(rules);
    }

    public boolean removeCompressionRule(CompressionRule ac) {
        return compressionRules.remove(ac);
    }

    public String getRemotePIXManagerApplication() {
        return remotePIXManagerApplication;
    }

    public void setRemotePIXManagerApplication(String remotePIXManagerApplication) {
        this.remotePIXManagerApplication = remotePIXManagerApplication;
    }

    public String getLocalPIXConsumerApplication() {
        return localPIXConsumerApplication;
    }

    public void setLocalPIXConsumerApplication(String localPIXConsumerApplication) {
        this.localPIXConsumerApplication = localPIXConsumerApplication;
    }

    public boolean isPixQuery() {
        return pixQuery;
    }

    public void setPixQuery(boolean pixQuery) {
        this.pixQuery = pixQuery;
    }

    @Override
    public void reconfigure(AEExtension from) {
        ArchiveAEExtension aeExt = (ArchiveAEExtension) from;
        storageID = aeExt.storageID;
        bulkDataSpoolDirectory = aeExt.bulkDataSpoolDirectory;
        pixQuery = aeExt.pixQuery;
        remotePIXManagerApplication = aeExt.remotePIXManagerApplication;
        localPIXConsumerApplication = aeExt.localPIXConsumerApplication;
        compressionRules.clear();
        compressionRules.add(aeExt.compressionRules);
    }

    public ArchiveDeviceExtension getArchiveDeviceExtension() {
        return ae.getDevice().getDeviceExtension(ArchiveDeviceExtension.class);
    }

    public StorageDescriptor getStorageDescriptor() {
        return storageID != null ? getArchiveDeviceExtension().getStorageDescriptor(storageID) : null;
    }

    public CompressionRule findCompressionRule(String aeTitle, ImageDescriptor imageDescriptor) {
        CompressionRule rule = compressionRules.findCompressionRule(aeTitle, imageDescriptor);
        if (rule == null)
            rule = getArchiveDeviceExtension().getCompressionRules().findCompressionRule(aeTitle, imageDescriptor);
        return rule;
    }

    public File getBulkDataSpoolDirectoryFile() {
        return fileOf(bulkDataSpoolDirectory != null ? bulkDataSpoolDirectory
                : getArchiveDeviceExtension().getBulkDataSpoolDirectory());
    }

    private File fileOf(String s) {
        return s != null ? new File(StringUtils.replaceSystemProperties(s)) : null;
    }
}
