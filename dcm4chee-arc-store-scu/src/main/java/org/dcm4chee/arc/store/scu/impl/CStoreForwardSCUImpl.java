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

import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.scu.CStoreForwardSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2016
 */
@ApplicationScoped
public class CStoreForwardSCUImpl implements CStoreForwardSCU {

    private final Map<MoveOriginator,CStoreForward> registry = new HashMap<>();

    @Override
    public synchronized boolean activateCStoreForward(RetrieveContext ctx) {
        MoveOriginator key = new MoveOriginator(ctx);
        if (registry.containsKey(key))
            return false;
        registry.put(key, new CStoreForward(ctx));
        return true;
    }

    @Override
    public synchronized boolean deactivateCStoreForward(RetrieveContext ctx) {
        return registry.remove(new MoveOriginator(ctx)) != null;
    }

    public void onStore(@Observes StoreContext storeContext) {
        CStoreForward entry = forStoreContext(storeContext);
        if (entry != null)
            entry.onStore(storeContext);
    }

    private CStoreForward forStoreContext(StoreContext storeContext) {
        String aeTitle = storeContext.getMoveOriginatorAETitle();
        return aeTitle != null
                ? registry.get(new MoveOriginator(aeTitle, storeContext.getMoveOriginatorMessageID()))
                : null;
    }

    private static class MoveOriginator {
        final String aeTitle;
        final int messageID;
        final int hash;

        MoveOriginator(RetrieveContext retrieveCtx) {
            this(retrieveCtx.getMoveOriginatorAETitle(), retrieveCtx.getMoveOriginatorMessageID());
        }

        MoveOriginator(String aeTitle, int messageID) {
            this.aeTitle = aeTitle;
            this.messageID = messageID;
            this.hash = 31 * aeTitle.hashCode() + messageID;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MoveOriginator that = (MoveOriginator) o;
            return aeTitle.equals(that.aeTitle) && messageID == that.messageID;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
