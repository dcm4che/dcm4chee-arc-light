<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <!-- Study ID derive from Requested Procedure ID -->
      <xsl:call-template name="attr">
        <xsl:with-param name="tag" select="'00200010'"/>
        <xsl:with-param name="vr" select="'SH'"/>
        <xsl:with-param name="val" select="DicomAttribute[@tag='00401001']/Value"/>
      </xsl:call-template>
      <!-- Study Description derive from Requested Procedure Description -->
      <xsl:call-template name="attr">
        <xsl:with-param name="tag" select="'00081030'"/>
        <xsl:with-param name="vr" select="'SH'"/>
        <xsl:with-param name="val" select="DicomAttribute[@tag='00321060']/Value"/>
      </xsl:call-template>
      <!-- Procedure Code Sequence derive from Requested Procedure Code Sequence -->
      <xsl:if test="DicomAttribute[@tag='00321064']/Item">
        <DicomAttribute tag="00081032" vr="SQ">
          <xsl:copy-of select="DicomAttribute[@tag='00321064']/Item"/>
        </DicomAttribute>
      </xsl:if>
      <xsl:copy-of select="DicomAttribute[@tag='00080050']"/>
      <xsl:copy-of select="DicomAttribute[@tag='00100010']"/>
      <xsl:copy-of select="DicomAttribute[@tag='00100020']"/>
      <xsl:copy-of select="DicomAttribute[@tag='00100021']"/>
      <xsl:copy-of select="DicomAttribute[@tag='00100030']"/>
      <xsl:copy-of select="DicomAttribute[@tag='00100040']"/>
      <DicomAttribute tag="00400275" vr="SQ">
        <Item number="1">
          <xsl:copy-of select="DicomAttribute[@tag='00080050']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00080051']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00081110']"/>
          <xsl:copy-of select="DicomAttribute[@tag='0020000D']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00321031']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00321032']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00321033']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00321034']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00321060']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00321064']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00400100']/Item/DicomAttribute[@tag='00400007']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00400100']/Item/DicomAttribute[@tag='00400008']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00400100']/Item/DicomAttribute[@tag='00400009']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00401001']"/>
          <xsl:copy-of select="DicomAttribute[@tag='00401002']"/>
          <xsl:copy-of select="DicomAttribute[@tag='0040100A']"/>
        </Item>
      </DicomAttribute>
    </NativeDicomModel>
  </xsl:template>
</xsl:stylesheet>
