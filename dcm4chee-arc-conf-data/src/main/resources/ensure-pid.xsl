<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <xsl:if test="not(DicomAttribute[@tag='00100020']/Value)">
        <DicomAttribute tag="00100020" vr="LO">
          <xsl:copy-of select="DicomAttribute[@tag='0020000D']/Value"/>
        </DicomAttribute>
        <DicomAttribute tag="00100021" vr="LO">
          <Value number="1">DCM4CHEE-ARC</Value>
        </DicomAttribute>
      </xsl:if>
    </NativeDicomModel>
  </xsl:template>
</xsl:stylesheet>
