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

package org.dcm4chee.arc.store.impl;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
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
    private Instance storedInstance;
    private Exception exception;
    private final List<Location> locations = new ArrayList<>();
    private String[] retrieveAETs;
    private Availability availability;
    private LocalDate expirationDate;

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
    public Instance getStoredInstance() {
        return storedInstance;
    }

    @Override
    public void setStoredInstance(Instance storedInstance) {
        this.storedInstance = storedInstance;
    }

    @Override
    public List<Location> getLocations() {
        return locations;
    }

    @Override
    public String[] getRetrieveAETs() {
        if (retrieveAETs != null)
            return retrieveAETs;

        String[] aets = storeSession.getArchiveAEExtension().retrieveAETitles();
        return aets != null && aets.length > 0
                ? aets
                : new String[] { storeSession.getCalledAET() };
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

    @Override
    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    @Override
    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    @Override
    public boolean isPreviousDifferentStudy() {
        return previousInstance != null
                && previousInstance.getSeries().getStudy().getPk() != storedInstance.getSeries().getStudy().getPk();
    }

    @Override
    public boolean isPreviousDifferentSeries() {
        return previousInstance != null
                && previousInstance.getSeries().getPk() != storedInstance.getSeries().getPk();
    }
}
