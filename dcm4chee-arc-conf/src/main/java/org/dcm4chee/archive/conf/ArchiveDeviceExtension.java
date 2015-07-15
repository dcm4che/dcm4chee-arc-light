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

import org.dcm4che3.net.DeviceExtension;
import org.dcm4che3.soundex.FuzzyStr;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public class ArchiveDeviceExtension extends DeviceExtension {

    private String fuzzyAlgorithmClass;

    private final AttributeFilter[] attributeFilters = new AttributeFilter[Entity.values().length];

    private final Map<String, StorageDescriptor> storageDescriptorMap = new HashMap<String, StorageDescriptor>();

    private transient FuzzyStr fuzzyStr;

    public String getFuzzyAlgorithmClass() {
        return fuzzyAlgorithmClass;
    }

    public void setFuzzyAlgorithmClass(String fuzzyAlgorithmClass) {
        this.fuzzyStr = fuzzyStr(fuzzyAlgorithmClass);
        this.fuzzyAlgorithmClass = fuzzyAlgorithmClass;
    }

    public FuzzyStr getFuzzyStr() {
        if (fuzzyStr == null)
            if (fuzzyAlgorithmClass == null)
                throw new IllegalStateException("No Fuzzy Algorithm Class configured");
            else
                fuzzyStr = fuzzyStr(fuzzyAlgorithmClass);
        return fuzzyStr;
    }

    private static FuzzyStr fuzzyStr(String s) {
        try {
            return (FuzzyStr) Class.forName(s).newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(s);
        }
    }

    public AttributeFilter getAttributeFilter(Entity entity) {
        return attributeFilters[entity.ordinal()];
    }

    public void setAttributeFilter(Entity entity, AttributeFilter filter) {
        attributeFilters[entity.ordinal()] = filter;
    }

    public StorageDescriptor getStorageDescriptor(String storageID) {
        return storageDescriptorMap.get(storageID);
    }

    public StorageDescriptor removeStorageDescriptor(String storageID) {
        return storageDescriptorMap.remove(storageID);
    }

    public void addStorageDescriptor(StorageDescriptor descriptor) {
        storageDescriptorMap.put(descriptor.getStorageID(), descriptor);
    }

    public Collection<StorageDescriptor> getStorageDescriptors() {
        return storageDescriptorMap.values();
    }

    @Override
    public void reconfigure(DeviceExtension from) {
        ArchiveDeviceExtension arcdev = (ArchiveDeviceExtension) from;
        fuzzyAlgorithmClass = arcdev.fuzzyAlgorithmClass;
        fuzzyStr = arcdev.fuzzyStr;
        System.arraycopy(arcdev.attributeFilters, 0, attributeFilters, 0, attributeFilters.length);
        storageDescriptorMap.clear();
        storageDescriptorMap.putAll(arcdev.storageDescriptorMap);
    }
}
