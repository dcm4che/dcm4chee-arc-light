<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <xsl:apply-templates select="DicomAttribute[@vr='SH'][string-length(descendant::Value)>16]" mode="truncate" />
      <xsl:apply-templates select="DicomAttribute[@vr='LO'][string-length(descendant::Value)>64]" mode="truncate" />
      <xsl:apply-templates select="DicomAttribute[@vr='DA']
        [not(string-length(descendant::Value)=8 and descendant::Value=number(Value) and
          number(substring(descendant::Value,5,2))&lt;=12 and number(substring(descendant::Value,7,2))&lt;=31)]" mode="da" />
      <xsl:apply-templates select="DicomAttribute[@vr='TM']" mode="tm"/>
      <xsl:apply-templates select="DicomAttribute[@vr='IS'][descendant::Value!=number(Value)]" mode="nullify"/>
      <xsl:apply-templates select="DicomAttribute[@vr='DS'][descendant::Value!=number(Value)]" mode="nullify"/>
      <xsl:apply-templates select="DicomAttribute[@vr='AS'][string-length(descendant::Value)!=4
        or string(number(substring(descendant::Value,1,3)))='NaN' or not(substring(descendant::Value,4)='D' or substring(descendant::Value,4)='W'
        or substring(descendant::Value,4)='M' or substring(descendant::Value,4)='Y')]" mode="nullify"/>
    </NativeDicomModel>
  </xsl:template>

  <xsl:template match="@*|node()" mode="nullify">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()[name() != (DicomAttribute[@vr='IS'] | DicomAttribute[@vr='DS']
      | DicomAttribute[@vr='AS'] | DicomAttribute[@vr='DA'] | DicomAttribute[@vr='TM'] | DicomAttribute[@vr='CS'])]" mode="nullify" />
    </xsl:copy>
  </xsl:template>

  <xsl:template match="@*|node()" mode="truncate">
    <xsl:variable name="val">
      <xsl:choose>
        <xsl:when test="DicomAttribute[@vr='LO'][string-length(ancestor-or-self::Value)>64]">
          <xsl:value-of select="substring(ancestor-or-self::Value,1,64)"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="substring(ancestor-or-self::Value,1,16)"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:copy>
      <xsl:apply-templates select="@*|node()[self::Value]" mode="truncate" />
      <xsl:value-of select="$val"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="@*|node()" mode="da">
    <xsl:variable name="translated-date" select="translate(descendant::Value,'-','')"/>
    <xsl:choose>
      <xsl:when test="string-length(descendant::Value)>8 and string-length($translated-date)=8
        and string(number($translated-date))!='NaN' and number(substring($translated-date,5,2))&lt;=12
        and number(substring($translated-date,7,2))&lt;=31">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[self::Value]" mode="da" />
          <xsl:value-of select="$translated-date"/>
        </xsl:copy>
      </xsl:when>
      <xsl:otherwise>
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[name() != DicomAttribute[@vr='DA']]" mode="nullify" />
        </xsl:copy>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>


  <xsl:template match="@*|node()" mode="tm">
    <xsl:if test="(string-length(descendant::Value)>13 and (not(contains(descendant::Value, ':')) or string-length(translate(descendant::Value,':',''))>13)) or
        (string-length(descendant::Value)=7 and string-length(translate(descendant::Value,'.',''))>6) or
        (string-length(descendant::Value) &lt;= 13 and
          (descendant::Value!=number(Value) or number(substring(descendant::Value,1,2))>23 or number(substring(descendant::Value,3,2))>59
          or number(substring(descendant::Value,5,2))>60))">
      <xsl:copy>
        <xsl:apply-templates select="@*|node()[name() != DicomAttribute[@vr='TM']]" mode="nullify" />
      </xsl:copy>
    </xsl:if>
    <xsl:if test="string-length(descendant::Value)>13 and string-length(translate(descendant::Value,':',''))=13">
      <xsl:apply-templates select="@*|node()" mode="tm-remove-colon"/>
    </xsl:if>
    <xsl:if test="string-length(descendant::Value)=7 and string-length(translate(descendant::Value,'.',''))=6">
      <xsl:apply-templates select="@*|node()" mode="tm-remove-dot"/>
    </xsl:if>
  </xsl:template>

  <xsl:template match="@*|node()" mode="tm-remove-colon">
    <xsl:variable name="val" select="translate(ancestor-or-self::Value, ':', '')"/>
    <xsl:copy>
      <xsl:apply-templates select="@*|node()[self::Value]" mode="tm-remove-colon" />
      <xsl:value-of select="$val"/>
    </xsl:copy>
  </xsl:template>
  <xsl:template match="@*|node()" mode="tm-remove-dot">
    <xsl:variable name="val" select="translate(ancestor-or-self::Value, '.', '')"/>
    <xsl:copy>
      <xsl:apply-templates select="@*|node()[self::Value]" mode="tm-remove-dot" />
      <xsl:value-of select="$val"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
