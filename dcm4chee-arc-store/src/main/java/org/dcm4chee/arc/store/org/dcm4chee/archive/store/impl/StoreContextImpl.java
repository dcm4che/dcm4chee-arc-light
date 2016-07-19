package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
class StoreContextImpl implements StoreContext {

    private final StoreSession storeSession;
    private String sopClassUID;
    private String sopInstanceUID;
    private String receiveTranferSyntaxUID;
    private String storeTranferSyntaxUID;
    private String acceptedStudyInstanceUID;
    private int moveOriginatorMessageID;
    private String moveOriginatorAETitle;
    private final EnumMap<Location.ObjectType,WriteContext> writeContexts =
            new EnumMap<Location.ObjectType, WriteContext>(Location.ObjectType.class);
    private Attributes attributes;
    private Attributes coercedAttributes;
    private String studyInstanceUID;
    private String seriesInstanceUID;
    private String mppsInstanceUID;
    private RejectionNote rejectionNote;
    private Instance previousInstance;
    private Exception exception;
    private final List<Location> locations = new ArrayList<>();
    private String[] retrieveAETs;
    private Availability availability;

    public StoreContextImpl(StoreSession storeSession) {
        this.storeSession = storeSession;
    }

    @Override
    public StoreSession getStoreSession() {
        return storeSession;
    }

    @Override
    public String getSopClassUID() {
        return sopClassUID;
    }

    @Override
    public void setSopClassUID(String sopClassUID) {
        this.sopClassUID = sopClassUID;
    }

    @Override
    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    @Override
    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    @Override
    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    @Override
    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    @Override
    public String getMppsInstanceUID() {
        return mppsInstanceUID;
    }

    @Override
    public String getReceiveTranferSyntax() {
        return receiveTranferSyntaxUID;
    }

    @Override
    public void setReceiveTransferSyntax(String transferSyntaxUID) {
        this.receiveTranferSyntaxUID = transferSyntaxUID;
        this.storeTranferSyntaxUID = transferSyntaxUID;
    }

    @Override
    public String getStoreTranferSyntax() {
        return storeTranferSyntaxUID;
    }

    @Override
    public void setStoreTranferSyntax(String storeTranferSyntaxUID) {
        this.storeTranferSyntaxUID = storeTranferSyntaxUID;
    }

    @Override
    public String getAcceptedStudyInstanceUID() {
        return acceptedStudyInstanceUID;
    }

    @Override
    public void setAcceptedStudyInstanceUID(String acceptedStudyInstanceUID) {
        this.acceptedStudyInstanceUID = acceptedStudyInstanceUID;
    }

    @Override
    public int getMoveOriginatorMessageID() {
        return moveOriginatorMessageID;
    }

    @Override
    public void setMoveOriginatorMessageID(int moveOriginatorMessageID) {
        this.moveOriginatorMessageID = moveOriginatorMessageID;
    }

    @Override
    public String getMoveOriginatorAETitle() {
        return moveOriginatorAETitle;
    }

    @Override
    public void setMoveOriginatorAETitle(String moveOriginatorAETitle) {
        this.moveOriginatorAETitle = moveOriginatorAETitle;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attrs) {
        this.studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        this.seriesInstanceUID = attrs.getString(Tag.SeriesInstanceUID);
        this.sopInstanceUID = attrs.getString(Tag.SOPInstanceUID);
        this.sopClassUID = attrs.getString(Tag.SOPClassUID);
        Attributes ppsRef = attrs.getNestedDataset(Tag.ReferencedPerformedProcedureStepSequence);
        this.mppsInstanceUID = ppsRef != null
                && UID.ModalityPerformedProcedureStepSOPClass.equals(ppsRef.getString(Tag.ReferencedSOPClassUID))
                ? ppsRef.getString(Tag.ReferencedSOPInstanceUID)
                : null;
        this.attributes = attrs;
        this.coercedAttributes = new Attributes(attrs.bigEndian());
    }

    @Override
    public WriteContext getWriteContext(Location.ObjectType objectType) {
        return writeContexts.get(objectType);
    }

    @Override
    public void setWriteContext(Location.ObjectType objectType, WriteContext writeCtx) {
        writeContexts.put(objectType, writeCtx);
    }

    @Override
    public Collection<WriteContext> getWriteContexts() {
        return writeContexts.values();
    }

    @Override
    public Attributes getCoercedAttributes() {
        return coercedAttributes;
    }

    @Override
    public RejectionNote getRejectionNote() {
        return rejectionNote;
    }

    @Override
    public void setRejectionNote(RejectionNote rejectionNote) {
        this.rejectionNote = rejectionNote;
    }

    @Override
    public Exception getException() {
        return exception;
    }

    @Override
    public void setException(Exception exception) {
        this.exception = exception;
    }

    @Override
    public Instance getPreviousInstance() {
        return previousInstance;
    }

    @Override
    public void setPreviousInstance(Instance previousInstance) {
        this.previousInstance = previousInstance;
    }

    @Override
    public List<Location> getLocations() {
        return locations;
    }

    @Override
    public String[] getRetrieveAETs() {
        if (retrieveAETs != null)
            return retrieveAETs;

        String[] aets = getWriteContext(Location.ObjectType.DICOM_FILE).getStorage().getStorageDescriptor()
                .getRetrieveAETitles();
        return aets != null && aets.length > 0
                ? aets
                : new String[] { storeSession.getLocalApplicationEntity().getAETitle() };
    }

    @Override
    public void setRetrieveAETs(String[] retrieveAETs) {
        this.retrieveAETs = retrieveAETs;
    }

    @Override
    public Availability getAvailability() {
        if (availability != null)
            return availability;

        return StringUtils.maskNull(
                getWriteContext(Location.ObjectType.DICOM_FILE).getStorage().getStorageDescriptor()
                        .getInstanceAvailability(),
                Availability.ONLINE);
    }

    @Override
    public void setAvailability(Availability availability) {
        this.availability = availability;
    }
}
