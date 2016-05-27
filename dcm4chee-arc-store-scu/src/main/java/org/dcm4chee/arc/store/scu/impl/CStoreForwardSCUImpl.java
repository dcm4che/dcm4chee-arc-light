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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.store.scu.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.scu.CStoreForwardSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2016
 */
@ApplicationScoped
public class CStoreForwardSCUImpl implements CStoreForwardSCU {

    @Inject
    @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    private final Map<MoveOriginator,CStoreForward> registry = new HashMap<>();

    @Override
    public synchronized int activate(RetrieveContext ctx) {
        String aeTitle = ctx.getMoveOriginatorAETitle();
        MoveOriginator key = new MoveOriginator(aeTitle, ctx.getMoveOriginatorMessageID());
        CStoreForward forward = registry.get(key);
        if (forward == null) {
            forward = new CStoreForward(ctx, retrieveEnd);
            registry.put(key, forward);
            switch (ctx.getQueryRetrieveLevel()) {
                case STUDY:
                    for (String studyIUID : ctx.getStudyInstanceUIDs()) {
                       registry.put(new MoveOriginator(aeTitle, studyIUID, null, null), forward);
                    }
                    break;
                case SERIES:
                    for (String seriesIUID : ctx.getSeriesInstanceUIDs()) {
                        registry.put(new MoveOriginator(aeTitle, ctx.getStudyInstanceUID(), seriesIUID, null), forward);
                    }
                    break;
                case IMAGE:
                    for (String sopIUID : ctx.getSopInstanceUIDs()) {
                        registry.put(new MoveOriginator(aeTitle, ctx.getStudyInstanceUID(),
                                ctx.getSeriesInstanceUID(), sopIUID), forward);
                    }
                    break;
            }
        }
        return forward.activate();
    }

    @Override
    public synchronized int deactivate(RetrieveContext ctx) {
        String aeTitle = ctx.getMoveOriginatorAETitle();
        MoveOriginator key = new MoveOriginator(aeTitle, ctx.getMoveOriginatorMessageID());
        CStoreForward forward = registry.get(key);
        if (forward == null)
            return -1;

        int active = forward.deactivate();
        if (active == 0) {
            registry.remove(key);
            switch (ctx.getQueryRetrieveLevel()) {
                case STUDY:
                    for (String studyIUID : ctx.getStudyInstanceUIDs()) {
                        registry.remove(new MoveOriginator(aeTitle, studyIUID, null, null));
                    }
                    break;
                case SERIES:
                    for (String seriesIUID : ctx.getSeriesInstanceUIDs()) {
                        registry.remove(new MoveOriginator(aeTitle, ctx.getStudyInstanceUID(), seriesIUID, null));
                    }
                    break;
                case IMAGE:
                    for (String sopIUID : ctx.getSopInstanceUIDs()) {
                        registry.remove(new MoveOriginator(aeTitle, ctx.getStudyInstanceUID(),
                                ctx.getSeriesInstanceUID(), sopIUID));
                    }
                    break;
            }
        }
        return active;
    }

    public void onStore(@Observes StoreContext storeContext) {
        CStoreForward forward = forStoreContext(storeContext);
        if (forward != null)
            forward.onStore(storeContext);
    }

    private CStoreForward forStoreContext(StoreContext storeContext) {
        String aeTitle = storeContext.getMoveOriginatorAETitle();
        if (aeTitle == null)
            return null;

        CStoreForward forward = registry.get(new MoveOriginator(aeTitle, storeContext.getMoveOriginatorMessageID()));
        if (forward != null)
            return forward;

        Attributes attrs = storeContext.getAttributes();
        String studyIUID = attrs.getString(Tag.StudyInstanceUID);
        String seriesIUID = attrs.getString(Tag.SeriesInstanceUID);
        forward = registry.get(new MoveOriginator(aeTitle, studyIUID, seriesIUID, storeContext.getSopInstanceUID()));
        if (forward == null) {
            forward = registry.get(new MoveOriginator(aeTitle, studyIUID, seriesIUID, null));
            if (forward == null) {
                forward = registry.get(new MoveOriginator(aeTitle, studyIUID, null, null));
            }
        }
        return forward;
    }

    private static class MoveOriginator {
        final String aeTitle;
        final int messageID;
        final String studyIUID;
        final String seriesIUID;
        final String sopIUID;
        final int hash;

        MoveOriginator(String aeTitle, int messageID) {
            this(aeTitle, messageID, null, null, null);
        }

        MoveOriginator(String aeTitle, String studyIUID, String seriesIUID, String sopIUID) {
            this(aeTitle, -1, studyIUID, seriesIUID, sopIUID);
        }

        MoveOriginator(String aeTitle, int messageID, String studyIUID, String seriesIUID, String sopIUID) {
            this.aeTitle = aeTitle;
            this.messageID = messageID;
            this.studyIUID = studyIUID;
            this.seriesIUID = seriesIUID;
            this.sopIUID = sopIUID;
            int h = aeTitle.hashCode();
            h = 31 * h + messageID;
            h = 31 * h + (studyIUID != null ? studyIUID.hashCode() : 0);
            h = 31 * h + (seriesIUID != null ? seriesIUID.hashCode() : 0);
            h = 31 * h + (sopIUID != null ? sopIUID.hashCode() : 0);
            this.hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MoveOriginator that = (MoveOriginator) o;

            if (messageID != that.messageID) return false;
            if (!aeTitle.equals(that.aeTitle)) return false;
            if (studyIUID != null ? !studyIUID.equals(that.studyIUID) : that.studyIUID != null) return false;
            if (seriesIUID != null ? !seriesIUID.equals(that.seriesIUID) : that.seriesIUID != null) return false;
            return sopIUID != null ? sopIUID.equals(that.sopIUID) : that.sopIUID == null;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
