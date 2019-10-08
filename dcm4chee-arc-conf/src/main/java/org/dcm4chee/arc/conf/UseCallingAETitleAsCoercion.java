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

import org.dcm4che3.data.*;
import org.dcm4che3.dict.archive.PrivateTag;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Oct 2019
 */
public class UseCallingAETitleAsCoercion implements AttributesCoercion {
    private final Type type;
    private final String callingAET;
    private final AttributesCoercion next;

    private UseCallingAETitleAsCoercion(Type type, String callingAET, AttributesCoercion next) {
        this.type = type;
        this.callingAET = callingAET;
        this.next = next;
    }

    public static AttributesCoercion of(Type type, String callingAET, AttributesCoercion next) {
        return type != null ? new UseCallingAETitleAsCoercion(type, callingAET, next) : next;
    }

    @Override
    public String remapUID(String uid) {
        return next != null ? next.remapUID(uid) : uid;
    }

    @Override
    public void coerce(Attributes attrs, Attributes modified) {
        type.coerce(attrs, modified, callingAET);
        if (next != null)
            next.coerce(attrs, modified);
    }

    private static Attributes ensureItem(Sequence sq) {
        if (sq.isEmpty()) {
            sq.add(new Attributes(1));
        }
        return sq.get(0);
    }

    public enum Type {
        ScheduledStationAETitle {
            @Override
            void coerce(Attributes attrs, Attributes modified, String callingAET) {
                Attributes sps = ensureItem(attrs.ensureSequence(Tag.ScheduledProcedureStepSequence, 1));
                if (!sps.containsValue(Tag.ScheduledStationAETitle)) {
                    sps.setString(Tag.ScheduledStationAETitle, VR.AE, callingAET);
                }
            }
        },
        SendingApplicationEntityTitleOfSeries {
            @Override
            void coerce(Attributes attrs, Attributes modified, String callingAET) {
                if (!attrs.containsValue(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries)) {
                    attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries,
                            VR.AE, callingAET);
                }
            }
        };

        abstract void coerce(Attributes attrs, Attributes modified, String callingAET);
    }
}
