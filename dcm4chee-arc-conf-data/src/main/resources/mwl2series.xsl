<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
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
          <xsl:copy-of select="DicomAttribute[@tag='00321062']"/>
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
