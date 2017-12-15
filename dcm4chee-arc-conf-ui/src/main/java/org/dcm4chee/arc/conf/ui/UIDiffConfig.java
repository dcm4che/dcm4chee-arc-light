/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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
 * ** BEGIN LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf.ui;

import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2017
 */
public class UIDiffConfig {
    private String name;
    private String callingAET;
    private String primaryCFindSCP;
    private String primaryCMoveSCP;
    private String primaryCStoreSCP;
    private String secondaryCFindSCP;
    private String secondaryCMoveSCP;
    private String secondaryCStoreSCP;
    private final Map<String,UIDiffCriteria> criterias = new HashMap<>();

    public UIDiffConfig() {
    }

    public UIDiffConfig(String commonName) {
        setName(commonName);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCallingAET() {
        return callingAET;
    }

    public void setCallingAET(String callingAET) {
        this.callingAET = callingAET;
    }

    public String getPrimaryCFindSCP() {
        return primaryCFindSCP;
    }

    public void setPrimaryCFindSCP(String primaryCFindSCP) {
        this.primaryCFindSCP = primaryCFindSCP;
    }

    public String getPrimaryCMoveSCP() {
        return primaryCMoveSCP;
    }

    public void setPrimaryCMoveSCP(String primaryCMoveSCP) {
        this.primaryCMoveSCP = primaryCMoveSCP;
    }

    public String getSecondaryCFindSCP() {
        return secondaryCFindSCP;
    }

    public void setSecondaryCFindSCP(String secondaryCFindSCP) {
        this.secondaryCFindSCP = secondaryCFindSCP;
    }

    public String getSecondaryCMoveSCP() {
        return secondaryCMoveSCP;
    }

    public void setSecondaryCMoveSCP(String secondaryCMoveSCP) {
        this.secondaryCMoveSCP = secondaryCMoveSCP;
    }

    public String getPrimaryCStoreSCP() {
        return primaryCStoreSCP;
    }

    public void setPrimaryCStoreSCP(String primaryCStoreSCP) {
        this.primaryCStoreSCP = primaryCStoreSCP;
    }

    public String getSecondaryCStoreSCP() {
        return secondaryCStoreSCP;
    }

    public void setSecondaryCStoreSCP(String secondaryCStoreSCP) {
        this.secondaryCStoreSCP = secondaryCStoreSCP;
    }

    public void addCriteria(UIDiffCriteria criteria) {
        criterias.put(criteria.getTitle(), criteria);
    }

    public UIDiffCriteria removeCriteria(String name) {
        return criterias.remove(name);
    }

    public UIDiffCriteria getCriteria(String name) {
        return criterias.get(name);
    }

    public Collection<UIDiffCriteria> getCriterias() {
        return criterias.values();
    }

    public UIDiffCriteria[] getSortedCriterias() {
        UIDiffCriteria[] a = criterias.values().toArray(new UIDiffCriteria[criterias.size()]);
        Arrays.sort(a, Comparator.comparing(UIDiffCriteria::getNumber));
        return a;
    }
}
