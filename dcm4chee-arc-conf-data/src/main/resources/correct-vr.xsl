<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <xsl:apply-templates select="DicomAttribute[@vr='SH'][string-length(descendant::Value)>16]" mode="truncate" />
      <xsl:apply-templates select="DicomAttribute[@vr='LO'][string-length(descendant::Value)>64]" mode="truncate" />
      <xsl:apply-templates select="DicomAttribute[@vr='DA']" mode="da" />
      <xsl:apply-templates select="DicomAttribute[@vr='TM']" mode="tm"/>
      <xsl:apply-templates select="DicomAttribute[@vr='DT']" mode="dt" />
      <xsl:apply-templates select="DicomAttribute[@vr='IS'][descendant::Value!=number(Value) or contains(descendant::Value,'.')]" mode="nullify"/>
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

  <xsl:template match="@*|node()" mode="dt">
    <xsl:variable name="translated-dt" select="translate(translate(descendant::Value,'-',''),':','')"/>
    <xsl:variable name="date" select="substring($translated-dt,1,6)"/>
    <xsl:variable name="time" select="substring($translated-dt,7)"/>
    <xsl:variable name="time-No" select="number($time)"/>
    <xsl:choose>
      <xsl:when test="string-length($date)>8
        or string(number($date))='NaN' or number(substring($date,5,2))>12
        or number(substring($date,7,2))>31
        or string-length($time)>13 or
          (string-length($time) &lt;= 13 and $time!=$time-No) or
            (number(substring($time,1,2))>23 or number(substring($time,3,2))>59
            or number(substring($time,5,2))>60)">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[name() != DicomAttribute[@vr='DT']]" mode="nullify" />
        </xsl:copy>
      </xsl:when>
      <xsl:when test="contains(descendant::Value,'-') or contains(descendant::Value,':')">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[self::Value]" mode="dt" />
          <xsl:value-of select="$translated-dt"/>
        </xsl:copy>
      </xsl:when>
      <xsl:otherwise/>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="@*|node()" mode="da">
    <xsl:variable name="translated-date" select="translate(descendant::Value,'-','')"/>
    <xsl:choose>
      <xsl:when test="string-length($translated-date)>8
        or string(number($translated-date))='NaN' or number(substring($translated-date,5,2))>12
        or number(substring($translated-date,7,2))>31">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[name() != DicomAttribute[@vr='DA']]" mode="nullify" />
        </xsl:copy>
      </xsl:when>
      <xsl:when test="contains(descendant::Value,'-')">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[self::Value]" mode="da" />
          <xsl:value-of select="$translated-date"/>
        </xsl:copy>
      </xsl:when>
      <xsl:otherwise/>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="@*|node()" mode="tm">
    <xsl:variable name="translated-time" select="translate(descendant::Value,':','')"/>
    <xsl:variable name="translated-time-No" select="number($translated-time)"/>
    <xsl:choose>
      <xsl:when test="string-length($translated-time)>13 or
          (string-length($translated-time) &lt;= 13 and $translated-time!=$translated-time-No) or
            (number(substring($translated-time,1,2))>23 or number(substring($translated-time,3,2))>59
            or number(substring($translated-time,5,2))>60)">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[name() != DicomAttribute[@vr='TM']]" mode="nullify" />
        </xsl:copy>
      </xsl:when>
      <xsl:when test="contains(descendant::Value,':')">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[self::Value]" mode="tm" />
          <xsl:value-of select="$translated-time"/>
        </xsl:copy>
      </xsl:when>
      <xsl:when test="string-length(descendant::Value)=7 and substring(descendant::Value,7,1)='.'">
        <xsl:copy>
          <xsl:apply-templates select="@*|node()[self::Value]" mode="tm" />
          <xsl:value-of select="translate($translated-time,'.','')"/>
        </xsl:copy>
      </xsl:when>
      <xsl:otherwise/>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>
