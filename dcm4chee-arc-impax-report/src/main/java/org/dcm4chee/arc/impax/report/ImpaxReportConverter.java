/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.impax.report;

import org.dcm4che3.data.*;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
public class ImpaxReportConverter {

    private static final ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
    private static final String DEFAULT_XSL = "${jboss.server.temp.url}/dcm4chee-arc/impax-report2sr.xsl";
    private static final String DEFAULT_DOC_TITLE = "(18748-4, LN, \"Diagnostic Imaging Report\")";
    private static final String DEFAULT_PAT_MISMATCH =
            "(IMPAXREP_PATDIFF, 99DCM4CHEE, \"Patient in IMPAX Report does not match Patient of Study in VNA\")";
    private static final String DEFAULT_LANGUAGE = "(en, RFC5646, \"English\")";
    private static final String DEFAULT_VERIFYING_ORGANIZATION = "N/A";
    private static final String NO_REPORT_PREFIX = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<agfa:DiagnosticRadiologyReport xmlns:agfa=\"http://www.agfa.com/hc\"><ReportDetails><ReportBody>";
    private static final String NO_REPORT_SUFFIX = "</ReportBody></ReportDetails></agfa:DiagnosticRadiologyReport>";

    private static final int[] TYPE2_TAGS = {
            Tag.Manufacturer,
            Tag.ReferencedPerformedProcedureStepSequence,
            Tag.StudyID,
            Tag.PerformedProcedureCodeSequence
    };

    private final String endpoint;
    private final Code languageCode;
    private final Code docTitleCode;
    private final Code noReportTitleCode;
    private final Code patientMismatchCode;
    private final String verifyingOrganization;
    private final String seriesIUID;
    private final Templates tpls;
    private final Attributes propsAttrs = new Attributes();
    private final Attributes studyAttrs;
    private int instanceNumber;

    public ImpaxReportConverter(Map<String, String> props, Attributes studyAttrs) throws Exception {
        this.endpoint = props.get(props.containsKey("wget") ? "wget" : "endpoint");
        this.languageCode = new Code(props.getOrDefault("Language", DEFAULT_LANGUAGE));
        this.docTitleCode = new Code(props.getOrDefault("DocumentTitle", DEFAULT_DOC_TITLE));
        this.patientMismatchCode = new Code(props.getOrDefault("PatientMismatch", DEFAULT_PAT_MISMATCH));
        this.noReportTitleCode = codeOrNull(props.get("NoReportTitle"));
        this.verifyingOrganization = props.getOrDefault("VerifyingOrganization", DEFAULT_VERIFYING_ORGANIZATION);
        this.tpls = TemplatesCache.getDefault().get(
                StringUtils.replaceSystemProperties(props.getOrDefault("xsl", DEFAULT_XSL)));
        this.studyAttrs = studyAttrs;
        this.seriesIUID = UIDUtils.remapUID(studyAttrs.getString(Tag.StudyInstanceUID));
        for (Map.Entry<String, String> prop : props.entrySet()) {
            if (prop.getKey().startsWith("SR.")) {
                int tag = TagUtils.forName(prop.getKey().substring(3));
                propsAttrs.setString(tag, dict.vrOf(tag), prop.getValue());
            }
        }
    }

    private static Code codeOrNull(String s) {
        return s != null ? new Code(s) : null;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Code patientMismatchOf(Attributes sr) {
        for (Attributes item : sr.getSequence(Tag.ContentSequence)) {
            if ("HAS OBS CONTEXT".equals(item.getString(Tag.RelationshipType)))
                switch (item.getNestedDataset(Tag.ConceptNameCodeSequence).getString(Tag.CodeValue)) {
                    case "121029": // Subject Name
                    case "121030": // Subject ID
                    case "121031": // Subject Birth Date
                    case "121032": // Subject Sex
                        return patientMismatchCode;
                }
        }
        return null;
    }

    public List<Attributes> convert(List<String> xmlReports) throws Exception {
        if (xmlReports.isEmpty())
            return noReportTitleCode == null ? Collections.emptyList() : Collections.singletonList(convert(
                NO_REPORT_PREFIX + noReportTitleCode.getCodeMeaning() + NO_REPORT_SUFFIX, noReportTitleCode));

        List<Attributes> srReports = new ArrayList<>(xmlReports.size());
        for (String xmlReport : xmlReports) {
            srReports.add(convert(xmlReport, docTitleCode));
        }
        return srReports;
    }

    private Attributes convert(String report, Code docTitleCode) throws Exception {
        Attributes attrs = new Attributes(64);
        attrs.addAll(studyAttrs);
        attrs.addAll(propsAttrs);
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, seriesIUID + '.' + ++instanceNumber);
        xslt(report, attrs, docTitleCode);
        if (!attrs.containsValue(Tag.SOPClassUID))
            attrs.setString(Tag.SOPClassUID, VR.UI, UID.BasicTextSRStorage);
        if (!attrs.containsValue(Tag.SeriesNumber))
            attrs.setString(Tag.SeriesNumber, VR.IS, "0");
        if (!attrs.containsValue(Tag.InstanceNumber))
            attrs.setString(Tag.InstanceNumber, VR.IS, Integer.toString(instanceNumber));
        if (!attrs.contains(Tag.ContentDate))
            attrs.setDate(Tag.ContentDateAndTime, new Date());
        else if (!attrs.contains(Tag.ContentTime))
            attrs.setString(Tag.ContentTime, VR.TM, "000000");
        for (int tag : TYPE2_TAGS)
            if (!attrs.contains(tag))
                attrs.setNull(tag, dict.vrOf(tag));
        return attrs;
    }

    private void xslt(String report, Attributes attrs, Code docTitleCode) throws Exception {
        Transformer t = tpls.newTransformer();
        t.setParameter("langCodeValue", languageCode.getCodeValue());
        t.setParameter("langCodingSchemeDesignator", languageCode.getCodingSchemeDesignator());
        t.setParameter("langCodeMeaning", languageCode.getCodeMeaning());
        t.setParameter("docTitleCodeValue", docTitleCode.getCodeValue());
        t.setParameter("docTitleCodingSchemeDesignator", docTitleCode.getCodingSchemeDesignator());
        t.setParameter("docTitleCodeMeaning", docTitleCode.getCodeMeaning());
        t.setParameter("VerifyingOrganization", verifyingOrganization);
        setParamIfNotNull(t, "PatientID", attrs.getString(Tag.PatientID));
        setParamIfNotNull(t, "PatientName", attrs.getString(Tag.PatientName));
        setParamIfNotNull(t, "PatientBirthDate", attrs.getString(Tag.PatientBirthDate));
        setParamIfNotNull(t, "PatientSex", attrs.getString(Tag.PatientSex));
        setParamIfNotNull(t, "StudyInstanceUID", studyAttrs.getString(Tag.StudyInstanceUID));
        setParamIfNotNull(t, "AccessionNumber", attrs.getString(Tag.AccessionNumber));
        setParamIfNotNull(t, "StudyDescription", attrs.getString(Tag.StudyDescription));
        setParamIfNotNull(t, "ReferringPhysicianName", attrs.getString(Tag.ReferringPhysicianName));
        t.transform(new StreamSource(new StringReader(report)), new SAXResult(new ContentHandlerAdapter(attrs)));
    }

    private void setParamIfNotNull(Transformer t, String name, String val) {
        if (val != null)
            t.setParameter(name, val);
    }
}
