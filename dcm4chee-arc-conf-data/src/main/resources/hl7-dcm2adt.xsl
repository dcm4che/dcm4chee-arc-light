<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" />
    <xsl:include href="hl7-common-outgoing.xsl"/>
    <xsl:param name="sender" />
    <xsl:param name="receiver" />
    <xsl:param name="dateTime" />
    <xsl:param name="msgType" />
    <xsl:param name="msgControlID" />
    <xsl:param name="charset" />
    <xsl:param name="priorPatientID" />
    <xsl:param name="priorPatientName" />
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
            <xsl:if test="string-length(DicomAttribute[@tag='00104000']/Value) > 0">
                <xsl:call-template name="nte-pid" />
            </xsl:if>
            <xsl:call-template name="MRG" />
        </hl7>
    </xsl:template>

    <xsl:template name="MRG">
        <xsl:if test="$priorPatientID">
            <MRG>
                <field>
                    <xsl:call-template name="priorIDWithIssuer" />
                </field>
                <field/>
                <field/>
                <field/>
                <field/>
                <field/>
                <field>
                    <xsl:call-template name="priorPatientName" />
                </field>
            </MRG>
        </xsl:if>
    </xsl:template>

    <xsl:template name="priorPatientName">
        <xsl:choose>
            <xsl:when test="$priorPatientName">
                <!-- family name -->
                <xsl:call-template name="nameVal">
                    <xsl:with-param name="val" select="$priorPatientName"/>
                </xsl:call-template>
                <!-- given name -->
                <component>
                    <xsl:call-template name="nameVal">
                        <xsl:with-param name="val" select="substring-after($priorPatientName, '^')"/>
                    </xsl:call-template>
                </component>
                <!-- middle name -->
                <component>
                    <xsl:call-template name="nameVal">
                        <xsl:with-param name="val" select="substring-after(substring-after($priorPatientName, '^'), '^')"/>
                    </xsl:call-template>
                </component>
                <!-- name suffix -->
                <xsl:variable name="nameSuffix">
                    <xsl:value-of select="substring-after(substring-after(substring-after($priorPatientName, '^'), '^'), '^')"/>
                </xsl:variable>
                <component>
                    <xsl:choose>
                        <xsl:when test="not(contains($nameSuffix, '^')) and contains($nameSuffix, ' ')"> <!-- eg. NS DEG -->
                            <xsl:value-of select="substring-before($nameSuffix, ' ')"/>
                        </xsl:when>
                        <xsl:when test="not(contains($nameSuffix, '^') and contains($nameSuffix, ' '))"> <!-- eg. NS -->
                            <xsl:value-of select="$nameSuffix"/>
                        </xsl:when>
                        <xsl:when test="contains($nameSuffix, '^') and contains($nameSuffix, ' ')"> <!-- eg. NS DEG^^ -->
                            <xsl:value-of select="substring-before(substring-before($nameSuffix, '^'), ' ')" />
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="substring-before($nameSuffix, '^')" /> <!-- eg. NS^^ -->
                        </xsl:otherwise>
                    </xsl:choose>
                </component>
                <!-- name prefix -->
                <component>
                    <xsl:call-template name="nameVal">
                        <xsl:with-param name="val" select="substring-after(substring-after(substring-after(substring-after($priorPatientName, '^'), '^'), '^'), '^')"/>
                    </xsl:call-template>
                </component>
                <!-- degree -->
                <component>
                    <xsl:choose>
                        <xsl:when test="not(contains($nameSuffix, '^')) and contains($nameSuffix, ' ')"> <!-- eg. NS DEG -->
                            <xsl:value-of select="substring-after($nameSuffix, ' ')"/>
                        </xsl:when>
                        <xsl:when test="contains($nameSuffix, '^') and contains($nameSuffix, ' ')"> <!-- eg. NS DEG^^ -->
                            <xsl:value-of select="substring-after(substring-before($nameSuffix, '^'), ' ')" />
                        </xsl:when>
                        <xsl:otherwise/>
                    </xsl:choose>
                </component>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$includeNullValues" />
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="nameVal">
        <xsl:param name="val"/>
        <xsl:choose>
            <xsl:when test="contains($val, '^')">
                <xsl:value-of select="substring-before($val, '^')" />
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$val" />
            </xsl:otherwise>
        </xsl:choose>
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

    <xsl:template name="priorIDWithIssuer">
        <xsl:variable name="id">
            <xsl:call-template name="decodePriorPatientID">
                <xsl:with-param name="val" select="$priorPatientID" />
                <xsl:with-param name="delimiter" select="'^'" />
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="issuer" select="substring-after(substring-after(substring-after($priorPatientID, '^'), '^'), '^')" />
        <xsl:variable name="issuerOfPIDSq" select="substring-after($issuer, '&amp;')" />
        <xsl:choose>
            <xsl:when test="$id">
                <xsl:value-of select="$id"/>
                <xsl:if test="$issuer">
                    <component/><component/>
                    <component>
                        <xsl:call-template name="decodePriorPatientID">
                            <xsl:with-param name="val" select="$issuer" />
                            <xsl:with-param name="delimiter" select="'&amp;'" />
                        </xsl:call-template>
                        <xsl:if test="$issuerOfPIDSq">
                            <subcomponent>
                                <xsl:value-of select="substring-before($issuerOfPIDSq, '&amp;')" />
                            </subcomponent>
                            <subcomponent>
                                <xsl:value-of select="substring-after($issuerOfPIDSq, '&amp;')" />
                            </subcomponent>
                        </xsl:if>
                    </component>
                </xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$includeNullValues" />
            </xsl:otherwise>
        </xsl:choose>
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

</xsl:stylesheet>
