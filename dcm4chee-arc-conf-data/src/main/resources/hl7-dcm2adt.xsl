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
    <xsl:param name="priorPatientName" />
    <xsl:param name="patientIdentifiers" />
    <xsl:param name="priorPatientIdentifiers" />
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
                <xsl:with-param name="patientIdentifiers" select="$patientIdentifiers"/>
                <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
            </xsl:call-template>
            <xsl:if test="string-length(DicomAttribute[@tag='00104000']/Value) > 0">
                <xsl:call-template name="nte-pid" />
            </xsl:if>
            <xsl:if test="string-length($priorPatientIdentifiers) > 0">
                <xsl:call-template name="MRG" />
            </xsl:if>
        </hl7>
    </xsl:template>

    <xsl:template name="MRG">
        <MRG>
            <field>
                <xsl:call-template name="patientIdentifier">
                    <xsl:with-param name="cx" select="$priorPatientIdentifiers"/>
                    <xsl:with-param name="includeNullValues" select="$includeNullValues"/>
                    <xsl:with-param name="repeat" select="'N'"/>
                </xsl:call-template>
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

</xsl:stylesheet>
