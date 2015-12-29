package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2015
 */
public enum MoveForwardLevel {
    STUDY(Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID),
    SERIES(Tag.SeriesInstanceUID, Tag.SOPInstanceUID),
    IMAGE(Tag.SOPInstanceUID);

    private final int required;
    private final int[] remove;

    MoveForwardLevel(int required, int... remove) {
        this.required = required;
        this.remove = remove;
    }

    public Attributes changeQueryRetrieveLevel(Attributes keys) {
        MoveForwardLevel origLevel;
        try {
            origLevel = MoveForwardLevel.valueOf(keys.getString(Tag.QueryRetrieveLevel));
        } catch (IllegalArgumentException e) {
            return keys;
        }
        if (compareTo(origLevel) < 0 && keys.containsValue(required)) {
            Attributes changed = new Attributes(keys);
            changed.setString(Tag.QueryRetrieveLevel, VR.CS, name());
            for (int tag : remove) {
                changed.remove(tag);
            }
            return changed;
        }
        return keys;
    }
}
