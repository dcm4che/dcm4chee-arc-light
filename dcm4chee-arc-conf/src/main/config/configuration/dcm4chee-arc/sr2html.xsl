<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ **** BEGIN LICENSE BLOCK *****
  ~ Version: MPL 1.1/GPL 2.0/LGPL 2.1
  ~
  ~ The contents of this file are subject to the Mozilla Public License Version
  ~ 1.1 (the "License"); you may not use this file except in compliance with
  ~ the License. You may obtain a copy of the License at
  ~ http://www.mozilla.org/MPL/
  ~
  ~ Software distributed under the License is distributed on an "AS IS" basis,
  ~ WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
  ~ for the specific language governing rights and limitations under the
  ~ License.
  ~
  ~ The Original Code is part of dcm4che, an implementation of DICOM(TM) in
  ~ Java(TM), hosted at https://github.com/gunterze/dcm4che.
  ~
  ~ The Initial Developer of the Original Code is
  ~ J4Care.
  ~ Portions created by the Initial Developer are Copyright (C) 2015
  ~ the Initial Developer. All Rights Reserved.
  ~
  ~ Contributor(s):
  ~ See @authors listed below
  ~
  ~ Alternatively, the contents of this file may be used under the terms of
  ~ either the GNU General Public License Version 2 or later (the "GPL"), or
  ~ the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
  ~ in which case the provisions of the GPL or the LGPL are applicable instead
  ~ of those above. If you wish to allow use of your version of this file only
  ~ under the terms of either the GPL or the LGPL, and not to allow others to
  ~ use your version of this file under the terms of the MPL, indicate your
  ~ decision by deleting the provisions above and replace them with the notice
  ~ and other provisions required by the GPL or the LGPL. If you do not delete
  ~ the provisions above, a recipient may use your version of this file under
  ~ the terms of any one of the MPL, the GPL or the LGPL.
  ~
  ~ **** END LICENSE BLOCK *****
  -->

