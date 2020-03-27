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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Feb 2020
 */
public class UPSProcessingRule {
    public static final Duration DEFAULT_RETRY_DELAY = Duration.valueOf("PT1M");

    private String commonName;
    private URI upsProcessorURI;
    private UPSPriority upsPriority;
    private InputReadinessState inputReadinessState = InputReadinessState.READY;
    private String procedureStepLabel;
    private String worklistLabel;
    private Code scheduledWorkitemCode;
    private Code scheduledStationName;
    private Code scheduledStationClass;
    private Code scheduledStationLocation;
    private String aeTitle;
    private int maxThreads = 1;
    private int maxRetries = 0;
    private Duration retryDelay = DEFAULT_RETRY_DELAY;
    private Duration maxRetryDelay;
    private int retryDelayMultiplier = 100;
    private boolean retryOnWarning;
    private ScheduleExpression[] schedules = {};
    private final Map<String, String> properties = new HashMap<>();

    public UPSProcessingRule() {}

    public UPSProcessingRule(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public URI getUPSProcessorURI() {
        return upsProcessorURI;
    }

    public void setUPSProcessorURI(URI upsProcessorURI) {
        this.upsProcessorURI = upsProcessorURI;
    }

    public UPSPriority getUPSPriority() {
        return upsPriority;
    }

    public void setUPSPriority(UPSPriority upsPriority) {
        this.upsPriority = upsPriority;
    }

    public InputReadinessState getInputReadinessState() {
        return inputReadinessState;
    }

    public void setInputReadinessState(InputReadinessState inputReadinessState) {
        this.inputReadinessState = inputReadinessState;
    }

    public String getProcedureStepLabel() {
        return procedureStepLabel;
    }

    public void setProcedureStepLabel(String procedureStepLabel) {
        this.procedureStepLabel = procedureStepLabel;
    }

    public String getWorklistLabel() {
        return worklistLabel;
    }

    public void setWorklistLabel(String worklistLabel) {
        this.worklistLabel = worklistLabel;
    }

    public Code getScheduledWorkitemCode() {
        return scheduledWorkitemCode;
    }

    public void setScheduledWorkitemCode(Code scheduledWorkitemCode) {
        this.scheduledWorkitemCode = scheduledWorkitemCode;
    }

    public Code getScheduledStationName() {
        return scheduledStationName;
    }

    public void setScheduledStationName(Code scheduledStationName) {
        this.scheduledStationName = scheduledStationName;
    }

    public Code getScheduledStationClass() {
        return scheduledStationClass;
    }

    public void setScheduledStationClass(Code scheduledStationClass) {
        this.scheduledStationClass = scheduledStationClass;
    }

    public Code getScheduledStationLocation() {
        return scheduledStationLocation;
    }

    public void setScheduledStationLocation(Code scheduledStationLocation) {
        this.scheduledStationLocation = scheduledStationLocation;
    }

    public String getAETitle() {
        return aeTitle;
    }

    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public long getRetryDelayInSeconds(int retry) {
        if (retry > maxRetries)
            return -1L;

        long delay = retryDelay.getSeconds();
        while (--retry > 0)
            delay = delay * retryDelayMultiplier / 100;

        return maxRetryDelay != null ? Math.min(delay, maxRetryDelay.getSeconds()) : delay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = Objects.requireNonNull(retryDelay, "RetryDelay");
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    public int getRetryDelayMultiplier() {
        return retryDelayMultiplier;
    }

    public void setRetryDelayMultiplier(int retryDelayMultiplier) {
        this.retryDelayMultiplier = retryDelayMultiplier;
    }

    public boolean isRetryOnWarning() {
        return retryOnWarning;
    }

    public void setRetryOnWarning(boolean retryOnWarning) {
        this.retryOnWarning = retryOnWarning;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression... schedules) {
        this.schedules = schedules;
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public String getProperty(String name, String defValue) {
        String value = properties.get(name);
        return value != null ? value : defValue;
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
        return "UPSProcessingRule{" +
                "cn='" + commonName + '\'' +
                '}';
    }
}
