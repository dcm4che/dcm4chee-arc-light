/*
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
 */

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;

import java.util.Arrays;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Apr 2025
 */
public class ChangeAccessControlIDRule {

    public static final ChangeAccessControlIDRule[] EMPTY = {};
    private String commonName;
    private String aeTitle;
    private String storeAccessControlID;
    private Entity entity = Entity.Study;
    private Duration delay;
    private Duration maxDelay;
    private EntitySelector[] entitySelectors = {};

    public static class EntitySelector {
        final String value;
        final Attributes keys = new Attributes();

        public EntitySelector(String value) {
            this.value = value;
            AttributesBuilder builder = new AttributesBuilder(keys);
            for (String queryParam : StringUtils.split(value, '&')) {
                String[] keyValue = StringUtils.split(queryParam, '=');
                if (keyValue.length != 2)
                    throw new IllegalArgumentException(queryParam);

                try {
                    builder.setString(TagUtils.parseTagPath(keyValue[0]), keyValue[1]);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(value);
                }
            }
        }

        @Override
        public String toString() {
            return value;
        }

        public Attributes getQueryKeys(ChangeAccessControlIDRule rule) {
            return rule.setStudyReceiveDateTimeRange(new Attributes(this.keys));
        }
    }

    public Attributes getQueryKeys() {
        return setStudyReceiveDateTimeRange(new Attributes(1));
    }

    private Attributes setStudyReceiveDateTimeRange(Attributes queryKeys) {
        long now = System.currentTimeMillis();
        queryKeys.setDateRange(PrivateTag.PrivateCreator, PrivateTag.StudyReceiveDateTime, VR.DT,
                new DateRange(
                        maxDelay != null ? new Date(now - maxDelay.getSeconds() * 1000L) : null,
                        new Date(now - delay.getSeconds() * 1000L)));
        return queryKeys;
    }

    public static EntitySelector[] entitySelectors(String... ss) {
        EntitySelector[] selectors = new EntitySelector[ss.length];
        for (int i = 0; i < ss.length; i++)
            selectors[i] = new EntitySelector(ss[i]);
        return selectors;
    }

    public ChangeAccessControlIDRule() {
    }

    public ChangeAccessControlIDRule(String commonName) {
        setCommonName(commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getAETitle() {
        return aeTitle;
    }

    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getStoreAccessControlID() {
        return storeAccessControlID;
    }

    public void setStoreAccessControlID(String storeAccessControlID) {
        this.storeAccessControlID = storeAccessControlID;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        switch (entity) {
            case Study:
            case Series:
                break;
            default:
                throw new IllegalArgumentException(entity.toString());
        }
        this.entity = entity;
    }

    public EntitySelector[] getEntitySelectors() {
        return entitySelectors;
    }

    public void setEntitySelectors(EntitySelector[] entitySelectors) {
        this.entitySelectors = entitySelectors;
    }

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public void setMaxDelay(Duration maxDelay) {
        this.maxDelay = maxDelay;
    }

    @Override
    public String toString() {
        return "ChangeAccessControlIDRule{"  +
                "commonName='" + commonName + '\'' +
                ", storeAccessControlID='" + storeAccessControlID + '\'' +
                ", entity=" + entity +
                ", selectors=" + Arrays.toString(entitySelectors) +
                ", delay=" + delay +
                ", maxDelay=" + maxDelay +
                '}';
    }
}
