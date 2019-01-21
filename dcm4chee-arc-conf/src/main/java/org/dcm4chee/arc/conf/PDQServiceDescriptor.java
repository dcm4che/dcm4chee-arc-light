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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
public class PDQServiceDescriptor {

    private String pdqServiceID;
    private String description;
    private URI pdqServiceURI;
    private int[] selection = {};
    private final Map<String, String> properties = new HashMap<>();
    private Entity entity = Entity.Patient;

    public PDQServiceDescriptor() {
    }

    public PDQServiceDescriptor(String pdqServiceID) {
        this.pdqServiceID = pdqServiceID;
    }

    public String getPDQServiceID() {
        return pdqServiceID;
    }

    public void setPDQServiceID(String pdqServiceID) {
        this.pdqServiceID = pdqServiceID;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public URI getPDQServiceURI() {
        return pdqServiceURI;
    }

    public void setPDQServiceURI(URI pdqServiceURI) {
        this.pdqServiceURI = pdqServiceURI;
    }

    public int[] getSelection() {
        return selection;
    }

    public void setSelection(int[] selection) {
        Arrays.sort(this.selection = selection);
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public String getProperty(String name, String defValue) {
        String value = properties.get(name);
        return value != null ? value : defValue;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        switch (entity) {
            case Study:
            case Patient:
                this.entity = entity;
                break;
            default:
                throw new IllegalArgumentException("entity: " + entity);
        }
    }

    public Map<String,String> getProperties() {
        return properties;
    }

    public void setProperties(String[] ss) {
        properties.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("Property in incorrect format : " + s);
            setProperty(s.substring(0, index), s.substring(index+1));
        }
    }

    @Override
    public String toString() {
        return "PDQService{id=" + pdqServiceID + ", uri=" + pdqServiceURI + '}';
    }
}
