<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:template match="/DiagnosticRadiologyReport">
    <NativeDicomModel>
      <xsl:call-template name="const-attrs"/>
      <xsl:apply-templates select="PatientDetails"/>
      <xsl:apply-templates select="OrderDetails"/>
      <xsl:apply-templates select="ReportDetails"/>
    </NativeDicomModel>
  </xsl:template>
  <xsl:template name="const-attrs">
    <!--Modality-->
    <DicomAttribute tag="00080060" vr="CS"><Value number="1">SR</Value></DicomAttribute>
    <!--Concept Name Code Sequence-->
    <xsl:call-template name="codeItem">
      <xsl:with-param name="sqtag">0040A043</xsl:with-param>
      <xsl:with-param name="code">11528-7</xsl:with-param>
      <xsl:with-param name="scheme">LN</xsl:with-param>
      <xsl:with-param name="meaning">Diagnostic Radiology Report</xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="PatientDetails">
    <!-- Patient Birth Date -->
    <DicomAttribute tag="00100030" vr="DA">
      <Value number="1"><xsl:value-of select="BirthDate"/></Value>
    </DicomAttribute>
    <!-- Patient Sex -->
    <DicomAttribute tag="00100040" vr="CS">
      <Value number="1">
        <xsl:call-template name="sex">
          <xsl:with-param name="val" select="Sex" />
        </xsl:call-template>
      </Value>
    </DicomAttribute>
    <xsl:call-template name="pnAttrs">
      <xsl:with-param name="tag" select="'00100010'" />
      <xsl:with-param name="val" select="Name/text()" />
    </xsl:call-template>
    <xsl:call-template name="pidAttrs">
      <xsl:with-param name="id" select="Id/IdText"/>
      <xsl:with-param name="issuer" select="Id/IdDomain"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template name="pnAttrs">
    <xsl:param name="tag"/>
    <xsl:param name="val"/>
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
  </xsl:template>

  <xsl:template name="pidAttrs">
    <xsl:param name="id"/>
    <xsl:param name="issuer"/>
    <DicomAttribute tag="00100020" vr="LO">
      <Value number="1">
        <xsl:value-of select="$id"/>
      </Value>
    </DicomAttribute>
    <xsl:if test="$issuer">
      <DicomAttribute tag="00100021" vr="LO">
        <Value number="1">
          <xsl:value-of select="$issuer"/>
        </Value>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>

  <xsl:template match="OrderDetails">
    <!-- Take Study Instance UID from first referenced Image - if available -->
    <xsl:variable name="suid"
                  select="StudyDetails/StudyInstanceUID"/>
    <!-- Study Instance UID -->
    <DicomAttribute tag="0020000D" vr="UI">
      <Value number="1"><xsl:value-of select="$suid"/></Value>
    </DicomAttribute>
    <!--Study Description-->
    <DicomAttribute tag="00081030" vr="LO">
      <Value number="1"><xsl:value-of select="StudyDetails/StudyDescription"/></Value>
    </DicomAttribute>
    <!--Study Date-->
    <DicomAttribute tag="00080020" vr="DA">
      <Value number="1"><xsl:value-of select="StudyDetails/StudyDate"/></Value>
    </DicomAttribute>
    <!--Study Time-->
    <DicomAttribute tag="00080030" vr="TM">
      <Value number="1"><xsl:value-of select="StudyDetails/StudyTime"/></Value>
    </DicomAttribute>
    <!--Referring Physician's Name-->
    <xsl:call-template name="pnAttrs">
      <xsl:with-param name="tag" select="'00080090'" />
      <xsl:with-param name="val" select="ReferringPhysician/Name/text()" />
    </xsl:call-template>
    <!--Accession Number-->
    <DicomAttribute tag="00080050" vr="SH">
      <Value number="1"><xsl:value-of select="AccessionNumber"/></Value>
    </DicomAttribute>
    <!--Referenced Request Sequence-->
    <DicomAttribute tag="0040A370" vr="SQ">
      <Item number="1">
        <!-- Study Instance UID -->
        <DicomAttribute tag="0020000D" vr="UI">
          <Value number="1"><xsl:value-of select="$suid"/></Value>
        </DicomAttribute>
        <!--Accession Number-->
        <DicomAttribute tag="00080050" vr="SH"/>
        <!--Referenced Study Sequence-->
        <DicomAttribute tag="00081110" vr="SQ"/>
        <!--Requested Procedure Description-->
        <DicomAttribute tag="00321060" vr="LO"/>
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
  <xsl:template match="ReportDetails">
    <xsl:variable name="resultStatus" select="ReportStatus"/>
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
    <xsl:variable name="verificationFlag">
      <xsl:choose>
        <xsl:when test="$resultStatus='Finalized' and ReportAuthor/Name/text()">VERIFIED</xsl:when>
        <xsl:otherwise>UNVERIFIED</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <DicomAttribute tag="0040A493" vr="CS">
      <Value number="1"><xsl:value-of select="$verificationFlag"/></Value>
    </DicomAttribute>
    <!--Content Date/Time-->
    <DicomAttribute tag="00080023" vr="DA">
      <Value number="1"><xsl:value-of select="InterpretationRecordDate"/></Value>
    </DicomAttribute>
    <DicomAttribute tag="00080033" vr="TM">
      <Value number="1"><xsl:value-of select="InterpretationRecordTime"/></Value>
    </DicomAttribute>
    <xsl:if test="$verificationFlag = 'VERIFIED'">
      <!-- Verifying Observer Sequence -->
      <DicomAttribute tag="0040A073" vr="SQ">
        <Item number="1">
          <!-- Verifying Organization -->
          <DicomAttribute tag="0040A027" vr="LO" />
          <!-- Verification DateTime -->
          <DicomAttribute tag="0040A030" vr="DT">
            <Value number="1"><xsl:value-of select="InterpretationRecordDate"/>T<xsl:value-of select="InterpretationRecordTime"/></Value>
          </DicomAttribute>
          <xsl:if test="ReportAuthor/Name/text()">
            <!-- Verifying Observer Name -->
            <xsl:call-template name="pnAttrs">
              <xsl:with-param name="tag" select="'0040A075'" />
              <xsl:with-param name="val" select="ReportAuthor/Name/text()" />
            </xsl:call-template>
          </xsl:if>
          <!-- Verifying Observer Identification Code Sequence -->
          <DicomAttribute tag="0040A088" vr="SQ"/>
        </Item>
      </DicomAttribute>
    </xsl:if>
    <!--Value Type-->
    <DicomAttribute tag="0040A040" vr="CS"><Value number="1">TEXT</Value></DicomAttribute>
    <DicomAttribute tag="0040A160" vr="UT">
      <Value number="1"><xsl:value-of select="ReportBody/text()"/></Value>
    </DicomAttribute>
  </xsl:template>
</xsl:stylesheet>