<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <NativeDicomModel>
      <!-- Accession Number -->
      <xsl:copy-of select="DicomAttribute[@tag='00080050']"/>
      <!-- Issuer of Accession Number Sequence -->
      <xsl:copy-of select="DicomAttribute[@tag='00080051']"/>
      <!-- Referring Physician's Name -->
      <xsl:copy-of select="DicomAttribute[@tag='00080090']"/>
      <!-- Study Description from Requested Procedure Description -->
      <xsl:if test="DicomAttribute[@tag='00321060']">
        <DicomAttribute tag="00081030" vr="LO">
          <xsl:copy-of select="DicomAttribute[@tag='00321060']/Value"/>
        </DicomAttribute>
      </xsl:if>
      <!-- Procedure Code Sequence from Requested Procedure Code Sequence -->
      <xsl:if test="DicomAttribute[@tag='00321064']">
        <DicomAttribute tag="00081032" vr="SQ">
          <xsl:copy-of select="DicomAttribute[@tag='00321064']/Item"/>
        </DicomAttribute>
      </xsl:if>
      <!-- Patient ID -->
      <xsl:copy-of select="DicomAttribute[@tag='00100020']"/>
      <!-- Issuer of Patient ID -->
      <xsl:copy-of select="DicomAttribute[@tag='00100021']"/>
      <!-- Issuer of Patient ID Qualifiers Sequence -->
      <xsl:copy-of select="DicomAttribute[@tag='00100024']"/>
      <!-- Study ID from Requested Procedure ID -->
      <xsl:if test="DicomAttribute[@tag='00401001']">
        <DicomAttribute tag="00200010" vr="SH">
          <xsl:copy-of select="DicomAttribute[@tag='00401001']/Value"/>
        </DicomAttribute>
      </xsl:if>
      <!-- Requesting Service -->
      <xsl:copy-of select="DicomAttribute[@tag='00321033']"/>
      <!-- Requesting Service Code Sequence -->
      <xsl:copy-of select="DicomAttribute[@tag='00321034']"/>
      <!-- Reason For Performed Procedure Code Sequence from Reason for Requested Procedure Code Sequence -->
      <xsl:if test="DicomAttribute[@tag='0040100A']">
        <DicomAttribute tag="00401012" vr="SQ">
          <xsl:copy-of select="DicomAttribute[@tag='0040100A']/Item"/>
        </DicomAttribute>
      </xsl:if>
      <DicomAttribute tag="00400275" vr="SQ">
        <Item number="1">
          <!-- Accession Number -->
          <xsl:copy-of select="DicomAttribute[@tag='00080050']"/>
          <!-- Issuer of Accession Number Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00080051']"/>
          <!-- Referenced Study Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00081110']"/>
          <!-- Study Instance UID -->
          <xsl:copy-of select="DicomAttribute[@tag='0020000D']"/>
          <!-- Requesting Physician Identification Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00321031']"/>
          <!-- Requesting Physician -->
          <xsl:copy-of select="DicomAttribute[@tag='00321032']"/>
          <!-- Requesting Service -->
          <xsl:copy-of select="DicomAttribute[@tag='00321033']"/>
          <!-- Requesting Service Code Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00321034']"/>
          <!-- Requested Procedure Description -->
          <xsl:copy-of select="DicomAttribute[@tag='00321060']"/>
          <!-- RequestedProcedureCodeSequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00321064']"/>
          <!-- Scheduled Procedure Step Description -->
          <xsl:copy-of select="DicomAttribute[@tag='00400100']/Item/DicomAttribute[@tag='00400007']"/>
          <!-- Scheduled Protocol Code Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='00400100']/Item/DicomAttribute[@tag='00400008']"/>
          <!-- Scheduled Procedure Step ID -->
          <xsl:copy-of select="DicomAttribute[@tag='00400100']/Item/DicomAttribute[@tag='00400009']"/>
          <!-- Requested Procedure ID -->
          <xsl:copy-of select="DicomAttribute[@tag='00401001']"/>
          <!-- Reason for the Requested Procedure -->
          <xsl:copy-of select="DicomAttribute[@tag='00401002']"/>
          <!-- Reason for Requested Procedure Code Sequence -->
          <xsl:copy-of select="DicomAttribute[@tag='0040100A']"/>
        </Item>
      </DicomAttribute>
    </NativeDicomModel>
  </xsl:template>
</xsl:stylesheet>
