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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2021
 */
public class ArchiveAttributeCoercion2 {
    public enum OnFailure { RETHROW, CONTINUE, SUFFICIENT }
    public static final String NULLIFY_PIXEL_DATA = "nullify-pixel-data";
    public static final String RETRIEVE_AS_RECEIVED = "retrieve-as-received";
    private String commonName;
    private String description;
    private String uri;
    private int priority;
    private Dimse dimse;
    private TransferCapability.Role role;
    private String[] sopClasses = {};
    private Conditions conditions = new Conditions();
    private boolean sufficient;
    private OnFailure onFailure = OnFailure.RETHROW;
    private Attributes.UpdatePolicy attributeUpdatePolicy = Attributes.UpdatePolicy.MERGE;
    private MergeAttribute[] mergeAttributes = {};
    private Device otherDevice;
    private final Map<String, String> params = new HashMap<>();
    private String scheme;
    private String schemeSpecificPart;

    public ArchiveAttributeCoercion2() {
    }

    public ArchiveAttributeCoercion2(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public ArchiveAttributeCoercion2 setCommonName(String commonName) {
        this.commonName = commonName;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ArchiveAttributeCoercion2 setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getURI() {
        return uri;
    }

    public ArchiveAttributeCoercion2 setURI(String uri) {
        int index = uri.indexOf(':');
        if (index < 0) throw new IllegalArgumentException(uri);
        this.uri = uri;
        this.scheme = uri.substring(0, index);
        this.schemeSpecificPart = uri.substring(index + 1);
        return this;
    }

    public String getScheme() {
        return scheme;
    }

    public String getSchemeSpecificPart() {
        return schemeSpecificPart;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Dimse getDIMSE() {
        return dimse;
    }

    public ArchiveAttributeCoercion2 setDIMSE(Dimse dimse) {
        this.dimse = dimse;
        return this;
    }

    public TransferCapability.Role getRole() {
        return role;
    }

    public ArchiveAttributeCoercion2 setRole(TransferCapability.Role role) {
        this.role = role;
        return this;
    }

    public String[] getSOPClasses() {
        return sopClasses;
    }

    public ArchiveAttributeCoercion2 setSOPClasses(String... sopClasses) {
        this.sopClasses = sopClasses;
        return this;
    }

    public Conditions getConditions() {
        return conditions;
    }

    public void setConditions(Conditions conditions) {
        this.conditions = conditions;
    }

    public ArchiveAttributeCoercion2 setSendingHostname(String hostname) {
        conditions.setSendingHostname(hostname);
        return this;
    }

    public ArchiveAttributeCoercion2 setSendingAETitle(String aet) {
        conditions.setSendingAETitle(aet);
        return this;
    }

    public ArchiveAttributeCoercion2 setReceivingHostname(String hostname) {
        conditions.setReceivingHostname(hostname);
        return this;
    }

    public ArchiveAttributeCoercion2 setReceivingAETitle(String aet) {
        conditions.setReceivingAETitle(aet);
        return this;
    }

    public MergeAttribute[] getMergeAttributes() {
        return mergeAttributes;
    }

    public ArchiveAttributeCoercion2 setMergeAttributes(String... mergeAttributes) {
        this.mergeAttributes = MergeAttribute.of(mergeAttributes);
        return this;
    }

    public Attributes.UpdatePolicy getAttributeUpdatePolicy() {
        return attributeUpdatePolicy;
    }

    public ArchiveAttributeCoercion2 setAttributeUpdatePolicy(Attributes.UpdatePolicy attributeUpdatePolicy) {
        this.attributeUpdatePolicy = attributeUpdatePolicy;
        return this;
    }

    public final Device getOtherDevice() {
        return otherDevice;
    }

    public String getOtherDeviceName() {
        if (otherDevice == null)
            throw new IllegalStateException("OtherDevice not initialized");
        return otherDevice.getDeviceName();
    }

    public ArchiveAttributeCoercion2 setOtherDevice(Device otherDevice) {
        this.otherDevice = otherDevice;
        return this;
    }

    public boolean isCoercionSufficient() {
        return sufficient;
    }

    public ArchiveAttributeCoercion2 setCoercionSufficient(boolean sufficient) {
        this.sufficient = sufficient;
        return this;
    }

    public OnFailure getCoercionOnFailure() {
        return onFailure;
    }

    public void setCoercionOnFailure(OnFailure onFailure) {
        this.onFailure = onFailure;
    }

    public ArchiveAttributeCoercion2 setCoercionParam(String name, String value) {
        params.put(name, value);
        return this;
    }

    public String getCoercionParam(String name, String defValue) {
        String value = params.get(name);
        return value != null ? value : defValue;
    }

    public boolean parseBooleanCoercionParam(String name) {
        return Boolean.parseBoolean(params.get(name));
    }

    public Map<String,String> getCoercionParams() {
        return params;
    }

    public void setCoercionParams(String[] ss) {
        params.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("Coercion Param in incorrect format : " + s);
            setCoercionParam(s.substring(0, index), s.substring(index+1));
        }
    }

    public boolean match(TransferCapability.Role role, Dimse dimse, String sopClass,
                         String sendingHost, String sendingAET, String receivingHost, String receivingAET,
                         Attributes attrs) {
        return this.role == role && this.dimse == dimse && matchSOPClass(sopClass)
                && conditions.match(sendingHost, sendingAET, receivingHost, receivingAET, attrs);
    }

    private boolean matchSOPClass(String sopClass) {
        if (sopClasses.length == 0)
            return true;

        if (sopClass != null)
            for (Object o1 : sopClasses)
                if (o1.equals(sopClass))
                    return true;
        return false;
    }

    public static boolean containsScheme(Collection<ArchiveAttributeCoercion2> list, String scheme) {
        for (ArchiveAttributeCoercion2 coercion : list) {
            if (scheme.equals(coercion.scheme)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ArchiveAttributeCoercion2[cn=" + commonName
                + ", priority=" + priority
                + ", DIMSE=" + dimse
                + ", role=" + role
                + ", cuids=" + Arrays.toString(sopClasses)
                + ", conditions=" + conditions.toString()
                + ", uri=" + uri
                + ", description=" + description
                + ", onFailure=" + onFailure.name()
                + ", sufficient=" + sufficient
                + ", attributeUpdatePolicy=" + attributeUpdatePolicy.name()
                + ", mergeAttributes=" + Arrays.toString(mergeAttributes)
                + ", deviceCoercionParam=" + (otherDevice != null ? otherDevice.getDeviceName() : null)
                + ", otherCoercionParams=" + params
                + "]";
    }
}
