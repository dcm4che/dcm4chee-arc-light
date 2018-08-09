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

import org.dcm4che3.util.Property;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DelayedCompressionRule {
    public enum UsageFlag { MATCH, NO_MATCH };

    private String commonName;

    private String[] sopClassUIDs = {};

    private String[] sourceTransferSyntaxUIDs = {};

    private String[] sourceAETitles = {};

    private UsageFlag sourceAETitleUsageFlag = UsageFlag.MATCH;

    private String[] stationNames = {};

    private UsageFlag stationNameUsageFlag = UsageFlag.MATCH;

    private Duration delay;

    private String transferSyntax;

    private Property[] imageWriteParams = {};

    public DelayedCompressionRule() {
    }

    public DelayedCompressionRule(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String[] getSOPClassUIDs() {
        return sopClassUIDs;
    }

    public void setSOPClassUIDs(String[] sopClassUIDs) {
        this.sopClassUIDs = sopClassUIDs;
    }

    public String[] getSourceTransferSyntaxUIDs() {
        return sourceTransferSyntaxUIDs;
    }

    public void setSourceTransferSyntaxUIDs(String[] sourceTransferSyntaxUIDs) {
        this.sourceTransferSyntaxUIDs = sourceTransferSyntaxUIDs;
    }

    public String[] getSourceAETitles() {
        return sourceAETitles;
    }

    public void setSourceAETitles(String[] sourceAETitles) {
        this.sourceAETitles = sourceAETitles;
    }

    public UsageFlag getSourceAETitleUsageFlag() {
        return sourceAETitleUsageFlag;
    }

    public void setSourceAETitleUsageFlag(UsageFlag sourceAETitleUsageFlag) {
        this.sourceAETitleUsageFlag = sourceAETitleUsageFlag;
    }

    public String[] getStationNames() {
        return stationNames;
    }

    public void setStationNames(String[] stationNames) {
        this.stationNames = stationNames;
    }

    public UsageFlag getStationNameUsageFlag() {
        return stationNameUsageFlag;
    }

    public void setStationNameUsageFlag(UsageFlag stationNameUsageFlag) {
        this.stationNameUsageFlag = stationNameUsageFlag;
    }

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public String getTransferSyntax() {
        return transferSyntax;
    }

    public void setTransferSyntax(String transferSyntax) {
        this.transferSyntax = transferSyntax;
    }

    public Property[] getImageWriteParams() {
        return imageWriteParams;
    }

    public void setImageWriteParams(Property[] imageWriteParams) {
        this.imageWriteParams = imageWriteParams;
    }

    @Override
    public String toString() {
        return "DelayedCompressionRule{" +
                "cn=" + commonName +
                ", sopClassUIDs=" + Arrays.toString(sopClassUIDs) +
                ", sourceTransferSyntaxUIDs=" + Arrays.toString(sourceTransferSyntaxUIDs) +
                ", sourceAETitles=" + Arrays.toString(sourceAETitles) +
                ", sourceAETitleUsageFlag=" + sourceAETitleUsageFlag +
                ", stationNames=" + Arrays.toString(stationNames) +
                ", stationNameUsageFlag=" + stationNameUsageFlag +
                ", delay=" + delay +
                ", transferSyntax=" + transferSyntax +
                ", imageWriteParams=" + Arrays.toString(imageWriteParams) +
                '}';
    }
}
