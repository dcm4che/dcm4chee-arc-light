package org.dcm4chee.archive.stgcmt.scp.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.entity.Location;
import org.dcm4chee.archive.entity.QInstance;
import org.dcm4chee.archive.entity.QLocation;
import org.dcm4chee.archive.stgcmt.scp.StgCmtSCP;
import org.dcm4chee.archive.storage.ReadContext;
import org.dcm4chee.archive.storage.Storage;
import org.dcm4chee.archive.storage.StorageFactory;
import org.hibernate.Session;
import org.jboss.ejb3.annotation.TransactionTimeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenContext;
import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/stgcmtscp"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
@TransactionTimeout(600)
public class StgCmtMDB implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(StgCmtMDB.class);

    private static final Expression<?>[] SELECT = {
            QLocation.location.pk,
            QLocation.location.storageID,
            QLocation.location.storagePath,
            QLocation.location.digest,
            QLocation.location.status,
            QInstance.instance.sopClassUID,
            QInstance.instance.sopInstanceUID,
            QInstance.instance.retrieveAETs
    };

    private static final int BUFFER_SIZE = 8192;

    @Resource
    private MessageDrivenContext ctx;

    @Inject
    private Device device;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private StgCmtSCP stgCmtSCP;

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Override
    public void onMessage(Message msg) {
        try {
            Attributes actionInfo = (Attributes) ((ObjectMessage) msg).getObject();
            Attributes eventInfo = calculateResult(actionInfo);
            stgCmtSCP.sendNEventReport(msg.getStringProperty("LocalAET"),
                    msg.getStringProperty("RemoteAET"),
                    eventInfo);
        } catch (Throwable th) {
            ctx.setRollbackOnly();
            LOG.warn("Failed to process " + msg, th);
        }
    }

    private Attributes calculateResult(Attributes actionInfo) {
        Sequence requestSeq = actionInfo.getSequence(Tag.ReferencedSOPSequence);
        int size = requestSeq.size();
        HashMap<String,List<Tuple>> instances = new HashMap<>(size * 4 / 3);
        String commonRetrieveAETs = null;
        for (Tuple location : queryLocations(actionInfo)) {
            String iuid = location.get(QInstance.instance.sopInstanceUID);
            List<Tuple> list = instances.get(iuid);
            if (list == null) {
                instances.put(iuid, list = new ArrayList<>());
                if (instances.isEmpty())
                    commonRetrieveAETs = location.get(QInstance.instance.retrieveAETs);
                else if (commonRetrieveAETs != null
                        && !commonRetrieveAETs.equals(location.get(QInstance.instance.retrieveAETs)))
                    commonRetrieveAETs = null;
            }
            list.add(location);
        }
        Attributes eventInfo = new Attributes(4);
        if (commonRetrieveAETs != null)
            eventInfo.setString(Tag.RetrieveAETitle, VR.AE, commonRetrieveAETs);
        eventInfo.setString(Tag.TransactionUID, VR.UI, actionInfo.getString(Tag.TransactionUID));
        Sequence successSeq = eventInfo.newSequence(Tag.ReferencedSOPSequence, size);
        Sequence failedSeq = eventInfo.newSequence(Tag.FailedSOPSequence, size);
        HashMap<String,Storage> storageMap = new HashMap<>();
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (Attributes refSOP : requestSeq) {
                String iuid = refSOP.getString(Tag.ReferencedSOPInstanceUID);
                String cuid = refSOP.getString(Tag.ReferencedSOPClassUID);
                List<Tuple> tuples = instances.get(iuid);
                if (tuples == null)
                    failedSeq.add(refSOP(iuid, cuid, Status.NoSuchObjectInstance));
                else {
                    Tuple tuple = tuples.get(0);
                    if (!cuid.equals(tuple.get(QInstance.instance.sopClassUID)))
                        failedSeq.add(refSOP(iuid, cuid, Status.ClassInstanceConflict));
                    else if (validateLocations(tuples, storageMap, buffer))
                        successSeq.add(refSOP(cuid, iuid,
                                    commonRetrieveAETs == null ? tuple.get(QInstance.instance.retrieveAETs) : null));
                    else
                        failedSeq.add(refSOP(iuid, cuid, Status.ProcessingFailure));
                }
           }
        } finally {
            for (Storage storage : storageMap.values())
                SafeClose.close(storage);
        }
        if (failedSeq.isEmpty())
            eventInfo.remove(Tag.FailedSOPSequence);
        return eventInfo;
    }

    private List<Tuple> queryLocations(Attributes actionInfo) {
        HibernateQuery<Tuple> query = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(SELECT)
                .from(QLocation.location)
                .join(QLocation.location.instance, QInstance.instance);

        Sequence requestSeq = actionInfo.getSequence(Tag.ReferencedSOPSequence);
        int size = requestSeq.size();
        String[] sopIUIDs = new String[size];
        for (int i = 0; i < size; i++)
            sopIUIDs[i] = requestSeq.get(i).getString(Tag.ReferencedSOPInstanceUID);
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(QInstance.instance.sopInstanceUID.in(sopIUIDs));
        return query.where(builder).fetch();
    }

    private boolean validateLocations(List<Tuple> tuples, HashMap<String, Storage> storageMap, byte[] buffer) {
        for (Tuple tuple : tuples)
            if (validateLocation(tuple, storageMap, buffer))
                return true;
        return false;
    }

    private boolean validateLocation(Tuple tuple, HashMap<String, Storage> storageMap, byte[] buffer) {
        if (tuple.get(QLocation.location.status) != Location.Status.OK)
            return false;

        String digest = tuple.get(QLocation.location.digest);
        if (digest == null)
            return true;

        Storage storage = getStorage(storageMap, tuple.get(QLocation.location.storageID));
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(tuple.get(QLocation.location.storagePath));
        readContext.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        try {
            try (InputStream stream = storage.openInputStream(readContext)) {
               StreamUtils.copy(stream, null, buffer);
            }
        } catch (IOException e) {
            return false;
        }
        if (TagUtils.toHexString(readContext.getDigest()).equals(digest))
            return true;

        return false;
    }

    private Storage getStorage(HashMap<String, Storage> storageMap, String storageID) {
        Storage storage = storageMap.get(storageID);
        if (storage == null) {
            storage = storageFactory.getStorage(
                    device.getDeviceExtension(ArchiveDeviceExtension.class).getStorageDescriptor(storageID));
            storageMap.put(storageID, storage);
        }
        return storage;
    }

    private static Attributes refSOP(String cuid, String iuid, String retrieveAETs) {
        Attributes attrs = new Attributes(3);
        if (retrieveAETs != null)
            attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI,  iuid);
        return attrs;
    }

    private static Attributes refSOP(String cuid, String iuid, int failureReason) {
        Attributes attrs = new Attributes(3);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        attrs.setInt(Tag.FailureReason, VR.US, failureReason);
        return attrs;
    }
}
