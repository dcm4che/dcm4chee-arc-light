package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;

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
    private Attributes attributes;
    private Attributes coercedAttributes;
    private WriteContext writeContext;
    private String studyInstanceUID;
    private String seriesInstanceUID;
    private String mppsInstanceUID;
    private Location location;
    private RejectionNote rejectionNote;
    private Instance previousInstance;
    private Exception exception;

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
    public WriteContext getWriteContext() {
        return writeContext;
    }

    @Override
    public void setWriteContext(WriteContext writeContext) {
        this.writeContext = writeContext;
    }

    @Override
    public Attributes getCoercedAttributes() {
        return coercedAttributes;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public void setLocation(Location location) {
        this.location = location;
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
}
