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

package org.dcm4chee.arc.conf;

import org.dcm4che3.util.TagUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2021
 */
public class MWLImport {
    private String mwlImportID;
    private String aeTitle;
    private String mwlSCP;
    private String destinationAE;
    private String[] includeFields = {};
    private Duration prefetchBefore;
    private Duration notOlderThan;
    private boolean filterBySCU;
    private boolean deleteNotFound;
    private final Map<String, String> filter = new HashMap<>();

    public MWLImport() {
    }

    public MWLImport(String mwlImportID) {
        setMWLImportID(mwlImportID);
    }

    public String getMWLImportID() {
        return mwlImportID;
    }

    public void setMWLImportID(String mwlImportID) {
        this.mwlImportID = mwlImportID;
    }

    public String getAETitle() {
        return aeTitle;
    }

    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getMWLSCP() {
        return mwlSCP;
    }

    public void setMWLSCP(String mwlSCP) {
        this.mwlSCP = mwlSCP;
    }

    public String getDestinationAE() {
        return destinationAE;
    }

    public void setDestinationAE(String destinationAE) {
        this.destinationAE = destinationAE;
    }

    public String[] getIncludeFields() {
        return includeFields;
    }

    public void setIncludeFields(String... includeFields) {
        for (String tagPath : includeFields) {
            if (!"all".equals(tagPath)) TagUtils.parseTagPath(tagPath);
        }
        this.includeFields = includeFields;
    }

    public Duration getPrefetchBefore() {
        return prefetchBefore;
    }

    public void setPrefetchBefore(Duration prefetchBefore) {
        this.prefetchBefore = prefetchBefore;
    }

    public Duration getNotOlderThan() {
        return notOlderThan;
    }

    public void setNotOlderThan(Duration notOlderThan) {
        this.notOlderThan = notOlderThan;
    }

    public boolean isFilterBySCU() {
        return filterBySCU;
    }

    public void setFilterBySCU(boolean filterBySCU) {
        this.filterBySCU = filterBySCU;
    }

    public boolean isDeleteNotFound() {
        return deleteNotFound;
    }

    public void setDeleteNotFound(boolean deleteNotFound) {
        this.deleteNotFound = deleteNotFound;
    }

    public void setFilter(String tagPath, String value) {
        TagUtils.parseTagPath(tagPath);
        filter.put(tagPath, value);
    }

    public String getFilter(String tagPath, String defValue) {
        String value = filter.get(tagPath);
        return value != null ? value : defValue;
    }

    public Map<String,String> getFilter() {
        return filter;
    }

    public void setFilter(String[] ss) {
        HashMap<String,String> backup = new HashMap<>(filter);
        filter.clear();
        try {
            for (String s : ss) {
                int index = s.indexOf('=');
                if (index < 0)
                    throw new IllegalArgumentException("Filter in incorrect format : " + s);
                setFilter(s.substring(0, index), s.substring(index+1));
            }
        } catch (IllegalArgumentException e) {
            filter.clear();
            filter.putAll(backup);
            throw e;
        }
    }

    @Override
    public String toString() {
        return "MWLImport{" +
                "mwlImportID='" + mwlImportID + '\'' +
                ", aeTitle='" + aeTitle + '\'' +
                ", mwlSCP='" + mwlSCP + '\'' +
                ", destinationAE='" + destinationAE + '\'' +
                ", prefetchBefore=" + prefetchBefore +
                ", notOlderThan=" + notOlderThan +
                ", filterBySCU=" + filterBySCU +
                ", deleteNotFound=" + deleteNotFound +
                ", filter=" + filter +
                ", includeFields=" + Arrays.toString(includeFields) +
                '}';
    }
}
