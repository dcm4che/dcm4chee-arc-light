<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:template match="/hl7">
    <NativeDicomModel>
      <xsl:apply-templates select="PID"/>
      <xsl:apply-templates select="MRG"/>
    </NativeDicomModel>
  </xsl:template>
</xsl:stylesheet>
