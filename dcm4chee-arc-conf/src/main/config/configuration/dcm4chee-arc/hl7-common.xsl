<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:param name="hl7CharacterSet"/>

  <xsl:template name="attr">
    <xsl:param name="tag"/>
    <xsl:param name="vr"/>
    <xsl:param name="val"/>
    <xsl:if test="$val">
      <DicomAttribute tag="{$tag}" vr="{$vr}">
        <xsl:if test="$val != '&quot;&quot;'">
          <Value number="1">
            <xsl:value-of select="$val"/>
          </Value>
        </xsl:if>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>

  <xsl:template name="sex">
    <xsl:param name="val"/>
    <xsl:if test="$val">
      <xsl:choose>
        <xsl:when test="$val = 'F' or $val = 'M' or $val = 'O'">
          <xsl:value-of select="$val"/>
        </xsl:when>
        <xsl:when test="$val = 'A' or $val = 'N'">O</xsl:when>
        <xsl:otherwise>&quot;&quot;</xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>

  <xsl:template name="pnComp">
    <xsl:param name="name"/>
    <xsl:param name="val"/>
    <xsl:if test="$val and $val != '&quot;&quot;'">
      <xsl:element name="{$name}">
        <xsl:value-of select="$val"/>
      </xsl:element>
    </xsl:if>
  </xsl:template>

  <xsl:template name="concat">
    <xsl:param name="val1"/>
    <xsl:param name="val2"/>
    <xsl:choose>
      <xsl:when test="not ($val1 and $val1 != '&quot;&quot;')">
        <xsl:value-of select="$val2"/>
      </xsl:when>
      <xsl:when test="not ($val2 and $val2 != '&quot;&quot;')">
        <xsl:value-of select="$val1"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="concat($val1,' ',$val2)"/>
      </xsl:otherwise>
    </xsl:choose>
   </xsl:template>

  <xsl:template name="pnAttr">
    <xsl:param name="tag"/>
    <xsl:param name="val"/>
    <xsl:param name="fn"/>
    <xsl:param name="gn"/>
    <xsl:param name="mn"/>
    <xsl:param name="np"/>
    <xsl:param name="ns"/>
    <xsl:param name="deg"/>
    <xsl:if test="$val">
      <DicomAttribute tag="{$tag}" vr="PN">
        <xsl:if test="$val != '&quot;&quot;'">
          <PersonName number="1">
            <Alphabetic>
              <xsl:call-template name="pnComp">
                <xsl:with-param name="name">FamilyName</xsl:with-param>
                <xsl:with-param name="val" select="$fn"/>
              </xsl:call-template>
              <xsl:call-template name="pnComp">
                <xsl:with-param name="name">GivenName</xsl:with-param>
                <xsl:with-param name="val" select="$gn"/>
              </xsl:call-template>
              <xsl:call-template name="pnComp">
                <xsl:with-param name="name">MiddleName</xsl:with-param>
                <xsl:with-param name="val" select="$mn"/>
              </xsl:call-template>
              <xsl:call-template name="pnComp">
                <xsl:with-param name="name">NamePrefix</xsl:with-param>
                <xsl:with-param name="val" select="$np"/>
              </xsl:call-template>
              <xsl:call-template name="pnComp">
                <xsl:with-param name="name">NameSuffix</xsl:with-param>
                <xsl:with-param name="val">
                  <xsl:call-template name="concat">
                    <xsl:with-param name="val1" select="$ns"/>
                    <xsl:with-param name="val2" select="$deg"/>
                  </xsl:call-template>
                </xsl:with-param>
              </xsl:call-template>
            </Alphabetic>
          </PersonName>
        </xsl:if>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>

  <xsl:template name="cx2pidAttrs">
    <xsl:param name="cx"/>
    <DicomAttribute tag="00100020" vr="LO">
      <Value number="1">
        <xsl:value-of select="$cx/text()"/>
      </Value>
    </DicomAttribute>
    <xsl:variable name="hd" select="$cx/component[3]" />
    <xsl:if test="$hd">
      <DicomAttribute tag="00100021" vr="LO">
        <Value number="1">
          <xsl:value-of select="$hd/text()"/>
        </Value>
      </DicomAttribute>
      <xsl:if test="$hd/subcomponent[2]">
        <DicomAttribute tag="00100024" vr="SQ">
          <Item number="1">
            <DicomAttribute tag="00400032" vr="UT">
              <Value number="1">
                <xsl:value-of select="$hd/subcomponent[1]"/>
              </Value>
            </DicomAttribute>
            <DicomAttribute tag="00400033" vr="CS">
              <Value number="1">
                <xsl:value-of select="$hd/subcomponent[2]"/>
              </Value>
            </DicomAttribute>
          </Item>
        </DicomAttribute>
      </xsl:if>
    </xsl:if>
  </xsl:template>

  <xsl:template name="xpn2pnAttr">
    <xsl:param name="tag"/>
    <xsl:param name="xpn"/>
    <xsl:param name="xpn25" select="$xpn/component"/>
    <xsl:call-template name="pnAttr">
      <xsl:with-param name="tag" select="$tag"/>
      <xsl:with-param name="val" select="string($xpn/text())"/>
      <xsl:with-param name="fn" select="string($xpn/text())"/>
      <xsl:with-param name="gn" select="string($xpn25[1]/text())"/>
      <xsl:with-param name="mn" select="string($xpn25[2]/text())"/>
      <xsl:with-param name="ns" select="string($xpn25[3]/text())"/>
      <xsl:with-param name="np" select="string($xpn25[4]/text())"/>
      <xsl:with-param name="deg" select="string($xpn25[5]/text())"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template name="cn2pnAttr">
    <xsl:param name="tag"/>
    <xsl:param name="cn"/>
    <xsl:param name="cn26" select="$cn/component"/>
    <xsl:call-template name="pnAttr">
      <xsl:with-param name="tag" select="$tag"/>
      <xsl:with-param name="val" select="string($cn/text())"/>
      <xsl:with-param name="fn" select="string($cn26[1]/text())"/>
      <xsl:with-param name="gn" select="string($cn26[2]/text())"/>
      <xsl:with-param name="mn" select="string($cn26[3]/text())"/>
      <xsl:with-param name="ns" select="string($cn26[4]/text())"/>
      <xsl:with-param name="np" select="string($cn26[5]/text())"/>
      <xsl:with-param name="deg" select="string($cn26[6]/text())"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template name="codeItem">
    <xsl:param name="sqtag"/>
    <xsl:param name="code"/>
    <xsl:param name="scheme"/>
    <xsl:param name="meaning"/>
    <xsl:if test="$code">
      <DicomAttribute tag="{$sqtag}" vr="SQ">
        <Item number="1">
          <!-- Code Value -->
          <DicomAttribute tag="00080100" vr="SH">
            <Value number="1">
              <xsl:value-of select="$code"/>
            </Value>
          </DicomAttribute>
          <!-- Coding Scheme Designator -->
          <DicomAttribute tag="00080102" vr="SH">
            <Value number="1">
              <xsl:value-of select="$scheme"/>
            </Value>
          </DicomAttribute>
          <!-- Code Meaning -->
          <DicomAttribute tag="00080104" vr="LO">
            <Value number="1">
              <xsl:value-of select="$meaning"/>
            </Value>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
    </xsl:if>
  </xsl:template>

  <xsl:template match="PID">
    <!-- Patient Name -->
    <xsl:call-template name="xpn2pnAttr">
      <xsl:with-param name="tag" select="'00100010'"/>
      <xsl:with-param name="xpn" select="field[5]"/>
    </xsl:call-template>
    <!-- Patient ID -->
    <xsl:call-template name="cx2pidAttrs">
      <xsl:with-param name="cx" select="field[3]"/>
    </xsl:call-template>
    <!-- Patient Birth Date -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00100030'"/>
      <xsl:with-param name="vr" select="'DA'"/>
      <xsl:with-param name="val" select="substring(field[7],1,8)"/>
    </xsl:call-template>
    <!-- Patient Sex -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00100040'"/>
      <xsl:with-param name="vr" select="'CS'"/>
      <xsl:with-param name="val">
        <xsl:call-template name="sex">
          <xsl:with-param name="val" select="field[8]"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
    <!-- Patient's Mother's Birth Name -->
    <xsl:call-template name="xpn2pnAttr">
      <xsl:with-param name="tag" select="'00101060'"/>
      <xsl:with-param name="xpn" select="field[6]"/>
    </xsl:call-template>
  </xsl:template>
  <xsl:template match="MRG">
    <!-- Modified Attributes Sequence -->
    <DicomAttribute tag="04000550" vr="SQ">
      <Item number="1">
        <!-- Patient Name -->
        <xsl:call-template name="xpn2pnAttr">
            <xsl:with-param name="tag" select="'00100010'"/>
            <xsl:with-param name="xpn" select="field[7]"/>
        </xsl:call-template>
        <!-- Patient ID -->
        <xsl:call-template name="cx2pidAttrs">
            <xsl:with-param name="cx" select="field[1]"/>
        </xsl:call-template>
      </Item>
    </DicomAttribute>
  </xsl:template>
</xsl:stylesheet>
