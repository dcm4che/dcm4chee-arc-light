<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" />
    <xsl:include href="hl7-common-outgoing.xsl"/>
    <xsl:param name="sender" />
    <xsl:param name="receiver" />
    <xsl:param name="dateTime" />
    <xsl:param name="msgControlID" />
    <xsl:param name="msgType" />
    <xsl:param name="charset" />
    <xsl:param name="isPIDPV1" />
    <xsl:param name="includeNullValues" />
    <xsl:param name="patientIdentifiers" />
    <xsl:param name="resultStatus"/>
    <xsl:param name="RequestedProcedureID"/>
    <xsl:param name="AccessionNumber"/>
    <xsl:param name="PlacerOrderNumberImagingServiceRequest"/>
    <xsl:param name="FillerOrderNumberImagingServiceRequest"/>

    <xsl:template match="/NativeDicomModel">
        <hl7>
            <xsl:call-template name="MSH">
                <xsl:with-param name="sender" select="$sender"/>
                <xsl:with-param name="receiver" select="$receiver"/>
                <xsl:with-param name="dateTime" select="$dateTime"/>
                <xsl:with-param name="msgType">
                    <xsl:call-template name="hl7MsgType"/>
                </xsl:with-param>
                <xsl:with-param name="msgControlID" select="$msgControlID"/>
                <xsl:with-param name="charset" select="$charset"/>
            </xsl:call-template>
            <xsl:if test="$isPIDPV1 = true() or starts-with($msgType, 'ORU')">
                <xsl:call-template name="PID">
                    <xsl:with-param name="patientIdentifiers" select="$patientIdentifiers"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
                <xsl:if test="string-length(DicomAttribute[@tag='00104000']/Value) > 0">
                    <xsl:call-template name="nte-pid" />
                </xsl:if>
                <xsl:call-template name="PV1" />
            </xsl:if>

            <xsl:variable name="placerNoSer" select="DicomAttribute[@tag='00400275']/Item/DicomAttribute[@tag='00402016']/Value" />
            <xsl:variable name="placerNoSqSer" select="DicomAttribute[@tag='00400275']/Item/DicomAttribute[@tag='00402026']/Item" />
            <xsl:variable name="placerNoInst" select="DicomAttribute[@tag='0040A370']/Item/DicomAttribute[@tag='00402016']/Value" />
            <xsl:variable name="placerNoSqInst" select="DicomAttribute[@tag='0040A370']/Item/DicomAttribute[@tag='00402026']/Item" />

            <xsl:variable name="fillerNoSer" select="DicomAttribute[@tag='00400275']/Item/DicomAttribute[@tag='00402017']/Value" />
            <xsl:variable name="fillerNoSqSer" select="DicomAttribute[@tag='00400275']/Item/DicomAttribute[@tag='00402027']/Item" />
            <xsl:variable name="fillerNoInst" select="DicomAttribute[@tag='0040A370']/Item/DicomAttribute[@tag='00402017']/Value" />
            <xsl:variable name="fillerNoSqInst" select="DicomAttribute[@tag='0040A370']/Item/DicomAttribute[@tag='00402027']/Item" />

            <xsl:call-template name="ORC">
                <xsl:with-param name="placerNoSer" select="$placerNoSer"/>
                <xsl:with-param name="placerNoSqSer" select="$placerNoSqSer"/>
                <xsl:with-param name="placerNoInst" select="$placerNoInst"/>
                <xsl:with-param name="placerNoSqInst" select="$placerNoSqInst"/>
                <xsl:with-param name="fillerNoSer" select="$fillerNoSer"/>
                <xsl:with-param name="fillerNoSqSer" select="$fillerNoSqSer"/>
                <xsl:with-param name="fillerNoInst" select="$fillerNoInst"/>
                <xsl:with-param name="fillerNoSqInst" select="$fillerNoSqInst"/>
            </xsl:call-template>
            <xsl:call-template name="OBR">
                <xsl:with-param name="placerNoSer" select="$placerNoSer"/>
                <xsl:with-param name="placerNoSqSer" select="$placerNoSqSer"/>
                <xsl:with-param name="placerNoInst" select="$placerNoInst"/>
                <xsl:with-param name="placerNoSqInst" select="$placerNoSqInst"/>
                <xsl:with-param name="fillerNoSer" select="$fillerNoSer"/>
                <xsl:with-param name="fillerNoSqSer" select="$fillerNoSqSer"/>
                <xsl:with-param name="fillerNoInst" select="$fillerNoInst"/>
                <xsl:with-param name="fillerNoSqInst" select="$fillerNoSqInst"/>
            </xsl:call-template>
            <xsl:call-template name="TQ1" />
            <xsl:choose>
                <xsl:when test="starts-with($msgType, 'OMI')">
                    <xsl:call-template name="IPC"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="OBX"/>
                </xsl:otherwise>
            </xsl:choose>
        </hl7>
    </xsl:template>

    <xsl:template name="hl7MsgType">
        <xsl:choose>
            <xsl:when test="starts-with($msgType, 'OMG')">
                <xsl:value-of select="'OMG^O19^OMG_O19'"/>
            </xsl:when>
            <xsl:when test="starts-with($msgType, 'OMI')">
                <xsl:value-of select="'OMI^O23^OMI_O23'"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="'ORU^R01^ORU_R01'"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="PV1">
        <xsl:variable name="routeOfAdmissions" select="DicomAttribute[@tag='00380016']/Value"/>
        <PV1>
            <field/>
            <field>
                <xsl:choose>
                    <xsl:when test="$routeOfAdmissions">
                        <xsl:value-of select="$routeOfAdmissions"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="'U'" />
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:call-template name="idWithIssuer">
                    <xsl:with-param name="idTag" select="'00380010'"/>
                    <xsl:with-param name="sqTag" select="'00380014'"/>
                </xsl:call-template>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:value-of select="'V'" />
            </field>
        </PV1>
    </xsl:template>

    <xsl:template name="ORC">
        <xsl:param name="placerNoSer"/>
        <xsl:param name="placerNoSqSer"/>
        <xsl:param name="placerNoInst"/>
        <xsl:param name="placerNoSqInst"/>
        <xsl:param name="fillerNoSer"/>
        <xsl:param name="fillerNoSqSer"/>
        <xsl:param name="fillerNoInst"/>
        <xsl:param name="fillerNoSqInst"/>
        <ORC>
            <field>
                <xsl:value-of select="'SC'" />
            </field>
            <field>
                <xsl:call-template name="placer">
                    <xsl:with-param name="placerNoSer" select="$placerNoSer"/>
                    <xsl:with-param name="placerNoSqSer" select="$placerNoSqSer"/>
                    <xsl:with-param name="placerNoInst" select="$placerNoInst"/>
                    <xsl:with-param name="placerNoSqInst" select="$placerNoSqInst"/>
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="filler">
                    <xsl:with-param name="fillerNoSer" select="$fillerNoSer"/>
                    <xsl:with-param name="fillerNoSqSer" select="$fillerNoSqSer"/>
                    <xsl:with-param name="fillerNoInst" select="$fillerNoInst"/>
                    <xsl:with-param name="fillerNoSqInst" select="$fillerNoSqInst"/>
                </xsl:call-template>
            </field>
            <field/>
            <field>
                <xsl:value-of select="'CM'" />
            </field>
        </ORC>
    </xsl:template>

    <xsl:template name="OBR">
        <xsl:param name="placerNoSer"/>
        <xsl:param name="placerNoSqSer"/>
        <xsl:param name="placerNoInst"/>
        <xsl:param name="placerNoSqInst"/>
        <xsl:param name="fillerNoSer"/>
        <xsl:param name="fillerNoSqSer"/>
        <xsl:param name="fillerNoInst"/>
        <xsl:param name="fillerNoSqInst"/>
        <OBR>
            <field/>
            <field>
                <xsl:call-template name="placer">
                    <xsl:with-param name="placerNoSer" select="$placerNoSer"/>
                    <xsl:with-param name="placerNoSqSer" select="$placerNoSqSer"/>
                    <xsl:with-param name="placerNoInst" select="$placerNoInst"/>
                    <xsl:with-param name="placerNoSqInst" select="$placerNoSqInst"/>
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="filler">
                    <xsl:with-param name="fillerNoSer" select="$fillerNoSer"/>
                    <xsl:with-param name="fillerNoSqSer" select="$fillerNoSqSer"/>
                    <xsl:with-param name="fillerNoInst" select="$fillerNoInst"/>
                    <xsl:with-param name="fillerNoSqInst" select="$fillerNoSqInst"/>
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="universalServiceIDAndProcedureCode"/>
            </field>
            <field/>
            <field/>
            <field>
                <xsl:call-template name="observationDateTime"/>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:choose>
                    <xsl:when test="string-length(DicomAttribute[@tag='00080050']/Value) > 0">
                        <xsl:call-template name="idWithIssuer">
                            <xsl:with-param name="idTag" select="'00080050'"/>
                            <xsl:with-param name="sqTag" select="'00080051'"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$AccessionNumber"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field>
                <xsl:variable name="reqProcID" select="DicomAttribute[@tag='0040A370']/Item/DicomAttribute[@tag='00401001']/Value" />
                <xsl:choose>
                    <xsl:when test="string-length($reqProcID) > 0">
                        <xsl:value-of select="$reqProcID"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$RequestedProcedureID"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:call-template name="diagnosticServiceSectionID"/>
            </field>
            <field>
                <xsl:value-of select="$resultStatus" />
            </field>
            <field/>
            <field>
                <component/>
                <component/>
                <component/>
                <component/>
                <component>
                    <xsl:value-of select="'R'" />
                </component>
            </field>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:call-template name="reasonForStudy"/>
            </field>
            <field/>
            <field/>
            <field>
                <xsl:call-template name="technician"/>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:call-template name="universalServiceIDAndProcedureCode"/>
            </field>
        </OBR>
    </xsl:template>

    <xsl:template name="placer">
        <xsl:param name="placerNoSer"/>
        <xsl:param name="placerNoSqSer"/>
        <xsl:param name="placerNoInst"/>
        <xsl:param name="placerNoSqInst"/>
        <xsl:choose>
            <xsl:when test="string-length($placerNoSer) > 0">
                <xsl:value-of select="$placerNoSer"/>
                <xsl:call-template name="populateIssuer">
                    <xsl:with-param name="issuer" select="$placerNoSqSer"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="string-length($placerNoInst) > 0">
                <xsl:value-of select="$placerNoInst"/>
                <xsl:call-template name="populateIssuer">
                    <xsl:with-param name="issuer" select="$placerNoSqInst"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$PlacerOrderNumberImagingServiceRequest"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="filler">
        <xsl:param name="fillerNoSer"/>
        <xsl:param name="fillerNoSqSer"/>
        <xsl:param name="fillerNoInst"/>
        <xsl:param name="fillerNoSqInst"/>
        <xsl:choose>
            <xsl:when test="string-length($fillerNoSer) > 0">
                <xsl:value-of select="$fillerNoSer"/>
                <xsl:call-template name="populateIssuer">
                    <xsl:with-param name="issuer" select="$fillerNoSqSer"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="string-length($fillerNoInst) > 0">
                <xsl:value-of select="$fillerNoInst"/>
                <xsl:call-template name="populateIssuer">
                    <xsl:with-param name="issuer" select="$fillerNoSqInst"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$FillerOrderNumberImagingServiceRequest"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="TQ1">
        <TQ1>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:value-of select="$dateTime" />
            </field>
            <field/>
            <field>
                <xsl:value-of select="'R'" />
                <component>
                    <xsl:value-of select="'Routine'" />
                </component>
                <component>
                    <xsl:value-of select="'HL70078'" />
                </component>
            </field>
        </TQ1>
    </xsl:template>

    <xsl:template name="OBX">
        <OBX>
            <field>
                <xsl:value-of select="'1'" />
            </field>
            <field>
                <xsl:value-of select="'ST'" />
            </field>
            <field>
                <xsl:value-of select="'113014'" />
                <component>
                    <xsl:value-of select="'DICOM Study'" />
                </component>
                <component>
                    <xsl:value-of select="'DCM'" />
                </component>
            </field>
            <field/>
            <field>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'0020000D'"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:value-of select="'O'" />
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
        </OBX>
    </xsl:template>

    <xsl:template name="IPC">
        <IPC>
            <field>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'00080050'"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
            <field/>
            <field>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'0020000D'"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
            <field/>
            <field>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'00080060'"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
        </IPC>
    </xsl:template>

    <xsl:template name="idWithIssuer">
        <xsl:param name="idTag"/>
        <xsl:param name="sqTag"/>
        <xsl:variable name="id" select="DicomAttribute[@tag=$idTag]/Value" />
        <xsl:if test="$id">
            <xsl:value-of select="$id" />
            <xsl:call-template name="issuer">
                <xsl:with-param name="sqTag" select="$sqTag"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template name="issuer">
        <xsl:param name="sqTag"/>
        <xsl:variable name="issuer" select="DicomAttribute[@tag=$sqTag]/Item" />
        <xsl:if test="$issuer">
            <component/>
            <component/>
            <component>
                <xsl:value-of select="$issuer/DicomAttribute[@tag='00400031']/Value" />
                <xsl:variable name="universalEntityID" select="$issuer/DicomAttribute[@tag='00400032']/Value"/>
                <xsl:variable name="universalEntityIDType" select="$issuer/DicomAttribute[@tag='00400033']/Value"/>
                <xsl:if test="$universalEntityID">
                    <subcomponent>
                        <xsl:value-of select="$universalEntityID" />
                    </subcomponent>
                    <subcomponent>
                        <xsl:value-of select="$universalEntityIDType" />
                    </subcomponent>
                </xsl:if>
            </component>
        </xsl:if>
    </xsl:template>

    <xsl:template name="populateIssuer">
        <xsl:param name="issuer"/>
        <xsl:if test="$issuer">
            <component/>
            <component/>
            <component>
                <xsl:value-of select="$issuer/DicomAttribute[@tag='00400031']/Value" />
                <xsl:variable name="universalEntityID" select="$issuer/DicomAttribute[@tag='00400032']/Value"/>
                <xsl:variable name="universalEntityIDType" select="$issuer/DicomAttribute[@tag='00400033']/Value"/>
                <xsl:if test="$universalEntityID">
                    <subcomponent>
                        <xsl:value-of select="$universalEntityID" />
                    </subcomponent>
                    <subcomponent>
                        <xsl:value-of select="$universalEntityIDType" />
                    </subcomponent>
                </xsl:if>
            </component>
        </xsl:if>
    </xsl:template>

    <xsl:template name="diagnosticServiceSectionID">
        <xsl:variable name="instDeptCode" select="DicomAttribute[@tag='00081041']/Item" />
        <xsl:choose>
            <xsl:when test="$instDeptCode">
                <xsl:value-of select="$instDeptCode/DicomAttribute[@tag='00080100']/Value"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="'RAD'"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="observationDateTime">
        <xsl:variable name="studyDate" select="DicomAttribute[@tag='00080020']/Value"/>
        <xsl:variable name="seriesDate" select="DicomAttribute[@tag='00080021']/Value"/>
        <xsl:variable name="verifyingObserverSqItem" select="DicomAttribute[@tag='0040A073']/Item"/>
        <xsl:choose>
            <xsl:when test="$studyDate">
                <xsl:value-of select="concat($studyDate, DicomAttribute[@tag='00080030']/Value)"/>
            </xsl:when>
            <xsl:when test="$seriesDate">
                <xsl:value-of select="concat($seriesDate, DicomAttribute[@tag='00080031']/Value)"/>
            </xsl:when>
            <xsl:when test="$verifyingObserverSqItem">
                <xsl:value-of select="$verifyingObserverSqItem/DicomAttribute[@tag='0040A030']/Value"/>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="universalServiceIDAndProcedureCode">
        <xsl:variable name="reqProcedureCodeMWL" select="DicomAttribute[@tag='00321064']/Item"/>
        <xsl:variable name="procedureCodeStudy" select="DicomAttribute[@tag='00081032']/Item"/>
        <xsl:variable name="requestAttrsSeries" select="DicomAttribute[@tag='00400275']/Item"/>
        <xsl:choose>
            <xsl:when test="$reqProcedureCodeMWL">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="$reqProcedureCodeMWL"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:choose>
                    <xsl:when test="$procedureCodeStudy">
                        <xsl:call-template name="codeItem">
                            <xsl:with-param name="item" select="$procedureCodeStudy"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:when test="$requestAttrsSeries">
                        <xsl:variable name="requestAttrsSeriesReqProcedureCode" select="$requestAttrsSeries/Item/DicomAttribute[@tag='00321064']/Item"/>
                        <xsl:if test="$requestAttrsSeriesReqProcedureCode">
                            <xsl:call-template name="codeItem">
                                <xsl:with-param name="item" select="$requestAttrsSeriesReqProcedureCode"/>
                            </xsl:call-template>
                        </xsl:if>
                    </xsl:when>
                    <xsl:otherwise/>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="cnn">
        <xsl:param name="personNameTag"/>
        <xsl:param name="idCodeTag"/>
        <xsl:variable name="idCode" select="DicomAttribute[@tag=$idCodeTag]/Item/DicomAttribute[@tag='00401101']/Item"/>
        <xsl:variable name="name" select="DicomAttribute[@tag=$personNameTag]/PersonName[1]/Alphabetic" />
        <xsl:choose>
            <xsl:when test="$idCode != '' or $name != ''">
                <xsl:if test="$idCode != ''">
                    <xsl:value-of select="$idCode/DicomAttribute[@tag='00080100']/Value" />
                </xsl:if>
                <xsl:if test="$name != ''">
                    <subcomponent>
                        <xsl:value-of select="$name/FamilyName" />
                    </subcomponent>
                    <subcomponent>
                        <xsl:value-of select="$name/GivenName" />
                    </subcomponent>
                    <subcomponent>
                        <xsl:value-of select="$name/MiddleName" />
                    </subcomponent>
                    <xsl:variable name="ns" select="$name/NameSuffix" />
                    <subcomponent>
                        <xsl:choose>
                            <xsl:when test="contains($ns, ' ')">
                                <xsl:value-of select="substring-before($ns, ' ')" />
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="$ns" />
                            </xsl:otherwise>
                        </xsl:choose>
                    </subcomponent>
                    <subcomponent>
                        <xsl:value-of select="$name/NamePrefix" />
                    </subcomponent>
                    <subcomponent>
                        <xsl:value-of select="substring-after($ns, ' ')" />
                    </subcomponent>
                </xsl:if>
                <xsl:choose>
                    <xsl:when test="$idCode != '' and $name != ''">
                        <subcomponent/>
                        <subcomponent>
                            <xsl:value-of select="$idCode/DicomAttribute[@tag='00080102']/Value"/>
                        </subcomponent>
                    </xsl:when>
                    <xsl:when test="$idCode != '' and $name = ''">
                        <subcomponent/>
                        <subcomponent/>
                        <subcomponent/>
                        <subcomponent/>
                        <subcomponent/>
                        <subcomponent/>
                        <subcomponent/>
                        <subcomponent>
                            <xsl:value-of select="$idCode/DicomAttribute[@tag='00080102']/Value"/>
                        </subcomponent>
                    </xsl:when>
                </xsl:choose>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$includeNullValues" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="technician">
        <xsl:variable name="hasOperator" select="boolean(DicomAttribute[@tag='00081070']/PersonName)" />
        <xsl:variable name="hasOperatorIDCode" select="boolean(DicomAttribute[@tag='00081072']/Item)"/>
        <xsl:variable name="hasPerformingPhysician" select="boolean(DicomAttribute[@tag='00081050']/PersonName)" />
        <xsl:variable name="hasPerformingPhysicianIDCode" select="boolean(DicomAttribute[@tag='00081052']/Item)"/>
        <xsl:choose>
            <xsl:when test="$hasOperator or $hasOperatorIDCode">
                <xsl:call-template name="cnn">
                    <xsl:with-param name="idCodeTag" select="'00081072'"/>
                    <xsl:with-param name="personNameTag" select="'00081070'"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:choose>
                    <xsl:when test="$hasPerformingPhysician or $hasPerformingPhysicianIDCode">
                        <xsl:call-template name="cnn">
                            <xsl:with-param name="idCodeTag" select="'00081052'"/>
                            <xsl:with-param name="personNameTag" select="'00081050'"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise/>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="reasonForStudy">
        <xsl:variable name="reasonForPerformedProcedureCode" select="DicomAttribute[@tag='00401012']/Item"/>
        <xsl:variable name="requestAttrs" select="DicomAttribute[@tag='00400275']/Item"/>
        <xsl:variable name="reasonForVisit"
                      select="boolean(DicomAttribute[@tag='00321067']/Item) or boolean(DicomAttribute[@tag='00321066']/Value)"/>
        <xsl:variable name="admittingDiagnoses"
                      select="boolean(DicomAttribute[@tag='00081084']/Item) or boolean(DicomAttribute[@tag='00081080']/Value)"/>
        <xsl:choose>
            <xsl:when test="$reasonForPerformedProcedureCode">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="$reasonForPerformedProcedureCode"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$requestAttrs and (boolean($requestAttrs/DicomAttribute[@tag='0040100A']/Item)
                                                or boolean($requestAttrs/DicomAttribute[@tag='00401002']/Value))">
                <xsl:call-template name="codeOrDesc">
                    <xsl:with-param name="item" select="$requestAttrs/DicomAttribute[@tag='0040100A']/Item"/>
                    <xsl:with-param name="desc" select="$requestAttrs/DicomAttribute[@tag='00401002']/Value"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$reasonForVisit">
                <xsl:call-template name="ce2codeItemWithDesc">
                    <xsl:with-param name="descTag" select="'00321066'"/>
                    <xsl:with-param name="sqTag" select="'00321067'"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$admittingDiagnoses">
                <xsl:call-template name="ce2codeItemWithDesc">
                    <xsl:with-param name="descTag" select="'00081080'"/>
                    <xsl:with-param name="sqTag" select="'00081084'"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
