package org.dcm4chee.arc.conf.rs;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2017
 */
@Path("elasticsearch")
@RequestScoped
public class QueryElasticSearch {

    @Inject
    private Device device;

    @GET
    @NoCache
    @Produces("application/json")
    public String getElasticSearchURL() throws Exception {
        return "{\"url\":\"" + device.getDeviceExtension(ArchiveDeviceExtension.class).getElasticSearchURL() + "\"}";
    }
}
