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

package org.dcm4chee.arc.ups;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4chee.arc.conf.ArchiveAEExtension;

import java.util.List;
import java.util.Optional;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
public class UPSEvent {
    public final Type type;
    public final String upsIUID;
    public final Attributes attrs;
    public final List<String> subscriberAETs;
    public final ArchiveAEExtension arcAE;

    public UPSEvent(ArchiveAEExtension arcAE, Type type, String upsIUID, Attributes attrs,
            List<String> subscriberAETs) {
        this.arcAE = arcAE;
        this.type = type;
        this.upsIUID = upsIUID;
        this.attrs = attrs;
        this.subscriberAETs = subscriberAETs;
    }

    public Optional<Attributes> inprocessStateReport() {
        if (type != Type.StateReportInProcessAndCanceled) {
            return Optional.empty();
        }
        Attributes eventInformation = new Attributes(6);
        eventInformation.setString(Tag.InputReadinessState, VR.CS, attrs.getString(Tag.InputReadinessState));
        eventInformation.setString(Tag.ProcedureStepState, VR.CS, "IN PROGRESS");
        return Optional.of(eventInformation);
    }

    public Attributes withCommandAttributes(Attributes src, int messageID) {
        Attributes dest = new Attributes(src);
        dest.setString(Tag.AffectedSOPClassUID, VR.UI, UID.UnifiedProcedureStepPushSOPClass);
        dest.setInt(Tag.MessageID, VR.US, messageID);
        dest.setString(Tag.AffectedSOPInstanceUID, VR.UI, upsIUID);
        dest.setInt(Tag.EventTypeID, VR.US, type.eventTypeID());
        return dest;
    }

    public enum Type {
        StateReportInProcessAndCanceled {
            @Override
            public int eventTypeID() {
                return 1;
            }
        },
        StateReport,
        CancelRequested,
        ProgressReport,
        StatusChange,
        Assigned;

        public int eventTypeID() {
            return ordinal();
        }
    }
}
