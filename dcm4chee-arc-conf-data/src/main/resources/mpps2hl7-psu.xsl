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
    <xsl:param name="ppsStatus" />
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
            <xsl:variable name="ppsStartDateTime" select="concat(DicomAttribute[@tag='00400244']/Value, DicomAttribute[@tag='00400245']/Value)"/>
            <xsl:call-template name="ORC" />
            <xsl:choose>
                <xsl:when test="starts-with($msgType, 'OMG')">
                    <xsl:call-template name="TQ1">
                        <xsl:with-param name="ppsStartDateTime" select="$ppsStartDateTime"/>
                    </xsl:call-template>
                    <xsl:call-template name="OBR">
                        <xsl:with-param name="ppsStartDateTime" select="$ppsStartDateTime"/>
                    </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="OBR">
                        <xsl:with-param name="ppsStartDateTime" select="$ppsStartDateTime"/>
                    </xsl:call-template>
                    <xsl:call-template name="TQ1">
                        <xsl:with-param name="ppsStartDateTime" select="$ppsStartDateTime"/>
                    </xsl:call-template>
                </xsl:otherwise>
            </xsl:choose>
            
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
        <xsl:variable name="scheduledStepAttributesSqItem" select="DicomAttribute[@tag='00400270']/Item"/>
        <ORC>
            <field>
                <xsl:value-of select="'SC'" />
            </field>
            <field>
                <xsl:variable name="placerNo" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402016']/Value" />
                <xsl:variable name="placerNoSq" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402026']/Value" />
                <xsl:choose>
                    <xsl:when test="string-length($placerNo) > 0">
                        <xsl:value-of select="$placerNo"/>
                        <xsl:call-template name="populateIssuer">
                            <xsl:with-param name="issuer" select="$placerNoSq"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$PlacerOrderNumberImagingServiceRequest"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field>
                <xsl:variable name="fillerNo" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402017']/Value" />
                <xsl:variable name="fillerNoSq" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402027']/Value" />
                <xsl:choose>
                    <xsl:when test="string-length($fillerNo) > 0">
                        <xsl:value-of select="$fillerNo"/>
                        <xsl:call-template name="populateIssuer">
                            <xsl:with-param name="issuer" select="$fillerNoSq"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$FillerOrderNumberImagingServiceRequest"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field/>
            <field>
                <xsl:choose>
                    <xsl:when test="$ppsStatus = 'IN_PROGRESS'">
                        <xsl:value-of select="'IP'"/>
                    </xsl:when>
                    <xsl:when test="$ppsStatus = 'DISCONTINUED'">
                        <xsl:value-of select="'DC'"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="'CM'" />
                    </xsl:otherwise>
                </xsl:choose>
            </field>
        </ORC>
    </xsl:template>

    <xsl:template name="OBR">
        <xsl:param name="ppsStartDateTime"/>
        <xsl:variable name="scheduledStepAttributesSqItem" select="DicomAttribute[@tag='00400270']/Item"/>
        <OBR>
            <field/>
            <field>
                <xsl:variable name="placerNo" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402016']/Value" />
                <xsl:variable name="placerNoSq" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402026']/Value" />
                <xsl:choose>
                    <xsl:when test="string-length($placerNo) > 0">
                        <xsl:value-of select="$placerNo"/>
                        <xsl:call-template name="populateIssuer">
                            <xsl:with-param name="issuer" select="$placerNoSq"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$PlacerOrderNumberImagingServiceRequest"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field>
                <xsl:variable name="fillerNo" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402017']/Value" />
                <xsl:variable name="fillerNoSq" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00402027']/Value" />
                <xsl:choose>
                    <xsl:when test="string-length($fillerNo) > 0">
                        <xsl:value-of select="$fillerNo"/>
                        <xsl:call-template name="populateIssuer">
                            <xsl:with-param name="issuer" select="$fillerNoSq"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$FillerOrderNumberImagingServiceRequest"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field>
                <xsl:call-template name="universalServiceIDAndProcedureCode"/>
            </field>
            <field/>
            <field/>
            <field>
                <xsl:value-of select="$ppsStartDateTime"/>
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
                <xsl:variable name="accNo" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00080050']/Value" />
                <xsl:variable name="accNoSeq" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00080051']/Value" />
                <xsl:choose>
                    <xsl:when test="string-length($accNo) > 0">
                        <xsl:value-of select="$accNo"/>
                        <xsl:call-template name="populateIssuer">
                            <xsl:with-param name="issuer" select="$accNoSeq"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$AccessionNumber"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field>
                <xsl:variable name="reqProcID" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00401001']/Value" />
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
                <xsl:value-of select="'RAD'"/>
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
                <xsl:call-template name="technician">
                    <xsl:with-param name="performedSeriesSqItem" select="DicomAttribute[@tag='00400340']/Item"/>
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
            <field>
                <xsl:call-template name="universalServiceIDAndProcedureCode"/>
            </field>
        </OBR>
    </xsl:template>

    <xsl:template name="TQ1">
        <xsl:param name="ppsStartDateTime"/>
        <TQ1>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:value-of select="$ppsStartDateTime" />
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
                <xsl:call-template name="attrVal">
                    <xsl:with-param name="val" select="DicomAttribute[@tag='00400270']/Item/DicomAttribute[@tag='0020000D']/Value"/>
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
            <xsl:variable name="scheduledStepAttributesSqItem" select="DicomAttribute[@tag='00400270']/Item"/>
            <field>
                <xsl:call-template name="attrVal">
                    <xsl:with-param name="val" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='00080050']/Value"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
            <field/>
            <field>
                <xsl:call-template name="attrVal">
                    <xsl:with-param name="val" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag='0020000D']/Value"/>
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
            <field>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'00400241'"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
        </IPC>
    </xsl:template>

    <xsl:template name="idWithIssuer">
        <xsl:param name="idTag"/>
        <xsl:param name="sqTag"/>
        <xsl:choose>
            <xsl:when test="$idTag = '00380010'">
                <xsl:call-template name="addIDWithIssuer">
                    <xsl:with-param name="id" select="DicomAttribute[@tag=$idTag]/Value" />
                    <xsl:with-param name="issuer" select="DicomAttribute[@tag=$sqTag]/Item" />
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="scheduledStepAttributesSqItem" select="DicomAttribute[@tag='00400270']/Item"/>
                <xsl:call-template name="addIDWithIssuer">
                    <xsl:with-param name="id" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag=$idTag]/Value" />
                    <xsl:with-param name="issuer" select="$scheduledStepAttributesSqItem/DicomAttribute[@tag=$sqTag]/Item" />
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="addIDWithIssuer">
        <xsl:param name="id"/>
        <xsl:param name="issuer"/>
        <xsl:if test="$id">
            <xsl:value-of select="$id" />
            <xsl:call-template name="issuer">
                <xsl:with-param name="issuer" select="$issuer"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template name="issuer">
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

    <xsl:template name="item2cnn">
        <xsl:param name="item"/>
        <xsl:param name="defVal"/>
        <xsl:choose>
            <xsl:when test="$item">
                <xsl:variable name="idSqItem" select="$item/DicomAttribute[@tag='0040A088']/Item"/>
                <xsl:if test="$idSqItem">
                    <xsl:value-of select="$idSqItem/DicomAttribute[@tag='00080100']/Value"/>
                </xsl:if>
                <xsl:variable name="name" select="$item/DicomAttribute[@tag='0040A075']/PersonName[1]/Alphabetic"/>
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
                <subcomponent/>
                <subcomponent>
                    <xsl:if test="$idSqItem">
                        <xsl:value-of select="$idSqItem/DicomAttribute[@tag='00080102']/Value"/>
                    </xsl:if>
                </subcomponent>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$defVal" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="universalServiceIDAndProcedureCode">
        <xsl:variable name="procedureCode" select="DicomAttribute[@tag='00081032']/Item"/>
        <xsl:choose>
            <xsl:when test="$procedureCode">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="$procedureCode"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="technician">
        <xsl:param name="performedSeriesSqItem"/>
        <xsl:variable name="operatorName" select="$performedSeriesSqItem/DicomAttribute[@tag='00081070']/PersonName/Alphabetic" />
        <xsl:variable name="performingPhysicianName" select="$performedSeriesSqItem/DicomAttribute[@tag='00081050']/PersonName/Alphabetic" />
        <xsl:choose>
            <xsl:when test="string-length($operatorName) > 0">
                <xsl:call-template name="pnComponent">
                    <xsl:with-param name="name" select="$operatorName"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="string-length($performingPhysicianName) > 0">
                <xsl:call-template name="pnComponent">
                    <xsl:with-param name="name" select="$performingPhysicianName"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$includeNullValues" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="reasonForStudy">
        <xsl:variable name="reasonForPerformedProcedureCode" select="DicomAttribute[@tag='00401012']/Item"/>
        <xsl:choose>
            <xsl:when test="$reasonForPerformedProcedureCode">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="$reasonForPerformedProcedureCode"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
