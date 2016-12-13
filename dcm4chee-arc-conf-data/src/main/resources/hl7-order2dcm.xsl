<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:template match="/hl7">
    <NativeDicomModel>
      <DicomAttribute tag="00080005" vr="CS">
        <Value number="1">ISO_IR 100</Value>
      </DicomAttribute>
      <xsl:apply-templates select="PID"/>
      <xsl:apply-templates select="PV1"/>
      <xsl:apply-templates select="ORC[1]"/>
      <xsl:apply-templates select="OBR[1]"/>
      <!-- Scheduled Procedure Step Sequence -->
      <DicomAttribute tag="00400100" vr="SQ">
        <xsl:apply-templates select="ORC" mode="sps"/>
      </DicomAttribute>
      <xsl:apply-templates select="ZDS"/>
      <xsl:apply-templates select="IPC"/>
    </NativeDicomModel>
  </xsl:template>
  <xsl:template match="PV1">
    <!-- Referring Physican Name -->
    <xsl:call-template name="cn2pnAttr">
      <xsl:with-param name="tag" select="'00080090'"/>
      <xsl:with-param name="cn" select="field[8]"/>
    </xsl:call-template>
    <xsl:call-template name="pregnancyStatus">
      <xsl:with-param name="ambulantStatus" select="string(field[15]/text())"/>
    </xsl:call-template>
    <!-- Admission ID, Issuer -->
    <DicomAttribute tag="00380010" vr="LO">
      <Value number="1">
        <xsl:value-of select="string(field[19]/text())"/>
      </Value>
    </DicomAttribute>
    <xsl:variable name="issuerOfAdmissionID" select="string(field[19]/component[3]/text())"/>
    <xsl:if test="$issuerOfAdmissionID">
      <!-- Issuer of Admission ID Sequence -->
      <DicomAttribute tag="00380014" vr="SQ">
        <Item number="1">
          <!-- Local Namespace Entity ID -->
          <DicomAttribute tag="00400031" vr="UT">
            <Value number="1">
              <xsl:value-of select="$issuerOfAdmissionID"/>
            </Value>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>
  <xsl:template name="pregnancyStatus">
    <xsl:param name="ambulantStatus"/>
    <xsl:if test="normalize-space($ambulantStatus)">
      <DicomAttribute tag="001021C0" vr="US">
        <Value number="1">
          <xsl:if test="$ambulantStatus = 'B6'">3</xsl:if>
        </Value>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>
  <xsl:template match="ORC[1]">
    <!-- Placer Order Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00402016'"/>
      <xsl:with-param name="ei" select="field[2]"/>
    </xsl:call-template>
    <!-- Filler Order Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00402017'"/>
      <xsl:with-param name="ei" select="field[3]"/>
    </xsl:call-template>
    <xsl:call-template name="procedurePriority">
      <xsl:with-param name="priority" select="string(field[7]/component[5]/text())"/>
    </xsl:call-template>
  </xsl:template>
  <xsl:template name="procedurePriority">
    <xsl:param name="priority"/>
    <xsl:if test="normalize-space($priority)">
      <DicomAttribute tag="00401003" vr="CS">
        <Value number="1">
          <xsl:choose>
            <xsl:when test="$priority = 'S'">STAT</xsl:when>
            <xsl:when test="$priority = 'A' or $priority = 'P' or $priority = 'C' ">HIGH</xsl:when>
            <xsl:when test="$priority = 'R'">ROUTINE</xsl:when>
            <xsl:when test="$priority = 'T'">MEDIUM</xsl:when>
          </xsl:choose>
        </Value>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>
  <xsl:template match="OBR[1]">
    <!-- Accession Number -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00080050'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val" select="string(field[18]/text())"/>
    </xsl:call-template>
    <!-- Medical Alerts -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00102000'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="substring(field[13]/text(),1,64)"/>
    </xsl:call-template>
    <!-- Requesting Physician -->
    <xsl:call-template name="cn2pnAttr">
      <xsl:with-param name="tag" select="'00321032'"/>
      <xsl:with-param name="cn" select="field[16]"/>
    </xsl:call-template>
    <!-- Requested Procedure Description -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00321060'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="field[44]/component[1]"/>
    </xsl:call-template>
    <!-- Requested Procedure Code Sequence -->
    <xsl:call-template name="codeItem">
      <xsl:with-param name="sqtag" select="'00321064'"/>
      <xsl:with-param name="code" select="string(field[44]/text())"/>
      <xsl:with-param name="scheme" select="string(field[44]/component[2]/text())"/>
      <xsl:with-param name="meaning" select="substring(field[44]/component[1]/text(),1,64)"/>
    </xsl:call-template>
    <!-- Patient State -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00380500'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="substring(field[12]/text(),1,64)"/>
    </xsl:call-template>
    <!-- Requested Procedure ID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401001'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val" select="string(field[19]/text())"/>
    </xsl:call-template>
    <!-- Patient Transport Arrangements -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401004'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="substring(field[30]/text(),1,64)"/>
    </xsl:call-template>
  </xsl:template>
  <xsl:template match="ORC" mode="sps">
    <Item number="1">
      <!-- Scheduled Procedure Step Start Date/Time -->
      <xsl:call-template name="attrDATM">
        <xsl:with-param name="datag" select="'00400002'"/>
        <xsl:with-param name="tmtag" select="'00400003'"/>
        <xsl:with-param name="val" select="string(field[7]/component[3]/text())"/>
      </xsl:call-template>
      <!-- Scheduled Procedure Step Status -->
      <xsl:call-template name="attr">
        <xsl:with-param name="tag" select="'00400020'"/>
        <xsl:with-param name="vr" select="'CS'"/>
        <xsl:with-param name="val" select="concat(string(field[1]), '_', string(field[5]))"/>
      </xsl:call-template>
      <xsl:apply-templates select="following-sibling::OBR[1]" mode="sps"/>
    </Item>
  </xsl:template>
  <xsl:template match="OBR" mode="sps">
    <!-- Modality -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00080060'"/>
      <xsl:with-param name="vr" select="'CS'"/>
      <xsl:with-param name="val" select="string(field[24]/text())"/>
    </xsl:call-template>
    <!-- Scheduled Performing Physican Name -->
    <xsl:call-template name="cn2pnAttr">
      <xsl:with-param name="tag" select="'00400006'"/>
      <xsl:with-param name="cn" select="field[34]"/>
      <xsl:with-param name="cn26" select="field[34]/subcomponent"/>
    </xsl:call-template>
    <!-- Scheduled Procedure Step Description -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00400007'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="substring(field[4]/component[4]/text(),1,64)"/>
    </xsl:call-template>
    <!-- Scheduled Protocol Code Sequence -->
    <xsl:call-template name="codeItem">
      <xsl:with-param name="sqtag" select="'00400008'"/>
      <xsl:with-param name="code" select="string(field[4]/component[3]/text())"/>
      <xsl:with-param name="scheme" select="string(field[4]/component[5]/text())"/>
      <xsl:with-param name="meaning" select="substring(field[4]/component[4]/text(),1,64)"/>
    </xsl:call-template>
    <!-- Scheduled Procedure Step ID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00400009'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val" select="string(field[20]/text())"/>
    </xsl:call-template>
  </xsl:template>
  <xsl:template match="ZDS">
    <!-- Study Instance UID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'0020000D'"/>
      <xsl:with-param name="vr" select="'UI'"/>
      <xsl:with-param name="val" select="string(field[1]/text())"/>
    </xsl:call-template>
  </xsl:template>
  <xsl:template match="IPC">
    <!-- Study Instance UID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'0020000D'"/>
      <xsl:with-param name="vr" select="'UI'"/>
      <xsl:with-param name="val" select="string(field[3]/text())"/>
    </xsl:call-template>
  </xsl:template>
</xsl:stylesheet>
