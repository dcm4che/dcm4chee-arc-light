package org.dcm4chee.archive.store.scp;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomService;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreService;
import org.dcm4chee.archive.store.StoreSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
@Typed(DicomService.class)
class CStoreSCP extends BasicCStoreSCP {

    @Inject
    private StoreService storeService;

    @Override
    protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {
        StoreSession session = getStoreSession(as);
        StoreContext ctx = newStoreContext(session, pc, rq);
        storeService.store(ctx, data);
    }

    private StoreContext newStoreContext(StoreSession session,  PresentationContext pc, Attributes rq) {
        StoreContext ctx = storeService.newStoreContext(session);
        ctx.setSopClassUID(rq.getString(Tag.AffectedSOPClassUID));
        ctx.setSopInstanceUID(rq.getString(Tag.AffectedSOPInstanceUID));
        ctx.setReceiveTransferSyntax(pc.getTransferSyntax());
        return ctx;

    }

    private StoreSession getStoreSession(Association as) {
        StoreSession session = as.getProperty(StoreSession.class);
        if (session == null) {
            session = storeService.newStoreSession(as);
            as.setProperty(StoreSession.class, session);
        }
        return session;
    }
}
