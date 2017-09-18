package org.dcm4chee.arc.hl7;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.hl7.HL7Charset;
import org.dcm4che3.hl7.HL7ContentHandler;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Parser;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.SAXTransformer.SetupTransformer;
import org.dcm4che3.io.SAXWriter;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.util.StringUtils;
import org.xml.sax.SAXException;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import java.io.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
class SAXTransformer {

    private SAXTransformer() {}

    private static SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();

    public static Attributes transform(byte[] data, String hl7charset, String uri, SetupTransformer setup)
            throws TransformerConfigurationException, IOException, SAXException {
        Templates tpl = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(uri));
        Attributes attrs = new Attributes();
        String dicomCharset = HL7Charset.toDicomCharacterSetCode(hl7charset);
        if (dicomCharset != null)
            attrs.setString(Tag.SpecificCharacterSet, VR.CS, dicomCharset);
        TransformerHandler th = factory.newTransformerHandler(tpl);
        th.setResult(new SAXResult(new ContentHandlerAdapter(attrs)));
        if (setup != null)
            setup.setup(th.getTransformer());
        new HL7Parser(th).parse(new InputStreamReader(
                new ByteArrayInputStream(data),
                HL7Charset.toCharsetName(hl7charset)));
        return attrs;
    }

    public static byte[] transform(Attributes attrs, String hl7charset, String uri, SetupTransformer setup)
            throws TransformerConfigurationException, SAXException, UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Templates tpl = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(uri));
        TransformerHandler th = factory.newTransformerHandler(tpl);
        th.setResult(new SAXResult(new HL7ContentHandler(new OutputStreamWriter(out, "US-ASCII"))));
        if (setup != null)
            setup.setup(th.getTransformer());

        SAXWriter saxWriter = new SAXWriter(th);
        saxWriter.setIncludeKeyword(true);
        saxWriter.setIncludeNamespaceDeclaration(false);
        saxWriter.write(attrs);

        return out.toByteArray();
    }

}
