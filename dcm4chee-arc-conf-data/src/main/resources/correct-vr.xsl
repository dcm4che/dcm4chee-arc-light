<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:java="http://xml.apache.org/xalan/java"
                exclude-result-prefixes="java" >
  <xsl:output method="xml"/>

  <xsl:variable name="regExAS" select="java:org.dcm4che3.util.RegEx.new('\d{3}[DMWY]')" />
  <xsl:variable name="regExCS" select="java:org.dcm4che3.util.RegEx.new('[0-9A-Z_ ]{0,16}')" />
  <xsl:variable name="regExDA" select="java:org.dcm4che3.util.RegEx.new('\d{8}')" />
  <xsl:variable name="regExDT" select="java:org.dcm4che3.util.RegEx.new('\d{4}(\d{2}(\d{2}(\d{2}(\d{2}(\d{2}(\.\d{0,6})?)?)?)?)?)?([+-]\d{4})?')" />
  <xsl:variable name="regExIS" select="java:org.dcm4che3.util.RegEx.new('[+-]?\d{1,12}')" />
  <xsl:variable name="regExTM" select="java:org.dcm4che3.util.RegEx.new('\d{2}(\d{2}(\d{2}(\.\d{0,6})?)?)?')" />

  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <xsl:apply-templates select="DicomAttribute" />
    </NativeDicomModel>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='AS']">
    <xsl:call-template name="nullifyIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value" mode="AS"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='CS']">
    <xsl:call-template name="nullifyIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value" mode="CS"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='DA']">
    <xsl:call-template name="nullifyIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value" mode="DA"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='DS']">
    <xsl:call-template name="nullifyIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value" mode="DS"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='DT']">
    <xsl:call-template name="nullifyIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value" mode="DT"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='IS']">
    <xsl:call-template name="nullifyIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value" mode="IS"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='LO']">
    <xsl:call-template name="truncateIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value">
          <xsl:with-param name="max" select="'LO'"/>
        </xsl:apply-templates>
      </xsl:with-param>4
      <xsl:with-param name="max" select="'6'"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='SH']">
    <xsl:call-template name="truncateIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value">
          <xsl:with-param name="max" select="'16'"/>
        </xsl:apply-templates>
      </xsl:with-param>
      <xsl:with-param name="max" select="'16'"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute[@vr='TM']">
    <xsl:call-template name="nullifyIfInvalid">
      <xsl:with-param name="invalid">
        <xsl:apply-templates select="Value" mode="TM"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DicomAttribute"/>

  <xsl:template name="nullifyIfInvalid">
    <xsl:param name="invalid"/>
    <xsl:if test="$invalid">
      <xsl:copy>
        <xsl:for-each select="@*">
          <xsl:copy/>
        </xsl:for-each>
      </xsl:copy>
      <xsl:comment>
        <xsl:value-of select="$invalid"/>
      </xsl:comment>
    </xsl:if>
  </xsl:template>

  <xsl:template name="truncateIfInvalid">
    <xsl:param name="invalid"/>
    <xsl:param name="max"/>
    <xsl:if test="$invalid">
      <xsl:copy>
        <xsl:for-each select="@*|node()">
          <xsl:copy>
            <xsl:for-each select="node()">
              <xsl:value-of select="substring(.,1,$max)"/>
            </xsl:for-each>
          </xsl:copy>
        </xsl:for-each>
      </xsl:copy>
      <xsl:comment>
        <xsl:value-of select="$invalid"/>
      </xsl:comment>
    </xsl:if>
  </xsl:template>

  <xsl:template match="Value">
    <xsl:param name="max" />
    <xsl:if test="string-length(.) > $max"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

  <xsl:template match="Value" mode="AS">
    <xsl:if test="not(java:match($regExAS,.))"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

  <xsl:template match="Value" mode="CS">
    <xsl:if test="not(java:match($regExCS,.))"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

  <xsl:template match="Value" mode="DA">
    <xsl:if test="not(java:match($regExDA,.))"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

  <xsl:template match="Value" mode="DT">
    <xsl:if test="not(java:match($regExDT,.))"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

  <xsl:template match="Value" mode="DS">
    <xsl:if test="number()!=number()"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

  <xsl:template match="Value" mode="IS">
    <xsl:if test="not(java:match($regExIS,.))"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

  <xsl:template match="Value" mode="TM">
    <xsl:if test="not(java:match($regExTM,.))"><xsl:copy-of select="."/></xsl:if>
  </xsl:template>

</xsl:stylesheet>
