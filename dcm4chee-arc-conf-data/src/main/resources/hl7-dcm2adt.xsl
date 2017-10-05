<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" />
    <xsl:param name="sendingApplication" />
    <xsl:param name="sendingFacility" />
    <xsl:param name="receivingApplication" />
    <xsl:param name="receivingFacility" />
    <xsl:param name="dateTime" />
    <xsl:param name="msgType" />
    <xsl:param name="msgControlID" />
    <xsl:param name="charset" />
    <xsl:param name="priorPatientID" />
    <xsl:param name="includeNullValues" />

    <xsl:template match="/NativeDicomModel">
        <hl7>
            <xsl:call-template name="MSH" />
            <xsl:call-template name="PID" />
            <xsl:call-template name="MRG" />
        </hl7>
    </xsl:template>

    <xsl:template name="MSH">
        <MSH fieldDelimiter="|" componentDelimiter="^" repeatDelimiter="~" escapeDelimiter="\" subcomponentDelimiter="&amp;">
            <field>
                <xsl:value-of select="$sendingApplication" />
            </field>
            <field>
                <xsl:value-of select="$sendingFacility" />
            </field>
            <field>
                <xsl:value-of select="$receivingApplication" />
            </field>
            <field>
                <xsl:value-of select="$receivingFacility" />
            </field>
            <field>
                <xsl:value-of select="$dateTime" />
            </field>
            <field/>
            <field>
                <xsl:value-of select="substring($msgType, 1, 3)" />
                <component>
                    <xsl:value-of select="substring($msgType, 5, 3)" />
                </component>
                <component>
                    <xsl:value-of select="substring($msgType, 9, 7)" />
                </component>
            </field>
            <field>
                <xsl:value-of select="$msgControlID" />
            </field>
            <field>P</field>
            <field>2.5.1</field>
            <field/>
            <field/>
            <field/>
            <field/>
            <field/>
            <field>
                <xsl:if test="$charset != 'ASCII'">
                    <xsl:value-of select="$charset"/>
                </xsl:if>
            </field>
        </MSH>
    </xsl:template>

    <xsl:template name="PID">
        <PID>
            <field/>
            <xsl:variable name="otherPIDSq" select="DicomAttribute[@tag='00101002']" />
            <field>
                <xsl:call-template name="otherPID">
                    <xsl:with-param name="sq" select="$otherPIDSq" />
                    <xsl:with-param name="itemNo" select="'1'" />
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="idWithIssuer" />
            </field>
            <field>
                <xsl:call-template name="otherPID">
                    <xsl:with-param name="sq" select="$otherPIDSq" />
                    <xsl:with-param name="itemNo" select="'2'" />
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="name">
                    <xsl:with-param name="tag" select="'00100010'" />
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="name">
                    <xsl:with-param name="tag" select="'00101060'" />
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="value">
                    <xsl:with-param name="val" select="DicomAttribute[@tag='00100030']/Value" />
                </xsl:call-template>
            </field>
            <field>
                <xsl:value-of select="DicomAttribute[@tag='00100040']/Value" />
                <xsl:call-template name="neutered">
                    <xsl:with-param name="val" select="DicomAttribute[@tag='00102203']/Value" />
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="name">
                    <xsl:with-param name="tag" select="'00102297'" />
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
            <field>
                <xsl:call-template name="ce2codeItemWithDesc">
                    <xsl:with-param name="descTag" select="'00102201'" />
                    <xsl:with-param name="sqTag" select="'00102202'" />
                </xsl:call-template>
            </field>
            <field>
                <xsl:call-template name="ce2codeItemWithDesc">
                    <xsl:with-param name="descTag" select="'00102292'" />
                    <xsl:with-param name="sqTag" select="'00102293'" />
                </xsl:call-template>
            </field>
        </PID>
    </xsl:template>

    <xsl:template name="MRG">
        <xsl:if test="$priorPatientID">
            <MRG>
                <field>
                    <xsl:call-template name="priorIDWithIssuer" />
                </field>
            </MRG>
        </xsl:if>
    </xsl:template>

    <xsl:template name="idWithIssuer">
        <xsl:value-of select="DicomAttribute[@tag='00100020']/Value" />
        <xsl:variable name="issuerOfPID" select="DicomAttribute[@tag='00100021']/Value" />
        <xsl:variable name="issuerOfPIDSq" select="DicomAttribute[@tag='00100024']/Item" />
        <component/><component/>
        <component>
            <xsl:value-of select="$issuerOfPID" />
            <subcomponent>
                <xsl:value-of select="$issuerOfPIDSq/DicomAttribute[@tag='00400032']/Value" />
            </subcomponent>
            <subcomponent>
                <xsl:value-of select="$issuerOfPIDSq/DicomAttribute[@tag='00400033']/Value" />
            </subcomponent>
        </component>
    </xsl:template>

    <xsl:template name="name">
        <xsl:param name="tag" />
        <xsl:variable name="name" select="DicomAttribute[@tag=$tag]/PersonName[1]/Alphabetic" />
        <xsl:if test="$name">
            <xsl:value-of select="$name/FamilyName" />
            <component>
                <xsl:value-of select="$name/GivenName" />
            </component>
            <component>
                <xsl:value-of select="$name/MiddleName" />
            </component>
            <xsl:variable name="ns" select="$name/NameSuffix" />
            <component>
                <xsl:choose>
                    <xsl:when test="contains($ns, ' ')">
                        <xsl:value-of select="substring-before($ns, ' ')" />
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$ns" />
                    </xsl:otherwise>
                </xsl:choose>
            </component>
            <component>
                <xsl:value-of select="$name/NamePrefix" />
            </component>
            <component>
                <xsl:value-of select="substring-after($ns, ' ')" />
            </component>
        </xsl:if>
    </xsl:template>

    <xsl:template name="otherPID">
        <xsl:param name="sq" />
        <xsl:param name="itemNo" />
        <xsl:variable name="item" select="$sq/Item[$itemNo]" />
        <xsl:value-of select="$item/DicomAttribute[@tag='00100020']/Value" />
        <component/><component/>
        <component>
            <xsl:value-of select="$item/DicomAttribute[@tag='00100021']/Value" />
        </component>
    </xsl:template>

    <xsl:template name="value">
        <xsl:param name="val" />
        <xsl:choose>
            <xsl:when test="$val">
                <xsl:value-of select="$val" />
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$includeNullValues" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="neutered">
        <xsl:param name="val" />
        <xsl:if test="$val">
            <component>
                <xsl:choose>
                    <xsl:when test="$val = 'ALTERED'">Y</xsl:when>
                    <xsl:otherwise>N</xsl:otherwise>
                </xsl:choose>
            </component>
        </xsl:if>
    </xsl:template>

    <xsl:template name="priorIDWithIssuer">
        <xsl:variable name="id">
            <xsl:call-template name="decodePriorPatientID">
                <xsl:with-param name="val" select="$priorPatientID" />
                <xsl:with-param name="delimiter" select="'^'" />
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="issuer" select="substring-after(substring-after(substring-after($priorPatientID, '^'), '^'), '^')" />
        <xsl:variable name="issuerOfPIDSq" select="substring-after($issuer, '&amp;')" />
        <xsl:value-of select="$id"/>
        <component/><component/>
        <component>
            <xsl:call-template name="decodePriorPatientID">
                <xsl:with-param name="val" select="$issuer" />
                <xsl:with-param name="delimiter" select="'&amp;'" />
            </xsl:call-template>
            <subcomponent>
                <xsl:value-of select="substring-before($issuerOfPIDSq, '&amp;')" />
            </subcomponent>
            <subcomponent>
                <xsl:value-of select="substring-after($issuerOfPIDSq, '&amp;')" />
            </subcomponent>
        </component>
    </xsl:template>

    <xsl:template name="decodePriorPatientID">
        <xsl:param name="val" />
        <xsl:param name="delimiter" />
        <xsl:choose>
            <xsl:when test="contains($val, $delimiter)">
                <xsl:value-of select="substring-before($val, $delimiter)" />
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$val" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="ce2codeItemWithDesc">
        <xsl:param name="descTag" />
        <xsl:param name="sqTag" />
        <xsl:variable name="item" select="DicomAttribute[@tag=$sqTag]/Item" />
        <xsl:choose>
            <xsl:when test="$item">
                <xsl:value-of select="$item/DicomAttribute[@tag='00080100']/Value" />
                <component>
                    <xsl:value-of select="$item/DicomAttribute[@tag='00080104']/Value" />
                </component>
                <component>
                    <xsl:value-of select="$item/DicomAttribute[@tag='00080102']/Value" />
                </component>
            </xsl:when>
            <xsl:otherwise>
                <component>
                    <xsl:call-template name="value">
                        <xsl:with-param name="val" select="DicomAttribute[@tag=$descTag]/Value" />
                    </xsl:call-template>
                </component>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>