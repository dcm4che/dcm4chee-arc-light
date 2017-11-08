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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class ArchiveAttributeCoercion {

    private String commonName;
    private int priority;
    private Dimse dimse;
    private TransferCapability.Role role;
    private String[] sopClasses = {};
    private String[] aeTitles = {};
    private String[] hostNames = {};
    private String xsltStylesheetURI;
    private boolean noKeywords;
    private String leadingCFindSCP;
    private MergeMWLMatchingKey mergeMWLMatchingKey;
    private String mergeMWLTemplateURI;
    private Attributes.UpdatePolicy attributeUpdatePolicy = Attributes.UpdatePolicy.MERGE;
    private int[] nullifyTags = {};
    private Device supplementFromDevice;

    public ArchiveAttributeCoercion() {
    }

    public ArchiveAttributeCoercion(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public ArchiveAttributeCoercion setCommonName(String commonName) {
        this.commonName = commonName;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    public ArchiveAttributeCoercion setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public Dimse getDIMSE() {
        return dimse;
    }

    public ArchiveAttributeCoercion setDIMSE(Dimse dimse) {
        this.dimse = dimse;
        return this;
    }

    public TransferCapability.Role getRole() {
        return role;
    }

    public ArchiveAttributeCoercion setRole(TransferCapability.Role role) {
        this.role = role;
        return this;
    }

    public String[] getSOPClasses() {
        return sopClasses;
    }

    public ArchiveAttributeCoercion setSOPClasses(String... sopClasses) {
        this.sopClasses = sopClasses;
        return this;
    }

    public String[] getAETitles() {
        return aeTitles;
    }

    public ArchiveAttributeCoercion setAETitles(String... aeTitles) {
        this.aeTitles = aeTitles;
        return this;
    }

    public String[] getHostNames() {
        return hostNames;
    }

    public ArchiveAttributeCoercion setHostNames(String... hostNames) {
        this.hostNames = hostNames;
        return this;
    }

    public String getXSLTStylesheetURI() {
        return xsltStylesheetURI;
    }

    public ArchiveAttributeCoercion setXSLTStylesheetURI(String xsltStylesheetURI) {
        this.xsltStylesheetURI = xsltStylesheetURI;
        return this;
    }

    public boolean isNoKeywords() {
        return noKeywords;
    }

    public ArchiveAttributeCoercion setNoKeywords(boolean noKeywords) {
        this.noKeywords = noKeywords;
        return this;
    }

    public String getLeadingCFindSCP() {
        return leadingCFindSCP;
    }

    public ArchiveAttributeCoercion setLeadingCFindSCP(String leadingCFindSCP) {
        this.leadingCFindSCP = leadingCFindSCP;
        return this;
    }

    public MergeMWLMatchingKey getMergeMWLMatchingKey() {
        return mergeMWLMatchingKey;
    }

    public ArchiveAttributeCoercion setMergeMWLMatchingKey(MergeMWLMatchingKey mergeMWLMatchingKey) {
        this.mergeMWLMatchingKey = mergeMWLMatchingKey;
        return this;
    }

    public String getMergeMWLTemplateURI() {
        return mergeMWLTemplateURI;
    }

    public ArchiveAttributeCoercion setMergeMWLTemplateURI(String mergeMWLTemplateURI) {
        this.mergeMWLTemplateURI = mergeMWLTemplateURI;
        return this;
    }

    public Attributes.UpdatePolicy getAttributeUpdatePolicy() {
        return attributeUpdatePolicy;
    }

    public ArchiveAttributeCoercion setAttributeUpdatePolicy(Attributes.UpdatePolicy attributeUpdatePolicy) {
        this.attributeUpdatePolicy = attributeUpdatePolicy;
        return this;
    }

    public int[] getNullifyTags() {
        return nullifyTags;
    }

    public void setNullifyTags(int[] nullifyTags) {
        this.nullifyTags = nullifyTags;
    }

    public final Device getSupplementFromDevice() {
        return supplementFromDevice;
    }

    public String getSupplementFromDeviceName() {
        if (supplementFromDevice == null)
            throw new IllegalStateException("SupplementFromDevice not initialized");
        return supplementFromDevice.getDeviceName();
    }

    public ArchiveAttributeCoercion setSupplementFromDevice(Device supplementFromDevice) {
        this.supplementFromDevice = supplementFromDevice;
        return this;
    }

    public boolean match(String hostName, String aet, TransferCapability.Role role, Dimse dimse, String sopClass) {
        return this.role == role && this.dimse == dimse
                && isEmptyOrContains(hostNames, hostName)
                && isEmptyOrContains(aeTitles, aet)
                && isEmptyOrContains(sopClasses, sopClass);
    }

    private static boolean isEmptyOrContains(Object[] a, Object o) {
        if (a.length == 0)
            return true;

        if (o != null)
            for (Object o1 : a)
                if (o1.equals(o))
                    return true;
        return false;
    }

    @Override
    public String toString() {
        return "ArchiveAttributeCoercion[cn=" + commonName
                + ", priority=" + priority
                + ", DIMSE=" + dimse
                + ", role=" + role
                + ", aets=" + Arrays.toString(aeTitles)
                + ", hostNames=" + Arrays.toString(hostNames)
                + ", cuids=" + Arrays.toString(sopClasses)
                + ", xslturi=" + xsltStylesheetURI
                + ", noKeywords=" + noKeywords
                + ", leadingCFindSCP=" + leadingCFindSCP
                + ", mergeMWLMatchingKey=" + mergeMWLMatchingKey
                + ", mergeMWLTemplateURI=" + mergeMWLTemplateURI
                + ", attributeUpdatePolicy=" + attributeUpdatePolicy
                + ", supplementFromDeviceName="
                + (supplementFromDevice != null ? supplementFromDevice.getDeviceName() : null)
                + "]";
    }
}
