<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <DicomAttribute tag="00080050" vr="SH">
        <xsl:copy-of select="DicomAttribute[@tag='00080050']/Value"/>
      </DicomAttribute>
      <DicomAttribute tag="00200010" vr="SH">
        <xsl:copy-of select="DicomAttribute[@tag='00401001']/Value"/>
      </DicomAttribute>
      <DicomAttribute tag="00081030" vr="LO">
        <xsl:copy-of select="DicomAttribute[@tag='00321060']/Value"/>
      </DicomAttribute>
      <DicomAttribute tag="00081032" vr="SQ">
        <xsl:copy-of select="DicomAttribute[@tag='00321064']"/>
      </DicomAttribute>
      <DicomAttribute tag="00100010" vr="PN">
        <xsl:copy-of select="DicomAttribute[@tag='00100010']/Value"/>
      </DicomAttribute>
      <DicomAttribute tag="00100020" vr="LO">
        <xsl:copy-of select="DicomAttribute[@tag='00100020']/Value"/>
      </DicomAttribute>
      <DicomAttribute tag="00100021" vr="LO">
        <xsl:copy-of select="DicomAttribute[@tag='00100021']/Value"/>
      </DicomAttribute>
      <DicomAttribute tag="00100030" vr="DA">
        <xsl:copy-of select="DicomAttribute[@tag='00100030']/Value"/>
      </DicomAttribute>
      <DicomAttribute tag="00100040" vr="CS">
        <xsl:copy-of select="DicomAttribute[@tag='00100040']/Value"/>
      </DicomAttribute>
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
