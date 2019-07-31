package org.dcm4chee.arc.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4chee.arc.conf.ArchiveCompressionRule;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.ExportPriorsRule;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.RejectedInstance;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.WriteContext;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public interface StoreContext {

    StoreSession getStoreSession();

    String getSopClassUID();

    void setSopClassUID(String string);

    String getSopInstanceUID();

    void setSopInstanceUID(String string);

    String getMppsInstanceUID();

    String getReceiveTranferSyntax();

    void setReceiveTransferSyntax(String transferSyntax);

    String getStoreTranferSyntax();

    void setStoreTranferSyntax(String storeTranferSyntaxUID);

    ArchiveCompressionRule getCompressionRule();

    void setCompressionRule(ArchiveCompressionRule compressionRule);

    String getAcceptedStudyInstanceUID();

    void setAcceptedStudyInstanceUID(String acceptedStudyInstanceUID);

    int getMoveOriginatorMessageID();

    void setMoveOriginatorMessageID(int moveOriginatorMessageID);

    String getMoveOriginatorAETitle();

    void setMoveOriginatorAETitle(String moveOriginatorAETitle);

    Attributes getAttributes();

    void setAttributes(Attributes dataset);

    ReadContext getReadContext();

    void setReadContext(ReadContext readContext);

    Collection<WriteContext> getWriteContexts();

    Attributes getCoercedAttributes();

    void setCoercedAttributes(Attributes coercedAttributes);

    String getStudyInstanceUID();

    String getSeriesInstanceUID();

    WriteContext getWriteContext(Location.ObjectType objectType);

    void setWriteContext(Location.ObjectType objectType, WriteContext writeCtx);

    RejectionNote getRejectionNote();

    void setRejectionNote(RejectionNote rejectionNote);

    RejectedInstance getRejectedInstance();

    void setRejectedInstance(RejectedInstance rejectedInstance);

    Exception getException();

    void setException(Exception ex);

    Instance getPreviousInstance();

    void setPreviousInstance(Instance previousInstance);

    Instance getStoredInstance();

    void setStoredInstance(Instance storedInstance);

    List<Location> getLocations();

    String[] getRetrieveAETs();

    void setRetrieveAETs(String... retrieveAETs);

    Availability getAvailability();

    void setAvailability(Availability availability);

    LocalDate getExpirationDate();

    void setExpirationDate(LocalDate expirationDate);

    boolean isPreviousDifferentStudy();

    boolean isPreviousDifferentSeries();

    Code getImpaxReportPatientMismatch();

    void setImpaxReportPatientMismatch(Code impaxReportPatientMismatch);

    boolean isExportReoccurredInstances(ExportPriorsRule rule);
}