<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

  <xsl:output method="html" indent="yes" media-type="text/html" encoding="UTF-8" />
  <xsl:param name="wadoURL" />
  <xsl:variable name="sopRefs"
                select="/NativeDicomModel/DicomAttribute[@tag='0040A375']/Item/DicomAttribute[@tag='00081115']/Item/DicomAttribute[@tag='00081199']/Item"/>
  <xsl:template match="/NativeDicomModel">
    <html>
      <head>
        <title>
          <xsl:value-of select="DicomAttribute[@tag='0040A043']/Item/DicomAttribute[@tag='00080104']" />
        </title>
      </head>
      <body>
        <font size="-1">
          By
          <xsl:value-of select="DicomAttribute[@tag='00080080']/Value" />
          , Ref. Phys.
          <xsl:value-of select="DicomAttribute[@tag='00080090']/Value" />
        </font>
        <br />
        <table border="0">
          <tr>
            <td>Patient Name:</td>
            <td>
              <xsl:value-of select="DicomAttribute[@tag='00100010']/PersonName/Alphabetic/FamilyName" />
                <xsl:if test="DicomAttribute[@tag='00100010']/PersonName/Alphabetic/GivenName">
                  <xsl:text>, </xsl:text>
                  <xsl:value-of select="DicomAttribute[@tag='00100010']/PersonName/Alphabetic/GivenName" />
                </xsl:if>
            </td>
          </tr>
          <tr>
            <td>Patient ID:</td>
            <td>
              <xsl:value-of select="DicomAttribute[@tag='00100020']/Value" />
            </td>
          </tr>
          <tr>
            <td>Patient Birthdate:</td>
            <td>
              <xsl:value-of select="DicomAttribute[@tag='00100030']/Value" />
            </td>
          </tr>
          <tr>
            <td>Patient Sex:</td>
            <td>
              <xsl:value-of select="DicomAttribute[@tag='00100040']/Value" />
            </td>
          </tr>
        </table>
        <hr />

        <xsl:apply-templates select="DicomAttribute[@tag='0040A730']/Item" mode="content" />

      </body>
    </html>
  </xsl:template>

  <!-- Contentsequence output starts here -->

  <xsl:template match="Item" mode="content">
    <font size="+2">
      <xsl:value-of
        select="DicomAttribute[@tag='0040A043']/Item/DicomAttribute[@tag='00080104']/Value" />
    </font>
    <xsl:apply-templates select="." mode="contentItem" />
    <br />
  </xsl:template>


  <!-- Displays the content in the context of a list -->
  <xsl:template match="Item" mode="contentLI">
    <li>
      <font size="+1">
        <xsl:value-of
          select="DicomAttribute[@tag='0040A043']/Item/DicomAttribute[@tag='00080104']/Value" />
      </font>
      <xsl:apply-templates select="." mode="contentItem" />
    </li>
  </xsl:template>

  <xsl:template mode="contentItem" match="Item">
    <xsl:choose>
      <xsl:when test="DicomAttribute[@tag='0040A040']/Value='TEXT'">
        <p>
          <xsl:call-template name="escape_crlf">
            <xsl:with-param name="string"
              select="DicomAttribute[@tag='0040A160']/Value" />
          </xsl:call-template>
        </p>
      </xsl:when>

      <xsl:when test="DicomAttribute[@tag='0040A040']/Value='IMAGE'">
        <xsl:variable name="objectUID"
                      select="DicomAttribute[@tag='00081199']/Item/DicomAttribute[@tag='00081155']/Value"/>
        <xsl:apply-templates select="$sopRefs[DicomAttribute[@tag='00081155']/Value=$objectUID]" mode="imageref" />
      </xsl:when>

      <xsl:when test="DicomAttribute[@tag='0040A040']/Value='CODE'">
        <xsl:call-template name="escape_crlf">
          <xsl:with-param name="string"
            select="concat(': ',DicomAttribute[@tag='0040A168']/Item/DicomAttribute[@tag='00080104']/Value)" />
        </xsl:call-template>
      </xsl:when>

      <xsl:when
        test="DicomAttribute[@tag='0040A040']/Value='PNAME'">
        :
        <xsl:value-of select="DicomAttribute[@tag='0040A123']/Value" />
      </xsl:when>

      <xsl:when
        test="DicomAttribute[@tag='0040A040']/Value='NUM'">
        <xsl:value-of
          select="concat(': ',DicomAttribute[@tag='0040A300']/Item/DicomAttribute[@tag='0040A30A']/Value)" />
        <xsl:if
          test="DicomAttribute[@tag='0040A300']/Item/DicomAttribute[@tag='004008EA']/Item/DicomAttribute[@tag='00080100']/Value != 1"> <!-- No unit (UCUM) -->
          <xsl:value-of
            select="concat(' ',DicomAttribute[@tag='0040A300']/Item/DicomAttribute[@tag='004008EA']/Item/DicomAttribute[@tag='00080100']/Value)" />
        </xsl:if>
      </xsl:when>


      <xsl:when
        test="DicomAttribute[@tag='0040A040']/Value='CONTAINER'">
        <ul>
          <xsl:apply-templates select="DicomAttribute[@tag='0040A730']/Item"
            mode="contentLI" />
        </ul>
      </xsl:when>

      <xsl:otherwise>
        <i>
          [
          <xsl:value-of select="DicomAttribute[@tag='0040A040']/Value" />
          ] (Unspecified value)
        </i>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>


  <xsl:template match="Item" mode="imageref">
    Image
    <img align="top">
      <xsl:attribute name="src">
        <xsl:value-of select="$wadoURL" />
        <xsl:text>?requestType=WADO&amp;studyUID=</xsl:text>
        <xsl:value-of select="../../../../DicomAttribute[@tag='0020000D']/Value"/>
        <xsl:text>&amp;seriesUID=</xsl:text>
        <xsl:value-of select="../../DicomAttribute[@tag='0020000E']/Value"/>
        <xsl:text>&amp;objectUID=</xsl:text>
        <xsl:value-of select="DicomAttribute[@tag='00081155']/Value"/>
			</xsl:attribute>
    </img>
    <br />
  </xsl:template>

  <xsl:template name="escape_crlf">
    <xsl:param name="string" />
    <xsl:variable name="CR" select="'&#xD;'" />
    <xsl:variable name="LF" select="'&#xA;'" />
    <xsl:variable name="CRLF" select="concat($CR, $LF)" />

    <xsl:choose>
      <!-- crlf -->
      <xsl:when test="contains($string,$CRLF)">
        <xsl:value-of select="substring-before($string,$CRLF)" />
        <br />
        <xsl:call-template name="escape_crlf">
          <xsl:with-param name="string"
            select="substring-after($string,$CRLF)" />
        </xsl:call-template>
      </xsl:when>
      <!-- carriage return -->
      <xsl:when test="contains($string,$CR)">
        <xsl:value-of select="substring-before($string,$CR)" />
        <br />
        <xsl:call-template name="escape_crlf">
          <xsl:with-param name="string"
            select="substring-after($string,$CR)" />
        </xsl:call-template>
      </xsl:when>
      <!-- line feed -->
      <xsl:when test="contains($string,$LF)">
        <xsl:value-of select="substring-before($string,$LF)" />
        <br />
        <xsl:call-template name="escape_crlf">
          <xsl:with-param name="string"
            select="substring-after($string,$LF)" />
        </xsl:call-template>
      </xsl:when>

      <xsl:otherwise>
        <xsl:value-of select="$string" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>


