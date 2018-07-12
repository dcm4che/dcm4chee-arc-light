<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:agfa="http://www.agfa.com/hc"
                exclude-result-prefixes="agfa"
                version="1.0" >
  <xsl:output method="xml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:template match="/agfa:DiagnosticRadiologyReport">
    <NativeDicomModel>
      <!--Modality-->
      <DicomAttribute tag="00080060" vr="CS"><Value number="1">SR</Value></DicomAttribute>
      <xsl:apply-templates select="agfa:OrderDetails"/>
      <xsl:apply-templates select="agfa:ReportDetails"/>
    </NativeDicomModel>
  </xsl:template>

  <xsl:template match="agfa:OrderDetails">
    <!--Referenced Request Sequence-->
    <DicomAttribute tag="0040A370" vr="SQ">
      <Item number="1">
        <!-- Study Instance UID -->
        <DicomAttribute tag="0020000D" vr="UI">
          <Value number="1"><xsl:value-of select="agfa:StudyDetails/agfa:StudyInstanceUID"/></Value>
        </DicomAttribute>
        <!--Accession Number-->
        <DicomAttribute tag="00080050" vr="SH">
          <Value number="1"><xsl:value-of select="agfa:AccessionNumber"/></Value>
        </DicomAttribute>
        <!--Referenced Study Sequence-->
        <DicomAttribute tag="00081110" vr="SQ"/>
        <!--Requested Procedure Description-->
        <DicomAttribute tag="00321060" vr="LO">
          <Value number="1"><xsl:value-of select="agfa:StudyDetails/agfa:StudyDescription"/></Value>
        </DicomAttribute>
        <!--Requested Procedure Sequence-->
        <DicomAttribute tag="00321064" vr="SQ"/>
        <!--Requested Procedure ID-->
        <DicomAttribute tag="00401001" vr="SH"/>
        <!--Placer Order Number / Imaging Service Request-->
        <DicomAttribute tag="00402016" vr="LO" />
        <!--Filler Order Number / Imaging Service Request-->
        <DicomAttribute tag="00402017" vr="LO" />
      </Item>
    </DicomAttribute>
  </xsl:template>

  <xsl:template match="agfa:ReportDetails">
    <xsl:variable name="resultStatus" select="agfa:ReportStatus"/>
    <!--Completion Flag-->
    <DicomAttribute tag="0040A491" vr="CS">
      <Value number="1">
        <xsl:choose>
          <xsl:when test="$resultStatus='Finalized'">COMPLETE</xsl:when>
          <xsl:otherwise>PARTIAL</xsl:otherwise>
        </xsl:choose>
      </Value>
    </DicomAttribute>
    <!--Verification Flag-->
    <xsl:variable name="verifyingObserver" select="agfa:ReportAuthor/agfa:Name"/>
    <xsl:variable name="verificationFlag">
      <xsl:choose>
        <xsl:when test="$resultStatus='Finalized' and $verifyingObserver/text()">VERIFIED</xsl:when>
        <xsl:otherwise>UNVERIFIED</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <DicomAttribute tag="0040A493" vr="CS">
      <Value number="1"><xsl:value-of select="$verificationFlag"/></Value>
    </DicomAttribute>
    <!--Content Date/Time-->
    <DicomAttribute tag="00080023" vr="DA">
      <Value number="1"><xsl:value-of select="agfa:InterpretationRecordDate"/></Value>
    </DicomAttribute>
    <DicomAttribute tag="00080033" vr="TM">
      <Value number="1"><xsl:value-of select="agfa:InterpretationRecordTime"/></Value>
    </DicomAttribute>
    <xsl:if test="$verificationFlag = 'VERIFIED'">
      <!-- Verifying Observer Sequence -->
      <DicomAttribute tag="0040A073" vr="SQ">
        <Item number="1">
          <!-- Verifying Organization -->
          <DicomAttribute tag="0040A027" vr="LO" />
          <!-- Verification DateTime -->
          <DicomAttribute tag="0040A030" vr="DT">
            <Value number="1"><xsl:value-of select="agfa:InterpretationRecordDate"/>T<xsl:value-of select="agfa:InterpretationRecordTime"/></Value>
          </DicomAttribute>
          <xsl:if test="$verifyingObserver">
            <!-- Verifying Observer Name -->
            <xsl:call-template name="pnAttrs">
              <xsl:with-param name="tag" select="'0040A075'" />
              <xsl:with-param name="val" select="$verifyingObserver" />
            </xsl:call-template>
          </xsl:if>
          <!-- Verifying Observer Identification Code Sequence -->
          <DicomAttribute tag="0040A088" vr="SQ"/>
        </Item>
      </DicomAttribute>
    </xsl:if>
    <DicomAttribute tag="0040A730" vr="SQ">
      <xsl:call-template name="hasConceptModItem">
        <xsl:with-param name="itemNo">1</xsl:with-param>
      </xsl:call-template>
      <xsl:call-template name="item">
        <xsl:with-param name="itemNo">2</xsl:with-param>
        <xsl:with-param name="parentCode"><xsl:value-of select="'121070'"/></xsl:with-param>
        <xsl:with-param name="parentCodeMeaning"><xsl:value-of select="'Report Body'"/></xsl:with-param>
        <xsl:with-param name="childCode"><xsl:value-of select="'121071'"/></xsl:with-param>
        <xsl:with-param name="childCodeMeaning"><xsl:value-of select="'Finding'"/></xsl:with-param>
        <xsl:with-param name="val"><xsl:value-of select="agfa:ReportBody/text()"/></xsl:with-param>
      </xsl:call-template>
      <xsl:call-template name="item">
        <xsl:with-param name="itemNo">3</xsl:with-param>
        <xsl:with-param name="parentCode"><xsl:value-of select="'121076'"/></xsl:with-param>
        <xsl:with-param name="parentCodeMeaning"><xsl:value-of select="'Conclusions'"/></xsl:with-param>
        <xsl:with-param name="childCode"><xsl:value-of select="'121077'"/></xsl:with-param>
        <xsl:with-param name="childCodeMeaning"><xsl:value-of select="'Conclusion'"/></xsl:with-param>
        <xsl:with-param name="val"><xsl:value-of select="agfa:Conclusions/text()"/></xsl:with-param>
      </xsl:call-template>
    </DicomAttribute>
  </xsl:template>

  <xsl:template name="hasConceptModItem">
    <xsl:param name="itemNo"/>
    <Item number="{$itemNo}">
      <DicomAttribute tag="0040A010" vr="CS">
        <Value number="1">HAS CONCEPT MOD</Value>
      </DicomAttribute>
      <DicomAttribute tag="0040A040" vr="CS">
        <Value number="1">CODE</Value>
      </DicomAttribute>
      <xsl:call-template name="codeItem">
        <xsl:with-param name="sqtag">0040A043</xsl:with-param>
        <xsl:with-param name="code">121049</xsl:with-param>
        <xsl:with-param name="scheme">DCM</xsl:with-param>
        <xsl:with-param name="meaning">Language of Content Item and Descendants</xsl:with-param>
      </xsl:call-template>
    </Item>
  </xsl:template>

  <xsl:template name="item">
    <xsl:param name="itemNo"/>
    <xsl:param name="parentCode"/>
    <xsl:param name="parentCodeMeaning"/>
    <xsl:param name="childCode"/>
    <xsl:param name="childCodeMeaning"/>
    <xsl:param name="val"/>
    <Item number="{$itemNo}">
      <DicomAttribute tag="0040A010" vr="CS">
        <Value number="1">CONTAINS</Value>
      </DicomAttribute>
      <DicomAttribute tag="0040A040" vr="CS">
        <Value number="1">CONTAINER</Value>
      </DicomAttribute>
      <xsl:call-template name="codeItem">
        <xsl:with-param name="sqtag">0040A043</xsl:with-param>
        <xsl:with-param name="code"><xsl:value-of select="$parentCode" /></xsl:with-param>
        <xsl:with-param name="scheme">DCM</xsl:with-param>
        <xsl:with-param name="meaning"><xsl:value-of select="$parentCodeMeaning" /></xsl:with-param>
      </xsl:call-template>
      <xsl:if test="$val">
        <DicomAttribute tag="0040A050" vr="CS">
          <Value number="1">SEPARATE</Value>
        </DicomAttribute>
        <DicomAttribute tag="0040A730" vr="SQ">
          <Item number="1">
            <DicomAttribute tag="0040A010" vr="CS">
              <Value number="1">CONTAINS</Value>
            </DicomAttribute>
            <DicomAttribute tag="0040A040" vr="CS">
              <Value number="1">TEXT</Value>
            </DicomAttribute>
            <xsl:call-template name="codeItem">
              <xsl:with-param name="sqtag">0040A043</xsl:with-param>
              <xsl:with-param name="code"><xsl:value-of select="$childCode" /></xsl:with-param>
              <xsl:with-param name="scheme">DCM</xsl:with-param>
              <xsl:with-param name="meaning"><xsl:value-of select="$childCodeMeaning" /></xsl:with-param>
            </xsl:call-template>
            <DicomAttribute tag="0040A160" vr="UT">
              <Value number="1"><xsl:value-of select="$val"/></Value>
            </DicomAttribute>
          </Item>
        </DicomAttribute>
      </xsl:if>
    </Item>
  </xsl:template>

  <xsl:template name="pnAttrs">
    <xsl:param name="tag"/>
    <xsl:param name="val"/>
    <DicomAttribute tag="{$tag}" vr="PN">
      <PersonName number="1">
        <Alphabetic>
          <xsl:call-template name="pnComp">
            <xsl:with-param name="name">FamilyName</xsl:with-param>
            <xsl:with-param name="val" select="$val/agfa:SingleByteName/agfa:LastName"/>
          </xsl:call-template>
          <xsl:call-template name="pnComp">
            <xsl:with-param name="name">GivenName</xsl:with-param>
            <xsl:with-param name="val" select="$val/agfa:SingleByteName/agfa:FirstName"/>
          </xsl:call-template>
        </Alphabetic>
      </PersonName>
    </DicomAttribute>
  </xsl:template>

</xsl:stylesheet>