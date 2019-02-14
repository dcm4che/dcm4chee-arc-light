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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.ValueSelector;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class AttributeFilter implements Serializable {

    private static final long serialVersionUID = -2417549681350544302L;

    private int[] selection;
    private Attributes.UpdatePolicy attributeUpdatePolicy = Attributes.UpdatePolicy.PRESERVE;
    private ValueSelector customAttribute1;
    private ValueSelector customAttribute2;
    private ValueSelector customAttribute3;

    public AttributeFilter() {
    }

    public AttributeFilter(int... selection) {
        setSelection(selection);
    }

    public int[] getSelection() {
        return selection;
    }

    public int[] getSelection(boolean withOriginalAttributesSequence) {
        int index = Arrays.binarySearch(selection, Tag.OriginalAttributesSequence);
        if (withOriginalAttributesSequence) {
            if (index < 0)
                return includeOriginalAttributesSequence(selection, -(index + 1));
        } else {
            if (index >= 0) {
                return removeOriginalAttributesSequence(selection, index);
            }
        }
        return selection;
    }

    private static int[] includeOriginalAttributesSequence(int[] selection, int index) {
        int[] result = new int[selection.length + 1];
        System.arraycopy(selection, 0, result, 0, index);
        result[index] = Tag.OriginalAttributesSequence;
        System.arraycopy(selection, index, result, index + 1, selection.length - index);
        return result;
    }

    private static int[] removeOriginalAttributesSequence(int[] selection, int index) {
        int[] result = new int[selection.length - 1];
        System.arraycopy(selection, 0, result, 0, index);
        System.arraycopy(selection, index + 1, result, index - 1, selection.length - index - 1);
        return result;
    }

    public void setSelection(int[] selection) {
        Arrays.sort(this.selection = selection);
    }

    public Attributes.UpdatePolicy getAttributeUpdatePolicy() {
        return attributeUpdatePolicy;
    }

    public void setAttributeUpdatePolicy(Attributes.UpdatePolicy attributeUpdatePolicy) {
        this.attributeUpdatePolicy = attributeUpdatePolicy;
    }

    public static String selectStringValue(Attributes attrs, ValueSelector selector, String defVal) {
        return selector != null ? selector.selectStringValue(attrs, defVal) : defVal;
    }

    public void setCustomAttribute1(ValueSelector customAttribute1) {
        this.customAttribute1 = customAttribute1;
    }

    public ValueSelector getCustomAttribute1() {
        return customAttribute1;
    }

    public void setCustomAttribute2(ValueSelector customAttribute2) {
        this.customAttribute2 = customAttribute2;
    }

    public ValueSelector getCustomAttribute2() {
        return customAttribute2;
    }

    public void setCustomAttribute3(ValueSelector customAttribute3) {
        this.customAttribute3 = customAttribute3;
    }

    public ValueSelector getCustomAttribute3() {
        return customAttribute3;
    }

}
