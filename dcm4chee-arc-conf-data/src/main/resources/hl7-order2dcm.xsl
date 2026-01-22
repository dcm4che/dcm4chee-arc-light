<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:include href="hl7-common.xsl"/>
  <xsl:param name="hl7ScheduledProtocolCodeInOrder"/>
  <xsl:param name="hl7ScheduledStationAETInOrder"/>

  <xsl:template match="/hl7">
    <NativeDicomModel>
      <xsl:call-template name="institution">
        <xsl:with-param name="enteringOrganization" select="ORC/field[17]"/>
        <xsl:with-param name="sendingOrganization" select="MSH/field[2]"/>
      </xsl:call-template>
      <xsl:apply-templates select="PID"/>
      <xsl:apply-templates select="PV1"/>
      <xsl:apply-templates select="ORC[1]"/>
      <xsl:apply-templates select="OBR[1]"/>
      <xsl:apply-templates select="TQ1[1]"/>
      <!-- Scheduled Station AE Title -->
      <xsl:variable name="spsAETsDefault">
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00400001'"/>
          <xsl:with-param name="vr" select="'AE'"/>
          <xsl:with-param name="val">
            <xsl:choose>
              <xsl:when test="$hl7ScheduledStationAETInOrder = 'ORC_18'">
                <xsl:call-template name="multiValue">
                  <xsl:with-param name="field" select="ORC[1]/field[18]"/>
                </xsl:call-template>
              </xsl:when>
              <xsl:otherwise/>
            </xsl:choose>
          </xsl:with-param>
        </xsl:call-template>
      </xsl:variable>
      <xsl:variable name="spsStatus" select="concat(string(ORC[1]/field[1]), '_', string(ORC[1]/field[5]))" />
      <xsl:variable name="spsStartDateTime">
        <xsl:call-template name="spsStartDateTime">
          <xsl:with-param name="tq1QuantityTiming" select="TQ1[1]/field[7]" />
          <xsl:with-param name="orcQuantityTiming" select="ORC[1]/field[7]/component[3]" />
          <xsl:with-param name="obrQuantityTiming" select="OBR[1]/field[27]/component[3]" />
        </xsl:call-template>
      </xsl:variable>
      <xsl:variable name="spsScheduledPhysician" select="OBR[1]/field[34]" />

      <xsl:call-template name="spsSeq">
        <xsl:with-param name="spsAETsDefault" select="$spsAETsDefault"/>
        <xsl:with-param name="spsStatus" select="$spsStatus"/>
        <xsl:with-param name="spsStartDateTime" select="$spsStartDateTime"/>
        <xsl:with-param name="spsScheduledPhysician" select="$spsScheduledPhysician"/>
      </xsl:call-template>
      <xsl:call-template name="tz">
        <xsl:with-param name="val" select="$spsStartDateTime"/>
      </xsl:call-template>

      <xsl:apply-templates select="ZDS">
        <xsl:with-param name="spsAETsDefault" select="$spsAETsDefault"/>
        <xsl:with-param name="spsStatus" select="$spsStatus"/>
        <xsl:with-param name="spsStartDateTime" select="$spsStartDateTime"/>
        <xsl:with-param name="spsScheduledPhysician" select="$spsScheduledPhysician"/>
        <xsl:with-param name="obr" select="OBR[1]"/>
      </xsl:apply-templates>
      <xsl:apply-templates select="OBX"/>
      <xsl:apply-templates select="NTE"/>
      <!-- Admission ID, Issuer -->
      <xsl:call-template name="admissionID">
        <xsl:with-param name="visitNumber" select="PV1/field[19]"/>
        <xsl:with-param name="patientAccountNumber" select="PID/field[18]"/>
      </xsl:call-template>
      <!-- Admitting Date Time -->
      <xsl:call-template name="admittingDateTime">
        <xsl:with-param name="val" select="PV1/field[44]"/>
      </xsl:call-template>
    </NativeDicomModel>
  </xsl:template>

  <xsl:template match="ORC[1]">
    <!-- Placer Order Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00402016'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="sqtag" select="'00400026'"/>
      <xsl:with-param name="ei" select="field[2]"/>
    </xsl:call-template>
    <!-- Filler Order Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00402017'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="sqtag" select="'00400027'"/>
      <xsl:with-param name="ei" select="field[3]"/>
    </xsl:call-template>
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401003'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val">
        <xsl:call-template name="procedurePriority">
          <xsl:with-param name="priority" select="field[7]/component[5]/text()"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
    <!-- Institution Address -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00080081'"/>
      <xsl:with-param name="vr" select="'ST'"/>
      <xsl:with-param name="val">
        <xsl:call-template name="address">
          <xsl:with-param name="val" select="field[22]"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
    <!--Institutional Department Name-->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00081040'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="field[13]/component[8]"/>
    </xsl:call-template>
    <!-- Confidentiality Code -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401008'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="field[28]/text()"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template name="spsStartDateTime">
    <xsl:param name="tq1QuantityTiming"/>
    <xsl:param name="orcQuantityTiming"/>
    <xsl:param name="obrQuantityTiming"/>
    <xsl:choose>
      <xsl:when test="string-length($tq1QuantityTiming) > 0">
        <xsl:value-of select="$tq1QuantityTiming"/>
      </xsl:when>
      <xsl:when test="string-length($orcQuantityTiming) > 0">
        <xsl:value-of select="$orcQuantityTiming"/>
      </xsl:when>
      <xsl:when test="string-length($obrQuantityTiming) > 0">
        <xsl:value-of select="$obrQuantityTiming"/>
      </xsl:when>
      <xsl:otherwise/>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="institution">
    <xsl:param name="enteringOrganization"/>
    <xsl:param name="sendingOrganization"/>
    <xsl:choose>
      <xsl:when test="string-length($enteringOrganization) > 0">
        <xsl:call-template name="ce2codeItemWithDesc">
          <xsl:with-param name="descTag" select="'00080080'"/>
          <xsl:with-param name="seqTag" select="'00080082'"/>
          <xsl:with-param name="codedEntry" select="$enteringOrganization"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00080080'"/>
          <xsl:with-param name="vr" select="'LO'"/>
          <xsl:with-param name="val" select="$sendingOrganization"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="procedurePriority">
    <xsl:param name="priority"/>
    <xsl:if test="$priority">
      <xsl:choose>
        <xsl:when test="$priority = 'S'">STAT</xsl:when>
        <xsl:when test="$priority = 'A' or $priority = 'P' or $priority = 'C' ">HIGH</xsl:when>
        <xsl:when test="$priority = 'R'">ROUTINE</xsl:when>
        <xsl:when test="$priority = 'T'">MEDIUM</xsl:when>
        <xsl:otherwise>&quot;&quot;</xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>

  <xsl:template match="TQ1[1]">
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401003'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val">
        <xsl:call-template name="procedurePriority">
          <xsl:with-param name="priority" select="field[9]/text()"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="OBR[1]">
    <!-- Medical Alerts -->
    <xsl:call-template name="trimmedAttr">
      <xsl:with-param name="tag" select="'00102000'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val">
        <xsl:call-template name="unescape">
          <xsl:with-param name="val" select="field[13]"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
    <!-- Requesting Physician -->
    <xsl:call-template name="cn2pnAttr">
      <xsl:with-param name="tag" select="'00321032'"/>
      <xsl:with-param name="cn" select="field[16]"/>
    </xsl:call-template>
    <!-- Requested Procedure Description and Code Sequence -->
    <xsl:call-template name="ce2codeItemWithDesc">
      <xsl:with-param name="descTag" select="'00321060'"/>
      <xsl:with-param name="seqTag" select="'00321064'"/>
      <xsl:with-param name="codedEntry" select="field[44]"/>
    </xsl:call-template>
    <!-- Patient State -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00380500'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="substring(field[12]/text(),1,64)"/>
    </xsl:call-template>
    <!-- Reason for the Requested Procedure Code and Sequence -->
    <xsl:call-template name="ce2codeItemWithDesc">
      <xsl:with-param name="descTag" select="'00401002'"/>
      <xsl:with-param name="seqTag" select="'0040100A'"/>
      <xsl:with-param name="codedEntry" select="field[31]"/>
    </xsl:call-template>
    <!-- Patient Transport Arrangements -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401004'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val" select="substring(field[30]/text(),1,64)"/>
    </xsl:call-template>
    <xsl:variable name="ipc" select="following-sibling::IPC"/>
    <xsl:choose>
      <xsl:when test="$ipc">
        <xsl:apply-templates select="$ipc"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- Accession Number -->
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00080050'"/>
          <xsl:with-param name="vr" select="'SH'"/>
          <xsl:with-param name="val" select="string(field[18]/text())"/>
        </xsl:call-template>
        <!-- Requested Procedure ID -->
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00401001'"/>
          <xsl:with-param name="vr" select="'SH'"/>
          <xsl:with-param name="val" select="string(field[19]/text())"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="tz">
    <xsl:param name="val"/>
    <xsl:variable name="str" select="normalize-space($val)" />
    <xsl:if test="$str and $str != '&quot;&quot;'">
      <xsl:variable name="tm" select="substring($str,9)"/>
      <xsl:variable name="tm_plus" select="substring-before($tm,'+')"/>
      <xsl:variable name="tm_minus" select="substring-before($tm,'-')"/>
      <xsl:if test="$tm_plus or $tm_minus">
        <DicomAttribute tag="00080201" vr="SH">
          <Value number="1">
            <xsl:choose>
              <xsl:when test="$tm_plus">
                <xsl:value-of select="concat('+', substring-after($tm,'+'))"/>
              </xsl:when>
              <xsl:when test="$tm_minus">
                <xsl:value-of select="concat('-', substring-after($tm,'-'))"/>
              </xsl:when>
              <xsl:otherwise/>
            </xsl:choose>
          </Value>
        </DicomAttribute>
      </xsl:if>
    </xsl:if>
  </xsl:template>

  <xsl:template name="spsSeq">
    <xsl:param name="spsAETsDefault"/>
    <xsl:param name="spsStatus"/>
    <xsl:param name="spsStartDateTime"/>
    <xsl:param name="spsScheduledPhysician"/>
    <!-- Scheduled Procedure Step Sequence -->
    <DicomAttribute tag="00400100" vr="SQ">
      <xsl:for-each select="IPC">
        <Item number="{position()}">
          <!-- Scheduled Procedure Step Status -->
          <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00400020'"/>
            <xsl:with-param name="vr" select="'CS'"/>
            <xsl:with-param name="val" select="$spsStatus"/>
          </xsl:call-template>
          <!-- Scheduled Procedure Step Start Date/Time -->
          <xsl:call-template name="attrDATM">
            <xsl:with-param name="datag" select="'00400002'"/>
            <xsl:with-param name="tmtag" select="'00400003'"/>
            <xsl:with-param name="val" select="$spsStartDateTime"/>
          </xsl:call-template>
          <!-- Scheduled Performing Physician Name -->
          <xsl:call-template name="cnn2pnAttr">
            <xsl:with-param name="tag" select="'00400006'"/>
            <xsl:with-param name="cn" select="$spsScheduledPhysician"/>
          </xsl:call-template>
          <!-- Modality -->
          <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00080060'"/>
            <xsl:with-param name="vr" select="'CS'"/>
            <xsl:with-param name="val" select="field[5]/text()"/>
          </xsl:call-template>
          <!-- Scheduled Procedure Step ID -->
          <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00400009'"/>
            <xsl:with-param name="vr" select="'SH'"/>
            <xsl:with-param name="val" select="field[4]/text()"/>
          </xsl:call-template>
          <!-- Scheduled Protocol Step Description and Code Sequence -->
          <xsl:variable name="codedEntry" select="field[6]"/>
          <xsl:variable name="desc" select="$codedEntry/component[1]"/>
          <xsl:call-template name="trimmedAttr">
            <xsl:with-param name="tag" select="'00400007'"/>
            <xsl:with-param name="vr" select="'LO'"/>
            <xsl:with-param name="val">
              <xsl:call-template name="codeItemDesc">
                <xsl:with-param name="descVal" select="$desc"/>
                <xsl:with-param name="codedEntry" select="$codedEntry"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
          <!-- Scheduled Protocol Code Sequence -->
          <DicomAttribute tag="00400008" vr="SQ">
            <xsl:call-template name="codeItem1">
              <xsl:with-param name="itemNo" select="'1'"/>
              <xsl:with-param name="code">
                <xsl:value-of select="$codedEntry/text()"/>
              </xsl:with-param>
              <xsl:with-param name="scheme" select="$codedEntry/component[2]"/>
              <xsl:with-param name="meaning">
                <xsl:call-template name="unescape">
                  <xsl:with-param name="val" select="$codedEntry/component[1]"/>
                </xsl:call-template>
              </xsl:with-param>
            </xsl:call-template>
          </DicomAttribute>
          <!-- Scheduled Station Name -->
          <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00400010'"/>
            <xsl:with-param name="vr" select="'SH'"/>
            <xsl:with-param name="val">
              <xsl:call-template name="multiValue">
                <xsl:with-param name="field" select="field[7]"/>
              </xsl:call-template>
            </xsl:with-param>
          </xsl:call-template>
          <!-- Scheduled Procedure Step Location -->
          <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00400011'"/>
            <xsl:with-param name="vr" select="'SH'"/>
            <xsl:with-param name="val" select="field[8]/text()"/>
          </xsl:call-template>
          <!-- Scheduled Station AE Title -->
          <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00400001'"/>
            <xsl:with-param name="vr" select="'AE'"/>
            <xsl:with-param name="val">
              <xsl:choose>
                <xsl:when test="string-length(field[9]) > 0">
                  <xsl:call-template name="multiValue">
                    <xsl:with-param name="field" select="field[9]"/>
                  </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="$spsAETsDefault"/>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:with-param>
          </xsl:call-template>
        </Item>
      </xsl:for-each>
    </DicomAttribute>
  </xsl:template>

  <xsl:template name="spsDescProtocolCodeOBR">
    <xsl:param name="obr"/>
    <xsl:param name="spsDescCodeField"/>
    <xsl:param name="offset" select="0"/>
    <xsl:variable name="codedEntry" select="$obr/field[$spsDescCodeField]"/>
    <xsl:variable name="desc" select="$codedEntry/component[$offset+1]"/>
    <xsl:call-template name="trimmedAttr">
      <xsl:with-param name="tag" select="'00400007'"/>
      <xsl:with-param name="vr" select="'LO'"/>
      <xsl:with-param name="val">
        <xsl:call-template name="codeItemDesc">
          <xsl:with-param name="descVal" select="$desc"/>
          <xsl:with-param name="codedEntry" select="$codedEntry"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
    <!-- Scheduled Protocol Step Description and Code Sequence -->
    <DicomAttribute tag="00400008" vr="SQ">
      <xsl:call-template name="sps-protocol">
        <xsl:with-param name="itemNo" select="1"/>
        <xsl:with-param name="codedEntry" select="$codedEntry"/>
        <xsl:with-param name="offset" select="$offset"/>
      </xsl:call-template>
    </DicomAttribute>
  </xsl:template>

  <xsl:template name="sps-protocol">
    <xsl:param name="itemNo"/>
    <xsl:param name="codedEntry"/>
    <xsl:param name="offset"/>
    <xsl:call-template name="codeItem1">
      <xsl:with-param name="itemNo" select="$itemNo"/>
      <xsl:with-param name="code">
        <xsl:choose>
          <xsl:when test="$offset != 0"><xsl:value-of select="$codedEntry/component[$offset]"/></xsl:when>
          <xsl:otherwise><xsl:value-of select="$codedEntry/text()"/></xsl:otherwise>
        </xsl:choose>
      </xsl:with-param>
      <xsl:with-param name="scheme" select="$codedEntry/component[$offset+2]"/>
      <xsl:with-param name="meaning">
        <xsl:call-template name="unescape">
          <xsl:with-param name="val" select="$codedEntry/component[$offset+1]"/>
        </xsl:call-template>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="ZDS">
    <xsl:param name="spsAETsDefault"/>
    <xsl:param name="spsStatus"/>
    <xsl:param name="spsStartDateTime"/>
    <xsl:param name="spsScheduledPhysician"/>
    <xsl:param name="obr"/>
    <!-- Study Instance UID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'0020000D'"/>
      <xsl:with-param name="vr" select="'UI'"/>
      <xsl:with-param name="val" select="string(field[1]/text())"/>
    </xsl:call-template>
    <!-- Scheduled Procedure Step Sequence -->
    <DicomAttribute tag="00400100" vr="SQ">
      <Item number="1">
        <!-- Scheduled Procedure Step Status -->
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00400020'"/>
          <xsl:with-param name="vr" select="'CS'"/>
          <xsl:with-param name="val" select="$spsStatus"/>
        </xsl:call-template>
        <!-- Scheduled Procedure Step Start Date/Time -->
        <xsl:call-template name="attrDATM">
          <xsl:with-param name="datag" select="'00400002'"/>
          <xsl:with-param name="tmtag" select="'00400003'"/>
          <xsl:with-param name="val" select="$spsStartDateTime"/>
        </xsl:call-template>
        <!-- Scheduled Performing Physician Name -->
        <xsl:call-template name="cnn2pnAttr">
          <xsl:with-param name="tag" select="'00400006'"/>
          <xsl:with-param name="cn" select="$spsScheduledPhysician"/>
        </xsl:call-template>
        <!-- Scheduled Protocol Step Description and Code Sequence -->
        <xsl:call-template name="spsDescProtocolCodeOBR">
          <xsl:with-param name="obr" select="$obr"/>
          <xsl:with-param name="spsDescCodeField" select="'4'"/>
          <xsl:with-param name="offset">
            <xsl:choose>
              <xsl:when test="$hl7ScheduledProtocolCodeInOrder = 'OBR_4_1'">0</xsl:when>
              <xsl:otherwise>3</xsl:otherwise>
            </xsl:choose>
          </xsl:with-param>
        </xsl:call-template>
        <!-- Modality -->
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00080060'"/>
          <xsl:with-param name="vr" select="'CS'"/>
          <xsl:with-param name="val" select="$obr/field[24]"/>
        </xsl:call-template>
        <!-- Scheduled Procedure Step ID -->
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00400009'"/>
          <xsl:with-param name="vr" select="'SH'"/>
          <xsl:with-param name="val" select="$obr/field[20]"/>
        </xsl:call-template>
        <!-- Scheduled Station AE Title -->
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00400001'"/>
          <xsl:with-param name="vr" select="'AE'"/>
          <xsl:with-param name="val" select="$spsAETsDefault"/>
        </xsl:call-template>
      </Item>
    </DicomAttribute>
  </xsl:template>

  <xsl:template match="IPC">
    <!-- Study Instance UID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'0020000D'"/>
      <xsl:with-param name="vr" select="'UI'"/>
      <xsl:with-param name="val" select="string(field[3]/text())"/>
    </xsl:call-template>
    <!-- Accession Number -->
    <xsl:call-template name="ei2attr">
      <xsl:with-param name="tag" select="'00080050'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="sqtag" select="'00080051'"/>
      <xsl:with-param name="ei" select="field[1]"/>
    </xsl:call-template>
    <!-- Requested Procedure ID -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401001'"/>
      <xsl:with-param name="vr" select="'SH'"/>
      <xsl:with-param name="val" select="string(field[2]/text())"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template name="multiValue">
    <xsl:param name="field"/>
    <xsl:variable name="repeat">
      <xsl:for-each select="$field/repeat">
        <xsl:value-of select="concat('\', text())" />
      </xsl:for-each>
    </xsl:variable>
    <xsl:value-of select="concat($field/text(), $repeat)"/>
  </xsl:template>

  <xsl:template match="OBX">
    <xsl:variable name="observationIdentifier" select="translate(field[3]/component[1],'bodywheight','BODYWHEIGHT')"/>
    <xsl:variable name="units" select="field[6]"/>
    <xsl:choose>
      <xsl:when test="$units/text()='kg' and $observationIdentifier = 'BODY WEIGHT'">
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00101030'"/>
          <xsl:with-param name="vr" select="'DS'"/>
          <xsl:with-param name="val" select="field[5]"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$units/text()='m' and $observationIdentifier = 'BODY HEIGHT'">
        <xsl:call-template name="attr">
          <xsl:with-param name="tag" select="'00101020'"/>
          <xsl:with-param name="vr" select="'DS'"/>
          <xsl:with-param name="val" select="field[5]"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise/>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="NTE">
    <!-- Requested Procedure Comments -->
    <xsl:call-template name="attr">
      <xsl:with-param name="tag" select="'00401400'"/>
      <xsl:with-param name="vr" select="'LT'"/>
      <xsl:with-param name="val" select="field[3]" />
    </xsl:call-template>
  </xsl:template>

</xsl:stylesheet>