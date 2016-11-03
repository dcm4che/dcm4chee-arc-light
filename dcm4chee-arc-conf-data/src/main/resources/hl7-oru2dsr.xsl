<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:param name="VerifyingOrganization">Verifying Organization</xsl:param>
  <xsl:template match="/hl7">
    <NativeDicomModel>
      <xsl:call-template name="const-attrs"/>
      <!--SOP Instance UID-->
      <DicomAttribute tag="00080018" vr="UI">
        <Value number="1"><xsl:value-of select="OBX[field[3]/component='SR Instance UID']/field[5]"/></Value>
      </DicomAttribute>
      <xsl:apply-templates select="PID"/>
      <xsl:apply-templates select="OBR"/>
      <!--Content Sequence-->
      <DicomAttribute tag="0040A730" vr="SQ">
        <xsl:call-template name="const-obsctx"/>
        <xsl:apply-templates select="OBR" mode="obsctx"/>
        <xsl:apply-templates select="OBX[field[3]/component='SR Text']" mode="txt"/>
      </DicomAttribute>
    </NativeDicomModel>
  </xsl:template>
  <xsl:template name="const-attrs">
    <!-- Specific Character Set -->
    <DicomAttribute tag="00080005" vr="CS"><Value number="1">ISO_IR 100</Value></DicomAttribute>
    <!--SOP Class UID-->
    <DicomAttribute tag="00080016" vr="UI"><Value number="1">1.2.840.10008.5.1.4.1.1.88.11</Value></DicomAttribute>
    <!--Study Date-->
    <DicomAttribute tag="00080020" vr="DA"/>
    <!--Study Time-->
    <DicomAttribute tag="00080030" vr="TM"/>
    <!--Accession Number-->
    <DicomAttribute tag="00080050" vr="SH"/>
    <!--Modality-->
    <DicomAttribute tag="00080060" vr="CS"><Value number="1">SR</Value></DicomAttribute>
    <!--Manufacturer-->
    <DicomAttribute tag="00080070" vr="LO"/>
    <!--Referring Physician's Name-->
    <DicomAttribute tag="00080090" vr="PN"/>
    <!--Referenced Performed Procedure Step Sequence-->
    <DicomAttribute tag="00081111" vr="SQ"/>
    <!--Study ID-->
    <DicomAttribute tag="00200010" vr="SH"/>
    <!--Series Number-->
    <DicomAttribute tag="00200011" vr="IS"/>
    <!--Instance Number-->
    <DicomAttribute tag="00200013" vr="IS"><Value number="1">1</Value></DicomAttribute>
    <!--Value Type-->
    <DicomAttribute tag="0040A040" vr="CS"><Value number="1">CONTAINER</Value></DicomAttribute>
    <!--Concept Name Code Sequence-->
    <xsl:call-template name="codeItem">
      <xsl:with-param name="sqtag">0040A043</xsl:with-param>
      <xsl:with-param name="code">11528-7</xsl:with-param>
      <xsl:with-param name="scheme">LN</xsl:with-param>
      <xsl:with-param name="meaning">Radiology Report</xsl:with-param>
    </xsl:call-template>
    <!--Continuity Of Content-->
    <DicomAttribute tag="0040A050" vr="CS"><Value number="1">SEPARATE</Value></DicomAttribute>
    <!--Content Template Sequence-->
    <DicomAttribute tag="0040A504" vr="SQ"/>
  </xsl:template>
  <xsl:template match="OBR">
    <!--Content Date/Time-->
    <xsl:call-template name="attrDATM">
      <xsl:with-param name="datag">00080023</xsl:with-param>
      <xsl:with-param name="tmtag">00080033</xsl:with-param>
      <xsl:with-param name="val" select="field[7]"/>
    </xsl:call-template>
    <!-- Take Study Instance UID from first referenced Image - if available -->
    <xsl:variable name="suid"
                  select="normalize-space(../OBX[field[3]/component='Study Instance UID'][1]/field[5])"/>
    <!-- Study Instance UID -->
    <DicomAttribute tag="0020000D" vr="UI">
      <Value number="1"><xsl:value-of select="$suid"/></Value>
    </DicomAttribute>
    <xsl:variable name="seriesuid"
                  select="normalize-space(../OBX[field[3]/component='Series Instance UID'][1]/field[5])"/>
    <!-- Study Instance UID -->
    <DicomAttribute tag="0020000E" vr="UI">
      <Value number="1"><xsl:value-of select="$seriesuid"/></Value>
    </DicomAttribute>
    <!--Referenced Request Sequence-->
    <DicomAttribute tag="0040A370" vr="SQ">
      <Item number="1">
        <!--Accession Number-->
        <DicomAttribute tag="00080050" vr="SH"/>
        <!--Referenced Study Sequence-->
        <DicomAttribute tag="00081110" vr="SQ"/>
        <!--Study Instance UID-->
        <xsl:value-of select="$suid"/>
        <!--Requested Procedure Description-->
        <DicomAttribute tag="00321060" vr="LO">
          <Value number="1"><xsl:value-of select="field[4]/component"/></Value>
        </DicomAttribute>
        <!--Requested Procedure Code Sequence-->
        <xsl:call-template name="codeItem">
          <xsl:with-param name="sqtag">00321064</xsl:with-param>
          <xsl:with-param name="code"><xsl:value-of select="field[4]"/></xsl:with-param>
          <xsl:with-param name="scheme"><xsl:value-of select="field[4]/component[2]"/></xsl:with-param>
          <xsl:with-param name="meaning"><xsl:value-of select="field[4]/component"/></xsl:with-param>
        </xsl:call-template>
        <!--Requested Procedure ID-->
        <DicomAttribute tag="00401001" vr="SH"/>
        <!--Placer Order Number / Imaging Service Request-->
        <DicomAttribute tag="00402016" vr="LO">
          <Value number="1"><xsl:value-of select="field[2]"/></Value>
        </DicomAttribute>
        <!--Filler Order Number / Imaging Service Request-->
        <DicomAttribute tag="00402017" vr="LO">
          <Value number="1"><xsl:value-of select="field[3]"/></Value>
        </DicomAttribute>
      </Item>
    </DicomAttribute>
    <!-- Verifying Observer Sequence -->
    <DicomAttribute tag="0040A073" vr="SQ">
      <Item number="1">
        <!-- Verifying Organization -->
        <DicomAttribute tag="0040A027" vr="LO">
          <Value number="1"><xsl:value-of select="$VerifyingOrganization"/></Value>
        </DicomAttribute>
        <!-- Verification DateTime -->
        <DicomAttribute tag="0040A030" vr="DT">
          <Value number="1"><xsl:value-of select="field[7]"/></Value>
        </DicomAttribute>
        <!-- Verifying Observer Name -->
        <xsl:choose>
          <xsl:when test="field[32]/component">
            <xsl:call-template name="cn2pnAttr">
              <xsl:with-param name="tag" select="'0040A075'"/>
              <xsl:with-param name="cn" select="field[32]"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <DicomAttribute tag="0040A075" vr="PN">
              <PersonName number="1">
                <Alphabetic>
                  <FamilyName>UNKNOWN</FamilyName>
                </Alphabetic>
              </PersonName>
            </DicomAttribute>
          </xsl:otherwise>
        </xsl:choose>
        <!-- Verifying Observer Identification Code Sequence -->
        <DicomAttribute tag="0040A088" vr="SQ"/>
      </Item>
    </DicomAttribute>
    <xsl:variable name="resultStatus" select="normalize-space(field[25])"/>
    <!--Completion Flag-->
    <DicomAttribute tag="0040A491" vr="CS">
      <Value number="1">
        <xsl:choose>
          <xsl:when test="$resultStatus='P'">PARTIAL</xsl:when>
          <xsl:otherwise>COMPLETE</xsl:otherwise>
        </xsl:choose>
      </Value>
    </DicomAttribute>
    <!--Verification Flag-->
    <DicomAttribute tag="0040A493" vr="CS">
      <Value number="1">
        <xsl:choose>
          <xsl:when test="$resultStatus='P' or $resultStatus='F'">VERIFIED</xsl:when>
          <xsl:otherwise>UNVERIFIED</xsl:otherwise>
        </xsl:choose>
      </Value>
    </DicomAttribute>
  </xsl:template>
  <xsl:template name="const-obsctx">
    <Item number="1">
      <!--Relationship Type-->
      <DicomAttribute tag="0040A010" vr="CS"><Value number="1">HAS CONCEPT MOD</Value></DicomAttribute>
      <!--Value Type-->
      <DicomAttribute tag="0040A040" vr="CS"><Value number="1">CODE</Value></DicomAttribute>
      <!--Concept Name Code Sequence-->
      <xsl:call-template name="codeItem">
        <xsl:with-param name="sqtag">0040A043</xsl:with-param>
        <xsl:with-param name="code">121049</xsl:with-param>
        <xsl:with-param name="scheme">DCM</xsl:with-param>
        <xsl:with-param name="meaning">Language of Content Item and Descendants</xsl:with-param>
      </xsl:call-template>
      <!--Concept Code Sequence-->
      <xsl:call-template name="codeItem">
        <xsl:with-param name="sqtag">0040A168</xsl:with-param>
        <xsl:with-param name="code">eng</xsl:with-param>
        <xsl:with-param name="scheme">ISO639_2</xsl:with-param>
        <xsl:with-param name="meaning">English</xsl:with-param>
      </xsl:call-template>
    </Item>
  </xsl:template>
  <xsl:template match="OBR" mode="obsctx">
    <xsl:if test="field[32]/component">
      <Item number="1">
        <!--Relationship Type-->
        <DicomAttribute tag="0040A010" vr="CS"><Value number="1">HAS OBS CONTEXT</Value></DicomAttribute>
        <!--Value Type-->
        <DicomAttribute tag="0040A040" vr="CS"><Value number="1">PNAME</Value></DicomAttribute>
        <!--Concept Name Code Sequence-->
        <xsl:call-template name="codeItem">
          <xsl:with-param name="sqtag">0040A043</xsl:with-param>
          <xsl:with-param name="code">121008</xsl:with-param>
          <xsl:with-param name="scheme">DCM</xsl:with-param>
          <xsl:with-param name="meaning">Person Observer Name</xsl:with-param>
        </xsl:call-template>
        <!--Person Name-->
        <xsl:call-template name="cn2pnAttr">
          <xsl:with-param name="tag" select="'0040A123'"/>
          <xsl:with-param name="cn" select="field[32]"/>
        </xsl:call-template>
      </Item>
    </xsl:if>
    <Item number="1">
      <!--Relationship Type-->
      <DicomAttribute tag="0040A010" vr="CS"><Value number="1">HAS OBS CONTEXT</Value></DicomAttribute>
      <!--Value Type-->
      <DicomAttribute tag="0040A040" vr="CS"><Value number="1">CODE</Value></DicomAttribute>
      <!--Concept Name Code Sequence-->
      <xsl:call-template name="codeItem">
        <xsl:with-param name="sqtag">0040A043</xsl:with-param>
        <xsl:with-param name="code">121023</xsl:with-param>
        <xsl:with-param name="scheme">DCM</xsl:with-param>
        <xsl:with-param name="meaning">Procedure Code</xsl:with-param>
      </xsl:call-template>
      <!--Concept Code Sequence-->
      <xsl:call-template name="codeItem">
        <xsl:with-param name="sqtag">0040A168</xsl:with-param>
        <xsl:with-param name="code"><xsl:value-of select="field[4]"/></xsl:with-param>
        <xsl:with-param name="scheme"><xsl:value-of select="field[4]/component[2]"/></xsl:with-param>
        <xsl:with-param name="meaning"><xsl:value-of select="field[4]/component"/></xsl:with-param>
      </xsl:call-template>
    </Item>
  </xsl:template>
  <xsl:template match="OBX" mode="img">
    <Item number="1">
      <!--Referenced SOP Sequence-->
      <DicomAttribute tag="00081199" vr="SQ">
        <Item number="1">
          <!--Referenced SOP Class UID-->
          <DicomAttribute tag="00081150" vr="UI">
            <Value number="1"><xsl:value-of select="following-sibling::*[1]/field[5]"/></Value>
          </DicomAttribute>
          <!--Referenced SOP Instance UID-->
          <DicomAttribute tag="00081155" vr="UI">
            <Value number="1"><xsl:value-of select="field[5]"/></Value>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
      <!--Relationship Type-->
      <DicomAttribute tag="0040A010" vr="CS"><Value number="1">CONTAINS</Value></DicomAttribute>
      <!--Value Type-->
      <DicomAttribute tag="0040A040" vr="CS"><Value number="1">IMAGE</Value></DicomAttribute>
    </Item>
  </xsl:template>
  <xsl:template match="OBX" mode="txt">
    <xsl:variable name="text">
      <xsl:apply-templates select="field[5]" mode="txt"/>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="starts-with($text, 'History')">
        <xsl:call-template name="text">
          <xsl:with-param name="hcode">121060</xsl:with-param>
          <xsl:with-param name="hname">History</xsl:with-param>
          <xsl:with-param name="ecode">121060</xsl:with-param>
          <xsl:with-param name="ename">History</xsl:with-param>
          <xsl:with-param name="text" select="substring($text,9)"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="starts-with($text, 'Findings')">
        <xsl:call-template name="text">
          <xsl:with-param name="hcode">121070</xsl:with-param>
          <xsl:with-param name="hname">Findings</xsl:with-param>
          <xsl:with-param name="ecode">121071</xsl:with-param>
          <xsl:with-param name="ename">Finding</xsl:with-param>
          <xsl:with-param name="text" select="substring($text,10)"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="starts-with($text, 'Conclusions')">
        <xsl:call-template name="text">
          <xsl:with-param name="hcode">121076</xsl:with-param>
          <xsl:with-param name="hname">Conclusions</xsl:with-param>
          <xsl:with-param name="ecode">121077</xsl:with-param>
          <xsl:with-param name="ename">Conclusion</xsl:with-param>
          <xsl:with-param name="text" select="substring($text,13)"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="text">
          <xsl:with-param name="text" select="$text"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="text()" mode="txt">
    <xsl:value-of select='.'/>
  </xsl:template>
  <xsl:template match="escape" mode="txt">
    <xsl:choose>
      <xsl:when test="text()='.br'">
        <xsl:text>&#13;&#10;</xsl:text>
      </xsl:when>
      <xsl:when test="text()='F'">
        <xsl:text>|</xsl:text>
      </xsl:when>
      <xsl:when test="text()='S'">
        <xsl:text>^</xsl:text>
      </xsl:when>
      <xsl:when test="text()='T'">
        <xsl:text>&amp;</xsl:text>
      </xsl:when>
      <xsl:when test="text()='R'">
        <xsl:text>~</xsl:text>
      </xsl:when>
      <xsl:when test="text()='E'">
        <xsl:text>\\</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <xsl:template name="text">
    <xsl:param name="hcode">121070</xsl:param>
    <xsl:param name="hname">Findings</xsl:param>
    <xsl:param name="ecode">121071</xsl:param>
    <xsl:param name="ename">Finding</xsl:param>
    <xsl:param name="text"/>
    <Item number="1">
      <!--Relationship Type-->
      <DicomAttribute tag="0040A010" vr="CS"><Value number="1">CONTAINS</Value></DicomAttribute>
      <!--Value Type-->
      <DicomAttribute tag="0040A040" vr="CS"><Value number="1">CONTAINER</Value></DicomAttribute>
      <!--Concept Name Code Sequence-->
      <xsl:call-template name="codeItem">
        <xsl:with-param name="sqtag">0040A043</xsl:with-param>
        <xsl:with-param name="code"><xsl:value-of select="$hcode"/></xsl:with-param>
        <xsl:with-param name="scheme">DCM</xsl:with-param>
        <xsl:with-param name="meaning"><xsl:value-of select="$hname"/></xsl:with-param>
      </xsl:call-template>
      <!--Continuity Of Content-->
      <DicomAttribute tag="0040A050" vr="CS"><Value number="1">SEPARATE</Value></DicomAttribute>
      <!--Content Sequence-->
      <DicomAttribute tag="0040A730" vr="SQ">
        <Item number="1">
          <!--Relationship Type-->
          <DicomAttribute tag="0040A010" vr="CS"><Value number="1">CONTAINS</Value></DicomAttribute>
          <!--Value Type-->
          <DicomAttribute tag="0040A040" vr="CS"><Value number="1">TEXT</Value></DicomAttribute>
          <!--Concept Name Code Sequence-->
          <xsl:call-template name="codeItem">
            <xsl:with-param name="sqtag">0040A043</xsl:with-param>
            <xsl:with-param name="code"><xsl:value-of select="$ecode"/></xsl:with-param>
            <xsl:with-param name="scheme">DCM</xsl:with-param>
            <xsl:with-param name="meaning"><xsl:value-of select="$ename"/></xsl:with-param>
          </xsl:call-template>
          <!--Text Value-->
          <DicomAttribute tag="0040A160" vr="UT">
            <Value number="1"><xsl:value-of select="$text"/></Value>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
    </Item>
  </xsl:template>
</xsl:stylesheet>
