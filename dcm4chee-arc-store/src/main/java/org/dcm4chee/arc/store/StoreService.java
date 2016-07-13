package org.dcm4chee.arc.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface StoreService {
    StoreSession newStoreSession(Association as);

    StoreSession newStoreSession(HttpServletRequest httpRequest, ApplicationEntity ae);

    StoreSession newStoreSession(ApplicationEntity ae);

    StoreSession newStoreSession(Socket socket, HL7Segment msh, ApplicationEntity ae);

    StoreContext newStoreContext(StoreSession session);

    void store(StoreContext ctx, InputStream data) throws IOException;

    void store(StoreContext ctx, Attributes attrs) throws IOException;

    Attributes moveInstances(StoreSession session, Attributes instanceRefs, String targetStudyIUID);
}
