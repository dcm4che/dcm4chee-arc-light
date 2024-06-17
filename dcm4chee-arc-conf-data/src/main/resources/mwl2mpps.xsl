<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml"/>
  <xsl:param name="mppsAttrs"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <!-- Patient Name -->
      <xsl:copy-of select="DicomAttribute[@tag='00100010']"/>
      <!-- Patient ID -->
      <xsl:copy-of select="DicomAttribute[@tag='00100020']"/>
      <!-- Issuer of Patient ID -->
      <xsl:copy-of select="DicomAttribute[@tag='00100021']"/>
      <!-- Issuer of Patient ID Qualifiers Sequence -->
      <xsl:copy-of select="DicomAttribute[@tag='00100024']"/>
      <!-- Patient BirthDate -->
      <xsl:copy-of select="DicomAttribute[@tag='00100030']"/>
      <!-- Patient Sex -->
      <xsl:copy-of select="DicomAttribute[@tag='00100040']"/>
      <!-- Patient's Sex Neutered -->
      <xsl:copy-of select="DicomAttribute[@tag='00102203']"/>
      <!-- Admission ID -->
      <xsl:copy-of select="DicomAttribute[@tag='00380010']"/>
      <!-- Issuer of Admission ID Sequence -->
      <xsl:copy-of select="DicomAttribute[@tag='00380014']"/>
      <!-- Performed Procedure Step Description from Requested Procedure Description -->
      <xsl:if test="DicomAttribute[@tag='00321060']">
        <DicomAttribute tag="00400254" vr="LO">
          <xsl:copy-of select="DicomAttribute[@tag='00321060']/Value"/>
        </DicomAttribute>
      </xsl:if>
      <!-- Performed Procedure Step ID from Requested Procedure ID -->
      <xsl:if test="DicomAttribute[@tag='00401001']">
        <DicomAttribute tag="00400253" vr="SH">
          <xsl:copy-of select="DicomAttribute[@tag='00401001']/Value"/>
        </DicomAttribute>
      </xsl:if>
      <!-- Procedure Code Sequence from Requested Procedure Code Sequence -->
      <xsl:if test="DicomAttribute[@tag='00321064']">
        <DicomAttribute tag="00081032" vr="SQ">
          <xsl:copy-of select="DicomAttribute[@tag='00321064']/Item"/>
        </DicomAttribute>
      </xsl:if>
      <!-- Study ID from Requested Procedure ID -->
      <xsl:if test="DicomAttribute[@tag='00401001']">
        <DicomAttribute tag="00200010" vr="SH">
          <xsl:copy-of select="DicomAttribute[@tag='00401001']/Value"/>
        </DicomAttribute>
      </xsl:if>
      <xsl:variable name="ssa" select="$mppsAttrs/DicomAttribute[@tag='00400270']/Item"/>
      <xsl:variable name="refStudySeq" select="$ssa/DicomAttribute[@tag='00081110']"/>
      <xsl:variable name="studyUID" select="$ssa/DicomAttribute[@tag='0020000D']"/>
      <DicomAttribute tag="00400270" vr="SQ">
        <Item number="1">
          <!-- Accession Number -->
          <xsl:copy-of select="DicomAttribute[@tag='00080050']"/>
          <!-- Issuer of Accession Number Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00080051']"/>
          <!-- Referenced Study Sequence -->
          <xsl:copy-of select="$refStudySeq"/>
          <!-- Study Instance UID -->
          <xsl:copy-of select="$studyUID"/>
          <!-- Placer Order Number/Imaging Service Request -->
          <xsl:copy-of select="DicomAttribute[@tag='00402016']"/>
          <!-- Order Placer Identifier Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00400026']"/>
          <!-- Filler Order Number/Imaging Service Request -->
          <xsl:copy-of select="DicomAttribute[@tag='00402017']"/>
          <!-- Order Filler Identifier Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00400027']"/>
          <!-- Requested Procedure ID -->
          <xsl:copy-of select="DicomAttribute[@tag='00401001']"/>
          <!-- Requested Procedure Code Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00321064']"/>
          <!-- Requested Procedure Description -->
          <xsl:copy-of select="DicomAttribute[@tag='00321060']"/>
          <!-- Scheduled Procedure Step ID -->
          <xsl:copy-of select="DicomAttribute[@tag='00400009']"/>
          <!-- Scheduled Protocol Code Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00400008']"/>
          <!-- Scheduled Procedure Step Description -->
          <xsl:copy-of select="DicomAttribute[@tag='00400007']"/>
        </Item>
      </DicomAttribute>
    </NativeDicomModel>
  </xsl:template>
</xsl:stylesheet>
