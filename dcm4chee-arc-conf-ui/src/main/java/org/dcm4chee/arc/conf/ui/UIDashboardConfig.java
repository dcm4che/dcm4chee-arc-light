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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @since Dec 2017
 */
public class UIDashboardConfig {
    private String name;
    private String[] queueNames = {};
    private boolean showStarBlock = true;
    private String[] exportNames = {};
    private String[] deviceNames = {};
    private String countAet;
    private String[] ignoreParams = {};
    private String[] dockerContainers = {};
    private final Map<String,UICompareSide> compareSide = new HashMap<>();


    public UIDashboardConfig() {
    }

    public UIDashboardConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getQueueNames() {
        return queueNames;
    }

    public void setQueueNames(String[] queueNames) {
        this.queueNames = queueNames;
    }

    public boolean isShowStarBlock() {
        return showStarBlock;
    }

    public void setShowStarBlock(boolean showStarBlock) {
        this.showStarBlock = showStarBlock;
    }

    public String[] getExportNames() {
        return exportNames;
    }

    public void setExportNames(String[] exportNames) {
        this.exportNames = exportNames;
    }

    public String[] getDeviceNames() {
        return deviceNames;
    }

    public void setDeviceNames(String[] deviceNames) {
        this.deviceNames = deviceNames;
    }

    public String getCountAet() { return countAet; }

    public void setCountAet(String countAet) { this.countAet = countAet; }

    public String[] getIgnoreParams() {
        return ignoreParams;
    }

    public void setIgnoreParams(String[] ignoreParams) {
        this.ignoreParams = ignoreParams;
    }

    public void addCompareSide(UICompareSide side){
        this.compareSide.put(side.getName(),side);
    }

    public UICompareSide removeCompareSide(String name){
        return this.compareSide.remove(name);
    }

    public UICompareSide getCompareSide(String name){
        return this.compareSide.get(name);
    }

    public Collection<UICompareSide> getCompareSides(){
        return this.compareSide.values();
    }

    public String[] getDockerContainers() {
        return dockerContainers;
    }

    public void setDockerContainers(String[] dockerContainers) {
        this.dockerContainers = dockerContainers;
    }
}
