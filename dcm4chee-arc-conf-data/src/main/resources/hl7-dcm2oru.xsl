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
    <xsl:param name="includeNullValues" />

    <xsl:template match="/NativeDicomModel">
        <hl7>
            <xsl:call-template name="MSH">
                <xsl:with-param name="sender" select="$sender"/>
                <xsl:with-param name="receiver" select="$receiver"/>
                <xsl:with-param name="dateTime" select="$dateTime"/>
                <xsl:with-param name="msgType" select="$msgType"/>
                <xsl:with-param name="msgControlID" select="$msgControlID"/>
                <xsl:with-param name="charset" select="$charset"/>
            </xsl:call-template>
            <xsl:call-template name="PID">
                <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
            </xsl:call-template>
            <xsl:call-template name="PV1" />
            <xsl:call-template name="ORC" />
            <xsl:call-template name="OBR" />
            <xsl:call-template name="TQ1" />
            <xsl:call-template name="OBX-uid">
                <xsl:with-param name="tag" select="'0020000D'"/>
            </xsl:call-template>
            <xsl:call-template name="OBX-uid">
                <xsl:with-param name="tag" select="'0020000E'"/>
            </xsl:call-template>
            <xsl:call-template name="OBX-uid">
                <xsl:with-param name="tag" select="'00080018'"/>
            </xsl:call-template>
            <xsl:call-template name="OBX-uid">
                <xsl:with-param name="tag" select="'00080016'"/>
            </xsl:call-template>
            <xsl:call-template name="desc"/>
            <!-- Findings/History/Conclusion from ContentSequence remaining to be implemented -->
            <!-- Findings/Recommendations/Conclusion as per RAD-128 remaining to be implemented -->
        </hl7>
    </xsl:template>

    <xsl:template name="PV1">
        <PV1>
            <field/>
            <field>
                <xsl:value-of select="'U'" />
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
        <ORC>
            <field>
                <xsl:value-of select="'SC'" />
            </field>
            <field>
                <xsl:call-template name="idWithIssuer">
                    <xsl:with-param name="idTag" select="'00402016'"/>
                    <xsl:with-param name="sqTag" select="'00400026'"/>
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="idWithIssuer">
                    <xsl:with-param name="idTag" select="'00402017'"/>
                    <xsl:with-param name="sqTag" select="'00400027'"/>
                </xsl:call-template>
            </field>
            <field/>
            <field>
                <xsl:value-of select="'CM'" />
            </field>
        </ORC>
    </xsl:template>

    <xsl:template name="OBR">
        <OBR>
            <field/>
            <field>
                <xsl:call-template name="idWithIssuer">
                    <xsl:with-param name="idTag" select="'00402016'"/>
                    <xsl:with-param name="sqTag" select="'00400026'"/>
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="idWithIssuer">
                    <xsl:with-param name="idTag" select="'00402017'"/>
                    <xsl:with-param name="sqTag" select="'00400027'"/>
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
                <xsl:call-template name="idWithIssuer">
                    <xsl:with-param name="idTag" select="'00080050'"/>
                    <xsl:with-param name="sqTag" select="'00080051'"/>
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="'00401001'"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:call-template name="diagnosticServiceSectionID"/>
            </field>
            <field>
                <xsl:value-of select="'R'" />
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
            <field>
                <xsl:call-template name="item2cnn">
                    <xsl:with-param name="item" select="DicomAttribute[@tag='0040A073']/Item"/>
                    <xsl:with-param name="defVal" select="'UNKNOWN'"/>
                </xsl:call-template>
                <component/>
                <component/>
                <component/>
                <component/>
                <component/>
                <component/>
                <component>
                    <xsl:if test="DicomAttribute[@tag='0040A073']/Item">
                        <xsl:value-of select="DicomAttribute[@tag='0040A073']/Item/DicomAttribute[@tag='0040A027']/Value"/>
                    </xsl:if>
                </component>
            </field>
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

    <xsl:template name="OBX-uid">
        <xsl:param name="tag"/>
        <OBX>
            <field>
                <xsl:choose>
                    <xsl:when test="$tag='0020000D'">
                        <xsl:value-of select="'1'" />
                    </xsl:when>
                    <xsl:when test="$tag='0020000E'">
                        <xsl:value-of select="'2'" />
                    </xsl:when>
                    <xsl:when test="$tag='00080018'">
                        <xsl:value-of select="'3'"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="'4'"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field>
                <xsl:value-of select="'ST'" />
            </field>
            <field>
                <xsl:choose>
                    <xsl:when test="$tag='0020000D'">
                        <xsl:value-of select="'113014'" />
                        <component>
                            <xsl:value-of select="'DICOM Study'" />
                        </component>
                        <component>
                            <xsl:value-of select="'DCM'" />
                        </component>
                    </xsl:when>
                    <xsl:when test="$tag='0020000E'">
                        <component>
                            <xsl:value-of select="'Series Instance UID'"/>
                        </component>
                    </xsl:when>
                    <xsl:when test="$tag='00080018'">
                        <component>
                            <xsl:value-of select="'SOP Instance UID'"/>
                        </component>
                    </xsl:when>
                    <xsl:otherwise>
                        <component>
                            <xsl:value-of select="'SOP Class UID'"/>
                        </component>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field/>
            <field>
                <xsl:call-template name="attr">
                    <xsl:with-param name="tag" select="$tag"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                </xsl:call-template>
            </field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:choose>
                    <xsl:when test="$tag='0020000D'">
                        <xsl:value-of select="'O'" />
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="'F'"/>
                    </xsl:otherwise>
                </xsl:choose>
            </field>
            <field/>
            <field/>
            <field>
                <xsl:choose>
                    <xsl:when test="$tag='0020000D' and boolean(DicomAttribute[@tag='00080020']/Value)">
                        <xsl:value-of select="concat(DicomAttribute[@tag='00080020']/Value, DicomAttribute[@tag='00080030']/Value)"/>
                    </xsl:when>
                    <xsl:when test="$tag='0020000E' and boolean(DicomAttribute[@tag='00080021']/Value)">
                        <xsl:value-of select="concat(DicomAttribute[@tag='00080021']/Value, DicomAttribute[@tag='00080031']/Value)"/>
                    </xsl:when>
                    <xsl:when test="$tag='00080018' and boolean(DicomAttribute[@tag='00080023']/Value)">
                        <xsl:value-of select="concat(DicomAttribute[@tag='00080023']/Value, DicomAttribute[@tag='00080033']/Value)"/>
                    </xsl:when>
                    <xsl:otherwise/>
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
            <field>
                <xsl:if test="$tag='0020000E'">
                    <xsl:call-template name="attr">
                        <xsl:with-param name="tag" select="'00080080'"/>
                        <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                    </xsl:call-template>
                </xsl:if>
            </field>
            <field>
                <xsl:if test="$tag='0020000E'">
                    <xsl:call-template name="attr">
                        <xsl:with-param name="tag" select="'00080081'"/>
                        <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                    </xsl:call-template>
                </xsl:if>
            </field>
        </OBX>
    </xsl:template>

    <xsl:template name="desc">
        <xsl:variable name="studyDesc">
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'00081030'"/>
                <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="seriesDesc">
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'0008103E'"/>
                <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="$studyDesc and $seriesDesc and $studyDesc != '' and $seriesDesc != ''">
                <xsl:call-template name="OBX-finding">
                    <xsl:with-param name="desc" select="$studyDesc"/>
                    <xsl:with-param name="setID" select="'5'"/>
                    <xsl:with-param name="prefix" select="'Study Description : '"/>
                </xsl:call-template>
                <xsl:call-template name="OBX-finding">
                    <xsl:with-param name="desc" select="$seriesDesc"/>
                    <xsl:with-param name="setID" select="'6'"/>
                    <xsl:with-param name="prefix" select="'Series Description : '"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$studyDesc and $studyDesc != '' and not($seriesDesc)">
                <xsl:call-template name="OBX-finding">
                    <xsl:with-param name="desc" select="$studyDesc"/>
                    <xsl:with-param name="setID" select="'5'"/>
                    <xsl:with-param name="prefix" select="'Study Description : '"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$seriesDesc and $seriesDesc != '' and not($studyDesc)">
                <xsl:call-template name="OBX-finding">
                    <xsl:with-param name="desc" select="$seriesDesc"/>
                    <xsl:with-param name="setID" select="'5'"/>
                    <xsl:with-param name="prefix" select="'Series Description : '"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="OBX-finding">
        <xsl:param name="desc"/>
        <xsl:param name="setID"/>
        <xsl:param name="prefix"/>
        <OBX>
            <field>
                <xsl:value-of select="$setID" />
            </field>
            <field>
                <xsl:value-of select="'ST'" />
            </field>
            <field>
                <xsl:value-of select="'59776-5'"/>
                <component>
                    <xsl:value-of select="'Procedure Findings'"/>
                </component>
                <component>
                    <xsl:value-of select="'LN'"/>
                </component>
            </field>
            <field/>
            <field>
                <xsl:value-of select="concat($prefix, $desc)"/>
            </field>
        </OBX>
    </xsl:template>

    <xsl:template name="idWithIssuer">
        <xsl:param name="idTag"/>
        <xsl:param name="sqTag"/>
        <xsl:variable name="id" select="DicomAttribute[@tag=$idTag]/Value" />
        <xsl:if test="$id">
            <xsl:value-of select="$id" />
            <xsl:variable name="issuer" select="DicomAttribute[@tag=$sqTag]/Item" />
            <xsl:if test="$issuer">
                <component/>
                <component/>
                <component>
                    <xsl:value-of select="$issuer/DicomAttribute[@tag='00400031']/Value" />
                    <subcomponent>
                        <xsl:value-of select="$issuer/DicomAttribute[@tag='00400032']/Value" />
                    </subcomponent>
                    <subcomponent>
                        <xsl:value-of select="$issuer/DicomAttribute[@tag='00400033']/Value" />
                    </subcomponent>
                </component>
            </xsl:if>
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
        <xsl:variable name="procedureCode" select="DicomAttribute[@tag='00081032']/Item"/>
        <xsl:variable name="requestAttrs" select="DicomAttribute[@tag='00400275']/Item"/>
        <xsl:choose>
            <xsl:when test="$procedureCode">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="$procedureCode"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$requestAttrs and boolean(DicomAttribute[@tag='00321064']/Item)">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="DicomAttribute[@tag='00321064']/Item"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="technician">
        <xsl:variable name="hasOperator" select="boolean(DicomAttribute[@tag='00081070']/PersonName)" />
        <xsl:variable name="operatorIDCode" select="DicomAttribute[@tag='00081072']/Item"/>
        <xsl:variable name="hasPerformingPhysician" select="boolean(DicomAttribute[@tag='00081050']/PersonName)" />
        <xsl:variable name="performingPhysicianIDCode" select="DicomAttribute[@tag='00081052']/Item"/>
        <xsl:choose>
            <xsl:when test="$hasOperator">
                <xsl:call-template name="name">
                    <xsl:with-param name="tag" select="'00081070'"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$operatorIDCode">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="$operatorIDCode"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$hasPerformingPhysician">
                <xsl:call-template name="name">
                    <xsl:with-param name="tag" select="'00081050'"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="$performingPhysicianIDCode">
                <xsl:call-template name="codeItem">
                    <xsl:with-param name="item" select="$performingPhysicianIDCode"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise/>
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
