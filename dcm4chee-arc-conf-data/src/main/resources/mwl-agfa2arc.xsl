<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <xsl:apply-templates select="DicomAttribute"/>
      <xsl:call-template name="spsSeq"/>
    </NativeDicomModel>
  </xsl:template>

  <xsl:template match="DicomAttribute">
    <xsl:variable name="tag" select="@tag"/>
    <xsl:variable name="keyword" select="@keyword"/>
    <xsl:variable name="vr" select="@vr"/>
    <xsl:if test="$tag != '00400020' and $tag != '00400100'">
      <xsl:call-template name="copyAttr">
        <xsl:with-param name="keyword" select="$keyword"/>
        <xsl:with-param name="tag" select="$tag"/>
        <xsl:with-param name="vr" select="$vr"/>
      </xsl:call-template>
    </xsl:if>
  </xsl:template>

  <xsl:template name="spsSeq">
    <DicomAttribute keyword="'ScheduledProcedureStepSequence'" tag="00400100" vr="SQ">
      <Item number="1">
        <xsl:call-template name="attr">
          <xsl:with-param name="keyword" select="'ScheduledProcedureStepStatus'"/>
          <xsl:with-param name="tag" select="'00400020'"/>
          <xsl:with-param name="vr" select="'CS'"/>
          <xsl:with-param name="val">
            <xsl:call-template name="studyStatusID2SPSStatus">
              <xsl:with-param name="studyStatusID" select="DicomAttribute[@tag='0032000A']"/>
            </xsl:call-template>
          </xsl:with-param>
        </xsl:call-template>
        <xsl:for-each select="DicomAttribute[@tag='00400100']/Item">
          <xsl:apply-templates select="DicomAttribute"/>
        </xsl:for-each>
      </Item>
    </DicomAttribute>
  </xsl:template>

  <xsl:template name="attr">
    <xsl:param name="keyword"/>
    <xsl:param name="tag"/>
    <xsl:param name="vr"/>
    <xsl:param name="val"/>
    <DicomAttribute keyword="'{$keyword}'" vr="{$vr}" tag="{$tag}">
      <Value number="1">
        <xsl:value-of select="$val"/>
      </Value>
    </DicomAttribute>
  </xsl:template>

  <xsl:template name="copyAttr">
    <xsl:param name="keyword"/>
    <xsl:param name="tag"/>
    <xsl:param name="vr"/>
    <DicomAttribute keyword="{$keyword}" tag="{$tag}" vr="{$vr}">
      <xsl:copy-of select="node()"/>
    </DicomAttribute>
  </xsl:template>

  <xsl:template name="studyStatusID2SPSStatus">
    <xsl:param name="studyStatusID"/>
    <xsl:choose>
      <xsl:when test="$studyStatusID = 'VERIFIED'">
        <xsl:value-of select="'COMPLETED'"/>
      </xsl:when>
      <xsl:when test="$studyStatusID = 'CANCELLED'">
        <xsl:value-of select="'CANCELED'"/>
      </xsl:when>
      <xsl:when test="$studyStatusID = 'STARTED'
                          or $studyStatusID = 'ARRIVED'
                          or $studyStatusID = 'SCHEDULED'
                          or $studyStatusID = 'COMPLETED'">
        <xsl:value-of select="$studyStatusID"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="'SCHEDULED'"/> <!-- READ, CREATED, NONE -->
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>
