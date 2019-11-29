<?xml version="1.0" encoding="UTF-8"?><!--
  Title: Lantana's CDA Stylesheet
  Original Filename: cda.xsl
  Usage: This stylesheet is designed for use with clinical documents

  Revision History: 2015-08-31 Eric Parapini - Original Commit
  Revision History: 2015-08-31 Eric Parapini - Updating Built in CSS for Camara conversion, fixed the rendering issue with Table of contents linking (Sean's help)
  Revision History: 2015-09-01 Eric Parapini - Updating Colors, Revamping the CSS, New Vision of the Header Information, Hover Tables, Formatted Patient Information Initial Release
  Revision History: 2015-09-03 Eric Parapini - Cleaned up CSS - Documentationof, added Header/Body/Footer Elements
  Revision History: 2015-10-01 Eric Parapini - CSS is now separated, Encounter of is moved down, including Bootstrap elements
  Revision History: 2015-10-02 Eric Parapini - CSS now has new styles that will take over the other spots
  Revision History: 2015-10-05 Eric Parapini - CSS updated, better use of bootstrap elements, responsive
  Revision History: 2015-10-06 Eric Parapini - Stylesheet rendering updated, Author section redone, tables now render in section elements
  Revision History: 2015-10-07 Eric Parapini - Changed the font sizes
  Revision History: 2015-10-21 Eric Parapini - Fixed logic, cleaned everything up, making the document more consistent
  Revision History: 2015-10-22 Eric Parapini - Converted some more sections to the modern bootstrap formatting, reorganized the footer
                                               Fixed up the assigned entity formatting
                                               Fixed up the informant
  Revision History: 2015-10-22 Eric Parapini - Fixed a few more things, disabled table of content generation for now
                                               Removed the timezone offset in date renderings, deemed unecessary.
  Revision History: 2015-12-10 Eric Parapini - Removed some of the additional time errors
  Revision History: 2016-02-22 Eric Parapini - Added Logo space, added in some javascript background support for interactive navigation bars
  Revision History: 2016-02-23 Eric Parapini - Added smooth scrolling, making the document easier to navigate
  Revision History: 2016-02-24 Eric Parapini - Added some CSS and content to make the table of contents styling easier to control
  Revision History: 2016-02-29 Eric Parapini - Added patient information entry in the table of contents
  Revision History: 2016-03-09 Eric Parapini - Adding in simple matches for common identifier OIDS (SSN, Driver's licenses)
                                               Additional fixes to the TOC, working on scrollspy working
                                               Fixed issue with Care = PROV not being recrognized
  Revision History: 2016-05-10 Eric Parapini - Updated Table of Contents to properly highlight location within document
  Revision History: 2016-05-17 Eric Parapini - Updated location of the next of kin to be with the patient information
  Revision History: 2016-06-08 Eric Parapini - Removed Emergency Contact Table of Contents
  Revision History: 2016-08-06 Eric Parapini - Table of Contents Drag and Drop
  Revision History: 2016-08-08 Eric Parapini - Document Type shows up in rendered view
  Revision History: 2016-11-14 Eric Parapini - Further Separating supporting libraries
  Revision History: 2017-02-09 Eric Parapini - Fixed Bug removing styleCodes
  Revision History: 2017-02-24 Eric Parapini - Fixed titles
  Revision History: 2017-02-26 Eric Parapini - Cleaned up some code
  Revision History: 2017-03-31 Eric Parapini - Whitespace issues fixing
  Revision History: 2017-04-05 Eric Parapini - Whitespace tweaking in the header, added patient ID highlighting
  Revision History: 2017-04-06 Eric Parapini - Tweaked encounter whitespace organization

  This style sheet is based on a major revision of the original CDA XSL, which was made possible thanks to the contributions of:
  - Jingdong Li
  - KH
  - Rick Geimer
  - Sean McIlvenna
  - Dale Nelson

--><!--
Copyright 2016 Lantana Consulting Group

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
--><xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <!-- This is where all the styles are loaded -->
  
  

  <xsl:output xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" method="html" indent="yes" version="4.01" encoding="UTF-8" doctype-system="http://www.w3.org/TR/html4/strict.dtd" doctype-public="-//W3C//DTD HTML 4.01//EN"/>
  <xsl:param xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="limit-external-images" select="'yes'"/>
  <!-- A vertical bar separated list of URI prefixes, such as "http://www.example.com|https://www.example.com" -->
  <xsl:param xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="external-image-whitelist"/>
  <xsl:param xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="logo-location"/>
  <!-- string processing variables -->
  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="lc" select="'abcdefghijklmnopqrstuvwxyz'"/>
  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="uc" select="'ABCDEFGHIJKLMNOPQRSTUVWXYZ'"/>
  <!-- removes the following characters, in addition to line breaks "':;?`{}“”„‚’ -->
  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="simple-sanitizer-match">
    <xsl:text>
&#13;"':;?`{}“”„‚’</xsl:text>
  </xsl:variable>
  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="simple-sanitizer-replace" select="'***************'"/>
  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="javascript-injection-warning">WARNING: Javascript injection attempt detected
    in source CDA document. Terminating</xsl:variable>
  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="malicious-content-warning">WARNING: Potentially malicious content found in CDA
    document.</xsl:variable>

  <!-- global variable title -->
  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="title">
    <xsl:choose>
      <xsl:when test="string-length(/n1:ClinicalDocument/n1:title) &gt;= 1">
        <xsl:value-of select="/n1:ClinicalDocument/n1:title"/>
      </xsl:when>
      <xsl:when test="/n1:ClinicalDocument/n1:code/@displayName">
        <xsl:value-of select="/n1:ClinicalDocument/n1:code/@displayName"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>Clinical Document</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>


  <!-- Main -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="/">
    <xsl:apply-templates select="n1:ClinicalDocument"/>
  </xsl:template>

  <!-- produce browser rendered, human readable clinical document -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:ClinicalDocument">
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <xsl:comment> Do NOT edit this HTML directly: it was generated via an XSLT transformation from a CDA Release 2 XML document. </xsl:comment>
        <title class="cda-title">
          <xsl:value-of select="$title"/>
        </title>
        <xsl:call-template name="bootstrap-css"/>
        <xsl:call-template name="lantana-css"/>
      </head>
      <body data-spy="scroll" data-target="#navbar-cda">

        <div class="cda-render toc col-md-3" role="complementary">

          <!-- produce table of contents -->
          <xsl:if test="not(//n1:nonXMLBody)">
            <xsl:if test="count(/n1:ClinicalDocument/n1:component/n1:structuredBody/n1:component[n1:section]) &gt; 0">
              <xsl:call-template name="make-tableofcontents"/>
            </xsl:if>
          </xsl:if>
        </div>

        <!-- Container: CDA Render -->
        <div class="cda-render container-fluid col-md-9 cda-render-main" role="main">

          <row>
            <h1 id="top" class="cda-title">
              <xsl:value-of select="$title"/>
            </h1>
          </row>
          <!-- START display top portion of clinical document -->
          <div class="top container-fluid">
            <xsl:call-template name="recordTarget"/>
            <xsl:call-template name="documentationOf"/>
            <xsl:call-template name="author"/>
            <xsl:call-template name="componentOf"/>
            <xsl:call-template name="participant"/>
            <xsl:call-template name="informant"/>
            <xsl:call-template name="informationRecipient"/>
            <xsl:call-template name="legalAuthenticator"/>
          </div>
          <!-- END display top portion of clinical document -->

          <!-- produce human readable document content -->
          <div class="middle" id="doc-clinical-info">
            <xsl:apply-templates select="n1:component/n1:structuredBody | n1:component/n1:nonXMLBody"/>
          </div>
          <!-- Footer -->
          <div class="bottom" id="doc-info">
            <xsl:call-template name="authenticator"/>
            <xsl:call-template name="custodian"/>
            <xsl:call-template name="dataEnterer"/>
            <xsl:call-template name="documentGeneral"/>
          </div>
        </div>

      </body>
    </html>



    <!-- BEGIN TEMPLATES -->
  </xsl:template>
  <!-- generate table of contents -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="make-tableofcontents">

    <nav class="cda-render hidden-print hidden-xs hidden-sm affix toc-box" id="navbar-cda">
      <div class="container-fluid cda-render toc-header-container">
        <xsl:if test="$logo-location">
          <div class="col-md-1">
            <img src="logo.png" class="img-responsive" alt="Logo">
              <xsl:attribute name="src">
                <xsl:value-of select="$logo-location"/>
              </xsl:attribute>
            </img>
          </div>
        </xsl:if>
        <div class="cda-render toc-header">
          <xsl:for-each select="/n1:ClinicalDocument/n1:recordTarget/n1:patientRole">
            <xsl:call-template name="show-name">
              <xsl:with-param name="name" select="n1:patient/n1:name"/>
            </xsl:call-template>
          </xsl:for-each>
        </div>
        <div class="cda-render toc-header">
          <xsl:value-of select="$title"/>
        </div>
      </div>
      <ul class="cda-render nav nav-stacked fixed" id="navbar-list-cda">
        <li>
          <a class="cda-render lantana-toc" href="#top">BACK TO TOP</a>
        </li>
        <li>
          <a class="cda-render lantana-toc" href="#cda-patient">DEMOGRAPHICS</a>
        </li>
        <li>
          <a class="cda-render lantana-toc" href="#author-performer">AUTHORING DETAILS</a>
        </li>
        <li>
          <a class="cda-render lantana-toc bold" href="#doc-clinical-info">Clinical Sections</a>
          <ul class="cda-render nav nav-stacked fixed" id="navbar-list-cda-sortable">
            <xsl:for-each select="n1:component/n1:structuredBody/n1:component/n1:section/n1:title">
              <li>
                <a class="cda-render lantana-toc" href="#{generate-id(.)}">
                  <xsl:value-of select="."/>
                </a>
              </li>
            </xsl:for-each>
          </ul>
        </li>
        <li>
          <a class="cda-render lantana-toc" href="#doc-info">SIGNATURES</a>
        </li>
      </ul>
    </nav>
  </xsl:template>
  <!-- header elements -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="documentGeneral">
    <div class="container-fluid">
      <h2 class="section-title col-md-6">
        <xsl:text>Document Information</xsl:text>
      </h2>
      <div class="table-responsive col-md-6">
        <table class="table table-hover">
          <thead>
            <tr>
              <th>
                <xsl:text>Document Identifier</xsl:text>
              </th>
              <th>
                <xsl:text>Document Created</xsl:text>
              </th>
            </tr>

          </thead>
          <tbody>
            <tr>
              <td>
                <xsl:call-template name="show-id">
                  <xsl:with-param name="id" select="n1:id"/>
                </xsl:call-template>
              </td>
              <td>
                <xsl:call-template name="show-time">
                  <xsl:with-param name="datetime" select="n1:effectiveTime"/>
                </xsl:call-template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </xsl:template>
  <!-- confidentiality -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="confidentiality">
    <table class="header_table">
      <tbody>
        <td class="td_header_role_name">
          <xsl:text>Confidentiality</xsl:text>
        </td>
        <td class="td_header_role_value">
          <xsl:choose>
            <xsl:when test="n1:confidentialityCode/@code = 'N'">
              <xsl:text>Normal</xsl:text>
            </xsl:when>
            <xsl:when test="n1:confidentialityCode/@code = 'R'">
              <xsl:text>Restricted</xsl:text>
            </xsl:when>
            <xsl:when test="n1:confidentialityCode/@code = 'V'">
              <xsl:text>Very restricted</xsl:text>
            </xsl:when>
          </xsl:choose>
          <xsl:if test="n1:confidentialityCode/n1:originalText">
            <xsl:text> </xsl:text>
            <xsl:value-of select="n1:confidentialityCode/n1:originalText"/>
          </xsl:if>
        </td>
      </tbody>
    </table>
  </xsl:template>
  <!-- author -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="author">
    <xsl:if test="n1:author">
      <div class="header container-fluid">
        <xsl:for-each select="n1:author/n1:assignedAuthor">
          <div class="container-fluid">
            <div class="col-md-6">
              <h2 class="section-title col-md-6" id="author-performer">
                <xsl:text>Author</xsl:text>
              </h2>
              <div class="header-group-content col-md-8">
                <xsl:choose>
                  <xsl:when test="n1:assignedPerson/n1:name">
                    <xsl:call-template name="show-name">
                      <xsl:with-param name="name" select="n1:assignedPerson/n1:name"/>
                    </xsl:call-template>
                    <xsl:if test="n1:representedOrganization">
                      <xsl:text> - </xsl:text>
                      <xsl:call-template name="show-name">
                        <xsl:with-param name="name" select="n1:representedOrganization/n1:name"/>
                      </xsl:call-template>
                    </xsl:if>
                  </xsl:when>
                  <xsl:when test="n1:assignedAuthoringDevice/n1:softwareName">
                    <xsl:call-template name="show-code">
                      <xsl:with-param name="code" select="n1:assignedAuthoringDevice/n1:softwareName"/>
                    </xsl:call-template>

                  </xsl:when>
                  <xsl:when test="n1:representedOrganization">
                    <xsl:call-template name="show-name">
                      <xsl:with-param name="name" select="n1:representedOrganization/n1:name"/>
                    </xsl:call-template>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:for-each select="n1:id">
                      <xsl:call-template name="show-id">
                        <xsl:with-param name="id" select="."/>
                      </xsl:call-template>
                    </xsl:for-each>
                  </xsl:otherwise>
                </xsl:choose>
              </div>
            </div>
            <div class="col-md-6">
              <xsl:if test="n1:addr | n1:telecom">
                <h2 class="section-title col-md-6">Contact</h2>
                <div class="header-group-content col-md-8">
                  <xsl:call-template name="show-contactInfo">
                    <xsl:with-param name="contact" select="."/>
                  </xsl:call-template>
                </div>
              </xsl:if>
            </div>
          </div>
        </xsl:for-each>
      </div>
    </xsl:if>
  </xsl:template>
  <!--  authenticator -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="authenticator">
    <xsl:if test="n1:authenticator">
      <div class="header container-fluid">
        <xsl:for-each select="n1:authenticator">
          <div class="col-md-6">
            <h2 class="section-title col-md-6">
              <xsl:text>Signed</xsl:text>
            </h2>
            <div class="header-group-content col-md-8">
              <xsl:call-template name="show-name">
                <xsl:with-param name="name" select="n1:assignedEntity/n1:assignedPerson/n1:name"/>
              </xsl:call-template>
              <xsl:text> at </xsl:text>
              <xsl:call-template name="show-time">
                <xsl:with-param name="datetime" select="n1:time"/>
              </xsl:call-template>
            </div>
          </div>
          <div class="col-md-6">
            <xsl:if test="n1:assignedEntity/n1:addr | n1:assignedEntity/n1:telecom">
              <h2 class="section-title col-md-6">
                <xsl:text>Contact</xsl:text>
              </h2>
              <div class="header-group-content col-md-8">
                <xsl:call-template name="show-contactInfo">
                  <xsl:with-param name="contact" select="n1:assignedEntity"/>
                </xsl:call-template>
              </div>
            </xsl:if>
          </div>
        </xsl:for-each>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- legalAuthenticator -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="legalAuthenticator">
    <div class="container-fluid">
      <xsl:if test="n1:legalAuthenticator">
        <div class="header container-fluid">
          <div class="col-md-6">
            <h2 class="section-title col-md-6">
              <xsl:text>Legal authenticator</xsl:text>
            </h2>
            <div class="header-group-content col-md-8">
              <xsl:call-template name="show-assignedEntity">
                <xsl:with-param name="asgnEntity" select="n1:legalAuthenticator/n1:assignedEntity"/>
              </xsl:call-template>
              <xsl:text> </xsl:text>
              <xsl:call-template name="show-sig">
                <xsl:with-param name="sig" select="n1:legalAuthenticator/n1:signatureCode"/>
              </xsl:call-template>
              <xsl:if test="n1:legalAuthenticator/n1:time/@value">
                <xsl:text> at </xsl:text>
                <xsl:call-template name="show-time">
                  <xsl:with-param name="datetime" select="n1:legalAuthenticator/n1:time"/>
                </xsl:call-template>
              </xsl:if>
            </div>
          </div>
          <xsl:if test="n1:legalAuthenticator/n1:assignedEntity/n1:addr | n1:legalAuthenticator/n1:assignedEntity/n1:telecom">
            <div class="col-md-6">
              <h2 class="col-md-6 section-title">Contact</h2>
              <div class="header-group-content col-md-8">
                <xsl:call-template name="show-contactInfo">
                  <xsl:with-param name="contact" select="n1:legalAuthenticator/n1:assignedEntity"/>
                </xsl:call-template>
              </div>
            </div>
          </xsl:if>
        </div>
      </xsl:if>
    </div>
  </xsl:template>
  <!-- dataEnterer -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="dataEnterer">
    <xsl:if test="n1:dataEnterer">
      <div class="container-fluid header">
        <div class="col-md-6">
          <h2 class="section-title col-md-6">
            <xsl:text>Entered by</xsl:text>
          </h2>
          <div class="col-md-6 header-group-content">
            <xsl:call-template name="show-assignedEntity">
              <xsl:with-param name="asgnEntity" select="n1:dataEnterer/n1:assignedEntity"/>
            </xsl:call-template>
          </div>
        </div>
        <div class="col-md-6">
          <xsl:if test="n1:dataEnterer/n1:assignedEntity/n1:addr | n1:dataEnterer/n1:assignedEntity/n1:telecom">
            <h2 class="section-title col-md-6">
              <xsl:text>Contact</xsl:text>
            </h2>
            <div class="col-md-6 header-group-content">
              <xsl:call-template name="show-contactInfo">
                <xsl:with-param name="contact" select="n1:dataEnterer/n1:assignedEntity"/>
              </xsl:call-template>
            </div>
          </xsl:if>
        </div>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- componentOf -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="componentOf">
    <xsl:if test="n1:componentOf">
      <div class="header container-fluid">
        <xsl:for-each select="n1:componentOf/n1:encompassingEncounter">
          <div class="container-fluid col-md-8">
            <div class="container-fluid">
              <h2 class="section-title col-md-10">
                <xsl:text>Encounter</xsl:text>
              </h2>
              <div class="header-group-content col-md-10">
                <xsl:if test="n1:id">
                  <xsl:choose>
                    <xsl:when test="n1:code">
                      <div class="row">
                        <div class="attribute-title col-md-2">
                          <xsl:text>Identifier</xsl:text>
                        </div>
                        <div class="col-md-6">
                          <xsl:call-template name="show-id">
                            <xsl:with-param name="id" select="n1:id"/>
                          </xsl:call-template>
                        </div>
                      </div>
                      <div class="row">
                        <div class="attribute-title col-md-2">
                          <xsl:text>Type</xsl:text>
                        </div>
                        <div class="col-md-6">
                          <xsl:call-template name="show-code">
                            <xsl:with-param name="code" select="n1:code"/>
                          </xsl:call-template>
                        </div>
                      </div>
                    </xsl:when>
                    <xsl:otherwise>
                      <div class="row">
                        <div class="attribute-title col-md-2">
                          <xsl:text>Identifier</xsl:text>
                        </div>
                        <div class="col-md-6">
                          <xsl:call-template name="show-id">
                            <xsl:with-param name="id" select="n1:id"/>
                          </xsl:call-template>
                        </div>
                      </div>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:if>
                <div class="row">
                  <div class="attribute-title col-md-2">
                    <xsl:text>Date</xsl:text>
                  </div>
                  <xsl:if test="n1:effectiveTime">
                    <xsl:choose>
                      <xsl:when test="n1:effectiveTime/@value">
                        <div class="col-md-4">
                          <xsl:call-template name="show-time">
                            <xsl:with-param name="datetime" select="n1:effectiveTime"/>
                          </xsl:call-template>
                        </div>
                      </xsl:when>
                      <xsl:when test="n1:effectiveTime/n1:low">
                        <div class="col-md-4">
                          <span class="attribute-title">
                            <xsl:text>From: </xsl:text>
                          </span>
                          <xsl:call-template name="show-time">
                            <xsl:with-param name="datetime" select="n1:effectiveTime/n1:low"/>
                          </xsl:call-template>
                        </div>
                        <xsl:if test="n1:effectiveTime/n1:high">
                          <div class="col-md-4">
                            <span class="attribute-title">
                              <xsl:text>To: </xsl:text>
                            </span>
                            <xsl:call-template name="show-time">
                              <xsl:with-param name="datetime" select="n1:effectiveTime/n1:high"/>
                            </xsl:call-template>
                          </div>
                        </xsl:if>
                      </xsl:when>
                    </xsl:choose>
                  </xsl:if>
                </div>
                <xsl:if test="n1:location/n1:healthCareFacility">
                  <div class="row">
                    <div class="attribute-title col-md-2">
                      <xsl:text>Location</xsl:text>
                    </div>
                    <div class="col-md-6">
                      <xsl:choose>
                        <xsl:when test="n1:location/n1:healthCareFacility/n1:location/n1:name">
                          <xsl:call-template name="show-name">
                            <xsl:with-param name="name" select="n1:location/n1:healthCareFacility/n1:location/n1:name"/>
                          </xsl:call-template>
                          <xsl:for-each select="n1:location/n1:healthCareFacility/n1:serviceProviderOrganization/n1:name">
                            <xsl:text> of </xsl:text>
                            <xsl:call-template name="show-name">
                              <xsl:with-param name="name" select="n1:location/n1:healthCareFacility/n1:serviceProviderOrganization/n1:name"/>
                            </xsl:call-template>
                          </xsl:for-each>
                        </xsl:when>
                        <xsl:when test="n1:location/n1:healthCareFacility/n1:code">
                          <xsl:call-template name="show-code">
                            <xsl:with-param name="code" select="n1:location/n1:healthCareFacility/n1:code"/>
                          </xsl:call-template>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:if test="n1:location/n1:healthCareFacility/n1:id">
                            <span class="attribute-title">
                              <xsl:text>ID: </xsl:text>
                            </span>
                            <xsl:for-each select="n1:location/n1:healthCareFacility/n1:id">
                              <xsl:call-template name="show-id">
                                <xsl:with-param name="id" select="."/>
                              </xsl:call-template>
                            </xsl:for-each>
                          </xsl:if>
                        </xsl:otherwise>
                      </xsl:choose>
                    </div>
                  </div>
                </xsl:if>
              </div>
              <xsl:if test="n1:responsibleParty">
                <div class="col-md-6">
                  <h2 class="section-title col-md-6">
                    <xsl:text>Responsible Party</xsl:text>
                  </h2>
                  <div class="header-group-content col-md-8">
                    <xsl:call-template name="show-assignedEntity">
                      <xsl:with-param name="asgnEntity" select="n1:responsibleParty/n1:assignedEntity"/>
                    </xsl:call-template>
                  </div>
                </div>
              </xsl:if>
              <xsl:if test="n1:responsibleParty/n1:assignedEntity/n1:addr | n1:responsibleParty/n1:assignedEntity/n1:telecom">
                <div class="col-md-6">
                  <h2 class="section-title col-md-6">
                    <xsl:text>Contact</xsl:text>
                  </h2>
                  <div class="header-group-content col-md-8">
                    <xsl:call-template name="show-contactInfo">
                      <xsl:with-param name="contact" select="n1:responsibleParty/n1:assignedEntity"/>
                    </xsl:call-template>
                  </div>
                </div>
              </xsl:if>
            </div>
          </div>
        </xsl:for-each>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- custodian -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="custodian">
    <xsl:if test="n1:custodian">
      <div class="container-fluid header">
        <div class="col-md-6">
          <h2 class="section-title col-md-6">
            <xsl:text>Document maintained by</xsl:text>
          </h2>
          <div class="header-group-content col-md-8">
            <xsl:choose>
              <xsl:when test="n1:custodian/n1:assignedCustodian/n1:representedCustodianOrganization/n1:name">
                <xsl:call-template name="show-name">
                  <xsl:with-param name="name" select="n1:custodian/n1:assignedCustodian/n1:representedCustodianOrganization/n1:name"/>
                </xsl:call-template>
              </xsl:when>
              <xsl:otherwise>
                <xsl:for-each select="n1:custodian/n1:assignedCustodian/n1:representedCustodianOrganization/n1:id">
                  <xsl:call-template name="show-id"/>
                  <xsl:if test="position() != last()"> </xsl:if>
                </xsl:for-each>
              </xsl:otherwise>
            </xsl:choose>
          </div>
        </div>
        <xsl:if test="n1:custodian/n1:assignedCustodian/n1:representedCustodianOrganization/n1:addr | n1:custodian/n1:assignedCustodian/n1:representedCustodianOrganization/n1:telecom">
          <div class="col-md-6">
            <h2 class="section-title col-md-6"> Contact </h2>
            <div class="header-group-content col-md-8">
              <xsl:call-template name="show-contactInfo">
                <xsl:with-param name="contact" select="n1:custodian/n1:assignedCustodian/n1:representedCustodianOrganization"/>
              </xsl:call-template>
            </div>
          </div>
        </xsl:if>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- documentationOf -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="documentationOf">
    <xsl:if test="n1:documentationOf">
      <div class="header container-fluid">
        <xsl:for-each select="n1:documentationOf">
          <xsl:if test="n1:serviceEvent/@classCode and n1:serviceEvent/n1:code">
            <div class="container-fluid">
              <div class="container-fluid">
                <xsl:variable name="displayName">
                  <xsl:call-template name="show-actClassCode">
                    <xsl:with-param name="clsCode" select="n1:serviceEvent/@classCode"/>
                  </xsl:call-template>
                </xsl:variable>
                <xsl:if test="$displayName">
                  <div class="col-md-6">
                    <h2 class="section-title">
                      <xsl:call-template name="firstCharCaseUp">
                        <xsl:with-param name="data" select="$displayName"/>
                      </xsl:call-template>
                    </h2>
                  </div>
                  <div class="header-group-content col-md-8">
                    <xsl:call-template name="show-code">
                      <xsl:with-param name="code" select="n1:serviceEvent/n1:code"/>
                    </xsl:call-template>
                    <xsl:if test="n1:serviceEvent/n1:effectiveTime">
                      <xsl:choose>
                        <xsl:when test="n1:serviceEvent/n1:effectiveTime/@value">
                          <xsl:text> at </xsl:text>
                          <xsl:call-template name="show-time">
                            <xsl:with-param name="datetime" select="n1:serviceEvent/n1:effectiveTime"/>
                          </xsl:call-template>
                        </xsl:when>
                        <xsl:when test="n1:serviceEvent/n1:effectiveTime/n1:low">
                          <xsl:text> from </xsl:text>
                          <xsl:call-template name="show-time">
                            <xsl:with-param name="datetime" select="n1:serviceEvent/n1:effectiveTime/n1:low"/>
                          </xsl:call-template>
                          <xsl:if test="n1:serviceEvent/n1:effectiveTime/n1:high">
                            <xsl:text> to </xsl:text>
                            <xsl:call-template name="show-time">
                              <xsl:with-param name="datetime" select="n1:serviceEvent/n1:effectiveTime/n1:high"/>
                            </xsl:call-template>
                          </xsl:if>
                        </xsl:when>
                      </xsl:choose>
                    </xsl:if>
                  </div>
                </xsl:if>
              </div>
            </div>
          </xsl:if>
          <xsl:for-each select="n1:serviceEvent/n1:performer">
            <div class="header-group container-fluid">
              <xsl:variable name="displayName">
                <xsl:call-template name="show-participationType">
                  <xsl:with-param name="ptype" select="@typeCode"/>
                </xsl:call-template>
                <xsl:if test="n1:functionCode/@code">
                  <xsl:text> </xsl:text>
                  <xsl:call-template name="show-participationFunction">
                    <xsl:with-param name="pFunction" select="n1:functionCode/@code"/>
                  </xsl:call-template>
                </xsl:if>
              </xsl:variable>
              <div class="container-fluid">
                <h2 class="section-title col-md-6" id="service-event">
                  <xsl:text>Service Event</xsl:text>
                </h2>
                <div class="header-group-content col-md-8">
                  <xsl:call-template name="show-assignedEntity">
                    <xsl:with-param name="asgnEntity" select="n1:assignedEntity"/>
                  </xsl:call-template>
                </div>
                <div class="header-group-content col-md-8">
                  <xsl:if test="../n1:effectiveTime/n1:low">
                    <xsl:call-template name="show-time">
                      <xsl:with-param name="datetime" select="../n1:effectiveTime/n1:low"/>
                    </xsl:call-template>
                  </xsl:if>

                  <xsl:if test="../n1:effectiveTime/n1:high"> - <xsl:call-template name="show-time">
                      <xsl:with-param name="datetime" select="../n1:effectiveTime/n1:high"/>
                    </xsl:call-template>
                  </xsl:if>
                </div>
              </div>
            </div>
          </xsl:for-each>
        </xsl:for-each>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- inFulfillmentOf -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="inFulfillmentOf">
    <xsl:if test="n1:infulfillmentOf">
      <xsl:for-each select="n1:inFulfillmentOf">
        <xsl:text>In fulfillment of</xsl:text>
        <xsl:for-each select="n1:order">
          <xsl:for-each select="n1:id">
            <xsl:call-template name="show-id"/>
          </xsl:for-each>
          <xsl:for-each select="n1:code">
            <xsl:text> </xsl:text>
            <xsl:call-template name="show-code">
              <xsl:with-param name="code" select="."/>
            </xsl:call-template>
          </xsl:for-each>
          <xsl:for-each select="n1:priorityCode">
            <xsl:text> </xsl:text>
            <xsl:call-template name="show-code">
              <xsl:with-param name="code" select="."/>
            </xsl:call-template>
          </xsl:for-each>
        </xsl:for-each>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- informant -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="informant">
    <xsl:if test="n1:informant">
      <div class="header container-fluid">
        <xsl:for-each select="n1:informant">
          <div class="container-fluid">
            <div class="col-md-6">
              <h2 class="section-title col-md-6">
                <xsl:text>Informant</xsl:text>
              </h2>
              <div class="header-group-content col-md-8">
                <xsl:if test="n1:assignedEntity">
                  <xsl:call-template name="show-assignedEntity">
                    <xsl:with-param name="asgnEntity" select="n1:assignedEntity"/>
                  </xsl:call-template>
                </xsl:if>
                <xsl:if test="n1:relatedEntity">
                  <xsl:call-template name="show-relatedEntity">
                    <xsl:with-param name="relatedEntity" select="n1:relatedEntity"/>
                  </xsl:call-template>
                </xsl:if>
              </div>
            </div>
            <xsl:choose>
              <xsl:when test="n1:assignedEntity/n1:addr | n1:assignedEntity/n1:telecom">
                <div class="col-md-6">
                  <h2 class="section-title col-md-6">
                    <xsl:text>Contact</xsl:text>
                  </h2>
                  <div class="header-group-content col-md-8">
                    <xsl:if test="n1:assignedEntity">
                      <xsl:call-template name="show-contactInfo">
                        <xsl:with-param name="contact" select="n1:assignedEntity"/>
                      </xsl:call-template>
                    </xsl:if>
                  </div>
                </div>
              </xsl:when>
              <xsl:when test="n1:relatedEntity/n1:addr | n1:relatedEntity/n1:telecom">
                <div class="col-md-6">
                  <h2 class="col-md-6 section-title">
                    <xsl:text>Contact</xsl:text>
                  </h2>
                  <div class="col-md-6 header-group-content">
                    <xsl:if test="n1:relatedEntity">
                      <xsl:call-template name="show-contactInfo">
                        <xsl:with-param name="contact" select="n1:relatedEntity"/>
                      </xsl:call-template>
                    </xsl:if>
                  </div>
                </div>
              </xsl:when>
            </xsl:choose>
          </div>
        </xsl:for-each>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- informantionRecipient -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="informationRecipient">
    <div class="container-fluid">
      <xsl:if test="n1:informationRecipient">
        <div class="container-fluid header">
          <xsl:for-each select="n1:informationRecipient">
            <div class="container-fluid">
              <h2 class="section-title col-md-6">
                <xsl:text>Information Recipient</xsl:text>
              </h2>
              <div class="col-md-6 header-group-content">
                <xsl:choose>
                  <xsl:when test="n1:intendedRecipient/n1:informationRecipient/n1:name">
                    <xsl:for-each select="n1:intendedRecipient/n1:informationRecipient">
                      <xsl:call-template name="show-name">
                        <xsl:with-param name="name" select="n1:name"/>
                      </xsl:call-template>
                      <xsl:if test="position() != last()"> </xsl:if>
                    </xsl:for-each>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:for-each select="n1:intendedRecipient">
                      <xsl:for-each select="n1:id">
                        <xsl:call-template name="show-id"/>
                      </xsl:for-each>
                      <xsl:if test="position() != last()"> </xsl:if>
                    </xsl:for-each>
                  </xsl:otherwise>
                </xsl:choose>
              </div>
              <div class="col-md-6">
                <xsl:if test="n1:intendedRecipient/n1:addr | n1:intendedRecipient/n1:telecom">
                  <h2 class="section-title col-md-6">
                    <xsl:text>Contact</xsl:text>
                  </h2>
                  <div class="col-md-6">
                    <xsl:call-template name="show-contactInfo">
                      <xsl:with-param name="contact" select="n1:intendedRecipient"/>
                    </xsl:call-template>
                  </div>
                </xsl:if>
              </div>
            </div>
          </xsl:for-each>
        </div>
      </xsl:if>
    </div>
  </xsl:template>
  <!-- participant -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="participant">
    <div class="container-fluid">
      <xsl:if test="n1:participant">
        <div class="header container-fluid">
          <xsl:for-each select="n1:participant">
            <xsl:if test="not(n1:associatedEntity/@classCode = 'ECON' or n1:associatedEntity/@classCode = 'NOK')">
              <xsl:variable name="participtRole">
                <xsl:call-template name="translateRoleAssoCode">
                  <xsl:with-param name="classCode" select="n1:associatedEntity/@classCode"/>
                  <xsl:with-param name="code" select="n1:associatedEntity/n1:code"/>
                </xsl:call-template>
              </xsl:variable>
              <div class="col-md-6">
                <h2 class="col-md-6 section-title">
                  <xsl:choose>
                    <xsl:when test="$participtRole">
                      <xsl:call-template name="firstCharCaseUp">
                        <xsl:with-param name="data" select="$participtRole"/>
                      </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:text>Participant</xsl:text>
                    </xsl:otherwise>
                  </xsl:choose>
                </h2>
                <div class="header-group-content col-md-8">
                  <xsl:if test="n1:functionCode">
                    <xsl:call-template name="show-code">
                      <xsl:with-param name="code" select="n1:functionCode"/>
                    </xsl:call-template>
                  </xsl:if>
                  <xsl:call-template name="show-associatedEntity">
                    <xsl:with-param name="assoEntity" select="n1:associatedEntity"/>
                  </xsl:call-template>
                  <xsl:if test="n1:time">
                    <xsl:if test="n1:time/n1:low">
                      <xsl:text> from </xsl:text>
                      <xsl:call-template name="show-time">
                        <xsl:with-param name="datetime" select="n1:time/n1:low"/>
                      </xsl:call-template>
                    </xsl:if>
                    <xsl:if test="n1:time/n1:high">
                      <xsl:text> to </xsl:text>
                      <xsl:call-template name="show-time">
                        <xsl:with-param name="datetime" select="n1:time/n1:high"/>
                      </xsl:call-template>
                    </xsl:if>
                  </xsl:if>
                  <xsl:if test="position() != last()">
                    <br/>
                  </xsl:if>
                </div>
              </div>
              <div class="col-md-6">
                <xsl:if test="n1:associatedEntity/n1:addr | n1:associatedEntity/n1:telecom">
                  <h2 class="section-title col-md-6">
                    <xsl:text>Contact</xsl:text>
                  </h2>
                  <div class="col-md-6 header-group-content">
                    <xsl:call-template name="show-contactInfo">
                      <xsl:with-param name="contact" select="n1:associatedEntity"/>
                    </xsl:call-template>
                  </div>
                </xsl:if>
              </div>
            </xsl:if>
          </xsl:for-each>
        </div>
      </xsl:if>
    </div>
  </xsl:template>

  <!-- recordTarget / Patient -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="recordTarget">
    <div class="header container-fluid" id="cda-patient">
      <xsl:for-each select="/n1:ClinicalDocument/n1:recordTarget/n1:patientRole">
        <xsl:if test="not(n1:id/@nullFlavor)">
          <div class="patient-heading container-fluid">
            <div class="patient-name row">
              <xsl:call-template name="show-name">
                <xsl:with-param name="name" select="n1:patient/n1:name"/>
              </xsl:call-template>
            </div>
            <div class="patient-identifier container-fluid">
              <div class="attribute-title row">Patient Identifiers</div>
              <xsl:for-each select="n1:id">
                <div class="row">
                  <div class="col-md-6 patient-id">
                    <xsl:call-template name="show-id"/>
                  </div>
                </div>
              </xsl:for-each>
            </div>
          </div>
          <div class="patient-info container-fluid">
            <div class="col-md-6">
              <h2 class="section-title col-md-6">About</h2>
              <div class="header-group-content col-md-8">
                <div class="row">
                  <div class="attribute-title col-md-6">
                    <xsl:text>Date of Birth</xsl:text>
                  </div>
                  <div class="col-md-6">
                    <xsl:call-template name="show-time">
                      <xsl:with-param name="datetime" select="n1:patient/n1:birthTime"/>
                    </xsl:call-template>
                  </div>
                </div>
                <div class="row">
                  <div class="attribute-title col-md-6">
                    <xsl:text>Sex</xsl:text>
                  </div>
                  <div class="col-md-6">
                    <xsl:for-each select="n1:patient/n1:administrativeGenderCode">
                      <xsl:call-template name="show-gender"/>
                    </xsl:for-each>
                  </div>
                </div>
                <xsl:if test="n1:patient/n1:raceCode | (n1:patient/n1:ethnicGroupCode)">
                  <div class="row">
                    <div class="attribute-title col-md-6">
                      <xsl:text>Race</xsl:text>
                    </div>
                    <div class="col-md-6">
                      <xsl:choose>
                        <xsl:when test="n1:patient/n1:raceCode">
                          <xsl:for-each select="n1:patient/n1:raceCode">
                            <xsl:call-template name="show-race-ethnicity"/>
                          </xsl:for-each>
                        </xsl:when>
                        <xsl:otherwise>
                          <span class="generated-text">
                            <xsl:text>Information not available</xsl:text>
                          </span>
                        </xsl:otherwise>
                      </xsl:choose>
                    </div>
                  </div>
                  <div class="row">
                    <div class="attribute-title col-md-6">
                      <xsl:text>Ethnicity</xsl:text>
                    </div>
                    <div class="col-md-6">
                      <xsl:choose>
                        <xsl:when test="n1:patient/n1:ethnicGroupCode">
                          <xsl:for-each select="n1:patient/n1:ethnicGroupCode">
                            <xsl:call-template name="show-race-ethnicity"/>
                          </xsl:for-each>
                        </xsl:when>
                        <xsl:otherwise>
                          <span class="generated-text">
                            <xsl:text>Information not available</xsl:text>
                          </span>
                        </xsl:otherwise>
                      </xsl:choose>
                    </div>
                  </div>
                </xsl:if>
              </div>
            </div>
            <div class="col-md-6">
              <h2 class="section-title col-md-6">
                <xsl:text>Contact</xsl:text>
              </h2>
              <div class="header-group-content col-md-8">
                <xsl:call-template name="show-contactInfo">
                  <xsl:with-param name="contact" select="."/>
                </xsl:call-template>
              </div>
            </div>
          </div>
        </xsl:if>
      </xsl:for-each>
      <!-- list all the emergency contacts -->
      <xsl:if test="n1:participant">
        <xsl:for-each select="n1:participant">
          <xsl:if test="n1:associatedEntity/@classCode = 'ECON'">
            <div class="container-fluid" id="emergency-contact">
              <div class="col-md-6">
                <h2 class="section-title col-md-6">Emergency Contact</h2>
                <div class="header-group-content col-md-8">
                  <xsl:call-template name="show-associatedEntity">
                    <xsl:with-param name="assoEntity" select="n1:associatedEntity"/>
                  </xsl:call-template>
                </div>
              </div>
              <div class="col-md-6">
                <h2 class="section-title col-md-6">Contact</h2>
                <div class="header-group-content col-md-8">
                  <xsl:call-template name="show-contactInfo">
                    <xsl:with-param name="contact" select="n1:associatedEntity"/>
                  </xsl:call-template>
                </div>
              </div>
            </div>
          </xsl:if>
        </xsl:for-each>
      </xsl:if>

      <!-- list nex of kin-->
      <xsl:if test="n1:participant">
        <xsl:for-each select="n1:participant">
          <xsl:if test="n1:associatedEntity/@classCode = 'NOK'">
            <div class="container-fluid" id="emergency-contact">
              <div class="col-md-6">
                <h2 class="section-title col-md-6">Next of Kin</h2>
                <div class="header-group-content col-md-8">
                  <xsl:call-template name="show-associatedEntity">
                    <xsl:with-param name="assoEntity" select="n1:associatedEntity"/>
                  </xsl:call-template>
                </div>
              </div>
              <div class="col-md-6">
                <h2 class="section-title col-md-6">Contact</h2>
                <div class="header-group-content col-md-8">
                  <xsl:call-template name="show-contactInfo">
                    <xsl:with-param name="contact" select="n1:associatedEntity"/>
                  </xsl:call-template>
                </div>
              </div>
            </div>
          </xsl:if>
        </xsl:for-each>
      </xsl:if>
    </div>

  </xsl:template>
  <!-- relatedDocument -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="relatedDocument">
    <xsl:if test="n1:relatedDocument">
      <table class="header_table">
        <tbody>
          <xsl:for-each select="n1:relatedDocument">
            <tr>
              <td class="td_header_role_name">
                <span class="td_label">
                  <xsl:text>Related document</xsl:text>
                </span>
              </td>
              <td class="td_header_role_value">
                <xsl:for-each select="n1:parentDocument">
                  <xsl:for-each select="n1:id">
                    <xsl:call-template name="show-id"/>
                    <br/>
                  </xsl:for-each>
                </xsl:for-each>
              </td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table>
    </xsl:if>
  </xsl:template>
  <!-- authorization (consent) -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="authorization">
    <xsl:if test="n1:authorization">
      <table class="header_table">
        <tbody>
          <xsl:for-each select="n1:authorization">
            <tr>
              <td class="td_header_role_name">
                <span class="td_label">
                  <xsl:text>Consent</xsl:text>
                </span>
              </td>
              <td class="td_header_role_value">
                <xsl:choose>
                  <xsl:when test="n1:consent/n1:code">
                    <xsl:call-template name="show-code">
                      <xsl:with-param name="code" select="n1:consent/n1:code"/>
                    </xsl:call-template>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:call-template name="show-code">
                      <xsl:with-param name="code" select="n1:consent/n1:statusCode"/>
                    </xsl:call-template>
                  </xsl:otherwise>
                </xsl:choose>
                <br/>
              </td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table>
    </xsl:if>
  </xsl:template>
  <!-- setAndVersion -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="setAndVersion">
    <xsl:if test="n1:setId and n1:versionNumber">
      <table class="header_table">
        <tbody>
          <tr>
            <td class="td_header_role_name">
              <xsl:text>SetId and Version</xsl:text>
            </td>
            <td class="td_header_role_value">
              <xsl:text>SetId: </xsl:text>
              <xsl:call-template name="show-id">
                <xsl:with-param name="id" select="n1:setId"/>
              </xsl:call-template>
              <xsl:text>  Version: </xsl:text>
              <xsl:value-of select="n1:versionNumber/@value"/>
            </td>
          </tr>
        </tbody>
      </table>
    </xsl:if>
  </xsl:template>
  <!-- show StructuredBody  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:component/n1:structuredBody">
    <xsl:for-each select="n1:component/n1:section">
      <xsl:call-template name="section"/>
    </xsl:for-each>
  </xsl:template>
  <!-- show nonXMLBody -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:component/n1:nonXMLBody">
    <xsl:choose>
      <!-- if there is a reference, use that in an IFRAME -->
      <xsl:when test="n1:text/n1:reference">
        <xsl:variable name="source" select="string(n1:text/n1:reference/@value)"/>
        <xsl:variable name="mediaType" select="string(n1:text/@mediaType)"/>
        <xsl:variable name="lcSource" select="translate($source, $uc, $lc)"/>
        <xsl:variable name="scrubbedSource" select="translate($source, $simple-sanitizer-match, $simple-sanitizer-replace)"/>
        <xsl:message>
<xsl:value-of select="$source"/>, <xsl:value-of select="$lcSource"/>
</xsl:message>
        <xsl:choose>
          <xsl:when test="contains($lcSource, 'javascript')">
            <p>
              <xsl:value-of select="$javascript-injection-warning"/>
            </p>
            <xsl:message>
              <xsl:value-of select="$javascript-injection-warning"/>
            </xsl:message>
          </xsl:when>
          <xsl:when test="not($source = $scrubbedSource)">
            <p>
              <xsl:value-of select="$malicious-content-warning"/>
            </p>
            <xsl:message>
              <xsl:value-of select="$malicious-content-warning"/>
            </xsl:message>
          </xsl:when>
          <xsl:otherwise>
            <iframe name="nonXMLBody" id="nonXMLBody" WIDTH="80%" HEIGHT="600" src="{$source}">
              <html>
                <body>
                  <object data="{$source}" type="{$mediaType}">
                    <embed src="{$source}" type="{$mediaType}"/>
                  </object>
                </body>
              </html>
            </iframe>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:when test="n1:text/@mediaType = &quot;text/plain&quot;">
        <pre>
<xsl:value-of select="n1:text/text()"/>
</pre>
      </xsl:when>
      <xsl:otherwise>
        <pre>Cannot display the text</pre>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- top level component/section: display title and text,
      and process any nested component/sections
    -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="section">
    <div class="container-fluid header">
      <xsl:call-template name="section-title">
        <xsl:with-param name="title" select="n1:title"/>
      </xsl:call-template>
      <xsl:call-template name="section-author"/>
      <xsl:call-template name="section-text"/>
      <xsl:for-each select="n1:component/n1:section">
        <div class="container-fluid">
          <xsl:call-template name="nestedSection">
            <xsl:with-param name="margin" select="2"/>
          </xsl:call-template>
        </div>
      </xsl:for-each>
    </div>
  </xsl:template>
  <!-- top level section title -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="section-title">
    <xsl:param name="title"/>
    <h1 class="section-title" id="{generate-id($title)}" ng-click="gotoAnchor('toc')">
      <xsl:value-of select="$title"/>
    </h1>
  </xsl:template>

  <!-- section author -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="section-author">
    <xsl:if test="count(n1:author) &gt; 0">
      <div class="section-author">
        <span class="emphasis">
          <xsl:text>Section Author: </xsl:text>
        </span>
        <xsl:for-each select="n1:author/n1:assignedAuthor">
          <xsl:choose>
            <xsl:when test="n1:assignedPerson/n1:name">
              <xsl:call-template name="show-name">
                <xsl:with-param name="name" select="n1:assignedPerson/n1:name"/>
              </xsl:call-template>
              <xsl:if test="n1:representedOrganization">
                <xsl:text>, </xsl:text>
                <xsl:call-template name="show-name">
                  <xsl:with-param name="name" select="n1:representedOrganization/n1:name"/>
                </xsl:call-template>
              </xsl:if>
            </xsl:when>
            <xsl:when test="n1:assignedAuthoringDevice/n1:softwareName">
              <xsl:call-template name="show-code">
                <xsl:with-param name="code" select="n1:assignedAuthoringDevice/n1:softwareName"/>
              </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
              <xsl:for-each select="n1:id">
                <xsl:call-template name="show-id"/>
                <br/>
              </xsl:for-each>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each>
        <br/>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- top-level section Text   -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="section-text">
    <div class="section-text">
      <xsl:apply-templates select="n1:text"/>
    </div>
  </xsl:template>
  <!-- nested component/section -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="nestedSection">
    <xsl:param name="margin"/>
    <h4>
      <xsl:value-of select="n1:title"/>
    </h4>
    <div class="nested-section" style="margin-left : {$margin}em;">
      <xsl:apply-templates select="n1:text"/>
    </div>
    <xsl:for-each select="n1:component/n1:section">
      <xsl:call-template name="nestedSection">
        <xsl:with-param name="margin" select="2 * $margin"/>
      </xsl:call-template>
    </xsl:for-each>
  </xsl:template>
  <!--   paragraph  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:paragraph">
    <xsl:element name="p">
      <xsl:call-template name="output-attrs"/>
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>
  <!--   pre format  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:pre">
    <xsl:element name="pre">
      <xsl:call-template name="output-attrs"/>
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>
  <!--   Content w/ deleted text is hidden -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:content[@revised = 'delete']"/>
  <!--   content  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:content">
    <xsl:element name="content">
      <xsl:call-template name="output-attrs"/>
      <!--<xsl:apply-templates select="@styleCode"/>-->
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>
  <!-- line break -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:br">
    <xsl:element name="br">
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>
  <!--   list  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:list">
    <xsl:if test="n1:caption">
      <p>
        <b>
          <xsl:apply-templates select="n1:caption"/>
        </b>
      </p>
    </xsl:if>
    <ul>
      <xsl:for-each select="n1:item">
        <li>
          <xsl:apply-templates/>
        </li>
      </xsl:for-each>
    </ul>
  </xsl:template>
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:list[@styleCode='none']">
    <xsl:if test="n1:caption">
      <p>
        <b>
          <xsl:apply-templates select="n1:caption"/>
        </b>
      </p>
    </xsl:if>
    <ul style="list-style-type:none">
      <xsl:for-each select="n1:item">
        <li>
          <xsl:apply-templates/>
        </li>
      </xsl:for-each>
    </ul>
  </xsl:template>
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:list[@listType = 'ordered']">
    <xsl:if test="n1:caption">
      <span style="font-weight:bold; ">
        <xsl:apply-templates select="n1:caption"/>
      </span>
    </xsl:if>
    <ol>
      <xsl:for-each select="n1:item">
        <li>
          <xsl:apply-templates/>
        </li>
      </xsl:for-each>
    </ol>
  </xsl:template>
  
  <!--   caption  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:caption">
    <xsl:apply-templates/>
    <xsl:text>: </xsl:text>
  </xsl:template>
  <!--  Tables   -->

  <xsl:variable xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="table-elem-attrs">
    <in:tableElems>
      <in:elem name="table">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="summary"/>
        <in:attr name="width"/>
        <!-- Commented out to keep table rendering consistent -->
        <!--<in:attr name="border"/>-->
        <in:attr name="frame"/>
        <in:attr name="rules"/>
        <in:attr name="cellspacing"/>
        <in:attr name="cellpadding"/>
      </in:elem>
      <in:elem name="thead">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
      <in:elem name="tfoot">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
      <in:elem name="tbody">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
      <in:elem name="colgroup">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="span"/>
        <in:attr name="width"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
      <in:elem name="col">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="span"/>
        <in:attr name="width"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
      <in:elem name="tr">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
      <in:elem name="th">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="abbr"/>
        <in:attr name="axis"/>
        <in:attr name="headers"/>
        <in:attr name="scope"/>
        <in:attr name="rowspan"/>
        <in:attr name="colspan"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
      <in:elem name="td">
        <in:attr name="ID"/>
        <in:attr name="language"/>
        <in:attr name="styleCode"/>
        <in:attr name="abbr"/>
        <in:attr name="axis"/>
        <in:attr name="headers"/>
        <in:attr name="scope"/>
        <in:attr name="rowspan"/>
        <in:attr name="colspan"/>
        <in:attr name="align"/>
        <in:attr name="char"/>
        <in:attr name="charoff"/>
        <in:attr name="valign"/>
      </in:elem>
    </in:tableElems>
  </xsl:variable>

  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="output-attrs">
    <xsl:variable name="elem-name" select="local-name(.)"/>
    <!-- This assigns all outputted elements the cda-render class -->
    <!-- <xsl:attribute name="class">cda-render</xsl:attribute>-->
    <xsl:choose>
      <xsl:when test="$elem-name = 'table'">
        <xsl:attribute name="class">table table-striped table-hover</xsl:attribute>
      </xsl:when>
    </xsl:choose>
    <xsl:for-each select="@*">
      <xsl:variable name="attr-name" select="local-name(.)"/>
      <xsl:variable name="source" select="."/>
      <xsl:variable name="lcSource" select="translate($source, $uc, $lc)"/>
      <xsl:variable name="scrubbedSource" select="translate($source, $simple-sanitizer-match, $simple-sanitizer-replace)"/>
      <xsl:choose>
        <xsl:when test="contains($lcSource, 'javascript')">
          <p>
            <xsl:value-of select="$javascript-injection-warning"/>
          </p>
          <xsl:message terminate="yes">
            <xsl:value-of select="$javascript-injection-warning"/>
          </xsl:message>
        </xsl:when>
        <xsl:when test="$attr-name = 'styleCode'">
          <xsl:apply-templates select="."/>
        </xsl:when>
        <!--<xsl:when
          test="not(document('')/xsl:stylesheet/xsl:variable[@name = 'table-elem-attrs']/in:tableElems/in:elem[@name = $elem-name]/in:attr[@name = $attr-name])">
          <xsl:message><xsl:value-of select="$attr-name"/> is not legal in <xsl:value-of
              select="$elem-name"/></xsl:message>
        </xsl:when>-->
        <xsl:when test="not($source = $scrubbedSource)">
          <p>
            <xsl:value-of select="$malicious-content-warning"/>
          </p>
          <xsl:message>
            <xsl:value-of select="$malicious-content-warning"/>
          </xsl:message>
        </xsl:when>
        <xsl:otherwise>
          <xsl:copy-of select="."/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>

  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:table">
    <div class="table-responsive">
      <xsl:element name="{local-name()}">
        <xsl:call-template name="output-attrs"/>
        <xsl:apply-templates/>
      </xsl:element>
    </div>
  </xsl:template>

  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:thead | n1:tfoot | n1:tbody | n1:colgroup | n1:col | n1:tr | n1:th | n1:td">
    <xsl:element name="{local-name()}">
      <xsl:call-template name="output-attrs"/>
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:table/n1:caption">
    <span style="font-weight:bold; ">
      <xsl:apply-templates/>
    </span>
  </xsl:template>

  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:linkHtml">
    <xsl:element name="a">
      <xsl:copy-of select="@* | text()"/>
    </xsl:element>
  </xsl:template>

  <!--   RenderMultiMedia
     this currently only handles GIF's and JPEG's.  It could, however,
     be extended by including other image MIME types in the predicate
     and/or by generating <object> or <applet> tag with the correct
     params depending on the media type  @ID  =$imageRef  referencedObject
     -->

  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="check-external-image-whitelist">
    <xsl:param name="current-whitelist"/>
    <xsl:param name="image-uri"/>
    <xsl:choose>
      <xsl:when test="string-length($current-whitelist) &gt; 0">
        <xsl:variable name="whitelist-item">
          <xsl:choose>
            <xsl:when test="contains($current-whitelist, '|')">
              <xsl:value-of select="substring-before($current-whitelist, '|')"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="$current-whitelist"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:choose>
          <xsl:when test="starts-with($image-uri, $whitelist-item)">
            <br clear="all"/>
            <xsl:element name="img">
              <xsl:attribute name="src">
                <xsl:value-of select="$image-uri"/>
              </xsl:attribute>
            </xsl:element>
            <xsl:message>
<xsl:value-of select="$image-uri"/> is in the whitelist</xsl:message>
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="check-external-image-whitelist">
              <xsl:with-param name="current-whitelist" select="substring-after($current-whitelist, '|')"/>
              <xsl:with-param name="image-uri" select="$image-uri"/>
            </xsl:call-template>
          </xsl:otherwise>
        </xsl:choose>

      </xsl:when>
      <xsl:otherwise>
        <p>WARNING: non-local image found <xsl:value-of select="$image-uri"/>. Removing. If you wish
          non-local images preserved please set the limit-external-images param to 'no'.</p>
        <xsl:message>WARNING: non-local image found <xsl:value-of select="$image-uri"/>. Removing.
          If you wish non-local images preserved please set the limit-external-images param to
          'no'.</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>


  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:renderMultiMedia">
    <xsl:variable name="imageRef" select="@referencedObject"/>
    <xsl:choose>
      <xsl:when test="//n1:regionOfInterest[@ID = $imageRef]">
        <!-- Here is where the Region of Interest image referencing goes -->
        <xsl:if test="             //n1:regionOfInterest[@ID = $imageRef]//n1:observationMedia/n1:value[@mediaType = 'image/gif' or             @mediaType = 'image/jpeg']">
          <xsl:variable name="image-uri" select="//n1:regionOfInterest[@ID = $imageRef]//n1:observationMedia/n1:value/n1:reference/@value"/>

          <xsl:choose>
            <xsl:when test="$limit-external-images = 'yes' and (contains($image-uri, ':') or starts-with($image-uri, '\\'))">
              <xsl:call-template name="check-external-image-whitelist">
                <xsl:with-param name="current-whitelist" select="$external-image-whitelist"/>
                <xsl:with-param name="image-uri" select="$image-uri"/>
              </xsl:call-template>
              <!--
                            <p>WARNING: non-local image found <xsl:value-of select="$image-uri"/>. Removing. If you wish non-local images preserved please set the limit-external-images param to 'no'.</p>
                            <xsl:message>WARNING: non-local image found <xsl:value-of select="$image-uri"/>. Removing. If you wish non-local images preserved please set the limit-external-images param to 'no'.</xsl:message>
                            -->
            </xsl:when>
            <!--
                        <xsl:when test="$limit-external-images='yes' and starts-with($image-uri,'\\')">
                            <p>WARNING: non-local image found <xsl:value-of select="$image-uri"/></p>
                            <xsl:message>WARNING: non-local image found <xsl:value-of select="$image-uri"/>. Removing. If you wish non-local images preserved please set the limit-external-images param to 'no'.</xsl:message>
                        </xsl:when>
                        -->
            <xsl:otherwise>
              <br clear="all"/>
              <xsl:element name="img">
                <xsl:attribute name="src">
                  <xsl:value-of select="$image-uri"/>
                </xsl:attribute>
              </xsl:element>
            </xsl:otherwise>
          </xsl:choose>

        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <!-- Here is where the direct MultiMedia image referencing goes -->
        <xsl:if test="//n1:observationMedia[@ID = $imageRef]/n1:value[@mediaType = 'image/gif' or @mediaType = 'image/jpeg']">
          <br clear="all"/>
          <xsl:element name="img">
            <xsl:attribute name="src">
              <xsl:value-of select="//n1:observationMedia[@ID = $imageRef]/n1:value/n1:reference/@value"/>
            </xsl:attribute>
          </xsl:element>
        </xsl:if>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--    Stylecode processing
     Supports Bold, Underline and Italics display
     -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="@styleCode">
    <xsl:attribute name="styleCode">
      <xsl:value-of select="."/>
    </xsl:attribute>
  </xsl:template>
  <!--    Superscript or Subscript   -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:sup">
    <xsl:element name="sup">
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" match="n1:sub">
    <xsl:element name="sub">
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>
  <!-- show-signature -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-sig">
    <xsl:param name="sig"/>
    <xsl:choose>
      <xsl:when test="$sig/@code = 'S'">
        <xsl:text>signed</xsl:text>
      </xsl:when>
      <xsl:when test="$sig/@code = 'I'">
        <xsl:text>intended</xsl:text>
      </xsl:when>
      <xsl:when test="$sig/@code = 'X'">
        <xsl:text>signature required</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!--  show-id -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-id">
    <xsl:param name="id" select="."/>
    <xsl:choose>
      <xsl:when test="not($id)">
        <xsl:if test="not(@nullFlavor)">
          <xsl:if test="@extension">
            <xsl:value-of select="@extension"/>
          </xsl:if>
          <xsl:text> </xsl:text>
          <xsl:call-template name="translate-id-type">
            <xsl:with-param name="id-oid" select="@root"/>
          </xsl:call-template>
        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <xsl:if test="not($id/@nullFlavor)">
          <xsl:if test="$id/@extension">
            <xsl:value-of select="$id/@extension"/>
          </xsl:if>
          <xsl:text> </xsl:text>
          <xsl:call-template name="translate-id-type">
            <xsl:with-param name="id-oid" select="$id/@root"/>
          </xsl:call-template>
        </xsl:if>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- show-name  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-name">
    <xsl:param name="name"/>
    <xsl:choose>
      <xsl:when test="$name/n1:family">
        <xsl:if test="$name/n1:prefix">
          <xsl:value-of select="$name/n1:prefix"/>
          <xsl:text> </xsl:text>
        </xsl:if>
        <xsl:value-of select="$name/n1:given"/>
        <xsl:text> </xsl:text>
        <xsl:value-of select="$name/n1:family"/>
        <xsl:if test="$name/n1:suffix">
          <xsl:text>, </xsl:text>
          <xsl:value-of select="$name/n1:suffix"/>
        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$name"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- show-gender  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-gender">
    <xsl:choose>
      <xsl:when test="@code = 'M' or @code = 'Male'">
        <xsl:text>Male</xsl:text>
      </xsl:when>
      <xsl:when test="@code = 'F' or @code = 'Female'">
        <xsl:text>Female</xsl:text>
      </xsl:when>
      <xsl:when test="@code = 'UN' or @code = 'Undifferentiated'">
        <xsl:text>Undifferentiated</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- show-race-ethnicity  -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-race-ethnicity">
    <xsl:choose>
      <xsl:when test="@displayName">
        <xsl:value-of select="@displayName"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="@code"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- show-contactInfo -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-contactInfo">
    <xsl:param name="contact"/>
    <xsl:call-template name="show-address">
      <xsl:with-param name="address" select="$contact/n1:addr"/>
    </xsl:call-template>
    <xsl:call-template name="show-telecom">
      <xsl:with-param name="telecom" select="$contact/n1:telecom"/>
    </xsl:call-template>
  </xsl:template>
  <!-- show-address -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-address">
    <xsl:param name="address"/>
    <div class="address-group">
      <xsl:choose>
        <xsl:when test="$address">
          <div class="adress-group-header">
            <xsl:if test="$address/@use">
              <xsl:call-template name="translateTelecomCode">
                <xsl:with-param name="code" select="$address/@use"/>
              </xsl:call-template>
            </xsl:if>
          </div>
          <div class="address-group-content">
            <p class="tight">
              <xsl:for-each select="$address/n1:streetAddressLine">
                <xsl:value-of select="."/>
                <xsl:text> </xsl:text>
              </xsl:for-each>
              <xsl:if test="$address/n1:streetName">
                <xsl:value-of select="$address/n1:streetName"/>
                <xsl:text> </xsl:text>
                <xsl:value-of select="$address/n1:houseNumber"/>
              </xsl:if>
            </p>
            <p class="tight">
              <xsl:if test="string-length($address/n1:city) &gt; 0">
                <xsl:value-of select="$address/n1:city"/>
              </xsl:if>
              <xsl:if test="string-length($address/n1:state) &gt; 0">
                <xsl:text>, </xsl:text>
                <xsl:value-of select="$address/n1:state"/>
              </xsl:if>
            </p>
            <p class="tight">
              <xsl:if test="string-length($address/n1:postalCode) &gt; 0">
                <!--<xsl:text>&#160;</xsl:text>-->
                <xsl:value-of select="$address/n1:postalCode"/>
              </xsl:if>
              <xsl:if test="string-length($address/n1:country) &gt; 0">
                <xsl:text>, </xsl:text>
                <xsl:value-of select="$address/n1:country"/>
              </xsl:if>
            </p>
          </div>

        </xsl:when>
        <xsl:otherwise>
          <div class="address-group-content">
            <span class="generated-text">
              <xsl:text>&lt;&gt;</xsl:text>
            </span>
          </div>
        </xsl:otherwise>
      </xsl:choose>
    </div>
  </xsl:template>
  <!-- show-telecom -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-telecom">
    <xsl:param name="telecom"/>
    <div class="address-group">
      <xsl:choose>
        <xsl:when test="$telecom">
          <xsl:variable name="type" select="substring-before($telecom/@value, ':')"/>
          <xsl:variable name="value" select="substring-after($telecom/@value, ':')"/>
          <xsl:if test="$type">
            <div class="address-group-header">
              <xsl:call-template name="translateTelecomCode">
                <xsl:with-param name="code" select="$type"/>
              </xsl:call-template>
              <xsl:text> : </xsl:text>
              <xsl:if test="@use">
                <xsl:text> (</xsl:text>
                <xsl:call-template name="translateTelecomCode">
                  <xsl:with-param name="code" select="@use"/>
                </xsl:call-template>
                <xsl:text>)</xsl:text>
              </xsl:if>
              <xsl:value-of select="$value"/>
            </div>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>&lt;&gt;</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </div>
  </xsl:template>
  <!-- show-recipientType -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-recipientType">
    <xsl:param name="typeCode"/>
    <xsl:choose>
      <xsl:when test="$typeCode = 'PRCP'">Primary Recipient:</xsl:when>
      <xsl:when test="$typeCode = 'TRC'">Secondary Recipient:</xsl:when>
      <xsl:otherwise>Recipient:</xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Convert Telecom URL to display text -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="translateTelecomCode">
    <xsl:param name="code"/>
    <!--xsl:value-of select="document('voc.xml')/systems/system[@root=$code/@codeSystem]/code[@value=$code/@code]/@displayName"/-->
    <!--xsl:value-of select="document('codes.xml')/*/code[@code=$code]/@display"/-->
    <xsl:choose>
      <!-- lookup table Telecom URI -->
      <xsl:when test="$code = 'tel'">
        <xsl:text>Tel</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'fax'">
        <xsl:text>Fax</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'http'">
        <xsl:text>Web</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'mailto'">
        <xsl:text>Mail</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'H'">
        <xsl:text>Home</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'url'">
        <xsl:text>URL</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'HV'">
        <xsl:text>Vacation Home</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'HP'">
        <xsl:text>Primary Home</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'WP'">
        <xsl:text>Work Place</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'PUB'">
        <xsl:text>Pub</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'TMP'">
        <xsl:text>Temporary</xsl:text>
      </xsl:when>
      <xsl:when test="$code = 'BAD'">
        <xsl:text>Bad or Old</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>{$code='</xsl:text>
        <xsl:value-of select="$code"/>
        <xsl:text>'?}</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- convert RoleClassAssociative code to display text -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="translateRoleAssoCode">
    <xsl:param name="classCode"/>
    <xsl:param name="code"/>
    <xsl:choose>
      <xsl:when test="$classCode = 'AFFL'">
        <xsl:text>affiliate</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'AGNT'">
        <xsl:text>agent</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'ASSIGNED'">
        <xsl:text>assigned entity</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'COMPAR'">
        <xsl:text>commissioning party</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'CON'">
        <xsl:text>contact</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'ECON'">
        <xsl:text>emergency contact</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'NOK'">
        <xsl:text>next of kin</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'SGNOFF'">
        <xsl:text>signing authority</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'GUARD'">
        <xsl:text>guardian</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'GUAR'">
        <xsl:text>guardian</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'CIT'">
        <xsl:text>citizen</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'COVPTY'">
        <xsl:text>covered party</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'PRS'">
        <xsl:text>personal relationship</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'CAREGIVER'">
        <xsl:text>care giver</xsl:text>
      </xsl:when>
      <xsl:when test="$classCode = 'PROV'">
        <xsl:text>healthcare provider</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>{$classCode='</xsl:text>
        <xsl:value-of select="$classCode"/>
        <xsl:text>'?}</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:if test="($code/@code) and ($code/@codeSystem = '2.16.840.1.113883.5.111')">
      <xsl:text> </xsl:text>
      <xsl:choose>
        <xsl:when test="$code/@code = 'FTH'">
          <xsl:text>(Father)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'MTH'">
          <xsl:text>(Mother)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'NPRN'">
          <xsl:text>(Natural parent)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'STPPRN'">
          <xsl:text>(Step parent)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'SONC'">
          <xsl:text>(Son)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'DAUC'">
          <xsl:text>(Daughter)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'CHILD'">
          <xsl:text>(Child)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'EXT'">
          <xsl:text>(Extended family member)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'NBOR'">
          <xsl:text>(Neighbor)</xsl:text>
        </xsl:when>
        <xsl:when test="$code/@code = 'SIGOTHR'">
          <xsl:text>(Significant other)</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>{$code/@code='</xsl:text>
          <xsl:value-of select="$code/@code"/>
          <xsl:text>'?}</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:if>
  </xsl:template>
  <!-- show time -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-time">
    <xsl:param name="datetime"/>
    <xsl:choose>
      <xsl:when test="not($datetime)">
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="@value"/>
        </xsl:call-template>
        <xsl:text> </xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="formatDateTime">
          <xsl:with-param name="date" select="$datetime/@value"/>
        </xsl:call-template>
        <xsl:text> </xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- paticipant facility and date -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="facilityAndDates">
    <table class="header_table">
      <tbody>
        <!-- facility id -->
        <tr>
          <td class="td_header_role_name">
            <span class="td_label">
              <xsl:text>Facility ID</xsl:text>
            </span>
          </td>
          <td class="td_header_role_value">
            <xsl:choose>
              <xsl:when test="                   count(/n1:ClinicalDocument/n1:participant                   [@typeCode = 'LOC'][@contextControlCode = 'OP']                   /n1:associatedEntity[@classCode = 'SDLOC']/n1:id) &gt; 0">
                <!-- change context node -->
                <xsl:for-each select="                     /n1:ClinicalDocument/n1:participant                     [@typeCode = 'LOC'][@contextControlCode = 'OP']                     /n1:associatedEntity[@classCode = 'SDLOC']/n1:id">
                  <xsl:call-template name="show-id"/>
                  <!-- change context node again, for the code -->
                  <xsl:for-each select="../n1:code">
                    <xsl:text> (</xsl:text>
                    <xsl:call-template name="show-code">
                      <xsl:with-param name="code" select="."/>
                    </xsl:call-template>
                    <xsl:text>)</xsl:text>
                  </xsl:for-each>
                </xsl:for-each>
              </xsl:when>
              <xsl:otherwise> Not available </xsl:otherwise>
            </xsl:choose>
          </td>
        </tr>
        <!-- Period reported -->
        <tr>
          <td class="td_header_role_name">
            <span class="td_label">
              <xsl:text>First day of period reported</xsl:text>
            </span>
          </td>
          <td class="td_header_role_value">
            <xsl:call-template name="show-time">
              <xsl:with-param name="datetime" select="                   /n1:ClinicalDocument/n1:documentationOf                   /n1:serviceEvent/n1:effectiveTime/n1:low"/>
            </xsl:call-template>
          </td>
        </tr>
        <tr>
          <td class="td_header_role_name">
            <span class="td_label">
              <xsl:text>Last day of period reported</xsl:text>
            </span>
          </td>
          <td class="td_header_role_value">
            <xsl:call-template name="show-time">
              <xsl:with-param name="datetime" select="                   /n1:ClinicalDocument/n1:documentationOf                   /n1:serviceEvent/n1:effectiveTime/n1:high"/>
            </xsl:call-template>
          </td>
        </tr>
      </tbody>
    </table>
  </xsl:template>
  <!-- show assignedEntity -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-assignedEntity">
    <xsl:param name="asgnEntity"/>
    <xsl:choose>
      <xsl:when test="$asgnEntity/n1:assignedPerson/n1:name">
        <xsl:call-template name="show-name">
          <xsl:with-param name="name" select="$asgnEntity/n1:assignedPerson/n1:name"/>
        </xsl:call-template>
        <xsl:if test="$asgnEntity/n1:representedOrganization/n1:name">
          <xsl:text> of </xsl:text>
          <xsl:value-of select="$asgnEntity/n1:representedOrganization/n1:name"/>
        </xsl:if>
      </xsl:when>
      <xsl:when test="$asgnEntity/n1:representedOrganization">
        <xsl:value-of select="$asgnEntity/n1:representedOrganization/n1:name"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:for-each select="$asgnEntity/n1:id">
          <xsl:call-template name="show-id"/>
          <xsl:choose>
            <xsl:when test="position() != last()">
              <xsl:text>, </xsl:text>
            </xsl:when>
            <xsl:otherwise>
              <br/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- show relatedEntity -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-relatedEntity">
    <xsl:param name="relatedEntity"/>
    <xsl:choose>
      <xsl:when test="$relatedEntity/n1:relatedPerson/n1:name">
        <xsl:call-template name="show-name">
          <xsl:with-param name="name" select="$relatedEntity/n1:relatedPerson/n1:name"/>
        </xsl:call-template>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- show associatedEntity -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-associatedEntity">
    <xsl:param name="assoEntity"/>
    <xsl:choose>
      <xsl:when test="$assoEntity/n1:associatedPerson">
        <xsl:for-each select="$assoEntity/n1:associatedPerson/n1:name">
          <xsl:call-template name="show-name">
            <xsl:with-param name="name" select="."/>
          </xsl:call-template>
        </xsl:for-each>
      </xsl:when>
      <xsl:when test="$assoEntity/n1:scopingOrganization">
        <xsl:for-each select="$assoEntity/n1:scopingOrganization">
          <xsl:if test="n1:name">
            <xsl:call-template name="show-name">
              <xsl:with-param name="name" select="n1:name"/>
            </xsl:call-template>
            <br/>
          </xsl:if>
          <xsl:if test="n1:standardIndustryClassCode">
            <xsl:value-of select="n1:standardIndustryClassCode/@displayName"/>
            <xsl:text> code:</xsl:text>
            <xsl:value-of select="n1:standardIndustryClassCode/@code"/>
          </xsl:if>
        </xsl:for-each>
      </xsl:when>
      <xsl:when test="$assoEntity/n1:code">
        <xsl:call-template name="show-code">
          <xsl:with-param name="code" select="$assoEntity/n1:code"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$assoEntity/n1:id">
        <xsl:value-of select="$assoEntity/n1:id/@extension"/>
        <xsl:text> </xsl:text>
        <xsl:value-of select="$assoEntity/n1:id/@root"/>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- show code
     if originalText present, return it, otherwise, check and return attribute: display name
     -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-code">
    <xsl:param name="code"/>
    <xsl:variable name="this-codeSystem">
      <xsl:value-of select="$code/@codeSystem"/>
    </xsl:variable>
    <xsl:variable name="this-code">
      <xsl:value-of select="$code/@code"/>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$code/n1:originalText">
        <xsl:value-of select="$code/n1:originalText"/>
      </xsl:when>
      <xsl:when test="$code/@displayName">
        <xsl:value-of select="$code/@displayName"/>
      </xsl:when>

      <xsl:otherwise>
        <xsl:value-of select="$this-code"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- show classCode -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-actClassCode">
    <xsl:param name="clsCode"/>
    <xsl:choose>
      <xsl:when test="$clsCode = 'ACT'">
        <xsl:text>healthcare service</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'ACCM'">
        <xsl:text>accommodation</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'ACCT'">
        <xsl:text>account</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'ACSN'">
        <xsl:text>accession</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'ADJUD'">
        <xsl:text>financial adjudication</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'CONS'">
        <xsl:text>consent</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'CONTREG'">
        <xsl:text>container registration</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'CTTEVENT'">
        <xsl:text>clinical trial timepoint event</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'DISPACT'">
        <xsl:text>disciplinary action</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'ENC'">
        <xsl:text>encounter</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'INC'">
        <xsl:text>incident</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'INFRM'">
        <xsl:text>inform</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'INVE'">
        <xsl:text>invoice element</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'LIST'">
        <xsl:text>working list</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'MPROT'">
        <xsl:text>monitoring program</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'PCPR'">
        <xsl:text>care provision</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'PROC'">
        <xsl:text>procedure</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'REG'">
        <xsl:text>registration</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'REV'">
        <xsl:text>review</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'SBADM'">
        <xsl:text>substance administration</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'SPCTRT'">
        <xsl:text>speciment treatment</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'SUBST'">
        <xsl:text>substitution</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'TRNS'">
        <xsl:text>transportation</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'VERIF'">
        <xsl:text>verification</xsl:text>
      </xsl:when>
      <xsl:when test="$clsCode = 'XACT'">
        <xsl:text>financial transaction</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- show participationType -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-participationType">
    <xsl:param name="ptype"/>
    <xsl:choose>
      <xsl:when test="$ptype = 'PPRF'">
        <xsl:text>primary performer</xsl:text>
      </xsl:when>
      <xsl:when test="$ptype = 'PRF'">
        <xsl:text>performer</xsl:text>
      </xsl:when>
      <xsl:when test="$ptype = 'VRF'">
        <xsl:text>verifier</xsl:text>
      </xsl:when>
      <xsl:when test="$ptype = 'SPRF'">
        <xsl:text>secondary performer</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- show participationFunction -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-participationFunction">
    <xsl:param name="pFunction"/>
    <xsl:choose>
      <!-- From the HL7 v3 ParticipationFunction code system -->
      <xsl:when test="$pFunction = 'ADMPHYS'">
        <xsl:text>(admitting physician)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'ANEST'">
        <xsl:text>(anesthesist)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'ANRS'">
        <xsl:text>(anesthesia nurse)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'ATTPHYS'">
        <xsl:text>(attending physician)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'DISPHYS'">
        <xsl:text>(discharging physician)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'FASST'">
        <xsl:text>(first assistant surgeon)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'MDWF'">
        <xsl:text>(midwife)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'NASST'">
        <xsl:text>(nurse assistant)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'PCP'">
        <xsl:text>(primary care physician)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'PRISURG'">
        <xsl:text>(primary surgeon)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'RNDPHYS'">
        <xsl:text>(rounding physician)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'SASST'">
        <xsl:text>(second assistant surgeon)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'SNRS'">
        <xsl:text>(scrub nurse)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'TASST'">
        <xsl:text>(third assistant)</xsl:text>
      </xsl:when>
      <!-- From the HL7 v2 Provider Role code system (2.16.840.1.113883.12.443) which is used by HITSP -->
      <xsl:when test="$pFunction = 'CP'">
        <xsl:text>(consulting provider)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'PP'">
        <xsl:text>(primary care provider)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'RP'">
        <xsl:text>(referring provider)</xsl:text>
      </xsl:when>
      <xsl:when test="$pFunction = 'MP'">
        <xsl:text>(medical home provider)</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="formatDateTime">
    <xsl:param name="date"/>
    <!-- month -->
    <xsl:variable name="month" select="substring($date, 5, 2)"/>
    <!-- day -->
    <xsl:value-of select="$month"/>
    <xsl:text>/</xsl:text>
    <xsl:choose>
      <xsl:when test="substring($date, 7, 1) = &quot;0&quot;">
        <xsl:value-of select="substring($date, 8, 1)"/>
        <xsl:text>/</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="substring($date, 7, 2)"/>
        <xsl:text>/</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
    <!-- year -->
    <xsl:value-of select="substring($date, 1, 4)"/>
    <!-- time and US timezone -->
    <xsl:if test="string-length($date) &gt; 8">
      <!-- time -->
      <xsl:variable name="time">
        <xsl:value-of select="substring($date, 9, 6)"/>
      </xsl:variable>
      <xsl:variable name="hh">
        <xsl:value-of select="substring($time, 1, 2)"/>
      </xsl:variable>
      <xsl:variable name="mm">
        <xsl:value-of select="substring($time, 3, 2)"/>
      </xsl:variable>
      <xsl:variable name="ss">
        <xsl:value-of select="substring($time, 5, 2)"/>
      </xsl:variable>
      <xsl:if test="(string-length($hh) &gt; 1 and not($hh = '00')) or (string-length($mm) &gt; 1 and not($mm = '00'))">
        <xsl:text>, </xsl:text>
        <xsl:value-of select="$hh"/>
        <xsl:if test="string-length($mm) &gt; 1 and not(contains($mm, '-')) and not(contains($mm, '+'))">
          <xsl:text>:</xsl:text>
          <xsl:value-of select="$mm"/>
        </xsl:if>
      </xsl:if>
    </xsl:if>
  </xsl:template>
  <!-- convert to lower case -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="caseDown">
    <xsl:param name="data"/>
    <xsl:if test="$data">
      <xsl:value-of select="translate($data, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')"/>
    </xsl:if>
  </xsl:template>
  <!-- convert to upper case -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="caseUp">
    <xsl:param name="data"/>
    <xsl:if test="$data">
      <xsl:value-of select="translate($data, 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/>
    </xsl:if>
  </xsl:template>
  <!-- convert first character to upper case -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="firstCharCaseUp">
    <xsl:param name="data"/>
    <xsl:if test="$data">
      <xsl:call-template name="caseUp">
        <xsl:with-param name="data" select="substring($data, 1, 1)"/>
      </xsl:call-template>
      <xsl:value-of select="substring($data, 2)"/>
    </xsl:if>
  </xsl:template>
  <!-- show-noneFlavor -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="show-noneFlavor">
    <xsl:param name="nf"/>
    <xsl:choose>
      <xsl:when test="$nf = 'NI'">
        <xsl:text>no information</xsl:text>
      </xsl:when>
      <xsl:when test="$nf = 'INV'">
        <xsl:text>invalid</xsl:text>
      </xsl:when>
      <xsl:when test="$nf = 'MSK'">
        <xsl:text>masked</xsl:text>
      </xsl:when>
      <xsl:when test="$nf = 'NA'">
        <xsl:text>not applicable</xsl:text>
      </xsl:when>
      <xsl:when test="$nf = 'UNK'">
        <xsl:text>unknown</xsl:text>
      </xsl:when>
      <xsl:when test="$nf = 'OTH'">
        <xsl:text>other</xsl:text>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

  <!-- convert common OIDs for Identifiers -->
  <xsl:template xmlns:n1="urn:hl7-org:v3" xmlns:in="urn:lantana-com:inline-variable-data" name="translate-id-type">
    <xsl:param name="id-oid"/>
    <xsl:choose>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.1'">
        <xsl:text>United States Social Security Number</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.6'">
        <xsl:text>United States National Provider Identifier</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.2'">
        <xsl:text>Alaska Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.1'">
        <xsl:text>Alabama Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.5'">
        <xsl:text>Arkansas Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.4'">
        <xsl:text>Arizona Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.6'">
        <xsl:text>California Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.8'">
        <xsl:text>Colorado Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.9'">
        <xsl:text>Connecticut Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.11'">
        <xsl:text>DC Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.10'">
        <xsl:text>Delaware Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.12'">
        <xsl:text>Florida Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.13'">
        <xsl:text>Georgia Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.15'">
        <xsl:text>Hawaii Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.18'">
        <xsl:text>Indiana Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.19'">
        <xsl:text>Iowa Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.16'">
        <xsl:text>Idaho Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.17'">
        <xsl:text>Illinois Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.20'">
        <xsl:text>Kansas Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.21'">
        <xsl:text>Kentucky Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.22'">
        <xsl:text>Louisiana Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.25'">
        <xsl:text>Massachusetts Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.24'">
        <xsl:text>Maryland Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.23'">
        <xsl:text>Maine Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.26'">
        <xsl:text>Michigan Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.27'">
        <xsl:text>Minnesota Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.29'">
        <xsl:text>Missouri Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.28'">
        <xsl:text>Mississippi Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.30'">
        <xsl:text>Montana Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.36'">
        <xsl:text>New York Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.37'">
        <xsl:text>North Carolina Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.38'">
        <xsl:text>North Dakota Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.31'">
        <xsl:text>Nebraska Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.33'">
        <xsl:text>New Hampshire Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.34'">
        <xsl:text>New Jersey Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.35'">
        <xsl:text>New Mexico Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.32'">
        <xsl:text>Nevada Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.39'">
        <xsl:text>Ohio Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.40'">
        <xsl:text>Oklahoma Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.41'">
        <xsl:text>Oregon Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.42'">
        <xsl:text>Pennsylvania Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.44'">
        <xsl:text>Rhode Island Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.45'">
        <xsl:text>South Carolina Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.46'">
        <xsl:text>South Dakota Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.47'">
        <xsl:text>Tennessee Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.48'">
        <xsl:text>Texas Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.49'">
        <xsl:text>Utah Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.51'">
        <xsl:text>Virginia Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.50'">
        <xsl:text>Vermont Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.53'">
        <xsl:text>Washington Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.55'">
        <xsl:text>Wisconsin Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.54'">
        <xsl:text>West Virginia Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.4.3.56'">
        <xsl:text>Wyoming Driver's License</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.12.203'">
        <xsl:text>Identifier Type (HL7)</xsl:text>
      </xsl:when>

      <!-- Axesson-specific OIDs -->
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.1'">
        <xsl:text>Associated Pathology Medical Group</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.2'">
        <xsl:text>ATMS</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.3'">
        <xsl:text>AXESSON TRANSCRIPTION</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.4'">
        <xsl:text>Axesson Word Doc Transcriptions</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.5'">
        <xsl:text>CrossTx</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.9'">
        <xsl:text>Dignity Health Medical Group</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.9.4.1'">
        <xsl:text>Dignity Boulder Creek</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.10'">
        <xsl:text>Dominican Santa Cruz Hospital</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.10.4.1'">
        <xsl:text>Dignity Internal Medicine</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.10.4.2'">
        <xsl:text>Dignity Pediatrics</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50023'">
        <xsl:text>Joydip Bhattacharya</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50003'">
        <xsl:text>Balance Health of Ben Lomond</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50040'">
        <xsl:text>Edward T Bradbury MD A Prof. Corp</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50014'">
        <xsl:text>Bayview Gastroenterology</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50037'">
        <xsl:text>Peggy Chen, M.D.</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50004'">
        <xsl:text>Central Coast Sleep Disorders Center</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50024'">
        <xsl:text>Central Coast Oncology and Hematology</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50002'">
        <xsl:text>Albert Crevello, MD</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50021'">
        <xsl:text>Diabetes Health Center</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50005'">
        <xsl:text>Foot Doctors of Santa Cruz</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50032'">
        <xsl:text>Maria Granthom, M.D.</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50006'">
        <xsl:text>Gastroenterology</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50030'">
        <xsl:text>Harbor Medical Group</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50033'">
        <xsl:text>Monterey Bay Gastroenterology</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50034'">
        <xsl:text>Monterey Bay Urology</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '1.2.840.114398.1.35.1'">
        <xsl:text>No More Clipboard</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50010'">
        <xsl:text>Plazita Medical Clinic</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50009'">
        <xsl:text>Pajaro Valley Neurolgy Medical Associates</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50007'">
        <xsl:text>Milan Patel, MD</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50039'">
        <xsl:text>Santa Cruz Pulmonary Medical Group</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50038'">
        <xsl:text>Rio Del Mar Medical Clinic</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50011'">
        <xsl:text>Romo, Mary-Lou</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50027'">
        <xsl:text>Santa Cruz Office Santa Cruz Ear Nose and Throat Medical Group</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50012'">
        <xsl:text>Scotts Valley Medical Clinic</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50041'">
        <xsl:text>Simkin, Josefa MD</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.3.1.50013'">
        <xsl:text>Vu, Thanh</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.6'">
        <xsl:text>Bioreference Labs</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.7'">
        <xsl:text>BSCA Claims Data</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.8'">
        <xsl:text>CCSDC</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.11'">
        <xsl:text>Cedar Medical Clinic</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.12'">
        <xsl:text>Cedar Medical Clinic</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.13'">
        <xsl:text>DIANON</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.14'">
        <xsl:text>ANDREA EDWARDS MD</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.15'">
        <xsl:text>Elysium</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.16'">
        <xsl:text>Family Doctors of Santa Cruz</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.17'">
        <xsl:text>Hurray, Alvie</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.18'">
        <xsl:text>Hunter</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.19'">
        <xsl:text>LABCORP</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.20'">
        <xsl:text>LABCORP UNKNOWN</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.21'">
        <xsl:text>Melissa Lopez-Bermejo, MD</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.22'">
        <xsl:text>Monterey Bay Family Physicians</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.23'">
        <xsl:text>Medtek</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.24'">
        <xsl:text>Mirth Support Testing Facility</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.25'">
        <xsl:text>NSIGHT</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.26'">
        <xsl:text>NwHIN</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.27'">
        <xsl:text>OrthoNorCal</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.28'">
        <xsl:text>Pajaro Health Center</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.29'">
        <xsl:text>Pajaro Valley Medical Clinic</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.30'">
        <xsl:text>Pajaro Valley Personal Health</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.31'">
        <xsl:text>PMG</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.32'">
        <xsl:text>QUEST</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.33'">
        <xsl:text>Radiology Medical Group</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.34'">
        <xsl:text>Resneck-Sannes, L. David MD</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.35'">
        <xsl:text>Salud Para La Gente</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.36'">
        <xsl:text>SBWTest</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.37'">
        <xsl:text>Quest Diagnostics SC</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.38'">
        <xsl:text>Santa Cruz County Health Services Agency</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.39'">
        <xsl:text>Santa Cruz County Mental Health</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.40'">
        <xsl:text>SCHIEAUTH</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.41'">
        <xsl:text>Santa Cruz HIE</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.42'">
        <xsl:text>Santa Cruz Nephrology Medical Group, Inc</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.43'">
        <xsl:text>Santa Cruz Surgery Center</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.44'">
        <xsl:text>Quest Diagnostics SJ</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.45'">
        <xsl:text>Stanford Lab</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.46'">
        <xsl:text>Unknown</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.47'">
        <xsl:text>Watsonville Community Hospital</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.3.290.2.1.48'">
        <xsl:text>zzBAD_REFERENCE_FACILITY</xsl:text>
      </xsl:when>



      <!-- Example OIDS -->
      <xsl:when test="$id-oid = '2.16.840.1.113883.19.5'">
        <xsl:text>Meaningless identifier, not to be used for any actual entities. Examples only.</xsl:text>
      </xsl:when>
      <xsl:when test="$id-oid = '2.16.840.1.113883.19.5.99999.2'">
        <xsl:text>Meaningless identifier, not to be used for any actual entities. Examples only.</xsl:text>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>OID: </xsl:text>
        <xsl:value-of select="$id-oid"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

<xsl:template xmlns:xs="http://www.w3.org/2001/XMLSchema" name="lantana-css">
    <style>
      /* Catch all for the document */
      .cda-render{
          font-family:CenturyGothic, sans-serif;
          /*font-size:1.25em;*/
      }

      /* One-off - CDA Document Title */
      .cda-render h1.cda-title{
        color:#b3623d;
        font-size:1.5em;
        font-weight:bold;
        text-align:center;
        text-transform: uppercase;
      }


      /* One-off - Table of contents formatting */
      .cda-render .toc-header-container {
        padding-top:0.5em;
        border-bottom-width:0.1em;
        border-bottom-style:solid;
        border-bottom-color:#b3623d;
        padding-bottom:0.5em;
      }
      
      .cda-render .toc-header {
        text-transform:uppercase;
        color:#b3623d;
        font-weight:bold;
      }
      
      .cda-render .toc {
        margin-top:3em;
        padding: 0px 15px;
      }

      .cda-render .toc-box {
        
      }


      /* One-off - Patient Name Formatting */
      .cda-render .patient-name {
        color:#336b7a;
        font-size:1.25em;
        font-weight:bold;
      }

     /* Patient ID Formatting */
     .patient-id {
       border-left-width: 0.15em;
       border-left-style: solid;
       border-left-color: #478B95;
     }
      /* Re-usable - Section-Title */
      .cda-render .section-title {
        color:#336b7a;
        font-size:1.09em;
        font-weight:bold;
        text-transform: uppercase;
      }

      /* Re-usable - Attribute title */
      .cda-render .attribute-title {
        color:#000000;
        font-weight:bold;
        font-size:1.04em;
      }


      /***** Header Grouping */
      .cda-render .header{
          border-bottom-width:0.1em;
          border-bottom-style:solid;
          border-bottom-color:#1B6373;
          padding-bottom:0.5em;
      }

      .cda-render .header-group-content{
          margin-left:1em;
          padding-left:0.5em;
          border-left-width:0.15em;
          border-left-style:solid;
          border-left-color:#478B95;
      }

      .cda-render .tight{
          margin:0;
      }
      .cda-render .generated-text{
          white-space:no-wrap;
          margin:0em;
          color:#B0592C;
          font-style:italic;
      }
      .cda-render .bottom{
          border-top-width:0.2em;
          border-top-color:#B0592C;
          border-top-style:solid;
      }

      /***** Table of Contents Attributes */
      /* Table of contents entry */
      .cda-render .lantana-toc {
        text-transform: uppercase;
      }
      
      .cda-render .bold {
        font-weight: bold;
      }

      .cda-render .active {
        border-right-color: #336b7a;
        border-right-style: solid;
        border-left-color: #336b7a;
        border-left-style: solid;
        background-color:#eee;
      }

      #navbar-list-cda {
        overflow: auto;
      }
    </style>
  </xsl:template>

<xsl:template xmlns:xs="http://www.w3.org/2001/XMLSchema" name="bootstrap-css">
        <style type="text/css">
            /*!
            * Bootstrap v3.3.5 (http://getbootstrap.com)
            * Copyright 2011-2015 Twitter, Inc.
            * Licensed under MIT (https://github.com/twbs/bootstrap/blob/master/LICENSE)
            *//*! normalize.css v3.0.3 | MIT License | github.com/necolas/normalize.css */
            <xsl:if test="2 &gt; 1">
                
html{font-family:sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%}body{margin:0}article,aside,details,figcaption,figure,footer,header,hgroup,main,menu,nav,section,summary{display:block}audio,canvas,progress,video{display:inline-block;vertical-align:baseline}audio:not([controls]){display:none;height:0}[hidden],template{display:none}a{background-color:transparent}a:active,a:hover{outline:0}abbr[title]{border-bottom:1px dotted}b,strong{font-weight:700}dfn{font-style:italic}h1{margin:.67em 0;font-size:2em}mark{color:#000;background:#ff0}small{font-size:80%}sub,sup{position:relative;font-size:75%;line-height:0;vertical-align:baseline}sup{top:-.5em}sub{bottom:-.25em}img{border:0}svg:not(:root){overflow:hidden}figure{margin:1em 40px}hr{height:0;-webkit-box-sizing:content-box;-moz-box-sizing:content-box;box-sizing:content-box}pre{overflow:auto}code,kbd,pre,samp{font-family:monospace,monospace;font-size:1em}button,input,optgroup,select,textarea{margin:0;font:inherit;color:inherit}button{overflow:visible}button,select{text-transform:none}button,html input[type=button],input[type=reset],input[type=submit]{-webkit-appearance:button;cursor:pointer}button[disabled],html input[disabled]{cursor:default}button::-moz-focus-inner,input::-moz-focus-inner{padding:0;border:0}input{line-height:normal}input[type=checkbox],input[type=radio]{-webkit-box-sizing:border-box;-moz-box-sizing:border-box;box-sizing:border-box;padding:0}input[type=number]::-webkit-inner-spin-button,input[type=number]::-webkit-outer-spin-button{height:auto}input[type=search]{-webkit-box-sizing:content-box;-moz-box-sizing:content-box;box-sizing:content-box;-webkit-appearance:textfield}input[type=search]::-webkit-search-cancel-button,input[type=search]::-webkit-search-decoration{-webkit-appearance:none}fieldset{padding:.35em .625em .75em;margin:0 2px;border:1px solid silver}legend{padding:0;border:0}textarea{overflow:auto}optgroup{font-weight:700}table{border-spacing:0;border-collapse:collapse}td,th{padding:0}/*! Source: https://github.com/h5bp/html5-boilerplate/blob/master/src/css/main.css */@media print{*,:after,:before{color:#000!important;text-shadow:none!important;background:0 0!important;-webkit-box-shadow:none!important;box-shadow:none!important}a,a:visited{text-decoration:underline}a[href]:after{content:" (" attr(href) ")"}abbr[title]:after{content:" (" attr(title) ")"}a[href^="javascript:"]:after,a[href^="#"]:after{content:""}blockquote,pre{border:1px solid #999;page-break-inside:avoid}thead{display:table-header-group}img,tr{page-break-inside:avoid}img{max-width:100%!important}h2,h3,p{orphans:3;widows:3}h2,h3{page-break-after:avoid}.navbar{display:none}.btn&gt;.caret,.dropup&gt;.btn&gt;.caret{border-top-color:#000!important}.label{border:1px solid #000}.table{border-collapse:collapse!important}.table td,.table th{background-color:#fff!important}.table-bordered td,.table-bordered th{border:1px solid #ddd!important}}@font-face{font-family:'Glyphicons Halflings';src:url(../fonts/glyphicons-halflings-regular.eot);src:url(../fonts/glyphicons-halflings-regular.eot?#iefix) format('embedded-opentype'),url(../fonts/glyphicons-halflings-regular.woff2) format('woff2'),url(../fonts/glyphicons-halflings-regular.woff) format('woff'),url(../fonts/glyphicons-halflings-regular.ttf) format('truetype'),url(../fonts/glyphicons-halflings-regular.svg#glyphicons_halflingsregular) format('svg')}.glyphicon{position:relative;top:1px;display:inline-block;font-family:'Glyphicons Halflings';font-style:normal;font-weight:400;line-height:1;-webkit-font-smoothing:antialiased;-moz-osx-font-smoothing:grayscale}.glyphicon-asterisk:before{content:"\2a"}.glyphicon-plus:before{content:"\2b"}.glyphicon-eur:before,.glyphicon-euro:before{content:"\20ac"}.glyphicon-minus:before{content:"\2212"}.glyphicon-cloud:before{content:"\2601"}.glyphicon-envelope:before{content:"\2709"}.glyphicon-pencil:before{content:"\270f"}.glyphicon-glass:before{content:"\e001"}.glyphicon-music:before{content:"\e002"}.glyphicon-search:before{content:"\e003"}.glyphicon-heart:before{content:"\e005"}.glyphicon-star:before{content:"\e006"}.glyphicon-star-empty:before{content:"\e007"}.glyphicon-user:before{content:"\e008"}.glyphicon-film:before{content:"\e009"}.glyphicon-th-large:before{content:"\e010"}.glyphicon-th:before{content:"\e011"}.glyphicon-th-list:before{content:"\e012"}.glyphicon-ok:before{content:"\e013"}.glyphicon-remove:before{content:"\e014"}.glyphicon-zoom-in:before{content:"\e015"}.glyphicon-zoom-out:before{content:"\e016"}.glyphicon-off:before{content:"\e017"}.glyphicon-signal:before{content:"\e018"}.glyphicon-cog:before{content:"\e019"}.glyphicon-trash:before{content:"\e020"}.glyphicon-home:before{content:"\e021"}.glyphicon-file:before{content:"\e022"}.glyphicon-time:before{content:"\e023"}.glyphicon-road:before{content:"\e024"}.glyphicon-download-alt:before{content:"\e025"}.glyphicon-download:before{content:"\e026"}.glyphicon-upload:before{content:"\e027"}.glyphicon-inbox:before{content:"\e028"}.glyphicon-play-circle:before{content:"\e029"}.glyphicon-repeat:before{content:"\e030"}.glyphicon-refresh:before{content:"\e031"}.glyphicon-list-alt:before{content:"\e032"}.glyphicon-lock:before{content:"\e033"}.glyphicon-flag:before{content:"\e034"}.glyphicon-headphones:before{content:"\e035"}.glyphicon-volume-off:before{content:"\e036"}.glyphicon-volume-down:before{content:"\e037"}.glyphicon-volume-up:before{content:"\e038"}.glyphicon-qrcode:before{content:"\e039"}.glyphicon-barcode:before{content:"\e040"}.glyphicon-tag:before{content:"\e041"}.glyphicon-tags:before{content:"\e042"}.glyphicon-book:before{content:"\e043"}.glyphicon-bookmark:before{content:"\e044"}.glyphicon-print:before{content:"\e045"}.glyphicon-camera:before{content:"\e046"}.glyphicon-font:before{content:"\e047"}.glyphicon-bold:before{content:"\e048"}.glyphicon-italic:before{content:"\e049"}.glyphicon-text-height:before{content:"\e050"}.glyphicon-text-width:before{content:"\e051"}.glyphicon-align-left:before{content:"\e052"}.glyphicon-align-center:before{content:"\e053"}.glyphicon-align-right:before{content:"\e054"}.glyphicon-align-justify:before{content:"\e055"}.glyphicon-list:before{content:"\e056"}.glyphicon-indent-left:before{content:"\e057"}.glyphicon-indent-right:before{content:"\e058"}.glyphicon-facetime-video:before{content:"\e059"}.glyphicon-picture:before{content:"\e060"}.glyphicon-map-marker:before{content:"\e062"}.glyphicon-adjust:before{content:"\e063"}.glyphicon-tint:before{content:"\e064"}.glyphicon-edit:before{content:"\e065"}.glyphicon-share:before{content:"\e066"}.glyphicon-check:before{content:"\e067"}.glyphicon-move:before{content:"\e068"}.glyphicon-step-backward:before{content:"\e069"}.glyphicon-fast-backward:before{content:"\e070"}.glyphicon-backward:before{content:"\e071"}.glyphicon-play:before{content:"\e072"}.glyphicon-pause:before{content:"\e073"}.glyphicon-stop:before{content:"\e074"}.glyphicon-forward:before{content:"\e075"}.glyphicon-fast-forward:before{content:"\e076"}.glyphicon-step-forward:before{content:"\e077"}.glyphicon-eject:before{content:"\e078"}.glyphicon-chevron-left:before{content:"\e079"}.glyphicon-chevron-right:before{content:"\e080"}.glyphicon-plus-sign:before{content:"\e081"}.glyphicon-minus-sign:before{content:"\e082"}.glyphicon-remove-sign:before{content:"\e083"}.glyphicon-ok-sign:before{content:"\e084"}.glyphicon-question-sign:before{content:"\e085"}.glyphicon-info-sign:before{content:"\e086"}.glyphicon-screenshot:before{content:"\e087"}.glyphicon-remove-circle:before{content:"\e088"}.glyphicon-ok-circle:before{content:"\e089"}.glyphicon-ban-circle:before{content:"\e090"}.glyphicon-arrow-left:before{content:"\e091"}.glyphicon-arrow-right:before{content:"\e092"}.glyphicon-arrow-up:before{content:"\e093"}.glyphicon-arrow-down:before{content:"\e094"}.glyphicon-share-alt:before{content:"\e095"}.glyphicon-resize-full:before{content:"\e096"}.glyphicon-resize-small:before{content:"\e097"}.glyphicon-exclamation-sign:before{content:"\e101"}.glyphicon-gift:before{content:"\e102"}.glyphicon-leaf:before{content:"\e103"}.glyphicon-fire:before{content:"\e104"}.glyphicon-eye-open:before{content:"\e105"}.glyphicon-eye-close:before{content:"\e106"}.glyphicon-warning-sign:before{content:"\e107"}.glyphicon-plane:before{content:"\e108"}.glyphicon-calendar:before{content:"\e109"}.glyphicon-random:before{content:"\e110"}.glyphicon-comment:before{content:"\e111"}.glyphicon-magnet:before{content:"\e112"}.glyphicon-chevron-up:before{content:"\e113"}.glyphicon-chevron-down:before{content:"\e114"}.glyphicon-retweet:before{content:"\e115"}.glyphicon-shopping-cart:before{content:"\e116"}.glyphicon-folder-close:before{content:"\e117"}.glyphicon-folder-open:before{content:"\e118"}.glyphicon-resize-vertical:before{content:"\e119"}.glyphicon-resize-horizontal:before{content:"\e120"}.glyphicon-hdd:before{content:"\e121"}.glyphicon-bullhorn:before{content:"\e122"}.glyphicon-bell:before{content:"\e123"}.glyphicon-certificate:before{content:"\e124"}.glyphicon-thumbs-up:before{content:"\e125"}.glyphicon-thumbs-down:before{content:"\e126"}.glyphicon-hand-right:before{content:"\e127"}.glyphicon-hand-left:before{content:"\e128"}.glyphicon-hand-up:before{content:"\e129"}.glyphicon-hand-down:before{content:"\e130"}.glyphicon-circle-arrow-right:before{content:"\e131"}.glyphicon-circle-arrow-left:before{content:"\e132"}.glyphicon-circle-arrow-up:before{content:"\e133"}.glyphicon-circle-arrow-down:before{content:"\e134"}.glyphicon-globe:before{content:"\e135"}.glyphicon-wrench:before{content:"\e136"}.glyphicon-tasks:before{content:"\e137"}.glyphicon-filter:before{content:"\e138"}.glyphicon-briefcase:before{content:"\e139"}.glyphicon-fullscreen:before{content:"\e140"}.glyphicon-dashboard:before{content:"\e141"}.glyphicon-paperclip:before{content:"\e142"}.glyphicon-heart-empty:before{content:"\e143"}.glyphicon-link:before{content:"\e144"}.glyphicon-phone:before{content:"\e145"}.glyphicon-pushpin:before{content:"\e146"}.glyphicon-usd:before{content:"\e148"}.glyphicon-gbp:before{content:"\e149"}.glyphicon-sort:before{content:"\e150"}.glyphicon-sort-by-alphabet:before{content:"\e151"}.glyphicon-sort-by-alphabet-alt:before{content:"\e152"}.glyphicon-sort-by-order:before{content:"\e153"}.glyphicon-sort-by-order-alt:before{content:"\e154"}.glyphicon-sort-by-attributes:before{content:"\e155"}.glyphicon-sort-by-attributes-alt:before{content:"\e156"}.glyphicon-unchecked:before{content:"\e157"}.glyphicon-expand:before{content:"\e158"}.glyphicon-collapse-down:before{content:"\e159"}.glyphicon-collapse-up:before{content:"\e160"}.glyphicon-log-in:before{content:"\e161"}.glyphicon-flash:before{content:"\e162"}.glyphicon-log-out:before{content:"\e163"}.glyphicon-new-window:before{content:"\e164"}.glyphicon-record:before{content:"\e165"}.glyphicon-save:before{content:"\e166"}.glyphicon-open:before{content:"\e167"}.glyphicon-saved:before{content:"\e168"}.glyphicon-import:before{content:"\e169"}.glyphicon-export:before{content:"\e170"}.glyphicon-send:before{content:"\e171"}.glyphicon-floppy-disk:before{content:"\e172"}.glyphicon-floppy-saved:before{content:"\e173"}.glyphicon-floppy-remove:before{content:"\e174"}.glyphicon-floppy-save:before{content:"\e175"}.glyphicon-floppy-open:before{content:"\e176"}.glyphicon-credit-card:before{content:"\e177"}.glyphicon-transfer:before{content:"\e178"}.glyphicon-cutlery:before{content:"\e179"}.glyphicon-header:before{content:"\e180"}.glyphicon-compressed:before{content:"\e181"}.glyphicon-earphone:before{content:"\e182"}.glyphicon-phone-alt:before{content:"\e183"}.glyphicon-tower:before{content:"\e184"}.glyphicon-stats:before{content:"\e185"}.glyphicon-sd-video:before{content:"\e186"}.glyphicon-hd-video:before{content:"\e187"}.glyphicon-subtitles:before{content:"\e188"}.glyphicon-sound-stereo:before{content:"\e189"}.glyphicon-sound-dolby:before{content:"\e190"}.glyphicon-sound-5-1:before{content:"\e191"}.glyphicon-sound-6-1:before{content:"\e192"}.glyphicon-sound-7-1:before{content:"\e193"}.glyphicon-copyright-mark:before{content:"\e194"}.glyphicon-registration-mark:before{content:"\e195"}.glyphicon-cloud-download:before{content:"\e197"}.glyphicon-cloud-upload:before{content:"\e198"}.glyphicon-tree-conifer:before{content:"\e199"}.glyphicon-tree-deciduous:before{content:"\e200"}.glyphicon-cd:before{content:"\e201"}.glyphicon-save-file:before{content:"\e202"}.glyphicon-open-file:before{content:"\e203"}.glyphicon-level-up:before{content:"\e204"}.glyphicon-copy:before{content:"\e205"}.glyphicon-paste:before{content:"\e206"}.glyphicon-alert:before{content:"\e209"}.glyphicon-equalizer:before{content:"\e210"}.glyphicon-king:before{content:"\e211"}.glyphicon-queen:before{content:"\e212"}.glyphicon-pawn:before{content:"\e213"}.glyphicon-bishop:before{content:"\e214"}.glyphicon-knight:before{content:"\e215"}.glyphicon-baby-formula:before{content:"\e216"}.glyphicon-tent:before{content:"\26fa"}.glyphicon-blackboard:before{content:"\e218"}.glyphicon-bed:before{content:"\e219"}.glyphicon-apple:before{content:"\f8ff"}.glyphicon-erase:before{content:"\e221"}.glyphicon-hourglass:before{content:"\231b"}.glyphicon-lamp:before{content:"\e223"}.glyphicon-duplicate:before{content:"\e224"}.glyphicon-piggy-bank:before{content:"\e225"}.glyphicon-scissors:before{content:"\e226"}.glyphicon-bitcoin:before{content:"\e227"}.glyphicon-btc:before{content:"\e227"}.glyphicon-xbt:before{content:"\e227"}.glyphicon-yen:before{content:"\00a5"}.glyphicon-jpy:before{content:"\00a5"}.glyphicon-ruble:before{content:"\20bd"}.glyphicon-rub:before{content:"\20bd"}.glyphicon-scale:before{content:"\e230"}.glyphicon-ice-lolly:before{content:"\e231"}.glyphicon-ice-lolly-tasted:before{content:"\e232"}.glyphicon-education:before{content:"\e233"}.glyphicon-option-horizontal:before{content:"\e234"}.glyphicon-option-vertical:before{content:"\e235"}.glyphicon-menu-hamburger:before{content:"\e236"}.glyphicon-modal-window:before{content:"\e237"}.glyphicon-oil:before{content:"\e238"}.glyphicon-grain:before{content:"\e239"}.glyphicon-sunglasses:before{content:"\e240"}.glyphicon-text-size:before{content:"\e241"}.glyphicon-text-color:before{content:"\e242"}.glyphicon-text-background:before{content:"\e243"}.glyphicon-object-align-top:before{content:"\e244"}.glyphicon-object-align-bottom:before{content:"\e245"}.glyphicon-object-align-horizontal:before{content:"\e246"}.glyphicon-object-align-left:before{content:"\e247"}.glyphicon-object-align-vertical:before{content:"\e248"}.glyphicon-object-align-right:before{content:"\e249"}.glyphicon-triangle-right:before{content:"\e250"}.glyphicon-triangle-left:before{content:"\e251"}.glyphicon-triangle-bottom:before{content:"\e252"}.glyphicon-triangle-top:before{content:"\e253"}.glyphicon-console:before{content:"\e254"}.glyphicon-superscript:before{content:"\e255"}.glyphicon-subscript:before{content:"\e256"}.glyphicon-menu-left:before{content:"\e257"}.glyphicon-menu-right:before{content:"\e258"}.glyphicon-menu-down:before{content:"\e259"}.glyphicon-menu-up:before{content:"\e260"}*{-webkit-box-sizing:border-box;-moz-box-sizing:border-box;box-sizing:border-box}:after,:before{-webkit-box-sizing:border-box;-moz-box-sizing:border-box;box-sizing:border-box}html{font-size:10px;-webkit-tap-highlight-color:rgba(0,0,0,0)}body{font-family:"Helvetica Neue",Helvetica,Arial,sans-serif;font-size:14px;line-height:1.42857143;color:#333;background-color:#fff}button,input,select,textarea{font-family:inherit;font-size:inherit;line-height:inherit}a{color:#337ab7;text-decoration:none}a:focus,a:hover{color:#23527c;text-decoration:underline}a:focus{outline:thin dotted;outline:5px auto -webkit-focus-ring-color;outline-offset:-2px}figure{margin:0}img{vertical-align:middle}.carousel-inner&gt;.item&gt;a&gt;img,.carousel-inner&gt;.item&gt;img,.img-responsive,.thumbnail a&gt;img,.thumbnail&gt;img{display:block;max-width:100%;height:auto}.img-rounded{border-radius:6px}.img-thumbnail{display:inline-block;max-width:100%;height:auto;padding:4px;line-height:1.42857143;background-color:#fff;border:1px solid #ddd;border-radius:4px;-webkit-transition:all .2s ease-in-out;-o-transition:all .2s ease-in-out;transition:all .2s ease-in-out}.img-circle{border-radius:50%}hr{margin-top:20px;margin-bottom:20px;border:0;border-top:1px solid #eee}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);border:0}.sr-only-focusable:active,.sr-only-focusable:focus{position:static;width:auto;height:auto;margin:0;overflow:visible;clip:auto}[role=button]{cursor:pointer}.h1,.h2,.h3,.h4,.h5,.h6,h1,h2,h3,h4,h5,h6{font-family:inherit;font-weight:500;line-height:1.1;color:inherit}.h1 .small,.h1 small,.h2 .small,.h2 small,.h3 .small,.h3 small,.h4 .small,.h4 small,.h5 .small,.h5 small,.h6 .small,.h6 small,h1 .small,h1 small,h2 .small,h2 small,h3 .small,h3 small,h4 .small,h4 small,h5 .small,h5 small,h6 .small,h6 small{font-weight:400;line-height:1;color:#777}.h1,.h2,.h3,h1,h2,h3{margin-top:20px;margin-bottom:10px}.h1 .small,.h1 small,.h2 .small,.h2 small,.h3 .small,.h3 small,h1 .small,h1 small,h2 .small,h2 small,h3 .small,h3 small{font-size:65%}.h4,.h5,.h6,h4,h5,h6{margin-top:10px;margin-bottom:10px}.h4 .small,.h4 small,.h5 .small,.h5 small,.h6 .small,.h6 small,h4 .small,h4 small,h5 .small,h5 small,h6 .small,h6 small{font-size:75%}.h1,h1{font-size:36px}.h2,h2{font-size:30px}.h3,h3{font-size:24px}.h4,h4{font-size:18px}.h5,h5{font-size:14px}.h6,h6{font-size:12px}p{margin:0 0 10px}.lead{margin-bottom:20px;font-size:16px;font-weight:300;line-height:1.4}@media (min-width:768px){.lead{font-size:21px}}.small,small{font-size:85%}.mark,mark{padding:.2em;background-color:#fcf8e3}.text-left{text-align:left}.text-right{text-align:right}.text-center{text-align:center}.text-justify{text-align:justify}.text-nowrap{white-space:nowrap}.text-lowercase{text-transform:lowercase}.text-uppercase{text-transform:uppercase}.text-capitalize{text-transform:capitalize}.text-muted{color:#777}.text-primary{color:#337ab7}a.text-primary:focus,a.text-primary:hover{color:#286090}.text-success{color:#3c763d}a.text-success:focus,a.text-success:hover{color:#2b542c}.text-info{color:#31708f}a.text-info:focus,a.text-info:hover{color:#245269}.text-warning{color:#8a6d3b}a.text-warning:focus,a.text-warning:hover{color:#66512c}.text-danger{color:#a94442}a.text-danger:focus,a.text-danger:hover{color:#843534}.bg-primary{color:#fff;background-color:#337ab7}a.bg-primary:focus,a.bg-primary:hover{background-color:#286090}.bg-success{background-color:#dff0d8}a.bg-success:focus,a.bg-success:hover{background-color:#c1e2b3}.bg-info{background-color:#d9edf7}a.bg-info:focus,a.bg-info:hover{background-color:#afd9ee}.bg-warning{background-color:#fcf8e3}a.bg-warning:focus,a.bg-warning:hover{background-color:#f7ecb5}.bg-danger{background-color:#f2dede}a.bg-danger:focus,a.bg-danger:hover{background-color:#e4b9b9}.page-header{padding-bottom:9px;margin:40px 0 20px;border-bottom:1px solid #eee}ol,ul{margin-top:0;margin-bottom:10px}ol ol,ol ul,ul ol,ul ul{margin-bottom:0}.list-unstyled{padding-left:0;list-style:none}.list-inline{padding-left:0;margin-left:-5px;list-style:none}.list-inline&gt;li{display:inline-block;padding-right:5px;padding-left:5px}dl{margin-top:0;margin-bottom:20px}dd,dt{line-height:1.42857143}dt{font-weight:700}dd{margin-left:0}@media (min-width:768px){.dl-horizontal dt{float:left;width:160px;overflow:hidden;clear:left;text-align:right;text-overflow:ellipsis;white-space:nowrap}.dl-horizontal dd{margin-left:180px}}abbr[data-original-title],abbr[title]{cursor:help;border-bottom:1px dotted #777}.initialism{font-size:90%;text-transform:uppercase}blockquote{padding:10px 20px;margin:0 0 20px;font-size:17.5px;border-left:5px solid #eee}blockquote ol:last-child,blockquote p:last-child,blockquote ul:last-child{margin-bottom:0}blockquote .small,blockquote footer,blockquote small{display:block;font-size:80%;line-height:1.42857143;color:#777}blockquote .small:before,blockquote footer:before,blockquote small:before{content:'\2014 \00A0'}.blockquote-reverse,blockquote.pull-right{padding-right:15px;padding-left:0;text-align:right;border-right:5px solid #eee;border-left:0}.blockquote-reverse .small:before,.blockquote-reverse footer:before,.blockquote-reverse small:before,blockquote.pull-right .small:before,blockquote.pull-right footer:before,blockquote.pull-right small:before{content:''}.blockquote-reverse .small:after,.blockquote-reverse footer:after,.blockquote-reverse small:after,blockquote.pull-right .small:after,blockquote.pull-right footer:after,blockquote.pull-right small:after{content:'\00A0 \2014'}address{margin-bottom:20px;font-style:normal;line-height:1.42857143}code,kbd,pre,samp{font-family:Menlo,Monaco,Consolas,"Courier New",monospace}code{padding:2px 4px;font-size:90%;color:#c7254e;background-color:#f9f2f4;border-radius:4px}kbd{padding:2px 4px;font-size:90%;color:#fff;background-color:#333;border-radius:3px;-webkit-box-shadow:inset 0 -1px 0 rgba(0,0,0,.25);box-shadow:inset 0 -1px 0 rgba(0,0,0,.25)}kbd kbd{padding:0;font-size:100%;font-weight:700;-webkit-box-shadow:none;box-shadow:none}pre{display:block;padding:9.5px;margin:0 0 10px;font-size:13px;line-height:1.42857143;color:#333;word-break:break-all;word-wrap:break-word;background-color:#f5f5f5;border:1px solid #ccc;border-radius:4px}pre code{padding:0;font-size:inherit;color:inherit;white-space:pre-wrap;background-color:transparent;border-radius:0}.pre-scrollable{max-height:340px;overflow-y:scroll}.container{padding-right:15px;padding-left:15px;margin-right:auto;margin-left:auto}@media (min-width:768px){.container{width:750px}}@media (min-width:992px){.container{width:970px}}@media (min-width:1200px){.container{width:1170px}}.container-fluid{padding-right:15px;padding-left:15px;margin-right:auto;margin-left:auto}.row{margin-right:-15px;margin-left:-15px}.col-lg-1,.col-lg-10,.col-lg-11,.col-lg-12,.col-lg-2,.col-lg-3,.col-lg-4,.col-lg-5,.col-lg-6,.col-lg-7,.col-lg-8,.col-lg-9,.col-md-1,.col-md-10,.col-md-11,.col-md-12,.col-md-2,.col-md-3,.col-md-4,.col-md-5,.col-md-6,.col-md-7,.col-md-8,.col-md-9,.col-sm-1,.col-sm-10,.col-sm-11,.col-sm-12,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-5,.col-sm-6,.col-sm-7,.col-sm-8,.col-sm-9,.col-xs-1,.col-xs-10,.col-xs-11,.col-xs-12,.col-xs-2,.col-xs-3,.col-xs-4,.col-xs-5,.col-xs-6,.col-xs-7,.col-xs-8,.col-xs-9{position:relative;min-height:1px;padding-right:15px;padding-left:15px}.col-xs-1,.col-xs-10,.col-xs-11,.col-xs-12,.col-xs-2,.col-xs-3,.col-xs-4,.col-xs-5,.col-xs-6,.col-xs-7,.col-xs-8,.col-xs-9{float:left}.col-xs-12{width:100%}.col-xs-11{width:91.66666667%}.col-xs-10{width:83.33333333%}.col-xs-9{width:75%}.col-xs-8{width:66.66666667%}.col-xs-7{width:58.33333333%}.col-xs-6{width:50%}.col-xs-5{width:41.66666667%}.col-xs-4{width:33.33333333%}.col-xs-3{width:25%}.col-xs-2{width:16.66666667%}.col-xs-1{width:8.33333333%}.col-xs-pull-12{right:100%}.col-xs-pull-11{right:91.66666667%}.col-xs-pull-10{right:83.33333333%}.col-xs-pull-9{right:75%}.col-xs-pull-8{right:66.66666667%}.col-xs-pull-7{right:58.33333333%}.col-xs-pull-6{right:50%}.col-xs-pull-5{right:41.66666667%}.col-xs-pull-4{right:33.33333333%}.col-xs-pull-3{right:25%}.col-xs-pull-2{right:16.66666667%}.col-xs-pull-1{right:8.33333333%}.col-xs-pull-0{right:auto}.col-xs-push-12{left:100%}.col-xs-push-11{left:91.66666667%}.col-xs-push-10{left:83.33333333%}.col-xs-push-9{left:75%}.col-xs-push-8{left:66.66666667%}.col-xs-push-7{left:58.33333333%}.col-xs-push-6{left:50%}.col-xs-push-5{left:41.66666667%}.col-xs-push-4{left:33.33333333%}.col-xs-push-3{left:25%}.col-xs-push-2{left:16.66666667%}.col-xs-push-1{left:8.33333333%}.col-xs-push-0{left:auto}.col-xs-offset-12{margin-left:100%}.col-xs-offset-11{margin-left:91.66666667%}.col-xs-offset-10{margin-left:83.33333333%}.col-xs-offset-9{margin-left:75%}.col-xs-offset-8{margin-left:66.66666667%}.col-xs-offset-7{margin-left:58.33333333%}.col-xs-offset-6{margin-left:50%}.col-xs-offset-5{margin-left:41.66666667%}.col-xs-offset-4{margin-left:33.33333333%}.col-xs-offset-3{margin-left:25%}.col-xs-offset-2{margin-left:16.66666667%}.col-xs-offset-1{margin-left:8.33333333%}.col-xs-offset-0{margin-left:0}@media (min-width:768px){.col-sm-1,.col-sm-10,.col-sm-11,.col-sm-12,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-5,.col-sm-6,.col-sm-7,.col-sm-8,.col-sm-9{float:left}.col-sm-12{width:100%}.col-sm-11{width:91.66666667%}.col-sm-10{width:83.33333333%}.col-sm-9{width:75%}.col-sm-8{width:66.66666667%}.col-sm-7{width:58.33333333%}.col-sm-6{width:50%}.col-sm-5{width:41.66666667%}.col-sm-4{width:33.33333333%}.col-sm-3{width:25%}.col-sm-2{width:16.66666667%}.col-sm-1{width:8.33333333%}.col-sm-pull-12{right:100%}.col-sm-pull-11{right:91.66666667%}.col-sm-pull-10{right:83.33333333%}.col-sm-pull-9{right:75%}.col-sm-pull-8{right:66.66666667%}.col-sm-pull-7{right:58.33333333%}.col-sm-pull-6{right:50%}.col-sm-pull-5{right:41.66666667%}.col-sm-pull-4{right:33.33333333%}.col-sm-pull-3{right:25%}.col-sm-pull-2{right:16.66666667%}.col-sm-pull-1{right:8.33333333%}.col-sm-pull-0{right:auto}.col-sm-push-12{left:100%}.col-sm-push-11{left:91.66666667%}.col-sm-push-10{left:83.33333333%}.col-sm-push-9{left:75%}.col-sm-push-8{left:66.66666667%}.col-sm-push-7{left:58.33333333%}.col-sm-push-6{left:50%}.col-sm-push-5{left:41.66666667%}.col-sm-push-4{left:33.33333333%}.col-sm-push-3{left:25%}.col-sm-push-2{left:16.66666667%}.col-sm-push-1{left:8.33333333%}.col-sm-push-0{left:auto}.col-sm-offset-12{margin-left:100%}.col-sm-offset-11{margin-left:91.66666667%}.col-sm-offset-10{margin-left:83.33333333%}.col-sm-offset-9{margin-left:75%}.col-sm-offset-8{margin-left:66.66666667%}.col-sm-offset-7{margin-left:58.33333333%}.col-sm-offset-6{margin-left:50%}.col-sm-offset-5{margin-left:41.66666667%}.col-sm-offset-4{margin-left:33.33333333%}.col-sm-offset-3{margin-left:25%}.col-sm-offset-2{margin-left:16.66666667%}.col-sm-offset-1{margin-left:8.33333333%}.col-sm-offset-0{margin-left:0}}@media (min-width:992px){.col-md-1,.col-md-10,.col-md-11,.col-md-12,.col-md-2,.col-md-3,.col-md-4,.col-md-5,.col-md-6,.col-md-7,.col-md-8,.col-md-9{float:left}.col-md-12{width:100%}.col-md-11{width:91.66666667%}.col-md-10{width:83.33333333%}.col-md-9{width:75%}.col-md-8{width:66.66666667%}.col-md-7{width:58.33333333%}.col-md-6{width:50%}.col-md-5{width:41.66666667%}.col-md-4{width:33.33333333%}.col-md-3{width:25%}.col-md-2{width:16.66666667%}.col-md-1{width:8.33333333%}.col-md-pull-12{right:100%}.col-md-pull-11{right:91.66666667%}.col-md-pull-10{right:83.33333333%}.col-md-pull-9{right:75%}.col-md-pull-8{right:66.66666667%}.col-md-pull-7{right:58.33333333%}.col-md-pull-6{right:50%}.col-md-pull-5{right:41.66666667%}.col-md-pull-4{right:33.33333333%}.col-md-pull-3{right:25%}.col-md-pull-2{right:16.66666667%}.col-md-pull-1{right:8.33333333%}.col-md-pull-0{right:auto}.col-md-push-12{left:100%}.col-md-push-11{left:91.66666667%}.col-md-push-10{left:83.33333333%}.col-md-push-9{left:75%}.col-md-push-8{left:66.66666667%}.col-md-push-7{left:58.33333333%}.col-md-push-6{left:50%}.col-md-push-5{left:41.66666667%}.col-md-push-4{left:33.33333333%}.col-md-push-3{left:25%}.col-md-push-2{left:16.66666667%}.col-md-push-1{left:8.33333333%}.col-md-push-0{left:auto}.col-md-offset-12{margin-left:100%}.col-md-offset-11{margin-left:91.66666667%}.col-md-offset-10{margin-left:83.33333333%}.col-md-offset-9{margin-left:75%}.col-md-offset-8{margin-left:66.66666667%}.col-md-offset-7{margin-left:58.33333333%}.col-md-offset-6{margin-left:50%}.col-md-offset-5{margin-left:41.66666667%}.col-md-offset-4{margin-left:33.33333333%}.col-md-offset-3{margin-left:25%}.col-md-offset-2{margin-left:16.66666667%}.col-md-offset-1{margin-left:8.33333333%}.col-md-offset-0{margin-left:0}}@media (min-width:1200px){.col-lg-1,.col-lg-10,.col-lg-11,.col-lg-12,.col-lg-2,.col-lg-3,.col-lg-4,.col-lg-5,.col-lg-6,.col-lg-7,.col-lg-8,.col-lg-9{float:left}.col-lg-12{width:100%}.col-lg-11{width:91.66666667%}.col-lg-10{width:83.33333333%}.col-lg-9{width:75%}.col-lg-8{width:66.66666667%}.col-lg-7{width:58.33333333%}.col-lg-6{width:50%}.col-lg-5{width:41.66666667%}.col-lg-4{width:33.33333333%}.col-lg-3{width:25%}.col-lg-2{width:16.66666667%}.col-lg-1{width:8.33333333%}.col-lg-pull-12{right:100%}.col-lg-pull-11{right:91.66666667%}.col-lg-pull-10{right:83.33333333%}.col-lg-pull-9{right:75%}.col-lg-pull-8{right:66.66666667%}.col-lg-pull-7{right:58.33333333%}.col-lg-pull-6{right:50%}.col-lg-pull-5{right:41.66666667%}.col-lg-pull-4{right:33.33333333%}.col-lg-pull-3{right:25%}.col-lg-pull-2{right:16.66666667%}.col-lg-pull-1{right:8.33333333%}.col-lg-pull-0{right:auto}.col-lg-push-12{left:100%}.col-lg-push-11{left:91.66666667%}.col-lg-push-10{left:83.33333333%}.col-lg-push-9{left:75%}.col-lg-push-8{left:66.66666667%}.col-lg-push-7{left:58.33333333%}.col-lg-push-6{left:50%}.col-lg-push-5{left:41.66666667%}.col-lg-push-4{left:33.33333333%}.col-lg-push-3{left:25%}.col-lg-push-2{left:16.66666667%}.col-lg-push-1{left:8.33333333%}.col-lg-push-0{left:auto}.col-lg-offset-12{margin-left:100%}.col-lg-offset-11{margin-left:91.66666667%}.col-lg-offset-10{margin-left:83.33333333%}.col-lg-offset-9{margin-left:75%}.col-lg-offset-8{margin-left:66.66666667%}.col-lg-offset-7{margin-left:58.33333333%}.col-lg-offset-6{margin-left:50%}.col-lg-offset-5{margin-left:41.66666667%}.col-lg-offset-4{margin-left:33.33333333%}.col-lg-offset-3{margin-left:25%}.col-lg-offset-2{margin-left:16.66666667%}.col-lg-offset-1{margin-left:8.33333333%}.col-lg-offset-0{margin-left:0}}table{background-color:transparent}caption{padding-top:8px;padding-bottom:8px;color:#777;text-align:left}th{text-align:left}.table{width:100%;max-width:100%;margin-bottom:20px}.table&gt;tbody&gt;tr&gt;td,.table&gt;tbody&gt;tr&gt;th,.table&gt;tfoot&gt;tr&gt;td,.table&gt;tfoot&gt;tr&gt;th,.table&gt;thead&gt;tr&gt;td,.table&gt;thead&gt;tr&gt;th{padding:8px;line-height:1.42857143;vertical-align:top;border-top:1px solid #ddd}.table&gt;thead&gt;tr&gt;th{vertical-align:bottom;border-bottom:2px solid #ddd}.table&gt;caption+thead&gt;tr:first-child&gt;td,.table&gt;caption+thead&gt;tr:first-child&gt;th,.table&gt;colgroup+thead&gt;tr:first-child&gt;td,.table&gt;colgroup+thead&gt;tr:first-child&gt;th,.table&gt;thead:first-child&gt;tr:first-child&gt;td,.table&gt;thead:first-child&gt;tr:first-child&gt;th{border-top:0}.table&gt;tbody+tbody{border-top:2px solid #ddd}.table .table{background-color:#fff}.table-condensed&gt;tbody&gt;tr&gt;td,.table-condensed&gt;tbody&gt;tr&gt;th,.table-condensed&gt;tfoot&gt;tr&gt;td,.table-condensed&gt;tfoot&gt;tr&gt;th,.table-condensed&gt;thead&gt;tr&gt;td,.table-condensed&gt;thead&gt;tr&gt;th{padding:5px}.table-bordered{border:1px solid #ddd}.table-bordered&gt;tbody&gt;tr&gt;td,.table-bordered&gt;tbody&gt;tr&gt;th,.table-bordered&gt;tfoot&gt;tr&gt;td,.table-bordered&gt;tfoot&gt;tr&gt;th,.table-bordered&gt;thead&gt;tr&gt;td,.table-bordered&gt;thead&gt;tr&gt;th{border:1px solid #ddd}.table-bordered&gt;thead&gt;tr&gt;td,.table-bordered&gt;thead&gt;tr&gt;th{border-bottom-width:2px}.table-striped&gt;tbody&gt;tr:nth-of-type(odd){background-color:#f9f9f9}
				
            </xsl:if>
            <xsl:if test="2 &gt; 1">
                
.table-hover&gt;tbody&gt;tr:hover{background-color:#f5f5f5}table col[class*=col-]{position:static;display:table-column;float:none}table td[class*=col-],table th[class*=col-]{position:static;display:table-cell;float:none}.table&gt;tbody&gt;tr.active&gt;td,.table&gt;tbody&gt;tr.active&gt;th,.table&gt;tbody&gt;tr&gt;td.active,.table&gt;tbody&gt;tr&gt;th.active,.table&gt;tfoot&gt;tr.active&gt;td,.table&gt;tfoot&gt;tr.active&gt;th,.table&gt;tfoot&gt;tr&gt;td.active,.table&gt;tfoot&gt;tr&gt;th.active,.table&gt;thead&gt;tr.active&gt;td,.table&gt;thead&gt;tr.active&gt;th,.table&gt;thead&gt;tr&gt;td.active,.table&gt;thead&gt;tr&gt;th.active{background-color:#f5f5f5}.table-hover&gt;tbody&gt;tr.active:hover&gt;td,.table-hover&gt;tbody&gt;tr.active:hover&gt;th,.table-hover&gt;tbody&gt;tr:hover&gt;.active,.table-hover&gt;tbody&gt;tr&gt;td.active:hover,.table-hover&gt;tbody&gt;tr&gt;th.active:hover{background-color:#e8e8e8}.table&gt;tbody&gt;tr.success&gt;td,.table&gt;tbody&gt;tr.success&gt;th,.table&gt;tbody&gt;tr&gt;td.success,.table&gt;tbody&gt;tr&gt;th.success,.table&gt;tfoot&gt;tr.success&gt;td,.table&gt;tfoot&gt;tr.success&gt;th,.table&gt;tfoot&gt;tr&gt;td.success,.table&gt;tfoot&gt;tr&gt;th.success,.table&gt;thead&gt;tr.success&gt;td,.table&gt;thead&gt;tr.success&gt;th,.table&gt;thead&gt;tr&gt;td.success,.table&gt;thead&gt;tr&gt;th.success{background-color:#dff0d8}.table-hover&gt;tbody&gt;tr.success:hover&gt;td,.table-hover&gt;tbody&gt;tr.success:hover&gt;th,.table-hover&gt;tbody&gt;tr:hover&gt;.success,.table-hover&gt;tbody&gt;tr&gt;td.success:hover,.table-hover&gt;tbody&gt;tr&gt;th.success:hover{background-color:#d0e9c6}.table&gt;tbody&gt;tr.info&gt;td,.table&gt;tbody&gt;tr.info&gt;th,.table&gt;tbody&gt;tr&gt;td.info,.table&gt;tbody&gt;tr&gt;th.info,.table&gt;tfoot&gt;tr.info&gt;td,.table&gt;tfoot&gt;tr.info&gt;th,.table&gt;tfoot&gt;tr&gt;td.info,.table&gt;tfoot&gt;tr&gt;th.info,.table&gt;thead&gt;tr.info&gt;td,.table&gt;thead&gt;tr.info&gt;th,.table&gt;thead&gt;tr&gt;td.info,.table&gt;thead&gt;tr&gt;th.info{background-color:#d9edf7}.table-hover&gt;tbody&gt;tr.info:hover&gt;td,.table-hover&gt;tbody&gt;tr.info:hover&gt;th,.table-hover&gt;tbody&gt;tr:hover&gt;.info,.table-hover&gt;tbody&gt;tr&gt;td.info:hover,.table-hover&gt;tbody&gt;tr&gt;th.info:hover{background-color:#c4e3f3}.table&gt;tbody&gt;tr.warning&gt;td,.table&gt;tbody&gt;tr.warning&gt;th,.table&gt;tbody&gt;tr&gt;td.warning,.table&gt;tbody&gt;tr&gt;th.warning,.table&gt;tfoot&gt;tr.warning&gt;td,.table&gt;tfoot&gt;tr.warning&gt;th,.table&gt;tfoot&gt;tr&gt;td.warning,.table&gt;tfoot&gt;tr&gt;th.warning,.table&gt;thead&gt;tr.warning&gt;td,.table&gt;thead&gt;tr.warning&gt;th,.table&gt;thead&gt;tr&gt;td.warning,.table&gt;thead&gt;tr&gt;th.warning{background-color:#fcf8e3}.table-hover&gt;tbody&gt;tr.warning:hover&gt;td,.table-hover&gt;tbody&gt;tr.warning:hover&gt;th,.table-hover&gt;tbody&gt;tr:hover&gt;.warning,.table-hover&gt;tbody&gt;tr&gt;td.warning:hover,.table-hover&gt;tbody&gt;tr&gt;th.warning:hover{background-color:#faf2cc}.table&gt;tbody&gt;tr.danger&gt;td,.table&gt;tbody&gt;tr.danger&gt;th,.table&gt;tbody&gt;tr&gt;td.danger,.table&gt;tbody&gt;tr&gt;th.danger,.table&gt;tfoot&gt;tr.danger&gt;td,.table&gt;tfoot&gt;tr.danger&gt;th,.table&gt;tfoot&gt;tr&gt;td.danger,.table&gt;tfoot&gt;tr&gt;th.danger,.table&gt;thead&gt;tr.danger&gt;td,.table&gt;thead&gt;tr.danger&gt;th,.table&gt;thead&gt;tr&gt;td.danger,.table&gt;thead&gt;tr&gt;th.danger{background-color:#f2dede}.table-hover&gt;tbody&gt;tr.danger:hover&gt;td,.table-hover&gt;tbody&gt;tr.danger:hover&gt;th,.table-hover&gt;tbody&gt;tr:hover&gt;.danger,.table-hover&gt;tbody&gt;tr&gt;td.danger:hover,.table-hover&gt;tbody&gt;tr&gt;th.danger:hover{background-color:#ebcccc}.table-responsive{min-height:.01%;overflow-x:auto}@media screen and (max-width:767px){.table-responsive{width:100%;margin-bottom:15px;overflow-y:hidden;-ms-overflow-style:-ms-autohiding-scrollbar;border:1px solid #ddd}.table-responsive&gt;.table{margin-bottom:0}.table-responsive&gt;.table&gt;tbody&gt;tr&gt;td,.table-responsive&gt;.table&gt;tbody&gt;tr&gt;th,.table-responsive&gt;.table&gt;tfoot&gt;tr&gt;td,.table-responsive&gt;.table&gt;tfoot&gt;tr&gt;th,.table-responsive&gt;.table&gt;thead&gt;tr&gt;td,.table-responsive&gt;.table&gt;thead&gt;tr&gt;th{white-space:nowrap}.table-responsive&gt;.table-bordered{border:0}.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;td:first-child,.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;th:first-child,.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;td:first-child,.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;th:first-child,.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;td:first-child,.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;th:first-child{border-left:0}.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;td:last-child,.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;th:last-child,.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;td:last-child,.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;th:last-child,.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;td:last-child,.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;th:last-child{border-right:0}.table-responsive&gt;.table-bordered&gt;tbody&gt;tr:last-child&gt;td,.table-responsive&gt;.table-bordered&gt;tbody&gt;tr:last-child&gt;th,.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr:last-child&gt;td,.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr:last-child&gt;th{border-bottom:0}}fieldset{min-width:0;padding:0;margin:0;border:0}legend{display:block;width:100%;padding:0;margin-bottom:20px;font-size:21px;line-height:inherit;color:#333;border:0;border-bottom:1px solid #e5e5e5}label{display:inline-block;max-width:100%;margin-bottom:5px;font-weight:700}input[type=search]{-webkit-box-sizing:border-box;-moz-box-sizing:border-box;box-sizing:border-box}input[type=checkbox],input[type=radio]{margin:4px 0 0;margin-top:1px\9;line-height:normal}input[type=file]{display:block}input[type=range]{display:block;width:100%}select[multiple],select[size]{height:auto}input[type=file]:focus,input[type=checkbox]:focus,input[type=radio]:focus{outline:thin dotted;outline:5px auto -webkit-focus-ring-color;outline-offset:-2px}output{display:block;padding-top:7px;font-size:14px;line-height:1.42857143;color:#555}.form-control{display:block;width:100%;height:34px;padding:6px 12px;font-size:14px;line-height:1.42857143;color:#555;background-color:#fff;background-image:none;border:1px solid #ccc;border-radius:4px;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075);box-shadow:inset 0 1px 1px rgba(0,0,0,.075);-webkit-transition:border-color ease-in-out .15s,-webkit-box-shadow ease-in-out .15s;-o-transition:border-color ease-in-out .15s,box-shadow ease-in-out .15s;transition:border-color ease-in-out .15s,box-shadow ease-in-out .15s}.form-control:focus{border-color:#66afe9;outline:0;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 8px rgba(102,175,233,.6);box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 8px rgba(102,175,233,.6)}.form-control::-moz-placeholder{color:#999;opacity:1}.form-control:-ms-input-placeholder{color:#999}.form-control::-webkit-input-placeholder{color:#999}.form-control[disabled],.form-control[readonly],fieldset[disabled] .form-control{background-color:#eee;opacity:1}.form-control[disabled],fieldset[disabled] .form-control{cursor:not-allowed}textarea.form-control{height:auto}input[type=search]{-webkit-appearance:none}@media screen and (-webkit-min-device-pixel-ratio:0){input[type=date].form-control,input[type=time].form-control,input[type=datetime-local].form-control,input[type=month].form-control{line-height:34px}.input-group-sm input[type=date],.input-group-sm input[type=time],.input-group-sm input[type=datetime-local],.input-group-sm input[type=month],input[type=date].input-sm,input[type=time].input-sm,input[type=datetime-local].input-sm,input[type=month].input-sm{line-height:30px}.input-group-lg input[type=date],.input-group-lg input[type=time],.input-group-lg input[type=datetime-local],.input-group-lg input[type=month],input[type=date].input-lg,input[type=time].input-lg,input[type=datetime-local].input-lg,input[type=month].input-lg{line-height:46px}}.form-group{margin-bottom:15px}.checkbox,.radio{position:relative;display:block;margin-top:10px;margin-bottom:10px}.checkbox label,.radio label{min-height:20px;padding-left:20px;margin-bottom:0;font-weight:400;cursor:pointer}.checkbox input[type=checkbox],.checkbox-inline input[type=checkbox],.radio input[type=radio],.radio-inline input[type=radio]{position:absolute;margin-top:4px\9;margin-left:-20px}.checkbox+.checkbox,.radio+.radio{margin-top:-5px}.checkbox-inline,.radio-inline{position:relative;display:inline-block;padding-left:20px;margin-bottom:0;font-weight:400;vertical-align:middle;cursor:pointer}.checkbox-inline+.checkbox-inline,.radio-inline+.radio-inline{margin-top:0;margin-left:10px}fieldset[disabled] input[type=checkbox],fieldset[disabled] input[type=radio],input[type=checkbox].disabled,input[type=checkbox][disabled],input[type=radio].disabled,input[type=radio][disabled]{cursor:not-allowed}.checkbox-inline.disabled,.radio-inline.disabled,fieldset[disabled] .checkbox-inline,fieldset[disabled] .radio-inline{cursor:not-allowed}.checkbox.disabled label,.radio.disabled label,fieldset[disabled] .checkbox label,fieldset[disabled] .radio label{cursor:not-allowed}.form-control-static{min-height:34px;padding-top:7px;padding-bottom:7px;margin-bottom:0}.form-control-static.input-lg,.form-control-static.input-sm{padding-right:0;padding-left:0}.input-sm{height:30px;padding:5px 10px;font-size:12px;line-height:1.5;border-radius:3px}select.input-sm{height:30px;line-height:30px}select[multiple].input-sm,textarea.input-sm{height:auto}.form-group-sm .form-control{height:30px;padding:5px 10px;font-size:12px;line-height:1.5;border-radius:3px}.form-group-sm select.form-control{height:30px;line-height:30px}.form-group-sm select[multiple].form-control,.form-group-sm textarea.form-control{height:auto}.form-group-sm .form-control-static{height:30px;min-height:32px;padding:6px 10px;font-size:12px;line-height:1.5}.input-lg{height:46px;padding:10px 16px;font-size:18px;line-height:1.3333333;border-radius:6px}select.input-lg{height:46px;line-height:46px}select[multiple].input-lg,textarea.input-lg{height:auto}.form-group-lg .form-control{height:46px;padding:10px 16px;font-size:18px;line-height:1.3333333;border-radius:6px}.form-group-lg select.form-control{height:46px;line-height:46px}.form-group-lg select[multiple].form-control,.form-group-lg textarea.form-control{height:auto}.form-group-lg .form-control-static{height:46px;min-height:38px;padding:11px 16px;font-size:18px;line-height:1.3333333}.has-feedback{position:relative}.has-feedback .form-control{padding-right:42.5px}.form-control-feedback{position:absolute;top:0;right:0;z-index:2;display:block;width:34px;height:34px;line-height:34px;text-align:center;pointer-events:none}.form-group-lg .form-control+.form-control-feedback,.input-group-lg+.form-control-feedback,.input-lg+.form-control-feedback{width:46px;height:46px;line-height:46px}.form-group-sm .form-control+.form-control-feedback,.input-group-sm+.form-control-feedback,.input-sm+.form-control-feedback{width:30px;height:30px;line-height:30px}.has-success .checkbox,.has-success .checkbox-inline,.has-success .control-label,.has-success .help-block,.has-success .radio,.has-success .radio-inline,.has-success.checkbox label,.has-success.checkbox-inline label,.has-success.radio label,.has-success.radio-inline label{color:#3c763d}.has-success .form-control{border-color:#3c763d;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075);box-shadow:inset 0 1px 1px rgba(0,0,0,.075)}.has-success .form-control:focus{border-color:#2b542c;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 6px #67b168;box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 6px #67b168}.has-success .input-group-addon{color:#3c763d;background-color:#dff0d8;border-color:#3c763d}.has-success .form-control-feedback{color:#3c763d}.has-warning .checkbox,.has-warning .checkbox-inline,.has-warning .control-label,.has-warning .help-block,.has-warning .radio,.has-warning .radio-inline,.has-warning.checkbox label,.has-warning.checkbox-inline label,.has-warning.radio label,.has-warning.radio-inline label{color:#8a6d3b}.has-warning .form-control{border-color:#8a6d3b;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075);box-shadow:inset 0 1px 1px rgba(0,0,0,.075)}.has-warning .form-control:focus{border-color:#66512c;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 6px #c0a16b;box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 6px #c0a16b}.has-warning .input-group-addon{color:#8a6d3b;background-color:#fcf8e3;border-color:#8a6d3b}.has-warning .form-control-feedback{color:#8a6d3b}.has-error .checkbox,.has-error .checkbox-inline,.has-error .control-label,.has-error .help-block,.has-error .radio,.has-error .radio-inline,.has-error.checkbox label,.has-error.checkbox-inline label,.has-error.radio label,.has-error.radio-inline label{color:#a94442}.has-error .form-control{border-color:#a94442;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075);box-shadow:inset 0 1px 1px rgba(0,0,0,.075)}.has-error .form-control:focus{border-color:#843534;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 6px #ce8483;box-shadow:inset 0 1px 1px rgba(0,0,0,.075),0 0 6px #ce8483}.has-error .input-group-addon{color:#a94442;background-color:#f2dede;border-color:#a94442}.has-error .form-control-feedback{color:#a94442}.has-feedback label~.form-control-feedback{top:25px}.has-feedback label.sr-only~.form-control-feedback{top:0}.help-block{display:block;margin-top:5px;margin-bottom:10px;color:#737373}@media (min-width:768px){.form-inline .form-group{display:inline-block;margin-bottom:0;vertical-align:middle}.form-inline .form-control{display:inline-block;width:auto;vertical-align:middle}.form-inline .form-control-static{display:inline-block}.form-inline .input-group{display:inline-table;vertical-align:middle}.form-inline .input-group .form-control,.form-inline .input-group .input-group-addon,.form-inline .input-group .input-group-btn{width:auto}.form-inline .input-group&gt;.form-control{width:100%}.form-inline .control-label{margin-bottom:0;vertical-align:middle}.form-inline .checkbox,.form-inline .radio{display:inline-block;margin-top:0;margin-bottom:0;vertical-align:middle}.form-inline .checkbox label,.form-inline .radio label{padding-left:0}.form-inline .checkbox input[type=checkbox],.form-inline .radio input[type=radio]{position:relative;margin-left:0}.form-inline .has-feedback .form-control-feedback{top:0}}.form-horizontal .checkbox,.form-horizontal .checkbox-inline,.form-horizontal .radio,.form-horizontal .radio-inline{padding-top:7px;margin-top:0;margin-bottom:0}.form-horizontal .checkbox,.form-horizontal .radio{min-height:27px}.form-horizontal .form-group{margin-right:-15px;margin-left:-15px}@media (min-width:768px){.form-horizontal .control-label{padding-top:7px;margin-bottom:0;text-align:right}}.form-horizontal .has-feedback .form-control-feedback{right:15px}@media (min-width:768px){.form-horizontal .form-group-lg .control-label{padding-top:14.33px;font-size:18px}}@media (min-width:768px){.form-horizontal .form-group-sm .control-label{padding-top:6px;font-size:12px}}.btn{display:inline-block;padding:6px 12px;margin-bottom:0;font-size:14px;font-weight:400;line-height:1.42857143;text-align:center;white-space:nowrap;vertical-align:middle;-ms-touch-action:manipulation;touch-action:manipulation;cursor:pointer;-webkit-user-select:none;-moz-user-select:none;-ms-user-select:none;user-select:none;background-image:none;border:1px solid transparent;border-radius:4px}.btn.active.focus,.btn.active:focus,.btn.focus,.btn:active.focus,.btn:active:focus,.btn:focus{outline:thin dotted;outline:5px auto -webkit-focus-ring-color;outline-offset:-2px}.btn.focus,.btn:focus,.btn:hover{color:#333;text-decoration:none}.btn.active,.btn:active{background-image:none;outline:0;-webkit-box-shadow:inset 0 3px 5px rgba(0,0,0,.125);box-shadow:inset 0 3px 5px rgba(0,0,0,.125)}.btn.disabled,.btn[disabled],fieldset[disabled] .btn{cursor:not-allowed;filter:alpha(opacity=65);-webkit-box-shadow:none;box-shadow:none;opacity:.65}a.btn.disabled,fieldset[disabled] a.btn{pointer-events:none}.btn-default{color:#333;background-color:#fff;border-color:#ccc}.btn-default.focus,.btn-default:focus{color:#333;background-color:#e6e6e6;border-color:#8c8c8c}.btn-default:hover{color:#333;background-color:#e6e6e6;border-color:#adadad}.btn-default.active,.btn-default:active,.open&gt;.dropdown-toggle.btn-default{color:#333;background-color:#e6e6e6;border-color:#adadad}.btn-default.active.focus,.btn-default.active:focus,.btn-default.active:hover,.btn-default:active.focus,.btn-default:active:focus,.btn-default:active:hover,.open&gt;.dropdown-toggle.btn-default.focus,.open&gt;.dropdown-toggle.btn-default:focus,.open&gt;.dropdown-toggle.btn-default:hover{color:#333;background-color:#d4d4d4;border-color:#8c8c8c}.btn-default.active,.btn-default:active,.open&gt;.dropdown-toggle.btn-default{background-image:none}.btn-default.disabled,.btn-default.disabled.active,.btn-default.disabled.focus,.btn-default.disabled:active,.btn-default.disabled:focus,.btn-default.disabled:hover,.btn-default[disabled],.btn-default[disabled].active,.btn-default[disabled].focus,.btn-default[disabled]:active,.btn-default[disabled]:focus,.btn-default[disabled]:hover,fieldset[disabled] .btn-default,fieldset[disabled] .btn-default.active,fieldset[disabled] .btn-default.focus,fieldset[disabled] .btn-default:active,fieldset[disabled] .btn-default:focus,fieldset[disabled] .btn-default:hover{background-color:#fff;border-color:#ccc}.btn-default .badge{color:#fff;background-color:#333}.btn-primary{color:#fff;background-color:#337ab7;border-color:#2e6da4}.btn-primary.focus,.btn-primary:focus{color:#fff;background-color:#286090;border-color:#122b40}.btn-primary:hover{color:#fff;background-color:#286090;border-color:#204d74}.btn-primary.active,.btn-primary:active,.open&gt;.dropdown-toggle.btn-primary{color:#fff;background-color:#286090;border-color:#204d74}.btn-primary.active.focus,.btn-primary.active:focus,.btn-primary.active:hover,.btn-primary:active.focus,.btn-primary:active:focus,.btn-primary:active:hover,.open&gt;.dropdown-toggle.btn-primary.focus,.open&gt;.dropdown-toggle.btn-primary:focus,.open&gt;.dropdown-toggle.btn-primary:hover{color:#fff;background-color:#204d74;border-color:#122b40}.btn-primary.active,.btn-primary:active,.open&gt;.dropdown-toggle.btn-primary{background-image:none}.btn-primary.disabled,.btn-primary.disabled.active,.btn-primary.disabled.focus,.btn-primary.disabled:active,.btn-primary.disabled:focus,.btn-primary.disabled:hover,.btn-primary[disabled],.btn-primary[disabled].active,.btn-primary[disabled].focus,.btn-primary[disabled]:active,.btn-primary[disabled]:focus,.btn-primary[disabled]:hover,fieldset[disabled] .btn-primary,fieldset[disabled] .btn-primary.active,fieldset[disabled] .btn-primary.focus,fieldset[disabled] .btn-primary:active,fieldset[disabled] .btn-primary:focus,fieldset[disabled] .btn-primary:hover{background-color:#337ab7;border-color:#2e6da4}.btn-primary .badge{color:#337ab7;background-color:#fff}.btn-success{color:#fff;background-color:#5cb85c;border-color:#4cae4c}.btn-success.focus,.btn-success:focus{color:#fff;background-color:#449d44;border-color:#255625}.btn-success:hover{color:#fff;background-color:#449d44;border-color:#398439}.btn-success.active,.btn-success:active,.open&gt;.dropdown-toggle.btn-success{color:#fff;background-color:#449d44;border-color:#398439}.btn-success.active.focus,.btn-success.active:focus,.btn-success.active:hover,.btn-success:active.focus,.btn-success:active:focus,.btn-success:active:hover,.open&gt;.dropdown-toggle.btn-success.focus,.open&gt;.dropdown-toggle.btn-success:focus,.open&gt;.dropdown-toggle.btn-success:hover{color:#fff;background-color:#398439;border-color:#255625}.btn-success.active,.btn-success:active,.open&gt;.dropdown-toggle.btn-success{background-image:none}.btn-success.disabled,.btn-success.disabled.active,.btn-success.disabled.focus,.btn-success.disabled:active,.btn-success.disabled:focus,.btn-success.disabled:hover,.btn-success[disabled],.btn-success[disabled].active,.btn-success[disabled].focus,.btn-success[disabled]:active,.btn-success[disabled]:focus,.btn-success[disabled]:hover,fieldset[disabled] .btn-success,fieldset[disabled] .btn-success.active,fieldset[disabled] .btn-success.focus,fieldset[disabled] .btn-success:active,fieldset[disabled] .btn-success:focus,fieldset[disabled] .btn-success:hover{background-color:#5cb85c;border-color:#4cae4c}.btn-success .badge{color:#5cb85c;background-color:#fff}.btn-info{color:#fff;background-color:#5bc0de;border-color:#46b8da}.btn-info.focus,.btn-info:focus{color:#fff;background-color:#31b0d5;border-color:#1b6d85}.btn-info:hover{color:#fff;background-color:#31b0d5;border-color:#269abc}.btn-info.active,.btn-info:active,.open&gt;.dropdown-toggle.btn-info{color:#fff;background-color:#31b0d5;border-color:#269abc}.btn-info.active.focus,.btn-info.active:focus,.btn-info.active:hover,.btn-info:active.focus,.btn-info:active:focus,.btn-info:active:hover,.open&gt;.dropdown-toggle.btn-info.focus,.open&gt;.dropdown-toggle.btn-info:focus,.open&gt;.dropdown-toggle.btn-info:hover{color:#fff;background-color:#269abc;border-color:#1b6d85}.btn-info.active,.btn-info:active,.open&gt;.dropdown-toggle.btn-info{background-image:none}.btn-info.disabled,.btn-info.disabled.active,.btn-info.disabled.focus,.btn-info.disabled:active,.btn-info.disabled:focus,.btn-info.disabled:hover,.btn-info[disabled],.btn-info[disabled].active,.btn-info[disabled].focus,.btn-info[disabled]:active,.btn-info[disabled]:focus,.btn-info[disabled]:hover,fieldset[disabled] .btn-info,fieldset[disabled] .btn-info.active,fieldset[disabled] .btn-info.focus,fieldset[disabled] .btn-info:active,fieldset[disabled] .btn-info:focus,fieldset[disabled] .btn-info:hover{background-color:#5bc0de;border-color:#46b8da}.btn-info .badge{color:#5bc0de;background-color:#fff}.btn-warning{color:#fff;background-color:#f0ad4e;border-color:#eea236}.btn-warning.focus,.btn-warning:focus{color:#fff;background-color:#ec971f;border-color:#985f0d}.btn-warning:hover{color:#fff;background-color:#ec971f;border-color:#d58512}.btn-warning.active,.btn-warning:active,.open&gt;.dropdown-toggle.btn-warning{color:#fff;background-color:#ec971f;border-color:#d58512}.btn-warning.active.focus,.btn-warning.active:focus,.btn-warning.active:hover,.btn-warning:active.focus,.btn-warning:active:focus,.btn-warning:active:hover,.open&gt;.dropdown-toggle.btn-warning.focus,.open&gt;.dropdown-toggle.btn-warning:focus,.open&gt;.dropdown-toggle.btn-warning:hover{color:#fff;background-color:#d58512;border-color:#985f0d}.btn-warning.active,.btn-warning:active,.open&gt;.dropdown-toggle.btn-warning{background-image:none}.btn-warning.disabled,.btn-warning.disabled.active,.btn-warning.disabled.focus,.btn-warning.disabled:active,.btn-warning.disabled:focus,.btn-warning.disabled:hover,.btn-warning[disabled],.btn-warning[disabled].active,.btn-warning[disabled].focus,.btn-warning[disabled]:active,.btn-warning[disabled]:focus,.btn-warning[disabled]:hover,fieldset[disabled] .btn-warning,fieldset[disabled] .btn-warning.active,fieldset[disabled] .btn-warning.focus,fieldset[disabled] .btn-warning:active,fieldset[disabled] .btn-warning:focus,fieldset[disabled] .btn-warning:hover{background-color:#f0ad4e;border-color:#eea236}.btn-warning .badge{color:#f0ad4e;background-color:#fff}.btn-danger{color:#fff;background-color:#d9534f;border-color:#d43f3a}.btn-danger.focus,.btn-danger:focus{color:#fff;background-color:#c9302c;border-color:#761c19}.btn-danger:hover{color:#fff;background-color:#c9302c;border-color:#ac2925}.btn-danger.active,.btn-danger:active,.open&gt;.dropdown-toggle.btn-danger{color:#fff;background-color:#c9302c;border-color:#ac2925}.btn-danger.active.focus,.btn-danger.active:focus,.btn-danger.active:hover,.btn-danger:active.focus,.btn-danger:active:focus,.btn-danger:active:hover,.open&gt;.dropdown-toggle.btn-danger.focus,.open&gt;.dropdown-toggle.btn-danger:focus,.open&gt;.dropdown-toggle.btn-danger:hover{color:#fff;background-color:#ac2925;border-color:#761c19}.btn-danger.active,.btn-danger:active,.open&gt;.dropdown-toggle.btn-danger{background-image:none}.btn-danger.disabled,.btn-danger.disabled.active,.btn-danger.disabled.focus,.btn-danger.disabled:active,.btn-danger.disabled:focus,.btn-danger.disabled:hover,.btn-danger[disabled],.btn-danger[disabled].active,.btn-danger[disabled].focus,.btn-danger[disabled]:active,.btn-danger[disabled]:focus,.btn-danger[disabled]:hover,fieldset[disabled] .btn-danger,fieldset[disabled] .btn-danger.active,fieldset[disabled] .btn-danger.focus,fieldset[disabled] .btn-danger:active,fieldset[disabled] .btn-danger:focus,fieldset[disabled] .btn-danger:hover{background-color:#d9534f;border-color:#d43f3a}.btn-danger .badge{color:#d9534f;background-color:#fff}.btn-link{font-weight:400;color:#337ab7;border-radius:0}.btn-link,.btn-link.active,.btn-link:active,.btn-link[disabled],fieldset[disabled] .btn-link{background-color:transparent;-webkit-box-shadow:none;box-shadow:none}.btn-link,.btn-link:active,.btn-link:focus,.btn-link:hover{border-color:transparent}.btn-link:focus,.btn-link:hover{color:#23527c;text-decoration:underline;background-color:transparent}.btn-link[disabled]:focus,.btn-link[disabled]:hover,fieldset[disabled] .btn-link:focus,fieldset[disabled] .btn-link:hover{color:#777;text-decoration:none}.btn-group-lg&gt;.btn,.btn-lg{padding:10px 16px;font-size:18px;line-height:1.3333333;border-radius:6px}.btn-group-sm&gt;.btn,.btn-sm{padding:5px 10px;font-size:12px;line-height:1.5;border-radius:3px}.btn-group-xs&gt;.btn,.btn-xs{padding:1px 5px;font-size:12px;line-height:1.5;border-radius:3px}.btn-block{display:block;width:100%}.btn-block+.btn-block{margin-top:5px}input[type=button].btn-block,input[type=reset].btn-block,input[type=submit].btn-block{width:100%}.fade{opacity:0;-webkit-transition:opacity .15s linear;-o-transition:opacity .15s linear;transition:opacity .15s linear}.fade.in{opacity:1}.collapse{display:none}.collapse.in{display:block}tr.collapse.in{display:table-row}tbody.collapse.in{display:table-row-group}.collapsing{position:relative;height:0;overflow:hidden;-webkit-transition-timing-function:ease;-o-transition-timing-function:ease;transition-timing-function:ease;-webkit-transition-duration:.35s;-o-transition-duration:.35s;transition-duration:.35s;-webkit-transition-property:height,visibility;-o-transition-property:height,visibility;transition-property:height,visibility}.caret{display:inline-block;width:0;height:0;margin-left:2px;vertical-align:middle;border-top:4px dashed;border-top:4px solid\9;border-right:4px solid transparent;border-left:4px solid transparent}.dropdown,.dropup{position:relative}.dropdown-toggle:focus{outline:0}.dropdown-menu{position:absolute;top:100%;left:0;z-index:1000;display:none;float:left;min-width:160px;padding:5px 0;margin:2px 0 0;font-size:14px;text-align:left;list-style:none;background-color:#fff;-webkit-background-clip:padding-box;background-clip:padding-box;border:1px solid #ccc;border:1px solid rgba(0,0,0,.15);border-radius:4px;-webkit-box-shadow:0 6px 12px rgba(0,0,0,.175);box-shadow:0 6px 12px rgba(0,0,0,.175)}.dropdown-menu.pull-right{right:0;left:auto}.dropdown-menu .divider{height:1px;margin:9px 0;overflow:hidden;background-color:#e5e5e5}.dropdown-menu&gt;li&gt;a{display:block;padding:3px 20px;clear:both;font-weight:400;line-height:1.42857143;color:#333;white-space:nowrap}.dropdown-menu&gt;li&gt;a:focus,.dropdown-menu&gt;li&gt;a:hover{color:#262626;text-decoration:none;background-color:#f5f5f5}.dropdown-menu&gt;.active&gt;a,.dropdown-menu&gt;.active&gt;a:focus,.dropdown-menu&gt;.active&gt;a:hover{color:#fff;text-decoration:none;background-color:#337ab7;outline:0}.dropdown-menu&gt;.disabled&gt;a,.dropdown-menu&gt;.disabled&gt;a:focus,.dropdown-menu&gt;.disabled&gt;a:hover{color:#777}.dropdown-menu&gt;.disabled&gt;a:focus,.dropdown-menu&gt;.disabled&gt;a:hover{text-decoration:none;cursor:not-allowed;background-color:transparent;background-image:none;filter:progid:DXImageTransform.Microsoft.gradient(enabled=false)}.open&gt;.dropdown-menu{display:block}.open&gt;a{outline:0}.dropdown-menu-right{right:0;left:auto}.dropdown-menu-left{right:auto;left:0}.dropdown-header{display:block;padding:3px 20px;font-size:12px;line-height:1.42857143;color:#777;white-space:nowrap}.dropdown-backdrop{position:fixed;top:0;right:0;bottom:0;left:0;z-index:990}.pull-right&gt;.dropdown-menu{right:0;left:auto}.dropup .caret,.navbar-fixed-bottom .dropdown .caret{content:"";border-top:0;border-bottom:4px dashed;border-bottom:4px solid\9}.dropup .dropdown-menu,.navbar-fixed-bottom .dropdown .dropdown-menu{top:auto;bottom:100%;margin-bottom:2px}@media (min-width:768px){.navbar-right .dropdown-menu{right:0;left:auto}.navbar-right .dropdown-menu-left{right:auto;left:0}}.btn-group,.btn-group-vertical{position:relative;display:inline-block;vertical-align:middle}.btn-group-vertical&gt;.btn,.btn-group&gt;.btn{position:relative;float:left}.btn-group-vertical&gt;.btn.active,.btn-group-vertical&gt;.btn:active,.btn-group-vertical&gt;.btn:focus,.btn-group-vertical&gt;.btn:hover,.btn-group&gt;.btn.active,.btn-group&gt;.btn:active,.btn-group&gt;.btn:focus,.btn-group&gt;.btn:hover{z-index:2}.btn-group .btn+.btn,.btn-group .btn+.btn-group,.btn-group .btn-group+.btn,.btn-group .btn-group+.btn-group{margin-left:-1px}.btn-toolbar{margin-left:-5px}.btn-toolbar .btn,.btn-toolbar .btn-group,.btn-toolbar .input-group{float:left}.btn-toolbar&gt;.btn,.btn-toolbar&gt;.btn-group,.btn-toolbar&gt;.input-group{margin-left:5px}.btn-group&gt;.btn:not(:first-child):not(:last-child):not(.dropdown-toggle){border-radius:0}.btn-group&gt;.btn:first-child{margin-left:0}.btn-group&gt;.btn:first-child:not(:last-child):not(.dropdown-toggle){border-top-right-radius:0;border-bottom-right-radius:0}.btn-group&gt;.btn:last-child:not(:first-child),.btn-group&gt;.dropdown-toggle:not(:first-child){border-top-left-radius:0;border-bottom-left-radius:0}.btn-group&gt;.btn-group{float:left}.btn-group&gt;.btn-group:not(:first-child):not(:last-child)&gt;.btn{border-radius:0}.btn-group&gt;.btn-group:first-child:not(:last-child)&gt;.btn:last-child,.btn-group&gt;.btn-group:first-child:not(:last-child)&gt;.dropdown-toggle{border-top-right-radius:0;border-bottom-right-radius:0}.btn-group&gt;.btn-group:last-child:not(:first-child)&gt;.btn:first-child{border-top-left-radius:0;border-bottom-left-radius:0}.btn-group .dropdown-toggle:active,.btn-group.open .dropdown-toggle{outline:0}.btn-group&gt;.btn+.dropdown-toggle{padding-right:8px;padding-left:8px}.btn-group&gt;.btn-lg+.dropdown-toggle{padding-right:12px;padding-left:12px}.btn-group.open .dropdown-toggle{-webkit-box-shadow:inset 0 3px 5px rgba(0,0,0,.125);box-shadow:inset 0 3px 5px rgba(0,0,0,.125)}.btn-group.open .dropdown-toggle.btn-link{-webkit-box-shadow:none;box-shadow:none}.btn .caret{margin-left:0}.btn-lg .caret{border-width:5px 5px 0;border-bottom-width:0}.dropup .btn-lg .caret{border-width:0 5px 5px}.btn-group-vertical&gt;.btn,.btn-group-vertical&gt;.btn-group,.btn-group-vertical&gt;.btn-group&gt;.btn{display:block;float:none;width:100%;max-width:100%}.btn-group-vertical&gt;.btn-group&gt;.btn{float:none}.btn-group-vertical&gt;.btn+.btn,.btn-group-vertical&gt;.btn+.btn-group,.btn-group-vertical&gt;.btn-group+.btn,.btn-group-vertical&gt;.btn-group+.btn-group{margin-top:-1px;margin-left:0}.btn-group-vertical&gt;.btn:not(:first-child):not(:last-child){border-radius:0}.btn-group-vertical&gt;.btn:first-child:not(:last-child){border-top-right-radius:4px;border-bottom-right-radius:0;border-bottom-left-radius:0}.btn-group-vertical&gt;.btn:last-child:not(:first-child){border-top-left-radius:0;border-top-right-radius:0;border-bottom-left-radius:4px}.btn-group-vertical&gt;.btn-group:not(:first-child):not(:last-child)&gt;.btn{border-radius:0}.btn-group-vertical&gt;.btn-group:first-child:not(:last-child)&gt;.btn:last-child,.btn-group-vertical&gt;.btn-group:first-child:not(:last-child)&gt;.dropdown-toggle{border-bottom-right-radius:0;border-bottom-left-radius:0}.btn-group-vertical&gt;.btn-group:last-child:not(:first-child)&gt;.btn:first-child{border-top-left-radius:0;border-top-right-radius:0}.btn-group-justified{display:table;width:100%;table-layout:fixed;border-collapse:separate}.btn-group-justified&gt;.btn,.btn-group-justified&gt;.btn-group{display:table-cell;float:none;width:1%}.btn-group-justified&gt;.btn-group .btn{width:100%}.btn-group-justified&gt;.btn-group .dropdown-menu{left:auto}[data-toggle=buttons]&gt;.btn input[type=checkbox],[data-toggle=buttons]&gt;.btn input[type=radio],[data-toggle=buttons]&gt;.btn-group&gt;.btn input[type=checkbox],[data-toggle=buttons]&gt;.btn-group&gt;.btn input[type=radio]{position:absolute;clip:rect(0,0,0,0);pointer-events:none}.input-group{position:relative;display:table;border-collapse:separate}.input-group[class*=col-]{float:none;padding-right:0;padding-left:0}.input-group .form-control{position:relative;z-index:2;float:left;width:100%;margin-bottom:0}.input-group-lg&gt;.form-control,.input-group-lg&gt;.input-group-addon,.input-group-lg&gt;.input-group-btn&gt;.btn{height:46px;padding:10px 16px;font-size:18px;line-height:1.3333333;border-radius:6px}select.input-group-lg&gt;.form-control,select.input-group-lg&gt;.input-group-addon,select.input-group-lg&gt;.input-group-btn&gt;.btn{height:46px;line-height:46px}select[multiple].input-group-lg&gt;.form-control,select[multiple].input-group-lg&gt;.input-group-addon,select[multiple].input-group-lg&gt;.input-group-btn&gt;.btn,textarea.input-group-lg&gt;.form-control,textarea.input-group-lg&gt;.input-group-addon,textarea.input-group-lg&gt;.input-group-btn&gt;.btn{height:auto}.input-group-sm&gt;.form-control,.input-group-sm&gt;.input-group-addon,.input-group-sm&gt;.input-group-btn&gt;.btn{height:30px;padding:5px 10px;font-size:12px;line-height:1.5;border-radius:3px}select.input-group-sm&gt;.form-control,select.input-group-sm&gt;.input-group-addon,select.input-group-sm&gt;.input-group-btn&gt;.btn{height:30px;line-height:30px}select[multiple].input-group-sm&gt;.form-control,select[multiple].input-group-sm&gt;.input-group-addon,select[multiple].input-group-sm&gt;.input-group-btn&gt;.btn,textarea.input-group-sm&gt;.form-control,textarea.input-group-sm&gt;.input-group-addon,textarea.input-group-sm&gt;.input-group-btn&gt;.btn{height:auto}.input-group .form-control,.input-group-addon,.input-group-btn{display:table-cell}.input-group .form-control:not(:first-child):not(:last-child),.input-group-addon:not(:first-child):not(:last-child),.input-group-btn:not(:first-child):not(:last-child){border-radius:0}.input-group-addon,.input-group-btn{width:1%;white-space:nowrap;vertical-align:middle}.input-group-addon{padding:6px 12px;font-size:14px;font-weight:400;line-height:1;color:#555;text-align:center;background-color:#eee;border:1px solid #ccc;border-radius:4px}.input-group-addon.input-sm{padding:5px 10px;font-size:12px;border-radius:3px}.input-group-addon.input-lg{padding:10px 16px;font-size:18px;border-radius:6px}.input-group-addon input[type=checkbox],.input-group-addon input[type=radio]{margin-top:0}.input-group .form-control:first-child,.input-group-addon:first-child,.input-group-btn:first-child&gt;.btn,.input-group-btn:first-child&gt;.btn-group&gt;.btn,.input-group-btn:first-child&gt;.dropdown-toggle,.input-group-btn:last-child&gt;.btn-group:not(:last-child)&gt;.btn,.input-group-btn:last-child&gt;.btn:not(:last-child):not(.dropdown-toggle){border-top-right-radius:0;border-bottom-right-radius:0}.input-group-addon:first-child{border-right:0}.input-group .form-control:last-child,.input-group-addon:last-child,.input-group-btn:first-child&gt;.btn-group:not(:first-child)&gt;.btn,.input-group-btn:first-child&gt;.btn:not(:first-child),.input-group-btn:last-child&gt;.btn,.input-group-btn:last-child&gt;.btn-group&gt;.btn,.input-group-btn:last-child&gt;.dropdown-toggle{border-top-left-radius:0;border-bottom-left-radius:0}.input-group-addon:last-child{border-left:0}.input-group-btn{position:relative;font-size:0;white-space:nowrap}.input-group-btn&gt;.btn{position:relative}.input-group-btn&gt;.btn+.btn{margin-left:-1px}.input-group-btn&gt;.btn:active,.input-group-btn&gt;.btn:focus,.input-group-btn&gt;.btn:hover{z-index:2}.input-group-btn:first-child&gt;.btn,.input-group-btn:first-child&gt;.btn-group{margin-right:-1px}.input-group-btn:last-child&gt;.btn,.input-group-btn:last-child&gt;.btn-group{z-index:2;margin-left:-1px}.nav{padding-left:0;margin-bottom:0;list-style:none}.nav&gt;li{position:relative;display:block}.nav&gt;li&gt;a{position:relative;display:block;padding:10px 15px}.nav&gt;li&gt;a:focus,.nav&gt;li&gt;a:hover{text-decoration:none;background-color:#eee}.nav&gt;li.disabled&gt;a{color:#777}.nav&gt;li.disabled&gt;a:focus,.nav&gt;li.disabled&gt;a:hover{color:#777;text-decoration:none;cursor:not-allowed;background-color:transparent}.nav .open&gt;a,.nav .open&gt;a:focus,.nav .open&gt;a:hover{background-color:#eee;border-color:#337ab7}.nav .nav-divider{height:1px;margin:9px 0;overflow:hidden;background-color:#e5e5e5}.nav&gt;li&gt;a&gt;img{max-width:none}.nav-tabs{border-bottom:1px solid #ddd}.nav-tabs&gt;li{float:left;margin-bottom:-1px}.nav-tabs&gt;li&gt;a{margin-right:2px;line-height:1.42857143;border:1px solid transparent;border-radius:4px 4px 0 0}.nav-tabs&gt;li&gt;a:hover{border-color:#eee #eee #ddd}.nav-tabs&gt;li.active&gt;a,.nav-tabs&gt;li.active&gt;a:focus,.nav-tabs&gt;li.active&gt;a:hover{color:#555;cursor:default;background-color:#fff;border:1px solid #ddd;border-bottom-color:transparent}.nav-tabs.nav-justified{width:100%;border-bottom:0}.nav-tabs.nav-justified&gt;li{float:none}.nav-tabs.nav-justified&gt;li&gt;a{margin-bottom:5px;text-align:center}.nav-tabs.nav-justified&gt;.dropdown .dropdown-menu{top:auto;left:auto}@media (min-width:768px){.nav-tabs.nav-justified&gt;li{display:table-cell;width:1%}.nav-tabs.nav-justified&gt;li&gt;a{margin-bottom:0}}.nav-tabs.nav-justified&gt;li&gt;a{margin-right:0;border-radius:4px}.nav-tabs.nav-justified&gt;.active&gt;a,.nav-tabs.nav-justified&gt;.active&gt;a:focus,.nav-tabs.nav-justified&gt;.active&gt;a:hover{border:1px solid #ddd}@media (min-width:768px){.nav-tabs.nav-justified&gt;li&gt;a{border-bottom:1px solid #ddd;border-radius:4px 4px 0 0}.nav-tabs.nav-justified&gt;.active&gt;a,.nav-tabs.nav-justified&gt;.active&gt;a:focus,.nav-tabs.nav-justified&gt;.active&gt;a:hover{border-bottom-color:#fff}}.nav-pills&gt;li{float:left}.nav-pills&gt;li&gt;a{border-radius:4px}.nav-pills&gt;li+li{margin-left:2px}.nav-pills&gt;li.active&gt;a,.nav-pills&gt;li.active&gt;a:focus,.nav-pills&gt;li.active&gt;a:hover{color:#fff;background-color:#337ab7}.nav-stacked&gt;li{float:none}.nav-stacked&gt;li+li{margin-top:2px;margin-left:0}.nav-justified{width:100%}.nav-justified&gt;li{float:none}.nav-justified&gt;li&gt;a{margin-bottom:5px;text-align:center}.nav-justified&gt;.dropdown .dropdown-menu{top:auto;left:auto}@media (min-width:768px){.nav-justified&gt;li{display:table-cell;width:1%}.nav-justified&gt;li&gt;a{margin-bottom:0}}.nav-tabs-justified{border-bottom:0}.nav-tabs-justified&gt;li&gt;a{margin-right:0;border-radius:4px}.nav-tabs-justified&gt;.active&gt;a,.nav-tabs-justified&gt;.active&gt;a:focus,.nav-tabs-justified&gt;.active&gt;a:hover{border:1px solid #ddd}@media (min-width:768px){.nav-tabs-justified&gt;li&gt;a{border-bottom:1px solid #ddd;border-radius:4px 4px 0 0}.nav-tabs-justified&gt;.active&gt;a,.nav-tabs-justified&gt;.active&gt;a:focus,.nav-tabs-justified&gt;.active&gt;a:hover{border-bottom-color:#fff}}.tab-content&gt;.tab-pane{display:none}.tab-content&gt;.active{display:block}.nav-tabs .dropdown-menu{margin-top:-1px;border-top-left-radius:0;border-top-right-radius:0}.navbar{position:relative;min-height:50px;margin-bottom:20px;border:1px solid transparent}@media (min-width:768px){.navbar{border-radius:4px}}@media (min-width:768px){.navbar-header{float:left}}.navbar-collapse{padding-right:15px;padding-left:15px;overflow-x:visible;-webkit-overflow-scrolling:touch;border-top:1px solid transparent;-webkit-box-shadow:inset 0 1px 0 rgba(255,255,255,.1);box-shadow:inset 0 1px 0 rgba(255,255,255,.1)}.navbar-collapse.in{overflow-y:auto}@media (min-width:768px){.navbar-collapse{width:auto;border-top:0;-webkit-box-shadow:none;box-shadow:none}.navbar-collapse.collapse{display:block!important;height:auto!important;padding-bottom:0;overflow:visible!important}.navbar-collapse.in{overflow-y:visible}.navbar-fixed-bottom .navbar-collapse,.navbar-fixed-top .navbar-collapse,.navbar-static-top .navbar-collapse{padding-right:0;padding-left:0}}.navbar-fixed-bottom .navbar-collapse,.navbar-fixed-top .navbar-collapse{max-height:340px}@media (max-device-width:480px) and (orientation:landscape){.navbar-fixed-bottom .navbar-collapse,.navbar-fixed-top .navbar-collapse{max-height:200px}}.container-fluid&gt;.navbar-collapse,.container-fluid&gt;.navbar-header,.container&gt;.navbar-collapse,.container&gt;.navbar-header{margin-right:-15px;margin-left:-15px}@media (min-width:768px){.container-fluid&gt;.navbar-collapse,.container-fluid&gt;.navbar-header,.container&gt;.navbar-collapse,.container&gt;.navbar-header{margin-right:0;margin-left:0}}.navbar-static-top{z-index:1000;border-width:0 0 1px}@media (min-width:768px){.navbar-static-top{border-radius:0}}.navbar-fixed-bottom,.navbar-fixed-top{position:fixed;right:0;left:0;z-index:1030}@media (min-width:768px){.navbar-fixed-bottom,.navbar-fixed-top{border-radius:0}}.navbar-fixed-top{top:0;border-width:0 0 1px}.navbar-fixed-bottom{bottom:0;margin-bottom:0;border-width:1px 0 0}.navbar-brand{float:left;height:50px;padding:15px 15px;font-size:18px;line-height:20px}.navbar-brand:focus,.navbar-brand:hover{text-decoration:none}.navbar-brand&gt;img{display:block}@media (min-width:768px){.navbar&gt;.container .navbar-brand,.navbar&gt;.container-fluid .navbar-brand{margin-left:-15px}}.navbar-toggle{position:relative;float:right;padding:9px 10px;margin-top:8px;margin-right:15px;margin-bottom:8px;background-color:transparent;background-image:none;border:1px solid transparent;border-radius:4px}.navbar-toggle:focus{outline:0}.navbar-toggle .icon-bar{display:block;width:22px;height:2px;border-radius:1px}.navbar-toggle .icon-bar+.icon-bar{margin-top:4px}@media (min-width:768px){.navbar-toggle{display:none}}.navbar-nav{margin:7.5px -15px}.navbar-nav&gt;li&gt;a{padding-top:10px;padding-bottom:10px;line-height:20px}@media (max-width:767px){.navbar-nav .open .dropdown-menu{position:static;float:none;width:auto;margin-top:0;background-color:transparent;border:0;-webkit-box-shadow:none;box-shadow:none}.navbar-nav .open .dropdown-menu .dropdown-header,.navbar-nav .open .dropdown-menu&gt;li&gt;a{padding:5px 15px 5px 25px}.navbar-nav .open .dropdown-menu&gt;li&gt;a{line-height:20px}.navbar-nav .open .dropdown-menu&gt;li&gt;a:focus,.navbar-nav .open .dropdown-menu&gt;li&gt;a:hover{background-image:none}}@media (min-width:768px){.navbar-nav{float:left;margin:0}.navbar-nav&gt;li{float:left}.navbar-nav&gt;li&gt;a{padding-top:15px;padding-bottom:15px}}.navbar-form{padding:10px 15px;margin-top:8px;margin-right:-15px;margin-bottom:8px;margin-left:-15px;border-top:1px solid transparent;border-bottom:1px solid transparent;-webkit-box-shadow:inset 0 1px 0 rgba(255,255,255,.1),0 1px 0 rgba(255,255,255,.1);box-shadow:inset 0 1px 0 rgba(255,255,255,.1),0 1px 0 rgba(255,255,255,.1)}@media (min-width:768px){.navbar-form .form-group{display:inline-block;margin-bottom:0;vertical-align:middle}.navbar-form .form-control{display:inline-block;width:auto;vertical-align:middle}.navbar-form .form-control-static{display:inline-block}.navbar-form .input-group{display:inline-table;vertical-align:middle}.navbar-form .input-group .form-control,.navbar-form .input-group .input-group-addon,.navbar-form .input-group .input-group-btn{width:auto}.navbar-form .input-group&gt;.form-control{width:100%}.navbar-form .control-label{margin-bottom:0;vertical-align:middle}.navbar-form .checkbox,.navbar-form .radio{display:inline-block;margin-top:0;margin-bottom:0;vertical-align:middle}.navbar-form .checkbox label,.navbar-form .radio label{padding-left:0}.navbar-form .checkbox input[type=checkbox],.navbar-form .radio input[type=radio]{position:relative;margin-left:0}.navbar-form .has-feedback .form-control-feedback{top:0}}@media (max-width:767px){.navbar-form .form-group{margin-bottom:5px}.navbar-form .form-group:last-child{margin-bottom:0}}@media (min-width:768px){.navbar-form{width:auto;padding-top:0;padding-bottom:0;margin-right:0;margin-left:0;border:0;-webkit-box-shadow:none;box-shadow:none}}.navbar-nav&gt;li&gt;.dropdown-menu{margin-top:0;border-top-left-radius:0;border-top-right-radius:0}.navbar-fixed-bottom .navbar-nav&gt;li&gt;.dropdown-menu{margin-bottom:0;border-top-left-radius:4px;border-top-right-radius:4px;border-bottom-right-radius:0;border-bottom-left-radius:0}.navbar-btn{margin-top:8px;margin-bottom:8px}.navbar-btn.btn-sm{margin-top:10px;margin-bottom:10px}.navbar-btn.btn-xs{margin-top:14px;margin-bottom:14px}.navbar-text{margin-top:15px;margin-bottom:15px}@media (min-width:768px){.navbar-text{float:left;margin-right:15px;margin-left:15px}}@media (min-width:768px){.navbar-left{float:left!important}.navbar-right{float:right!important;margin-right:-15px}.navbar-right~.navbar-right{margin-right:0}}.navbar-default{background-color:#f8f8f8;border-color:#e7e7e7}.navbar-default .navbar-brand{color:#777}.navbar-default .navbar-brand:focus,.navbar-default .navbar-brand:hover{color:#5e5e5e;background-color:transparent}.navbar-default .navbar-text{color:#777}.navbar-default .navbar-nav&gt;li&gt;a{color:#777}.navbar-default .navbar-nav&gt;li&gt;a:focus,.navbar-default .navbar-nav&gt;li&gt;a:hover{color:#333;background-color:transparent}.navbar-default .navbar-nav&gt;.active&gt;a,.navbar-default .navbar-nav&gt;.active&gt;a:focus,.navbar-default .navbar-nav&gt;.active&gt;a:hover{color:#555;background-color:#e7e7e7}.navbar-default .navbar-nav&gt;.disabled&gt;a,.navbar-default .navbar-nav&gt;.disabled&gt;a:focus,.navbar-default .navbar-nav&gt;.disabled&gt;a:hover{color:#ccc;background-color:transparent}.navbar-default .navbar-toggle{border-color:#ddd}.navbar-default .navbar-toggle:focus,.navbar-default .navbar-toggle:hover{background-color:#ddd}.navbar-default .navbar-toggle .icon-bar{background-color:#888}.navbar-default .navbar-collapse,.navbar-default .navbar-form{border-color:#e7e7e7}.navbar-default .navbar-nav&gt;.open&gt;a,.navbar-default .navbar-nav&gt;.open&gt;a:focus,.navbar-default .navbar-nav&gt;.open&gt;a:hover{color:#555;background-color:#e7e7e7}@media (max-width:767px){.navbar-default .navbar-nav .open .dropdown-menu&gt;li&gt;a{color:#777}.navbar-default .navbar-nav .open .dropdown-menu&gt;li&gt;a:focus,.navbar-default .navbar-nav .open .dropdown-menu&gt;li&gt;a:hover{color:#333;background-color:transparent}.navbar-default .navbar-nav .open .dropdown-menu&gt;.active&gt;a,.navbar-default .navbar-nav .open .dropdown-menu&gt;.active&gt;a:focus,.navbar-default .navbar-nav .open .dropdown-menu&gt;.active&gt;a:hover{color:#555;background-color:#e7e7e7}.navbar-default .navbar-nav .open .dropdown-menu&gt;.disabled&gt;a,.navbar-default .navbar-nav .open .dropdown-menu&gt;.disabled&gt;a:focus,.navbar-default .navbar-nav .open .dropdown-menu&gt;.disabled&gt;a:hover{color:#ccc;background-color:transparent}}.navbar-default .navbar-link{color:#777}.navbar-default .navbar-link:hover{color:#333}.navbar-default .btn-link{color:#777}.navbar-default .btn-link:focus,.navbar-default .btn-link:hover{color:#333}.navbar-default .btn-link[disabled]:focus,.navbar-default .btn-link[disabled]:hover,fieldset[disabled] .navbar-default .btn-link:focus,fieldset[disabled] .navbar-default .btn-link:hover{color:#ccc}.navbar-inverse{background-color:#222;border-color:#080808}.navbar-inverse .navbar-brand{color:#9d9d9d}.navbar-inverse .navbar-brand:focus,.navbar-inverse .navbar-brand:hover{color:#fff;background-color:transparent}.navbar-inverse .navbar-text{color:#9d9d9d}.navbar-inverse .navbar-nav&gt;li&gt;a{color:#9d9d9d}.navbar-inverse .navbar-nav&gt;li&gt;a:focus,.navbar-inverse .navbar-nav&gt;li&gt;a:hover{color:#fff;background-color:transparent}.navbar-inverse .navbar-nav&gt;.active&gt;a,.navbar-inverse .navbar-nav&gt;.active&gt;a:focus,.navbar-inverse .navbar-nav&gt;.active&gt;a:hover{color:#fff;background-color:#080808}.navbar-inverse .navbar-nav&gt;.disabled&gt;a,.navbar-inverse .navbar-nav&gt;.disabled&gt;a:focus,.navbar-inverse .navbar-nav&gt;.disabled&gt;a:hover{color:#444;background-color:transparent}.navbar-inverse .navbar-toggle{border-color:#333}.navbar-inverse .navbar-toggle:focus,.navbar-inverse .navbar-toggle:hover{background-color:#333}.navbar-inverse .navbar-toggle .icon-bar{background-color:#fff}.navbar-inverse .navbar-collapse,.navbar-inverse .navbar-form{border-color:#101010}.navbar-inverse .navbar-nav&gt;.open&gt;a,.navbar-inverse .navbar-nav&gt;.open&gt;a:focus,.navbar-inverse .navbar-nav&gt;.open&gt;a:hover{color:#fff;background-color:#080808}@media (max-width:767px){.navbar-inverse .navbar-nav .open .dropdown-menu&gt;.dropdown-header{border-color:#080808}.navbar-inverse .navbar-nav .open .dropdown-menu .divider{background-color:#080808}.navbar-inverse .navbar-nav .open .dropdown-menu&gt;li&gt;a{color:#9d9d9d}.navbar-inverse .navbar-nav .open .dropdown-menu&gt;li&gt;a:focus,.navbar-inverse .navbar-nav .open .dropdown-menu&gt;li&gt;a:hover{color:#fff;background-color:transparent}.navbar-inverse .navbar-nav .open .dropdown-menu&gt;.active&gt;a,.navbar-inverse .navbar-nav .open .dropdown-menu&gt;.active&gt;a:focus,.navbar-inverse .navbar-nav .open .dropdown-menu&gt;.active&gt;a:hover{color:#fff;background-color:#080808}.navbar-inverse .navbar-nav .open .dropdown-menu&gt;.disabled&gt;a,.navbar-inverse .navbar-nav .open .dropdown-menu&gt;.disabled&gt;a:focus,.navbar-inverse .navbar-nav .open .dropdown-menu&gt;.disabled&gt;a:hover{color:#444;background-color:transparent}}.navbar-inverse .navbar-link{color:#9d9d9d}.navbar-inverse .navbar-link:hover{color:#fff}.navbar-inverse .btn-link{color:#9d9d9d}.navbar-inverse .btn-link:focus,.navbar-inverse .btn-link:hover{color:#fff}.navbar-inverse .btn-link[disabled]:focus,.navbar-inverse .btn-link[disabled]:hover,fieldset[disabled] .navbar-inverse .btn-link:focus,fieldset[disabled] .navbar-inverse .btn-link:hover{color:#444}.breadcrumb{padding:8px 15px;margin-bottom:20px;list-style:none;background-color:#f5f5f5;border-radius:4px}.breadcrumb&gt;li{display:inline-block}.breadcrumb&gt;li+li:before{padding:0 5px;color:#ccc;content:"/\00a0"}.breadcrumb&gt;.active{color:#777}.pagination{display:inline-block;padding-left:0;margin:20px 0;border-radius:4px}.pagination&gt;li{display:inline}.pagination&gt;li&gt;a,.pagination&gt;li&gt;span{position:relative;float:left;padding:6px 12px;margin-left:-1px;line-height:1.42857143;color:#337ab7;text-decoration:none;background-color:#fff;border:1px solid #ddd}.pagination&gt;li:first-child&gt;a,.pagination&gt;li:first-child&gt;span{margin-left:0;border-top-left-radius:4px;border-bottom-left-radius:4px}.pagination&gt;li:last-child&gt;a,.pagination&gt;li:last-child&gt;span{border-top-right-radius:4px;border-bottom-right-radius:4px}.pagination&gt;li&gt;a:focus,.pagination&gt;li&gt;a:hover,.pagination&gt;li&gt;span:focus,.pagination&gt;li&gt;span:hover{z-index:3;color:#23527c;background-color:#eee;border-color:#ddd}.pagination&gt;.active&gt;a,.pagination&gt;.active&gt;a:focus,.pagination&gt;.active&gt;a:hover,.pagination&gt;.active&gt;span,.pagination&gt;.active&gt;span:focus,.pagination&gt;.active&gt;span:hover{z-index:2;color:#fff;cursor:default;background-color:#337ab7;border-color:#337ab7}.pagination&gt;.disabled&gt;a,.pagination&gt;.disabled&gt;a:focus,.pagination&gt;.disabled&gt;a:hover,.pagination&gt;.disabled&gt;span,.pagination&gt;.disabled&gt;span:focus,.pagination&gt;.disabled&gt;span:hover{color:#777;cursor:not-allowed;background-color:#fff;border-color:#ddd}.pagination-lg&gt;li&gt;a,.pagination-lg&gt;li&gt;span{padding:10px 16px;font-size:18px;line-height:1.3333333}.pagination-lg&gt;li:first-child&gt;a,.pagination-lg&gt;li:first-child&gt;span{border-top-left-radius:6px;border-bottom-left-radius:6px}.pagination-lg&gt;li:last-child&gt;a,.pagination-lg&gt;li:last-child&gt;span{border-top-right-radius:6px;border-bottom-right-radius:6px}.pagination-sm&gt;li&gt;a,.pagination-sm&gt;li&gt;span{padding:5px 10px;font-size:12px;line-height:1.5}.pagination-sm&gt;li:first-child&gt;a,.pagination-sm&gt;li:first-child&gt;span{border-top-left-radius:3px;border-bottom-left-radius:3px}.pagination-sm&gt;li:last-child&gt;a,.pagination-sm&gt;li:last-child&gt;span{border-top-right-radius:3px;border-bottom-right-radius:3px}.pager{padding-left:0;margin:20px 0;text-align:center;list-style:none}.pager li{display:inline}.pager li&gt;a,.pager li&gt;span{display:inline-block;padding:5px 14px;background-color:#fff;border:1px solid #ddd;border-radius:15px}.pager li&gt;a:focus,.pager li&gt;a:hover{text-decoration:none;background-color:#eee}.pager .next&gt;a,.pager .next&gt;span{float:right}.pager .previous&gt;a,.pager .previous&gt;span{float:left}.pager .disabled&gt;a,.pager .disabled&gt;a:focus,.pager .disabled&gt;a:hover,.pager .disabled&gt;span{color:#777;cursor:not-allowed;background-color:#fff}.label{display:inline;padding:.2em .6em .3em;font-size:75%;font-weight:700;line-height:1;color:#fff;text-align:center;white-space:nowrap;vertical-align:baseline;border-radius:.25em}a.label:focus,a.label:hover{color:#fff;text-decoration:none;cursor:pointer}.label:empty{display:none}.btn .label{position:relative;top:-1px}.label-default{background-color:#777}.label-default[href]:focus,.label-default[href]:hover{background-color:#5e5e5e}.label-primary{background-color:#337ab7}.label-primary[href]:focus,.label-primary[href]:hover{background-color:#286090}.label-success{background-color:#5cb85c}.label-success[href]:focus,.label-success[href]:hover{background-color:#449d44}.label-info{background-color:#5bc0de}.label-info[href]:focus,.label-info[href]:hover{background-color:#31b0d5}.label-warning{background-color:#f0ad4e}.label-warning[href]:focus,.label-warning[href]:hover{background-color:#ec971f}.label-danger{background-color:#d9534f}.label-danger[href]:focus,.label-danger[href]:hover{background-color:#c9302c}.badge{display:inline-block;min-width:10px;padding:3px 7px;font-size:12px;font-weight:700;line-height:1;color:#fff;text-align:center;white-space:nowrap;vertical-align:middle;background-color:#777;border-radius:10px}.badge:empty{display:none}.btn .badge{position:relative;top:-1px}.btn-group-xs&gt;.btn .badge,.btn-xs .badge{top:0;padding:1px 5px}a.badge:focus,a.badge:hover{color:#fff;text-decoration:none;cursor:pointer}.list-group-item.active&gt;.badge,.nav-pills&gt;.active&gt;a&gt;.badge{color:#337ab7;background-color:#fff}.list-group-item&gt;.badge{float:right}.list-group-item&gt;.badge+.badge{margin-right:5px}.nav-pills&gt;li&gt;a&gt;.badge{margin-left:3px}.jumbotron{padding-top:30px;padding-bottom:30px;margin-bottom:30px;color:inherit;background-color:#eee}.jumbotron .h1,.jumbotron h1{color:inherit}.jumbotron p{margin-bottom:15px;font-size:21px;font-weight:200}.jumbotron&gt;hr{border-top-color:#d5d5d5}.container .jumbotron,.container-fluid .jumbotron{border-radius:6px}.jumbotron .container{max-width:100%}@media screen and (min-width:768px){.jumbotron{padding-top:48px;padding-bottom:48px}.container .jumbotron,.container-fluid .jumbotron{padding-right:60px;padding-left:60px}.jumbotron .h1,.jumbotron h1{font-size:63px}}.thumbnail{display:block;padding:4px;margin-bottom:20px;line-height:1.42857143;background-color:#fff;border:1px solid #ddd;border-radius:4px;-webkit-transition:border .2s ease-in-out;-o-transition:border .2s ease-in-out;transition:border .2s ease-in-out}.thumbnail a&gt;img,.thumbnail&gt;img{margin-right:auto;margin-left:auto}a.thumbnail.active,a.thumbnail:focus,a.thumbnail:hover{border-color:#337ab7}.thumbnail .caption{padding:9px;color:#333}.alert{padding:15px;margin-bottom:20px;border:1px solid transparent;border-radius:4px}.alert h4{margin-top:0;color:inherit}.alert .alert-link{font-weight:700}.alert&gt;p,.alert&gt;ul{margin-bottom:0}.alert&gt;p+p{margin-top:5px}.alert-dismissable,.alert-dismissible{padding-right:35px}.alert-dismissable .close,.alert-dismissible .close{position:relative;top:-2px;right:-21px;color:inherit}.alert-success{color:#3c763d;background-color:#dff0d8;border-color:#d6e9c6}.alert-success hr{border-top-color:#c9e2b3}.alert-success .alert-link{color:#2b542c}.alert-info{color:#31708f;background-color:#d9edf7;border-color:#bce8f1}.alert-info hr{border-top-color:#a6e1ec}.alert-info .alert-link{color:#245269}.alert-warning{color:#8a6d3b;background-color:#fcf8e3;border-color:#faebcc}.alert-warning hr{border-top-color:#f7e1b5}.alert-warning .alert-link{color:#66512c}.alert-danger{color:#a94442;background-color:#f2dede;border-color:#ebccd1}.alert-danger hr{border-top-color:#e4b9c0}.alert-danger .alert-link{color:#843534}@-webkit-keyframes progress-bar-stripes{from{background-position:40px 0}to{background-position:0 0}}@-o-keyframes progress-bar-stripes{from{background-position:40px 0}to{background-position:0 0}}@keyframes progress-bar-stripes{from{background-position:40px 0}to{background-position:0 0}}.progress{height:20px;margin-bottom:20px;overflow:hidden;background-color:#f5f5f5;border-radius:4px;-webkit-box-shadow:inset 0 1px 2px rgba(0,0,0,.1);box-shadow:inset 0 1px 2px rgba(0,0,0,.1)}.progress-bar{float:left;width:0;height:100%;font-size:12px;line-height:20px;color:#fff;text-align:center;background-color:#337ab7;-webkit-box-shadow:inset 0 -1px 0 rgba(0,0,0,.15);box-shadow:inset 0 -1px 0 rgba(0,0,0,.15);-webkit-transition:width .6s ease;-o-transition:width .6s ease;transition:width .6s ease}.progress-bar-striped,.progress-striped .progress-bar{background-image:-webkit-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:-o-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);-webkit-background-size:40px 40px;background-size:40px 40px}.progress-bar.active,.progress.active .progress-bar{-webkit-animation:progress-bar-stripes 2s linear infinite;-o-animation:progress-bar-stripes 2s linear infinite;animation:progress-bar-stripes 2s linear infinite}.progress-bar-success{background-color:#5cb85c}.progress-striped .progress-bar-success{background-image:-webkit-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:-o-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent)}.progress-bar-info{background-color:#5bc0de}.progress-striped .progress-bar-info{background-image:-webkit-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:-o-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent)}.progress-bar-warning{background-color:#f0ad4e}.progress-striped .progress-bar-warning{background-image:-webkit-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:-o-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent)}.progress-bar-danger{background-color:#d9534f}.progress-striped .progress-bar-danger{background-image:-webkit-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:-o-linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent);background-image:linear-gradient(45deg,rgba(255,255,255,.15) 25%,transparent 25%,transparent 50%,rgba(255,255,255,.15) 50%,rgba(255,255,255,.15) 75%,transparent 75%,transparent)}.media{margin-top:15px}.media:first-child{margin-top:0}.media,.media-body{overflow:hidden;zoom:1}.media-body{width:10000px}.media-object{display:block}.media-object.img-thumbnail{max-width:none}.media-right,.media&gt;.pull-right{padding-left:10px}.media-left,.media&gt;.pull-left{padding-right:10px}.media-body,.media-left,.media-right{display:table-cell;vertical-align:top}.media-middle{vertical-align:middle}.media-bottom{vertical-align:bottom}.media-heading{margin-top:0;margin-bottom:5px}.media-list{padding-left:0;list-style:none}.list-group{padding-left:0;margin-bottom:20px}.list-group-item{position:relative;display:block;padding:10px 15px;margin-bottom:-1px;background-color:#fff;border:1px solid #ddd}.list-group-item:first-child{border-top-left-radius:4px;border-top-right-radius:4px}.list-group-item:last-child{margin-bottom:0;border-bottom-right-radius:4px;border-bottom-left-radius:4px}a.list-group-item,button.list-group-item{color:#555}a.list-group-item .list-group-item-heading,button.list-group-item .list-group-item-heading{color:#333}
				
            </xsl:if>
            <xsl:if test="2 &gt; 1">
                
a.list-group-item:focus,a.list-group-item:hover,button.list-group-item:focus,button.list-group-item:hover{color:#555;text-decoration:none;background-color:#f5f5f5}button.list-group-item{width:100%;text-align:left}.list-group-item.disabled,.list-group-item.disabled:focus,.list-group-item.disabled:hover{color:#777;cursor:not-allowed;background-color:#eee}.list-group-item.disabled .list-group-item-heading,.list-group-item.disabled:focus .list-group-item-heading,.list-group-item.disabled:hover .list-group-item-heading{color:inherit}.list-group-item.disabled .list-group-item-text,.list-group-item.disabled:focus .list-group-item-text,.list-group-item.disabled:hover .list-group-item-text{color:#777}.list-group-item.active,.list-group-item.active:focus,.list-group-item.active:hover{z-index:2;color:#fff;background-color:#337ab7;border-color:#337ab7}.list-group-item.active .list-group-item-heading,.list-group-item.active .list-group-item-heading&gt;.small,.list-group-item.active .list-group-item-heading&gt;small,.list-group-item.active:focus .list-group-item-heading,.list-group-item.active:focus .list-group-item-heading&gt;.small,.list-group-item.active:focus .list-group-item-heading&gt;small,.list-group-item.active:hover .list-group-item-heading,.list-group-item.active:hover .list-group-item-heading&gt;.small,.list-group-item.active:hover .list-group-item-heading&gt;small{color:inherit}.list-group-item.active .list-group-item-text,.list-group-item.active:focus .list-group-item-text,.list-group-item.active:hover .list-group-item-text{color:#c7ddef}.list-group-item-success{color:#3c763d;background-color:#dff0d8}a.list-group-item-success,button.list-group-item-success{color:#3c763d}a.list-group-item-success .list-group-item-heading,button.list-group-item-success .list-group-item-heading{color:inherit}a.list-group-item-success:focus,a.list-group-item-success:hover,button.list-group-item-success:focus,button.list-group-item-success:hover{color:#3c763d;background-color:#d0e9c6}a.list-group-item-success.active,a.list-group-item-success.active:focus,a.list-group-item-success.active:hover,button.list-group-item-success.active,button.list-group-item-success.active:focus,button.list-group-item-success.active:hover{color:#fff;background-color:#3c763d;border-color:#3c763d}.list-group-item-info{color:#31708f;background-color:#d9edf7}a.list-group-item-info,button.list-group-item-info{color:#31708f}a.list-group-item-info .list-group-item-heading,button.list-group-item-info .list-group-item-heading{color:inherit}a.list-group-item-info:focus,a.list-group-item-info:hover,button.list-group-item-info:focus,button.list-group-item-info:hover{color:#31708f;background-color:#c4e3f3}a.list-group-item-info.active,a.list-group-item-info.active:focus,a.list-group-item-info.active:hover,button.list-group-item-info.active,button.list-group-item-info.active:focus,button.list-group-item-info.active:hover{color:#fff;background-color:#31708f;border-color:#31708f}.list-group-item-warning{color:#8a6d3b;background-color:#fcf8e3}a.list-group-item-warning,button.list-group-item-warning{color:#8a6d3b}a.list-group-item-warning .list-group-item-heading,button.list-group-item-warning .list-group-item-heading{color:inherit}a.list-group-item-warning:focus,a.list-group-item-warning:hover,button.list-group-item-warning:focus,button.list-group-item-warning:hover{color:#8a6d3b;background-color:#faf2cc}a.list-group-item-warning.active,a.list-group-item-warning.active:focus,a.list-group-item-warning.active:hover,button.list-group-item-warning.active,button.list-group-item-warning.active:focus,button.list-group-item-warning.active:hover{color:#fff;background-color:#8a6d3b;border-color:#8a6d3b}.list-group-item-danger{color:#a94442;background-color:#f2dede}a.list-group-item-danger,button.list-group-item-danger{color:#a94442}a.list-group-item-danger .list-group-item-heading,button.list-group-item-danger .list-group-item-heading{color:inherit}a.list-group-item-danger:focus,a.list-group-item-danger:hover,button.list-group-item-danger:focus,button.list-group-item-danger:hover{color:#a94442;background-color:#ebcccc}a.list-group-item-danger.active,a.list-group-item-danger.active:focus,a.list-group-item-danger.active:hover,button.list-group-item-danger.active,button.list-group-item-danger.active:focus,button.list-group-item-danger.active:hover{color:#fff;background-color:#a94442;border-color:#a94442}.list-group-item-heading{margin-top:0;margin-bottom:5px}.list-group-item-text{margin-bottom:0;line-height:1.3}.panel{margin-bottom:20px;background-color:#fff;border:1px solid transparent;border-radius:4px;-webkit-box-shadow:0 1px 1px rgba(0,0,0,.05);box-shadow:0 1px 1px rgba(0,0,0,.05)}.panel-body{padding:15px}.panel-heading{padding:10px 15px;border-bottom:1px solid transparent;border-top-left-radius:3px;border-top-right-radius:3px}.panel-heading&gt;.dropdown .dropdown-toggle{color:inherit}.panel-title{margin-top:0;margin-bottom:0;font-size:16px;color:inherit}.panel-title&gt;.small,.panel-title&gt;.small&gt;a,.panel-title&gt;a,.panel-title&gt;small,.panel-title&gt;small&gt;a{color:inherit}.panel-footer{padding:10px 15px;background-color:#f5f5f5;border-top:1px solid #ddd;border-bottom-right-radius:3px;border-bottom-left-radius:3px}.panel&gt;.list-group,.panel&gt;.panel-collapse&gt;.list-group{margin-bottom:0}.panel&gt;.list-group .list-group-item,.panel&gt;.panel-collapse&gt;.list-group .list-group-item{border-width:1px 0;border-radius:0}.panel&gt;.list-group:first-child .list-group-item:first-child,.panel&gt;.panel-collapse&gt;.list-group:first-child .list-group-item:first-child{border-top:0;border-top-left-radius:3px;border-top-right-radius:3px}.panel&gt;.list-group:last-child .list-group-item:last-child,.panel&gt;.panel-collapse&gt;.list-group:last-child .list-group-item:last-child{border-bottom:0;border-bottom-right-radius:3px;border-bottom-left-radius:3px}.panel&gt;.panel-heading+.panel-collapse&gt;.list-group .list-group-item:first-child{border-top-left-radius:0;border-top-right-radius:0}.panel-heading+.list-group .list-group-item:first-child{border-top-width:0}.list-group+.panel-footer{border-top-width:0}.panel&gt;.panel-collapse&gt;.table,.panel&gt;.table,.panel&gt;.table-responsive&gt;.table{margin-bottom:0}.panel&gt;.panel-collapse&gt;.table caption,.panel&gt;.table caption,.panel&gt;.table-responsive&gt;.table caption{padding-right:15px;padding-left:15px}.panel&gt;.table-responsive:first-child&gt;.table:first-child,.panel&gt;.table:first-child{border-top-left-radius:3px;border-top-right-radius:3px}.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child,.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child,.panel&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child,.panel&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child{border-top-left-radius:3px;border-top-right-radius:3px}.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child td:first-child,.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child th:first-child,.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child td:first-child,.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child th:first-child,.panel&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child td:first-child,.panel&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child th:first-child,.panel&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child td:first-child,.panel&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child th:first-child{border-top-left-radius:3px}.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child td:last-child,.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child th:last-child,.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child td:last-child,.panel&gt;.table-responsive:first-child&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child th:last-child,.panel&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child td:last-child,.panel&gt;.table:first-child&gt;tbody:first-child&gt;tr:first-child th:last-child,.panel&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child td:last-child,.panel&gt;.table:first-child&gt;thead:first-child&gt;tr:first-child th:last-child{border-top-right-radius:3px}.panel&gt;.table-responsive:last-child&gt;.table:last-child,.panel&gt;.table:last-child{border-bottom-right-radius:3px;border-bottom-left-radius:3px}.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child,.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child,.panel&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child,.panel&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child{border-bottom-right-radius:3px;border-bottom-left-radius:3px}.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child td:first-child,.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child th:first-child,.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child td:first-child,.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child th:first-child,.panel&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child td:first-child,.panel&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child th:first-child,.panel&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child td:first-child,.panel&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child th:first-child{border-bottom-left-radius:3px}.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child td:last-child,.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child th:last-child,.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child td:last-child,.panel&gt;.table-responsive:last-child&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child th:last-child,.panel&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child td:last-child,.panel&gt;.table:last-child&gt;tbody:last-child&gt;tr:last-child th:last-child,.panel&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child td:last-child,.panel&gt;.table:last-child&gt;tfoot:last-child&gt;tr:last-child th:last-child{border-bottom-right-radius:3px}.panel&gt;.panel-body+.table,.panel&gt;.panel-body+.table-responsive,.panel&gt;.table+.panel-body,.panel&gt;.table-responsive+.panel-body{border-top:1px solid #ddd}.panel&gt;.table&gt;tbody:first-child&gt;tr:first-child td,.panel&gt;.table&gt;tbody:first-child&gt;tr:first-child th{border-top:0}.panel&gt;.table-bordered,.panel&gt;.table-responsive&gt;.table-bordered{border:0}.panel&gt;.table-bordered&gt;tbody&gt;tr&gt;td:first-child,.panel&gt;.table-bordered&gt;tbody&gt;tr&gt;th:first-child,.panel&gt;.table-bordered&gt;tfoot&gt;tr&gt;td:first-child,.panel&gt;.table-bordered&gt;tfoot&gt;tr&gt;th:first-child,.panel&gt;.table-bordered&gt;thead&gt;tr&gt;td:first-child,.panel&gt;.table-bordered&gt;thead&gt;tr&gt;th:first-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;td:first-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;th:first-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;td:first-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;th:first-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;td:first-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;th:first-child{border-left:0}.panel&gt;.table-bordered&gt;tbody&gt;tr&gt;td:last-child,.panel&gt;.table-bordered&gt;tbody&gt;tr&gt;th:last-child,.panel&gt;.table-bordered&gt;tfoot&gt;tr&gt;td:last-child,.panel&gt;.table-bordered&gt;tfoot&gt;tr&gt;th:last-child,.panel&gt;.table-bordered&gt;thead&gt;tr&gt;td:last-child,.panel&gt;.table-bordered&gt;thead&gt;tr&gt;th:last-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;td:last-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr&gt;th:last-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;td:last-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr&gt;th:last-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;td:last-child,.panel&gt;.table-responsive&gt;.table-bordered&gt;thead&gt;tr&gt;th:last-child{border-right:0}.panel&gt;.table-bordered&gt;tbody&gt;tr:first-child&gt;td,.panel&gt;.table-bordered&gt;tbody&gt;tr:first-child&gt;th,.panel&gt;.table-bordered&gt;thead&gt;tr:first-child&gt;td,.panel&gt;.table-bordered&gt;thead&gt;tr:first-child&gt;th,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr:first-child&gt;td,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr:first-child&gt;th,.panel&gt;.table-responsive&gt;.table-bordered&gt;thead&gt;tr:first-child&gt;td,.panel&gt;.table-responsive&gt;.table-bordered&gt;thead&gt;tr:first-child&gt;th{border-bottom:0}.panel&gt;.table-bordered&gt;tbody&gt;tr:last-child&gt;td,.panel&gt;.table-bordered&gt;tbody&gt;tr:last-child&gt;th,.panel&gt;.table-bordered&gt;tfoot&gt;tr:last-child&gt;td,.panel&gt;.table-bordered&gt;tfoot&gt;tr:last-child&gt;th,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr:last-child&gt;td,.panel&gt;.table-responsive&gt;.table-bordered&gt;tbody&gt;tr:last-child&gt;th,.panel&gt;.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr:last-child&gt;td,.panel&gt;.table-responsive&gt;.table-bordered&gt;tfoot&gt;tr:last-child&gt;th{border-bottom:0}.panel&gt;.table-responsive{margin-bottom:0;border:0}.panel-group{margin-bottom:20px}.panel-group .panel{margin-bottom:0;border-radius:4px}.panel-group .panel+.panel{margin-top:5px}.panel-group .panel-heading{border-bottom:0}.panel-group .panel-heading+.panel-collapse&gt;.list-group,.panel-group .panel-heading+.panel-collapse&gt;.panel-body{border-top:1px solid #ddd}.panel-group .panel-footer{border-top:0}.panel-group .panel-footer+.panel-collapse .panel-body{border-bottom:1px solid #ddd}.panel-default{border-color:#ddd}.panel-default&gt;.panel-heading{color:#333;background-color:#f5f5f5;border-color:#ddd}.panel-default&gt;.panel-heading+.panel-collapse&gt;.panel-body{border-top-color:#ddd}.panel-default&gt;.panel-heading .badge{color:#f5f5f5;background-color:#333}.panel-default&gt;.panel-footer+.panel-collapse&gt;.panel-body{border-bottom-color:#ddd}.panel-primary{border-color:#337ab7}.panel-primary&gt;.panel-heading{color:#fff;background-color:#337ab7;border-color:#337ab7}.panel-primary&gt;.panel-heading+.panel-collapse&gt;.panel-body{border-top-color:#337ab7}.panel-primary&gt;.panel-heading .badge{color:#337ab7;background-color:#fff}.panel-primary&gt;.panel-footer+.panel-collapse&gt;.panel-body{border-bottom-color:#337ab7}.panel-success{border-color:#d6e9c6}.panel-success&gt;.panel-heading{color:#3c763d;background-color:#dff0d8;border-color:#d6e9c6}.panel-success&gt;.panel-heading+.panel-collapse&gt;.panel-body{border-top-color:#d6e9c6}.panel-success&gt;.panel-heading .badge{color:#dff0d8;background-color:#3c763d}.panel-success&gt;.panel-footer+.panel-collapse&gt;.panel-body{border-bottom-color:#d6e9c6}.panel-info{border-color:#bce8f1}.panel-info&gt;.panel-heading{color:#31708f;background-color:#d9edf7;border-color:#bce8f1}.panel-info&gt;.panel-heading+.panel-collapse&gt;.panel-body{border-top-color:#bce8f1}.panel-info&gt;.panel-heading .badge{color:#d9edf7;background-color:#31708f}.panel-info&gt;.panel-footer+.panel-collapse&gt;.panel-body{border-bottom-color:#bce8f1}.panel-warning{border-color:#faebcc}.panel-warning&gt;.panel-heading{color:#8a6d3b;background-color:#fcf8e3;border-color:#faebcc}.panel-warning&gt;.panel-heading+.panel-collapse&gt;.panel-body{border-top-color:#faebcc}.panel-warning&gt;.panel-heading .badge{color:#fcf8e3;background-color:#8a6d3b}.panel-warning&gt;.panel-footer+.panel-collapse&gt;.panel-body{border-bottom-color:#faebcc}.panel-danger{border-color:#ebccd1}.panel-danger&gt;.panel-heading{color:#a94442;background-color:#f2dede;border-color:#ebccd1}.panel-danger&gt;.panel-heading+.panel-collapse&gt;.panel-body{border-top-color:#ebccd1}.panel-danger&gt;.panel-heading .badge{color:#f2dede;background-color:#a94442}.panel-danger&gt;.panel-footer+.panel-collapse&gt;.panel-body{border-bottom-color:#ebccd1}.embed-responsive{position:relative;display:block;height:0;padding:0;overflow:hidden}.embed-responsive .embed-responsive-item,.embed-responsive embed,.embed-responsive iframe,.embed-responsive object,.embed-responsive video{position:absolute;top:0;bottom:0;left:0;width:100%;height:100%;border:0}.embed-responsive-16by9{padding-bottom:56.25%}.embed-responsive-4by3{padding-bottom:75%}.well{min-height:20px;padding:19px;margin-bottom:20px;background-color:#f5f5f5;border:1px solid #e3e3e3;border-radius:4px;-webkit-box-shadow:inset 0 1px 1px rgba(0,0,0,.05);box-shadow:inset 0 1px 1px rgba(0,0,0,.05)}.well blockquote{border-color:#ddd;border-color:rgba(0,0,0,.15)}.well-lg{padding:24px;border-radius:6px}.well-sm{padding:9px;border-radius:3px}.close{float:right;font-size:21px;font-weight:700;line-height:1;color:#000;text-shadow:0 1px 0 #fff;filter:alpha(opacity=20);opacity:.2}.close:focus,.close:hover{color:#000;text-decoration:none;cursor:pointer;filter:alpha(opacity=50);opacity:.5}button.close{-webkit-appearance:none;padding:0;cursor:pointer;background:0 0;border:0}.modal-open{overflow:hidden}.modal{position:fixed;top:0;right:0;bottom:0;left:0;z-index:1050;display:none;overflow:hidden;-webkit-overflow-scrolling:touch;outline:0}.modal.fade .modal-dialog{-webkit-transition:-webkit-transform .3s ease-out;-o-transition:-o-transform .3s ease-out;transition:transform .3s ease-out;-webkit-transform:translate(0,-25%);-ms-transform:translate(0,-25%);-o-transform:translate(0,-25%);transform:translate(0,-25%)}.modal.in .modal-dialog{-webkit-transform:translate(0,0);-ms-transform:translate(0,0);-o-transform:translate(0,0);transform:translate(0,0)}.modal-open .modal{overflow-x:hidden;overflow-y:auto}.modal-dialog{position:relative;width:auto;margin:10px}.modal-content{position:relative;background-color:#fff;-webkit-background-clip:padding-box;background-clip:padding-box;border:1px solid #999;border:1px solid rgba(0,0,0,.2);border-radius:6px;outline:0;-webkit-box-shadow:0 3px 9px rgba(0,0,0,.5);box-shadow:0 3px 9px rgba(0,0,0,.5)}.modal-backdrop{position:fixed;top:0;right:0;bottom:0;left:0;z-index:1040;background-color:#000}.modal-backdrop.fade{filter:alpha(opacity=0);opacity:0}.modal-backdrop.in{filter:alpha(opacity=50);opacity:.5}.modal-header{min-height:16.43px;padding:15px;border-bottom:1px solid #e5e5e5}.modal-header .close{margin-top:-2px}.modal-title{margin:0;line-height:1.42857143}.modal-body{position:relative;padding:15px}.modal-footer{padding:15px;text-align:right;border-top:1px solid #e5e5e5}.modal-footer .btn+.btn{margin-bottom:0;margin-left:5px}.modal-footer .btn-group .btn+.btn{margin-left:-1px}.modal-footer .btn-block+.btn-block{margin-left:0}.modal-scrollbar-measure{position:absolute;top:-9999px;width:50px;height:50px;overflow:scroll}@media (min-width:768px){.modal-dialog{width:600px;margin:30px auto}.modal-content{-webkit-box-shadow:0 5px 15px rgba(0,0,0,.5);box-shadow:0 5px 15px rgba(0,0,0,.5)}.modal-sm{width:300px}}@media (min-width:992px){.modal-lg{width:900px}}.tooltip{position:absolute;z-index:1070;display:block;font-family:"Helvetica Neue",Helvetica,Arial,sans-serif;font-size:12px;font-style:normal;font-weight:400;line-height:1.42857143;text-align:left;text-align:start;text-decoration:none;text-shadow:none;text-transform:none;letter-spacing:normal;word-break:normal;word-spacing:normal;word-wrap:normal;white-space:normal;filter:alpha(opacity=0);opacity:0;line-break:auto}.tooltip.in{filter:alpha(opacity=90);opacity:.9}.tooltip.top{padding:5px 0;margin-top:-3px}.tooltip.right{padding:0 5px;margin-left:3px}.tooltip.bottom{padding:5px 0;margin-top:3px}.tooltip.left{padding:0 5px;margin-left:-3px}.tooltip-inner{max-width:200px;padding:3px 8px;color:#fff;text-align:center;background-color:#000;border-radius:4px}.tooltip-arrow{position:absolute;width:0;height:0;border-color:transparent;border-style:solid}.tooltip.top .tooltip-arrow{bottom:0;left:50%;margin-left:-5px;border-width:5px 5px 0;border-top-color:#000}.tooltip.top-left .tooltip-arrow{right:5px;bottom:0;margin-bottom:-5px;border-width:5px 5px 0;border-top-color:#000}.tooltip.top-right .tooltip-arrow{bottom:0;left:5px;margin-bottom:-5px;border-width:5px 5px 0;border-top-color:#000}.tooltip.right .tooltip-arrow{top:50%;left:0;margin-top:-5px;border-width:5px 5px 5px 0;border-right-color:#000}.tooltip.left .tooltip-arrow{top:50%;right:0;margin-top:-5px;border-width:5px 0 5px 5px;border-left-color:#000}.tooltip.bottom .tooltip-arrow{top:0;left:50%;margin-left:-5px;border-width:0 5px 5px;border-bottom-color:#000}.tooltip.bottom-left .tooltip-arrow{top:0;right:5px;margin-top:-5px;border-width:0 5px 5px;border-bottom-color:#000}.tooltip.bottom-right .tooltip-arrow{top:0;left:5px;margin-top:-5px;border-width:0 5px 5px;border-bottom-color:#000}.popover{position:absolute;top:0;left:0;z-index:1060;display:none;max-width:276px;padding:1px;font-family:"Helvetica Neue",Helvetica,Arial,sans-serif;font-size:14px;font-style:normal;font-weight:400;line-height:1.42857143;text-align:left;text-align:start;text-decoration:none;text-shadow:none;text-transform:none;letter-spacing:normal;word-break:normal;word-spacing:normal;word-wrap:normal;white-space:normal;background-color:#fff;-webkit-background-clip:padding-box;background-clip:padding-box;border:1px solid #ccc;border:1px solid rgba(0,0,0,.2);border-radius:6px;-webkit-box-shadow:0 5px 10px rgba(0,0,0,.2);box-shadow:0 5px 10px rgba(0,0,0,.2);line-break:auto}.popover.top{margin-top:-10px}.popover.right{margin-left:10px}.popover.bottom{margin-top:10px}.popover.left{margin-left:-10px}.popover-title{padding:8px 14px;margin:0;font-size:14px;background-color:#f7f7f7;border-bottom:1px solid #ebebeb;border-radius:5px 5px 0 0}.popover-content{padding:9px 14px}.popover&gt;.arrow,.popover&gt;.arrow:after{position:absolute;display:block;width:0;height:0;border-color:transparent;border-style:solid}.popover&gt;.arrow{border-width:11px}.popover&gt;.arrow:after{content:"";border-width:10px}.popover.top&gt;.arrow{bottom:-11px;left:50%;margin-left:-11px;border-top-color:#999;border-top-color:rgba(0,0,0,.25);border-bottom-width:0}.popover.top&gt;.arrow:after{bottom:1px;margin-left:-10px;content:" ";border-top-color:#fff;border-bottom-width:0}.popover.right&gt;.arrow{top:50%;left:-11px;margin-top:-11px;border-right-color:#999;border-right-color:rgba(0,0,0,.25);border-left-width:0}.popover.right&gt;.arrow:after{bottom:-10px;left:1px;content:" ";border-right-color:#fff;border-left-width:0}.popover.bottom&gt;.arrow{top:-11px;left:50%;margin-left:-11px;border-top-width:0;border-bottom-color:#999;border-bottom-color:rgba(0,0,0,.25)}.popover.bottom&gt;.arrow:after{top:1px;margin-left:-10px;content:" ";border-top-width:0;border-bottom-color:#fff}.popover.left&gt;.arrow{top:50%;right:-11px;margin-top:-11px;border-right-width:0;border-left-color:#999;border-left-color:rgba(0,0,0,.25)}.popover.left&gt;.arrow:after{right:1px;bottom:-10px;content:" ";border-right-width:0;border-left-color:#fff}.carousel{position:relative}.carousel-inner{position:relative;width:100%;overflow:hidden}.carousel-inner&gt;.item{position:relative;display:none;-webkit-transition:.6s ease-in-out left;-o-transition:.6s ease-in-out left;transition:.6s ease-in-out left}.carousel-inner&gt;.item&gt;a&gt;img,.carousel-inner&gt;.item&gt;img{line-height:1}@media all and (transform-3d),(-webkit-transform-3d){.carousel-inner&gt;.item{-webkit-transition:-webkit-transform .6s ease-in-out;-o-transition:-o-transform .6s ease-in-out;transition:transform .6s ease-in-out;-webkit-backface-visibility:hidden;backface-visibility:hidden;-webkit-perspective:1000px;perspective:1000px}.carousel-inner&gt;.item.active.right,.carousel-inner&gt;.item.next{left:0;-webkit-transform:translate3d(100%,0,0);transform:translate3d(100%,0,0)}.carousel-inner&gt;.item.active.left,.carousel-inner&gt;.item.prev{left:0;-webkit-transform:translate3d(-100%,0,0);transform:translate3d(-100%,0,0)}.carousel-inner&gt;.item.active,.carousel-inner&gt;.item.next.left,.carousel-inner&gt;.item.prev.right{left:0;-webkit-transform:translate3d(0,0,0);transform:translate3d(0,0,0)}}.carousel-inner&gt;.active,.carousel-inner&gt;.next,.carousel-inner&gt;.prev{display:block}.carousel-inner&gt;.active{left:0}.carousel-inner&gt;.next,.carousel-inner&gt;.prev{position:absolute;top:0;width:100%}.carousel-inner&gt;.next{left:100%}.carousel-inner&gt;.prev{left:-100%}.carousel-inner&gt;.next.left,.carousel-inner&gt;.prev.right{left:0}.carousel-inner&gt;.active.left{left:-100%}.carousel-inner&gt;.active.right{left:100%}.carousel-control{position:absolute;top:0;bottom:0;left:0;width:15%;font-size:20px;color:#fff;text-align:center;text-shadow:0 1px 2px rgba(0,0,0,.6);filter:alpha(opacity=50);opacity:.5}.carousel-control.left{background-image:-webkit-linear-gradient(left,rgba(0,0,0,.5) 0,rgba(0,0,0,.0001) 100%);background-image:-o-linear-gradient(left,rgba(0,0,0,.5) 0,rgba(0,0,0,.0001) 100%);background-image:-webkit-gradient(linear,left top,right top,from(rgba(0,0,0,.5)),to(rgba(0,0,0,.0001)));background-image:linear-gradient(to right,rgba(0,0,0,.5) 0,rgba(0,0,0,.0001) 100%);filter:progid:DXImageTransform.Microsoft.gradient(startColorstr='#80000000', endColorstr='#00000000', GradientType=1);background-repeat:repeat-x}.carousel-control.right{right:0;left:auto;background-image:-webkit-linear-gradient(left,rgba(0,0,0,.0001) 0,rgba(0,0,0,.5) 100%);background-image:-o-linear-gradient(left,rgba(0,0,0,.0001) 0,rgba(0,0,0,.5) 100%);background-image:-webkit-gradient(linear,left top,right top,from(rgba(0,0,0,.0001)),to(rgba(0,0,0,.5)));background-image:linear-gradient(to right,rgba(0,0,0,.0001) 0,rgba(0,0,0,.5) 100%);filter:progid:DXImageTransform.Microsoft.gradient(startColorstr='#00000000', endColorstr='#80000000', GradientType=1);background-repeat:repeat-x}.carousel-control:focus,.carousel-control:hover{color:#fff;text-decoration:none;filter:alpha(opacity=90);outline:0;opacity:.9}.carousel-control .glyphicon-chevron-left,.carousel-control .glyphicon-chevron-right,.carousel-control .icon-next,.carousel-control .icon-prev{position:absolute;top:50%;z-index:5;display:inline-block;margin-top:-10px}.carousel-control .glyphicon-chevron-left,.carousel-control .icon-prev{left:50%;margin-left:-10px}.carousel-control .glyphicon-chevron-right,.carousel-control .icon-next{right:50%;margin-right:-10px}.carousel-control .icon-next,.carousel-control .icon-prev{width:20px;height:20px;font-family:serif;line-height:1}.carousel-control .icon-prev:before{content:'\2039'}.carousel-control .icon-next:before{content:'\203a'}.carousel-indicators{position:absolute;bottom:10px;left:50%;z-index:15;width:60%;padding-left:0;margin-left:-30%;text-align:center;list-style:none}.carousel-indicators li{display:inline-block;width:10px;height:10px;margin:1px;text-indent:-999px;cursor:pointer;background-color:#000\9;background-color:rgba(0,0,0,0);border:1px solid #fff;border-radius:10px}.carousel-indicators .active{width:12px;height:12px;margin:0;background-color:#fff}.carousel-caption{position:absolute;right:15%;bottom:20px;left:15%;z-index:10;padding-top:20px;padding-bottom:20px;color:#fff;text-align:center;text-shadow:0 1px 2px rgba(0,0,0,.6)}.carousel-caption .btn{text-shadow:none}@media screen and (min-width:768px){.carousel-control .glyphicon-chevron-left,.carousel-control .glyphicon-chevron-right,.carousel-control .icon-next,.carousel-control .icon-prev{width:30px;height:30px;margin-top:-15px;font-size:30px}.carousel-control .glyphicon-chevron-left,.carousel-control .icon-prev{margin-left:-15px}.carousel-control .glyphicon-chevron-right,.carousel-control .icon-next{margin-right:-15px}.carousel-caption{right:20%;left:20%;padding-bottom:30px}.carousel-indicators{bottom:20px}}.btn-group-vertical&gt;.btn-group:after,.btn-group-vertical&gt;.btn-group:before,.btn-toolbar:after,.btn-toolbar:before,.clearfix:after,.clearfix:before,.container-fluid:after,.container-fluid:before,.container:after,.container:before,.dl-horizontal dd:after,.dl-horizontal dd:before,.form-horizontal .form-group:after,.form-horizontal .form-group:before,.modal-footer:after,.modal-footer:before,.nav:after,.nav:before,.navbar-collapse:after,.navbar-collapse:before,.navbar-header:after,.navbar-header:before,.navbar:after,.navbar:before,.pager:after,.pager:before,.panel-body:after,.panel-body:before,.row:after,.row:before{display:table;content:" "}.btn-group-vertical&gt;.btn-group:after,.btn-toolbar:after,.clearfix:after,.container-fluid:after,.container:after,.dl-horizontal dd:after,.form-horizontal .form-group:after,.modal-footer:after,.nav:after,.navbar-collapse:after,.navbar-header:after,.navbar:after,.pager:after,.panel-body:after,.row:after{clear:both}.center-block{display:block;margin-right:auto;margin-left:auto}.pull-right{float:right!important}.pull-left{float:left!important}.hide{display:none!important}.show{display:block!important}.invisible{visibility:hidden}.text-hide{font:0/0 a;color:transparent;text-shadow:none;background-color:transparent;border:0}.hidden{display:none!important}.affix{position:fixed}@-ms-viewport{width:device-width}.visible-lg,.visible-md,.visible-sm,.visible-xs{display:none!important}.visible-lg-block,.visible-lg-inline,.visible-lg-inline-block,.visible-md-block,.visible-md-inline,.visible-md-inline-block,.visible-sm-block,.visible-sm-inline,.visible-sm-inline-block,.visible-xs-block,.visible-xs-inline,.visible-xs-inline-block{display:none!important}@media (max-width:767px){.visible-xs{display:block!important}table.visible-xs{display:table!important}tr.visible-xs{display:table-row!important}td.visible-xs,th.visible-xs{display:table-cell!important}}@media (max-width:767px){.visible-xs-block{display:block!important}}@media (max-width:767px){.visible-xs-inline{display:inline!important}}@media (max-width:767px){.visible-xs-inline-block{display:inline-block!important}}@media (min-width:768px) and (max-width:991px){.visible-sm{display:block!important}table.visible-sm{display:table!important}tr.visible-sm{display:table-row!important}td.visible-sm,th.visible-sm{display:table-cell!important}}@media (min-width:768px) and (max-width:991px){.visible-sm-block{display:block!important}}@media (min-width:768px) and (max-width:991px){.visible-sm-inline{display:inline!important}}@media (min-width:768px) and (max-width:991px){.visible-sm-inline-block{display:inline-block!important}}@media (min-width:992px) and (max-width:1199px){.visible-md{display:block!important}table.visible-md{display:table!important}tr.visible-md{display:table-row!important}td.visible-md,th.visible-md{display:table-cell!important}}@media (min-width:992px) and (max-width:1199px){.visible-md-block{display:block!important}}@media (min-width:992px) and (max-width:1199px){.visible-md-inline{display:inline!important}}@media (min-width:992px) and (max-width:1199px){.visible-md-inline-block{display:inline-block!important}}@media (min-width:1200px){.visible-lg{display:block!important}table.visible-lg{display:table!important}tr.visible-lg{display:table-row!important}td.visible-lg,th.visible-lg{display:table-cell!important}}@media (min-width:1200px){.visible-lg-block{display:block!important}}@media (min-width:1200px){.visible-lg-inline{display:inline!important}}@media (min-width:1200px){.visible-lg-inline-block{display:inline-block!important}}@media (max-width:767px){.hidden-xs{display:none!important}}@media (min-width:768px) and (max-width:991px){.hidden-sm{display:none!important}}@media (min-width:992px) and (max-width:1199px){.hidden-md{display:none!important}}@media (min-width:1200px){.hidden-lg{display:none!important}}.visible-print{display:none!important}@media print{.visible-print{display:block!important}table.visible-print{display:table!important}tr.visible-print{display:table-row!important}td.visible-print,th.visible-print{display:table-cell!important}}.visible-print-block{display:none!important}@media print{.visible-print-block{display:block!important}}.visible-print-inline{display:none!important}@media print{.visible-print-inline{display:inline!important}}.visible-print-inline-block{display:none!important}@media print{.visible-print-inline-block{display:inline-block!important}}@media print{.hidden-print{display:none!important}}
				
            </xsl:if>
        </style>
    </xsl:template>
</xsl:stylesheet>
