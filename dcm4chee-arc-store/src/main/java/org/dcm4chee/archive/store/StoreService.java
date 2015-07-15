package org.dcm4chee.archive.store;

import org.dcm4che3.net.Association;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface StoreService {
    StoreSession newStoreSession(Association as);

    StoreContext newStoreContext(StoreSession session);

    void store(StoreContext ctx, InputStream data) throws IOException;
}
