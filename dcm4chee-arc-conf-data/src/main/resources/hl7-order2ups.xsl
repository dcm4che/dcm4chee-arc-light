<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:include href="hl7-common.xsl"/>

  <xsl:template match="/hl7">
    <NativeDicomModel>
      <xsl:apply-templates select="PID"/>
      <xsl:apply-templates select="PV1"/>
      <xsl:apply-templates select="ORC"/>
      <xsl:call-template name="studyIUIDRefReq"/>
    </NativeDicomModel>
  </xsl:template>

  <xsl:template match="PV1">
    <!-- Admission ID, Issuer -->
    <xsl:param name="ei" select="field[19]"/>
    <xsl:variable name="val" select="$ei/text()"/>
    <xsl:if test="$val">
      <xsl:if test="$val != '&quot;&quot;'">
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00380010'"/>
          <xsl:with-param name="vr" select="'LO'"/>
          <xsl:with-param name="val" select="$val"/>
        </xsl:call-template>
      </xsl:if>
      <DicomAttribute tag="00380014" vr="SQ">
        <Item number="1">
          <xsl:if test="$ei/component and $val != '&quot;&quot;'">
            <xsl:call-template name="attr">
              <xsl:with-param name="tag" select="'00400031'"/>
              <xsl:with-param name="vr" select="'UT'"/>
              <xsl:with-param name="val" select="$ei/component[3]/text()"/>
            </xsl:call-template>
          </xsl:if>
        </Item>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>

  <xsl:template match="ORC">
    <xsl:variable name="tq1" select="following-sibling::TQ1"/>
    <xsl:choose>
      <xsl:when test="$tq1">
        <xsl:apply-templates select="$tq1"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- Scheduled Procedure Step Start Date/Time -->
        <xsl:call-template name="attrDT">
          <xsl:with-param name="dtTag" select="'00404005'"/>
          <xsl:with-param name="val" select="string(field[7]/component[3]/text())"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="TQ1">
    <!-- Scheduled Procedure Step Start Date/Time -->
    <xsl:call-template name="attrDT">
      <xsl:with-param name="dtTag" select="'00404005'"/>
      <xsl:with-param name="val" select="field[7]"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template name="studyIUIDRefReq">
    <xsl:variable name="ipc" select="following-sibling::IPC"/>
    <xsl:variable name="studyIUID">
      <xsl:choose>
        <xsl:when test="$ipc">
          <xsl:apply-templates select="$ipc"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="ZDS"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:call-template name="studyIUID">
      <xsl:with-param name="val" select="$studyIUID"/>
    </xsl:call-template>
    <!-- Referenced Request Sequence -->
    <DicomAttribute tag="0040A370" vr="SQ">
      <Item number="1">
        <xsl:call-template name="studyIUID">
          <xsl:with-param name="val" select="$studyIUID"/>
        </xsl:call-template>
        <xsl:apply-templates select="PV1" mode="rrs"/>
        <xsl:choose>
          <xsl:when test="$ipc">
            <xsl:apply-templates select="$ipc" mode="rrs"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:apply-templates select="OBR" mode="rrs"/>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:apply-templates select="OBR" mode="rrs-req"/>
        <xsl:apply-templates select="ORC" mode="rrs"/>
      </Item>
    </DicomAttribute>
  </xsl:template>

  <xsl:template match="ZDS">
    <xsl:value-of select="string(field[1]/text())"/>
  </xsl:template>

  <xsl:template match="IPC">
    <xsl:value-of select="string(field[3]/text())"/>
  </xsl:template>

  <xsl:template name="studyIUID">
    <xsl:param name="val"/>
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'0020000D'"/>
      <xsl:with-param name="vr" select="'UI'"/>
      <xsl:with-param name="val" select="$val"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="PV1" mode="rrs">
    <!-- Referring Physician Name -->
    <xsl:call-template name="cn2pnAttr">
      <xsl:with-param name="tag" select="'00080090'"/>
      <xsl:with-param name="cn" select="field[8]"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="IPC" mode="rrs">
    <!-- Accession Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00080050'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="sqtag" select="'00080051'"/>
      <xsl:with-param name="ei" select="field[1]"/>
    </xsl:call-template>
    <!-- Requested Procedure ID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401001'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val" select="string(field[2]/text())"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="OBR" mode="rrs">
    <!-- Accession Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00080050'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="sqtag" select="'00080051'"/>
      <xsl:with-param name="ei" select="field[18]"/>
    </xsl:call-template>
    <!-- Requested Procedure ID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401001'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val" select="string(field[19]/text())"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="OBR" mode="rrs-req">
    <!-- Requesting Physician -->
    <xsl:call-template name="cn2pnAttr">
      <xsl:with-param name="tag" select="'00321032'"/>
      <xsl:with-param name="cn" select="field[16]"/>
    </xsl:call-template>
    <!-- Requested Procedure Description and Code Sequence -->
    <xsl:call-template name="ce2codeItemWithDesc">
      <xsl:with-param name="descTag" select="'00321060'"/>
      <xsl:with-param name="seqTag" select="'00321064'"/>
      <xsl:with-param name="codedEntry" select="field[44]"/>
    </xsl:call-template>
    <!-- Reason for the Requested Procedure Code and Sequence -->
    <xsl:call-template name="ce2codeItemWithDesc">
      <xsl:with-param name="descTag" select="'00401002'"/>
      <xsl:with-param name="seqTag" select="'0040100A'"/>
      <xsl:with-param name="codedEntry" select="field[31]"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="ORC" mode="rrs">
    <!-- Placer Order Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00402016'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="sqtag" select="'00400026'"/>
      <xsl:with-param name="ei" select="field[2]"/>
    </xsl:call-template>
    <!-- Filler Order Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00402017'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="sqtag" select="'00400027'"/>
      <xsl:with-param name="ei" select="field[3]"/>
    </xsl:call-template>
  </xsl:template>

</xsl:stylesheet>