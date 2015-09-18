package org.dcm4chee.arc.hl7;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.service.DefaultHL7Service;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.patient.PatientService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@ApplicationScoped
@Typed(HL7Service.class)
class PatientUpdateService extends DefaultHL7Service {

    @Inject
    private PatientService patientService;

    public PatientUpdateService() {
        super("ADT^A02", "ADT^A03", "ADT^A06", "ADT^A07", "ADT^A08", "ADT^A40");
    }

    @Override
    public byte[] onMessage(HL7Application hl7App, Connection conn, Socket s, HL7Segment msh,
                            byte[] msg, int off, int len, int mshlen) throws HL7Exception {
        try {
            String hl7cs = msh.getField(17, hl7App.getHL7DefaultCharacterSet());
            Attributes attrs = SAXTransformer.transform(msg, off, len, hl7cs, getTemplate(hl7App));
            Attributes mrg = attrs.getNestedDataset(Tag.ModifiedAttributesSequence);
            if (mrg == null) {
                patientService.updatePatient(attrs);
            } else {
                patientService.mergePatient(attrs, mrg);
            }
            return super.onMessage(hl7App, conn, s, msh, msg, off, len, mshlen);
        } catch (Exception e) {
            throw new HL7Exception(HL7Exception.AE, e);
        }
    }

    private Templates getTemplate(HL7Application hl7App) throws TransformerConfigurationException {
        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        return TemplatesCache.getDefault().get(
                StringUtils.replaceSystemProperties(arcHL7App.patientUpdateTemplateURI()));
    }
}
