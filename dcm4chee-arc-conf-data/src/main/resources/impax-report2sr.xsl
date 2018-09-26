<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:agfa="http://www.agfa.com/hc"
                exclude-result-prefixes="agfa"
                version="1.0" >
  <xsl:output method="xml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:param name="langCodeValue"/>
  <xsl:param name="langCodingSchemeDesignator"/>
  <xsl:param name="langCodeMeaning"/>
  <xsl:param name="docTitleCodeValue"/>
  <xsl:param name="docTitleCodingSchemeDesignator"/>
  <xsl:param name="docTitleCodeMeaning"/>
  <xsl:param name="VerifyingOrganization"/>
  <xsl:param name="PatientID"/>
  <xsl:param name="PatientName"/>
  <xsl:param name="PatientBirthDate"/>
  <xsl:param name="PatientSex"/>
  <xsl:param name="StudyInstanceUID"/>
  <xsl:param name="AccessionNumber"/>
  <xsl:param name="StudyDescription"/>
  <xsl:param name="ReferringPhysicianName"/>

  <xsl:template match="/agfa:DiagnosticRadiologyReport">
    <NativeDicomModel>
        <!--Modality-->
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00080060'"/>
            <xsl:with-param name="vr" select="'CS'"/>
            <xsl:with-param name="val" select="'SR'"/>
        </xsl:call-template>
        <xsl:call-template name="contentTemplateSq"/>
        <xsl:call-template name="codeItem">
            <xsl:with-param name="sqtag" select="'0040A043'"/>
            <xsl:with-param name="code" select="$docTitleCodeValue"/>
            <xsl:with-param name="scheme" select="$docTitleCodingSchemeDesignator"/>
            <xsl:with-param name="meaning" select="$docTitleCodeMeaning"/>
        </xsl:call-template>
        <xsl:call-template name="containerValueType"/>
        <xsl:call-template name="continuityOfContent"/>
        <xsl:variable name="orderDetails" select="OrderDetails"/>
        <xsl:apply-templates select="OrderDetails">
            <xsl:with-param name="orderDetails" select="$orderDetails"/>
        </xsl:apply-templates>
        <xsl:apply-templates select="ReportDetails"/>
        <xsl:call-template name="contentSeq">
            <xsl:with-param name="orderDetails" select="$orderDetails"/>
        </xsl:call-template>
    </NativeDicomModel>
  </xsl:template>

    <xsl:template name="contentTemplateSq">
        <DicomAttribute tag="0040A504" vr="SQ">
            <Item number="1">
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'00080105'"/>
                    <xsl:with-param name="vr" select="'CS'"/>
                    <xsl:with-param name="val" select="'DCMR'"/>
                </xsl:call-template>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'0040DB00'"/>
                    <xsl:with-param name="vr" select="'CS'"/>
                    <xsl:with-param name="val" select="'2000'"/>
                </xsl:call-template>
            </Item>
        </DicomAttribute>
    </xsl:template>

  <xsl:template match="OrderDetails">
    <xsl:param name="orderDetails"/>
    <xsl:variable name="studyDesc" select="$orderDetails/StudyDetails/StudyDescription"/>
      <xsl:variable name="referringPhysicianName" select="$orderDetails/ReferringPhysician/Name"/>
      <xsl:variable name="studyIUID" select="$orderDetails/StudyDetails/StudyInstanceUID"/>
      <!--Study Description-->
      <xsl:if test="$StudyDescription = '' and $StudyInstanceUID = $studyIUID">
          <xsl:call-template name="attr">
              <xsl:with-param name="tag" select="'00081030'"/>
              <xsl:with-param name="vr" select="'LO'"/>
              <xsl:with-param name="val" select="$studyDesc"/>
          </xsl:call-template>
      </xsl:if>
      <!--Referring Physician's Name-->
      <xsl:if test="$ReferringPhysicianName = '' and $StudyInstanceUID = $studyIUID">
          <xsl:call-template name="pnAttr">
              <xsl:with-param name="tag" select="'00080090'"/>
              <xsl:with-param name="val" select="$referringPhysicianName"/>
          </xsl:call-template>
      </xsl:if>
    <!--Referenced Request Sequence-->
    <DicomAttribute tag="0040A370" vr="SQ">
      <Item number="1">
        <!-- Study Instance UID -->
          <xsl:call-template name="attr">
              <xsl:with-param name="tag" select="'0020000D'"/>
              <xsl:with-param name="vr" select="'UI'"/>
              <xsl:with-param name="val" select="$studyIUID"/>
          </xsl:call-template>
        <!--Accession Number-->
          <xsl:call-template name="attr">
              <xsl:with-param name="tag" select="'00080050'"/>
              <xsl:with-param name="vr" select="'SH'"/>
              <xsl:with-param name="val" select="$orderDetails/AccessionNumber"/>
          </xsl:call-template>
        <!-- Reason for the Requested Procedure -->
          <xsl:call-template name="attr">
              <xsl:with-param name="tag" select="'00401002'"/>
              <xsl:with-param name="vr" select="'UI'"/>
              <xsl:with-param name="val" select="$orderDetails/StudyDetails/ReasonForStudy"/>
          </xsl:call-template>
        <!--Referenced Study Sequence-->
        <DicomAttribute tag="00081110" vr="SQ"/>
        <!--Requested Procedure Description-->
          <xsl:call-template name="attr">
              <xsl:with-param name="tag" select="'00321060'"/>
              <xsl:with-param name="vr" select="'LO'"/>
              <xsl:with-param name="val" select="$studyDesc"/>
          </xsl:call-template>
        <!--Requested Procedure Sequence-->
        <DicomAttribute tag="00321064" vr="SQ"/>
        <!--Requested Procedure ID-->
        <DicomAttribute tag="00401001" vr="SH"/>
        <!--Placer Order Number / Imaging Service Request-->
        <DicomAttribute tag="00402016" vr="LO" />
        <!--Filler Order Number / Imaging Service Request-->
        <DicomAttribute tag="00402017" vr="LO" />
          <!-- Referring Physician's Name -->
          <xsl:call-template name="pnAttrs">
              <xsl:with-param name="tag" select="'00080090'" />
              <xsl:with-param name="val" select="$referringPhysicianName" />
          </xsl:call-template>
      </Item>
    </DicomAttribute>
  </xsl:template>

  <xsl:template match="ReportDetails">
    <xsl:variable name="resultStatus" select="ReportStatus"/>
    <!--Completion Flag-->
      <xsl:variable name="completionFlag">
          <xsl:choose>
              <xsl:when test="$resultStatus='Finalized'">COMPLETE</xsl:when>
              <xsl:otherwise>PARTIAL</xsl:otherwise>
          </xsl:choose>
      </xsl:variable>
      <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'0040A491'"/>
          <xsl:with-param name="vr" select="'CS'"/>
          <xsl:with-param name="val" select="$completionFlag"/>
      </xsl:call-template>
    <!--Verification Flag-->
    <xsl:variable name="verifyingObserver" select="InterpretationApprover"/>
    <xsl:variable name="interpretationRecordDate" select="translate(InterpretationRecordDate, '-', '')"/>
    <xsl:variable name="interpretationRecordTime" select="translate(InterpretationRecordTime, ':', '')"/>
    <xsl:variable name="interpretationApprovedDate" select="translate(InterpretationApprovedDate, '-', '')"/>
    <xsl:variable name="interpretationApprovedTime" select="translate(InterpretationApprovedTime, ':', '')"/>
    <xsl:variable name="verificationFlag">
      <xsl:choose>
        <xsl:when test="$resultStatus='Finalized' and $verifyingObserver/SingleByteName">VERIFIED</xsl:when>
        <xsl:otherwise>UNVERIFIED</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
      <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'0040A493'"/>
          <xsl:with-param name="vr" select="'CS'"/>
          <xsl:with-param name="val" select="$verificationFlag"/>
      </xsl:call-template>
    <!--Content Date/Time-->
      <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00080023'"/>
          <xsl:with-param name="vr" select="'DA'"/>
          <xsl:with-param name="val" select="$interpretationRecordDate"/>
      </xsl:call-template>
      <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00080033'"/>
          <xsl:with-param name="vr" select="'TM'"/>
          <xsl:with-param name="val" select="$interpretationRecordTime"/>
      </xsl:call-template>
    <xsl:if test="$verificationFlag = 'VERIFIED'">
      <!-- Verifying Observer Sequence -->
      <DicomAttribute tag="0040A073" vr="SQ">
        <Item number="1">
          <!-- Verifying Organization -->
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'0040A027'"/>
                <xsl:with-param name="vr" select="'LO'"/>
                <xsl:with-param name="val" select="$VerifyingOrganization"/>
            </xsl:call-template>
          <!-- Verification DateTime -->
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'0040A030'"/>
                <xsl:with-param name="vr" select="'DT'"/>
                <xsl:with-param name="val">
                    <xsl:value-of select="$interpretationApprovedDate"/><xsl:value-of select="$interpretationApprovedTime"/>
                </xsl:with-param>
            </xsl:call-template>
          <!-- Verifying Observer Name -->
          <xsl:call-template name="pnAttrs">
            <xsl:with-param name="tag" select="'0040A075'" />
            <xsl:with-param name="val" select="$verifyingObserver" />
          </xsl:call-template>
          <!-- Verifying Observer Identification Code Sequence -->
          <DicomAttribute tag="0040A088" vr="SQ"/>
        </Item>
      </DicomAttribute>
    </xsl:if>

    <!-- Author Observer Sequence -->
    <xsl:if test="ReportAuthor/Name/SingleByteName">
        <DicomAttribute tag="0040A078" vr="SQ">
            <Item number="1">
                <!-- Institution Name -->
                <DicomAttribute tag="00080080" vr="LO"/>
                <!-- Institution Code Sequence -->
                <DicomAttribute tag="00080082" vr="SQ"/>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'0040A084'"/>
                    <xsl:with-param name="vr" select="'CS'"/>
                    <xsl:with-param name="val" select="'PSN'"/>
                </xsl:call-template>
                <xsl:call-template name="pnAttrs">
                    <xsl:with-param name="tag" select="'0040A123'"/>
                    <xsl:with-param name="val" select="ReportAuthor/Name"/>
                </xsl:call-template>
                <!-- Person Identification Code Sequence -->
                <xsl:if test="ReportAuthor/Id/IdText">
                    <xsl:call-template name="codeItem">
                        <xsl:with-param name="sqtag" select="'00401101'"/>
                        <xsl:with-param name="code" select="ReportAuthor/Id/IdText"/>
                        <xsl:with-param name="scheme" select="ReportAuthor/Id/IdDomain"/>
                        <xsl:with-param name="meaning">
                            <xsl:call-template name="personName">
                                <xsl:with-param name="val" select="ReportAuthor/Name"/>
                            </xsl:call-template>
                        </xsl:with-param>
                    </xsl:call-template>
                </xsl:if>
            </Item>
        </DicomAttribute>
    </xsl:if>


      <!-- Participant Sequence -->
      <xsl:if test="InterpretationRecorder/SingleByteName">
          <DicomAttribute tag="0040A07A" vr="SQ">
              <Item number="1">
                  <!-- Institution Name -->
                  <DicomAttribute tag="00080080" vr="LO"/>
                  <!-- Institution Code Sequence -->
                  <DicomAttribute tag="00080082" vr="SQ"/>
                  <!-- Person Identification Code Sequence -->
                  <DicomAttribute tag="00401101" vr="SQ"/>
                  <!-- Participation Type -->
                  <xsl:call-template name="attr">
                      <xsl:with-param name="tag" select="'0040A080'"/>
                      <xsl:with-param name="vr" select="'CS'"/>
                      <xsl:with-param name="val" select="'ENT'"/>
                  </xsl:call-template>
                  <!-- Participation DateTime -->
                  <xsl:call-template name="attr">
                      <xsl:with-param name="tag" select="'0040A082'"/>
                      <xsl:with-param name="vr" select="'DT'"/>
                      <xsl:with-param name="val">
                          <xsl:value-of select="$interpretationRecordDate"/><xsl:value-of select="$interpretationRecordTime"/>
                      </xsl:with-param>
                  </xsl:call-template>
                  <!-- Observer Type -->
                  <xsl:call-template name="attr">
                      <xsl:with-param name="tag" select="'0040A084'"/>
                      <xsl:with-param name="vr" select="'CS'"/>
                      <xsl:with-param name="val" select="'PSN'"/>
                  </xsl:call-template>
                  <!-- Person Name -->
                  <xsl:call-template name="pnAttrs">
                      <xsl:with-param name="tag" select="'0040A123'"/>
                      <xsl:with-param name="val" select="InterpretationRecorder"/>
                  </xsl:call-template>
              </Item>
          </DicomAttribute>
      </xsl:if>

  </xsl:template>

    <xsl:template name="contentSeq">
        <xsl:param name="orderDetails"/>
        <xsl:param name="patDetails" select="PatientDetails"/>
        <xsl:param name="reportDetails" select="ReportDetails"/>
        <xsl:param name="pName" select="$patDetails/Name"/>
        <DicomAttribute tag="0040A730" vr="SQ">
            <xsl:call-template name="language">
                <xsl:with-param name="itemNo">1</xsl:with-param>
            </xsl:call-template>
            <xsl:call-template name="subjectCtxPatName">
                <xsl:with-param name="itemNo">2</xsl:with-param>
                <xsl:with-param name="pName" select="$pName"/>
            </xsl:call-template>
            <xsl:call-template name="subjectCtxPID">
                <xsl:with-param name="itemNo">3</xsl:with-param>
                <xsl:with-param name="pid" select="$patDetails/Id"/>
                <xsl:with-param name="pName" select="$pName"/>
            </xsl:call-template>
            <xsl:call-template name="subjectCtxPatBirthDate">
                <xsl:with-param name="itemNo">4</xsl:with-param>
                <xsl:with-param name="patBirthDate" select="$patDetails/BirthDate"/>
            </xsl:call-template>
            <xsl:call-template name="subjectCtxPatSex">
                <xsl:with-param name="itemNo">5</xsl:with-param>
                <xsl:with-param name="patSex" select="$patDetails/Sex"/>
            </xsl:call-template>
            <xsl:call-template name="procedureCtxStudyIUID">
                <xsl:with-param name="itemNo">6</xsl:with-param>
                <xsl:with-param name="studyIUID" select="$orderDetails/StudyDetails/StudyInstanceUID"/>
            </xsl:call-template>
            <xsl:call-template name="procedureCtxAcc">
                <xsl:with-param name="itemNo">7</xsl:with-param>
                <xsl:with-param name="accessionNo" select="$orderDetails/AccessionNumber"/>
            </xsl:call-template>
            <xsl:call-template name="procedureCtxStudyDesc">
                <xsl:with-param name="itemNo">8</xsl:with-param>
                <xsl:with-param name="studyDesc" select="$orderDetails/StudyDetails/StudyDescription"/>
            </xsl:call-template>
            <xsl:call-template name="reportItem">
                <xsl:with-param name="itemNo">9</xsl:with-param>
                <xsl:with-param name="parentCode"><xsl:value-of select="'121070'"/></xsl:with-param>
                <xsl:with-param name="parentCodeMeaning"><xsl:value-of select="'Findings'"/></xsl:with-param>
                <xsl:with-param name="childCode"><xsl:value-of select="'121071'"/></xsl:with-param>
                <xsl:with-param name="childCodeMeaning"><xsl:value-of select="'Finding'"/></xsl:with-param>
                <xsl:with-param name="val"><xsl:value-of select="$reportDetails/ReportBody/text()"/></xsl:with-param>
            </xsl:call-template>
            <xsl:call-template name="reportItem">
                <xsl:with-param name="itemNo">10</xsl:with-param>
                <xsl:with-param name="parentCode"><xsl:value-of select="'121076'"/></xsl:with-param>
                <xsl:with-param name="parentCodeMeaning"><xsl:value-of select="'Conclusions'"/></xsl:with-param>
                <xsl:with-param name="childCode"><xsl:value-of select="'121077'"/></xsl:with-param>
                <xsl:with-param name="childCodeMeaning"><xsl:value-of select="'Conclusion'"/></xsl:with-param>
                <xsl:with-param name="val"><xsl:value-of select="$reportDetails/Conclusions/text()"/></xsl:with-param>
            </xsl:call-template>
        </DicomAttribute>
    </xsl:template>

    <xsl:template name="language">
        <xsl:param name="itemNo"/>
        <Item number="{$itemNo}">
            <xsl:call-template name="conceptModRelation"/>
            <xsl:call-template name="codeValueType"/>
            <xsl:call-template name="conceptNameCodeSq">
                <xsl:with-param name="code" select="'121049'"/>
                <xsl:with-param name="meaning" select="'Language of Content Item and Descendants'"/>
            </xsl:call-template>
            <xsl:call-template name="codeItem">
                <xsl:with-param name="sqtag">0040A168</xsl:with-param>
                <xsl:with-param name="code" select="$langCodeValue"/>
                <xsl:with-param name="scheme" select="$langCodingSchemeDesignator"/>
                <xsl:with-param name="meaning" select="$langCodeMeaning"/>
            </xsl:call-template>
        </Item>
    </xsl:template>


    <xsl:template name="subjectCtxPatName">
        <xsl:param name="itemNo"/>
        <xsl:param name="pName"/>
        <Item number="{$itemNo}">
            <xsl:if test="$PatientName = '' or not($PatientName = $pName)">
                <xsl:call-template name="obsRelation"/>
                <xsl:call-template name="valueType">
                    <xsl:with-param name="val" select="'PNAME'"/>
                </xsl:call-template>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="'121029'"/>
                    <xsl:with-param name="meaning" select="'Subject Name'"/>
                </xsl:call-template>
                <xsl:call-template name="pnAttrs">
                    <xsl:with-param name="tag" select="'0040A123'" />
                    <xsl:with-param name="val" select="$pName" />
                </xsl:call-template>
            </xsl:if>
        </Item>
    </xsl:template>

    <xsl:template name="subjectCtxPID">
        <xsl:param name="itemNo"/>
        <xsl:param name="pid"/>
        <xsl:param name="pName"/>
        <Item number="{$itemNo}">
            <xsl:if test="$PatientID = '' or not($PatientID = $pid/IdText)">
                <xsl:call-template name="obsRelation"/>
                <xsl:call-template name="codeValueType"/>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="'121030'"/>
                    <xsl:with-param name="meaning" select="'Subject ID'"/>
                </xsl:call-template>
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="sqtag" select="'0040A168'"/>
                    <xsl:with-param name="code" select="$pid/IdText"/>
                    <xsl:with-param name="scheme" select="$pid/IdDomain"/>
                    <xsl:with-param name="meaning">
                        <xsl:call-template name="personName">
                            <xsl:with-param name="val" select="$pName"/>
                        </xsl:call-template>
                    </xsl:with-param>
                </xsl:call-template>
            </xsl:if>
        </Item>
    </xsl:template>

    <xsl:template name="subjectCtxPatBirthDate">
        <xsl:param name="itemNo"/>
        <xsl:param name="patBirthDate"/>
        <Item number="{$itemNo}">
            <xsl:if test="$PatientBirthDate = '' or not($PatientBirthDate = $patBirthDate)">
                <xsl:call-template name="obsRelation"/>
                <xsl:call-template name="valueType">
                    <xsl:with-param name="val" select="'DATE'"/>
                </xsl:call-template>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="'121031'"/>
                    <xsl:with-param name="meaning" select="'Subject Birth Date'"/>
                </xsl:call-template>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'0040A121'"/>
                    <xsl:with-param name="vr" select="'DA'"/>
                    <xsl:with-param name="val" select="$patBirthDate"/>
                </xsl:call-template>
            </xsl:if>
        </Item>
    </xsl:template>

    <xsl:template name="subjectCtxPatSex">
        <xsl:param name="itemNo"/>
        <xsl:param name="patSex"/>
        <Item number="{$itemNo}">
            <xsl:if test="$PatientSex = '' or not($PatientSex = substring($patSex, 0, 1))">
                <xsl:call-template name="obsRelation"/>
                <xsl:call-template name="codeValueType"/>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="'121032'"/>
                    <xsl:with-param name="meaning" select="'Subject Sex'"/>
                </xsl:call-template>
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="sqtag" select="'0040A168'"/>
                    <xsl:with-param name="code">
                        <xsl:call-template name="sex">
                            <xsl:with-param name="val" select="$patSex"/>
                        </xsl:call-template>
                    </xsl:with-param>
                    <xsl:with-param name="scheme" select="'DCM'"/>
                    <xsl:with-param name="meaning" select="$patSex"/>
                </xsl:call-template>
            </xsl:if>
        </Item>
    </xsl:template>

    <xsl:template name="procedureCtxStudyIUID">
        <xsl:param name="itemNo"/>
        <xsl:param name="studyIUID"/>
        <Item number="{$itemNo}">
            <xsl:if test="not($StudyInstanceUID = $studyIUID)">
                <xsl:call-template name="obsRelation"/>
                <xsl:call-template name="valueType">
                    <xsl:with-param name="val" select="'UIDREF'"/>
                </xsl:call-template>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="'121018'"/>
                    <xsl:with-param name="meaning" select="'Procedure Study Instance UID'"/>
                </xsl:call-template>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'0040A124'"/>
                    <xsl:with-param name="vr" select="'UI'"/>
                    <xsl:with-param name="val" select="$studyIUID"/>
                </xsl:call-template>
            </xsl:if>
        </Item>
    </xsl:template>

    <xsl:template name="procedureCtxAcc">
        <xsl:param name="itemNo"/>
        <xsl:param name="accessionNo"/>
        <Item number="{$itemNo}">
            <xsl:if test="$AccessionNumber = '' or not($AccessionNumber = $accessionNo)">
                <xsl:call-template name="obsRelation"/>
                <xsl:call-template name="textValueType"/>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="'121022'"/>
                    <xsl:with-param name="meaning" select="'Accession Number'"/>
                </xsl:call-template>
                <xsl:call-template name="text">
                    <xsl:with-param name="val" select="$accessionNo"/>
                </xsl:call-template>
            </xsl:if>
        </Item>
    </xsl:template>

    <xsl:template name="procedureCtxStudyDesc">
        <xsl:param name="itemNo"/>
        <xsl:param name="studyDesc"/>
        <Item number="{$itemNo}">
            <xsl:if test="$StudyDescription = '' or not($StudyDescription = $studyDesc)">
                <xsl:call-template name="obsRelation"/>
                <xsl:call-template name="textValueType"/>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="'121065'"/>
                    <xsl:with-param name="meaning" select="'Procedure Description'"/>
                </xsl:call-template>
                <xsl:call-template name="text">
                    <xsl:with-param name="val" select="$studyDesc"/>
                </xsl:call-template>
            </xsl:if>
        </Item>
    </xsl:template>

  <xsl:template name="reportItem">
    <xsl:param name="itemNo"/>
    <xsl:param name="parentCode"/>
    <xsl:param name="parentCodeMeaning"/>
    <xsl:param name="childCode"/>
    <xsl:param name="childCodeMeaning"/>
    <xsl:param name="val"/>
    <Item number="{$itemNo}">
      <xsl:if test="$val">
          <xsl:call-template name="containsRelation"/>
          <xsl:call-template name="containerValueType"/>
          <xsl:call-template name="conceptNameCodeSq">
              <xsl:with-param name="code" select="$parentCode"/>
              <xsl:with-param name="meaning" select="$parentCodeMeaning"/>
          </xsl:call-template>
          <xsl:call-template name="continuityOfContent"/>
          <DicomAttribute tag="0040A730" vr="SQ">
            <Item number="1">
                <xsl:call-template name="containsRelation"/>
                <xsl:call-template name="textValueType"/>
                <xsl:call-template name="conceptNameCodeSq">
                    <xsl:with-param name="code" select="$childCode"/>
                    <xsl:with-param name="meaning" select="$childCodeMeaning"/>
                </xsl:call-template>
                <xsl:call-template name="text">
                    <xsl:with-param name="val" select="$val"/>
                </xsl:call-template>
            </Item>
          </DicomAttribute>
      </xsl:if>
    </Item>
  </xsl:template>

    <xsl:template name="continuityOfContent">
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'0040A050'"/>
            <xsl:with-param name="vr" select="'CS'"/>
            <xsl:with-param name="val" select="'SEPARATE'"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="valueType">
        <xsl:param name="val"/>
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'0040A040'"/>
            <xsl:with-param name="vr" select="'CS'"/>
            <xsl:with-param name="val" select="$val"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="conceptNameCodeSq">
        <xsl:param name="code"/>
        <xsl:param name="meaning"/>
        <xsl:call-template name="codeItem">
            <xsl:with-param name="sqtag" select="'0040A043'"/>
            <xsl:with-param name="code" select="$code"/>
            <xsl:with-param name="scheme" select="'DCM'"/>
            <xsl:with-param name="meaning" select="$meaning"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="text">
        <xsl:param name="val"/>
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'0040A160'"/>
            <xsl:with-param name="vr" select="'UT'"/>
            <xsl:with-param name="val" select="$val"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="codeValueType">
        <xsl:call-template name="valueType">
            <xsl:with-param name="val" select="'CODE'"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="containerValueType">
        <xsl:call-template name="valueType">
            <xsl:with-param name="val" select="'CONTAINER'"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="textValueType">
        <xsl:call-template name="valueType">
            <xsl:with-param name="val" select="'TEXT'"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="conceptModRelation">
        <xsl:call-template name="relationshipType">
            <xsl:with-param name="val" select="'HAS CONCEPT MOD'"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="containsRelation">
        <xsl:call-template name="relationshipType">
            <xsl:with-param name="val" select="'CONTAINS'"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="obsRelation">
        <xsl:call-template name="relationshipType">
            <xsl:with-param name="val" select="'HAS OBS CONTEXT'"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="relationshipType">
        <xsl:param name="val"/>
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'0040A010'"/>
            <xsl:with-param name="vr" select="'CS'"/>
            <xsl:with-param name="val" select="$val"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="personName">
        <xsl:param name="val"/>
        <xsl:if test="$val and $val != '&quot;&quot;' and string-length($val) > 0">
            <xsl:value-of select="$val/SingleByteName/LastName"/>^<xsl:value-of select="$val/SingleByteName/FirstName"/>
        </xsl:if>
    </xsl:template>
    
  <xsl:template name="pnAttrs">
    <xsl:param name="tag"/>
    <xsl:param name="val"/>
    <xsl:if test="$val and $val != '&quot;&quot;' and string-length($val) > 0">
        <DicomAttribute tag="{$tag}" vr="PN">
          <PersonName number="1">
            <Alphabetic>
              <xsl:call-template name="pnComp">
                <xsl:with-param name="name">FamilyName</xsl:with-param>
                <xsl:with-param name="val" select="$val/SingleByteName/LastName"/>
              </xsl:call-template>
              <xsl:call-template name="pnComp">
                <xsl:with-param name="name">GivenName</xsl:with-param>
                <xsl:with-param name="val" select="$val/SingleByteName/FirstName"/>
              </xsl:call-template>
            </Alphabetic>
          </PersonName>
        </DicomAttribute>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>