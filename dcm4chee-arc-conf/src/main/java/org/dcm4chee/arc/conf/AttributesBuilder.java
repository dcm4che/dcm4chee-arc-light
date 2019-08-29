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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.util.TagUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2016
 */
public class AttributesBuilder {

    private static ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

    private final Attributes attrs;

    public AttributesBuilder(Attributes attrs) {
        this.attrs = attrs;
    }

    public void setString(int[] tagPath, String... ss) {
        int tag = tagPath[tagPath.length-1];
        VR vr = DICT.vrOf(tag);
        nestedKeys(tagPath).setString(tag, vr, ss);
    }

    public void setNullIfAbsent(int... tagPath) {
        int tag = tagPath[tagPath.length-1];
        Attributes item = nestedKeys(tagPath);
        if (TagUtils.groupNumber(tag) == 0x7777) {
            if (!item.contains(PrivateTag.PrivateCreator, tag))
                item.setNull(PrivateTag.PrivateCreator, tag, ElementDictionary.vrOf(tag, PrivateTag.PrivateCreator));
        } else {
            if (!item.contains(tag)) {
                VR vr = DICT.vrOf(tag);
                if (vr == VR.SQ)
                    item.newSequence(tag, 1).add(new Attributes(0));
                else
                    item.setNull(tag, vr);
            }
        }
    }

    private Attributes nestedKeys(int[] tags) {
        Attributes item = attrs;
        for (int i = 0; i < tags.length-1; i++) {
            int tag = tags[i];
            Sequence sq = item.getSequence(tag);
            if (sq == null)
                sq = item.newSequence(tag, 1);
            if (sq.isEmpty())
                sq.add(new Attributes());
            item = sq.get(0);
        }
        return item;
    }
}
