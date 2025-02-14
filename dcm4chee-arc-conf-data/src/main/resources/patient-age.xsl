<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:csl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml"/>
    <xsl:template match="/NativeDicomModel">
        <NativeDicomModel>
            <xsl:variable name="patientAge" select="DicomAttribute[@tag='00101010']/Value"/>
            <xsl:variable name="patientBirthDate" select="DicomAttribute[@tag='00100030']/Value"/>
            <variable name="date" select="Date:new()"/>
            <xsl:variable name="dateAsStr">
                <value-of select="Date:toString($date)"/>
            </xsl:variable>
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'00101010'"/>
                <xsl:with-param name="vr" select="'AS'"/>
                <xsl:with-param name="val" select="$dateAsStr"/>
            </xsl:call-template>
        </NativeDicomModel>
    </xsl:template>

    <xsl:template name="attr">
        <xsl:param name="tag"/>
        <xsl:param name="vr"/>
        <xsl:param name="val"/>
        <xsl:if test="$val">
            <DicomAttribute tag="{$tag}" vr="{$vr}">
                <xsl:if test="$val != '&quot;&quot;'">
                    <Value number="1">
                        <xsl:value-of select="$val"/>
                    </Value>
                </xsl:if>
            </DicomAttribute>
        </xsl:if>
    </xsl:template>


    <xsl:template name="age">
        <xsl:param name="patientBirthDate"/>
        <variable name="date" select="Date:new()"/>
        <xsl:variable name="dateAsStr">
            <value-of select="Date:toString($date)"/>
        </xsl:variable>
    </xsl:template>

</xsl:stylesheet>
