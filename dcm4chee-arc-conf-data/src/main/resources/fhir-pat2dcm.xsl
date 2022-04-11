<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fhir="http://hl7.org/fhir"
                exclude-result-prefixes="fhir" version="1.0">
    <xsl:output method="xml"/>

    <xsl:template match="/fhir:Bundle">
        <NativeDicomModel>
            <xsl:apply-templates select="fhir:entry/fhir:resource/fhir:Patient"/>
        </NativeDicomModel>
    </xsl:template>

    <xsl:template match="fhir:Patient">
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00100020'"/>
            <xsl:with-param name="vr" select="'LO'"/>
            <xsl:with-param name="val" select="fhir:identifier/fhir:value/@value"/>
        </xsl:call-template>
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00100021'"/>
            <xsl:with-param name="vr" select="'LO'"/>
            <xsl:with-param name="val" select="fhir:identifier/fhir:system/@value"/>
        </xsl:call-template>
        <xsl:call-template name="otherPIDs"/>
        <xsl:call-template name="patientName"/>
        <xsl:call-template name="otherPatientNames"/>
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00100040'"/>
            <xsl:with-param name="vr" select="'CS'"/>
            <xsl:with-param name="val">
                <xsl:call-template name="gender">
                    <xsl:with-param name="val" select="fhir:gender/@value"/>
                </xsl:call-template>
            </xsl:with-param>
        </xsl:call-template>
        <xsl:call-template name="attr">
            <xsl:with-param name="tag" select="'00100030'"/>
            <xsl:with-param name="vr" select="'DA'"/>
            <xsl:with-param name="val">
                <xsl:call-template name="date">
                    <xsl:with-param name="val" select="fhir:birthDate/@value"/>
                </xsl:call-template>
            </xsl:with-param>
        </xsl:call-template>
        <xsl:call-template name="address">
            <xsl:with-param name="val" select="fhir:address"/>
        </xsl:call-template>
        <xsl:call-template name="pnAttr">
            <xsl:with-param name="tag" select="'00101060'"/>
            <xsl:with-param name="val" select="fhir:extension[@url='http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName']/fhir:valueString/@value"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="address">
        <xsl:param name="val" />
        <xsl:variable name="line" select="$val/fhir:line/@value"/>
        <xsl:variable name="city" select="$val/fhir:city/@value"/>
        <xsl:variable name="state" select="$val/fhir:state/@value"/>
        <xsl:variable name="postalCode" select="$val/fhir:postalCode/@value"/>
        <xsl:variable name="country" select="$val/fhir:country/@value"/>
        <xsl:variable name="use" select="$val/fhir:use/@value"/>
        <xsl:if test="($line and $line != '&quot;&quot;' and string-length($line) > 0)
                        or ($city and $city != '&quot;&quot;' and string-length($city) > 0)
                        or ($state and $state != '&quot;&quot;' and string-length($state) > 0)
                        or ($postalCode and $postalCode != '&quot;&quot;' and string-length($postalCode) > 0)
                        or ($country and $country != '&quot;&quot;' and string-length($country) > 0) ">
            <xsl:variable name="addressType">
                <xsl:choose>
                    <xsl:when test="$use = 'home'">
                        <xsl:value-of select="'H'"/>
                    </xsl:when>
                    <xsl:when test="$use = 'work'">
                        <xsl:value-of select="'O'"/>
                    </xsl:when>
                    <xsl:when test="$use = 'temp'">
                        <xsl:value-of select="'C'"/>
                    </xsl:when>
                    <xsl:otherwise/>
                </xsl:choose>
            </xsl:variable>
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'00101040'"/>
                <xsl:with-param name="vr" select="'LO'"/>
                <xsl:with-param name="val" select="concat($line, '^^', $city, '^', $state, '^', $postalCode, '^', $country, '^', $addressType)"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template name="date">
        <xsl:param name="val"/>
        <xsl:choose>
            <xsl:when test="string-length($val) = 10">
                <xsl:value-of select="concat(substring($val, 0, 5), substring($val, 6, 2), substring($val, 9, 2))"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:message>
                    <xsl:copy-of select="$val"/>
                </xsl:message>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="gender">
        <xsl:param name="val"/>
        <xsl:choose>
            <xsl:when test="$val = 'male'">
                <xsl:value-of select="'M'"/>
            </xsl:when>
            <xsl:when test="$val = 'female'">
                <xsl:value-of select="'F'"/>
            </xsl:when>
            <xsl:when test="$val = 'other'">
                <xsl:value-of select="'O'"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:message>
                    <xsl:copy-of select="$val"/>
                </xsl:message>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="attr">
        <xsl:param name="tag"/>
        <xsl:param name="vr"/>
        <xsl:param name="val"/>
        <xsl:if test="$val and $val != '&quot;&quot;' and string-length($val) > 0">
            <DicomAttribute tag="{$tag}" vr="{$vr}">
                <Value number="1">
                    <xsl:value-of select="$val"/>
                </Value>
            </DicomAttribute>
        </xsl:if>
    </xsl:template>

    <xsl:template name="patientName">
        <xsl:for-each select="fhir:name">
            <xsl:variable name="use" select="fhir:use/@value"/>
            <xsl:variable name="text" select="fhir:text/@value"/>
            <xsl:choose>
                <xsl:when test="$use = 'official'">
                    <xsl:call-template name="pnAttrComp">
                        <xsl:with-param name="tag" select="'00100010'"/>
                        <xsl:with-param name="fn" select="fhir:family/@value"/>
                        <xsl:with-param name="gn">
                            <xsl:call-template name="multiValue">
                                <xsl:with-param name="key" select="fhir:given"/>
                            </xsl:call-template>
                        </xsl:with-param>
                        <xsl:with-param name="np">
                            <xsl:call-template name="multiValue">
                                <xsl:with-param name="key" select="fhir:prefix"/>
                            </xsl:call-template>
                        </xsl:with-param>
                        <xsl:with-param name="ns">
                            <xsl:call-template name="multiValue">
                                <xsl:with-param name="key" select="fhir:suffix"/>
                            </xsl:call-template>
                        </xsl:with-param>
                    </xsl:call-template>
                </xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="otherPIDs">
        <xsl:variable name="pids">
            <xsl:call-template name="pids"/>
        </xsl:variable>
        <xsl:if test="$pids and $pids != '&quot;&quot;' and string-length($pids) > 0">
            <DicomAttribute vr="SQ" tag="00101002">
                <xsl:call-template name="otherPIDItem">
                    <xsl:with-param name="val" select="$pids"/>
                </xsl:call-template>
            </DicomAttribute>
        </xsl:if>
    </xsl:template>

    <xsl:template name="pids">
        <xsl:for-each select="fhir:identifier">
            <xsl:variable name="type" select="fhir:type/fhir:coding/fhir:code/@value"/>
            <xsl:variable name="issuerOfPID" select="fhir:system/@value"/>
            <xsl:variable name="pid" select="fhir:value/@value"/>
            <xsl:choose>
                <xsl:when test="$type
                            and $type != '&quot;&quot;'
                            and string-length($type) > 0
                            and (position() = 1 or position() = 2)">
                    <xsl:variable name="otherPIDVal">
                        <xsl:value-of select="concat($pid, '^^^', $issuerOfPID, '^', $type)"/>
                    </xsl:variable>
                    <xsl:value-of select="$otherPIDVal"/>
                </xsl:when>
                <xsl:when test="$type
                            and $type != '&quot;&quot;'
                            and string-length($type) > 0
                            and position() > 2">
                    <xsl:variable name="otherPIDVal">
                        <xsl:value-of select="concat('|', $pid, '^^^', $issuerOfPID, '^', $type)"/>
                    </xsl:variable>
                    <xsl:value-of select="$otherPIDVal"/>
                </xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="names">
        <xsl:for-each select="fhir:name">
            <xsl:variable name="use" select="fhir:use/@value"/>
            <xsl:variable name="text" select="fhir:text/@value"/>
            <xsl:choose>
                <xsl:when test="$use != 'official' and position() = 1">
                    <xsl:variable name="otherPNVal">
                        <xsl:call-template name="otherPNs">
                            <xsl:with-param name="name" select="."/>
                        </xsl:call-template>
                    </xsl:variable>
                    <xsl:value-of select="$otherPNVal"/>
                </xsl:when>
                <xsl:when test="$use != 'official' and position() = 2 and preceding-sibling::fhir:name/fhir:use[@value = 'official']">
                    <xsl:variable name="otherPNVal">
                        <xsl:call-template name="otherPNs">
                            <xsl:with-param name="name" select="."/>
                        </xsl:call-template>
                    </xsl:variable>
                    <xsl:value-of select="$otherPNVal"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:choose>
                        <xsl:when test="$use != 'official'">
                            <xsl:variable name="otherPNVal">
                                <xsl:call-template name="otherPNs">
                                    <xsl:with-param name="name" select="."/>
                                </xsl:call-template>
                            </xsl:variable>
                            <xsl:value-of select="concat('|', $otherPNVal)"/>
                        </xsl:when>
                        <xsl:otherwise/>
                    </xsl:choose>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="otherPNs">
        <xsl:param name="name"/>
        <xsl:variable name="gn">
            <xsl:call-template name="multiValue">
                <xsl:with-param name="key" select="$name/fhir:given"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="np">
            <xsl:call-template name="multiValue">
                <xsl:with-param name="key" select="$name/fhir:prefix"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="ns">
            <xsl:call-template name="multiValue">
                <xsl:with-param name="key" select="$name/fhir:suffix"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:value-of select="concat($name/fhir:family/@value, '^', $gn, '^^', $np, '^', $ns)"/>
    </xsl:template>

    <xsl:template name="otherPatientNames">
        <xsl:variable name="otherPNs">
            <xsl:call-template name="names"/>
        </xsl:variable>
        <xsl:if test="$otherPNs and $otherPNs != '&quot;&quot;' and string-length($otherPNs) > 0">
            <xsl:call-template name="pnAttr">
                <xsl:with-param name="tag" select="'00101001'"/>
                <xsl:with-param name="val" select="$otherPNs"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template name="otherPIDItem">
        <xsl:param name="val"/>
        <xsl:param name="position" select="1"/>
        <xsl:variable name="otherPID">
            <xsl:choose>
                <xsl:when test="contains($val, '|')">
                    <xsl:value-of select="substring-before($val, '|')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$val"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="otherPIDs">
            <xsl:choose>
                <xsl:when test="contains($val, '|')">
                    <xsl:value-of select="substring-after($val, '|')"/>
                </xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:variable>
        <Item number="{$position}">
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'00100020'"/>
                <xsl:with-param name="vr" select="'LO'"/>
                <xsl:with-param name="val" select="substring-before($otherPID, '^^^')"/>
            </xsl:call-template>
            <xsl:variable name="issuerAndType" select="substring-after($otherPID, '^^^')"/>
            <xsl:call-template name="attr">
                <xsl:with-param name="tag" select="'00100021'"/>
                <xsl:with-param name="vr" select="'LO'"/>
                <xsl:with-param name="val" select="substring-before($issuerAndType, '^')"/>
            </xsl:call-template>
            <DicomAttribute vr="SQ" tag="00100024">
                <Item number="1">
                    <xsl:call-template name="attr">
                        <xsl:with-param name="tag" select="'00400035'"/>
                        <xsl:with-param name="vr" select="'CS'"/>
                        <xsl:with-param name="val" select="substring-after($issuerAndType, '^')"/>
                    </xsl:call-template>
                </Item>
            </DicomAttribute>
        </Item>
        <xsl:if test="string-length($otherPIDs) > 0">
            <xsl:call-template name="otherPIDItem">
                <xsl:with-param name="val" select="$otherPIDs"/>
                <xsl:with-param name="position" select="$position+1"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template name="personName">
        <xsl:param name="val"/>
        <xsl:param name="position" select="1"/>
        <xsl:variable name="otherPN">
            <xsl:choose>
                <xsl:when test="contains($val, '|')">
                    <xsl:value-of select="substring-before($val, '|')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$val"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="otherPNs">
            <xsl:choose>
                <xsl:when test="contains($val, '|')">
                    <xsl:value-of select="substring-after($val, '|')"/>
                </xsl:when>
                <xsl:otherwise/>
            </xsl:choose>
        </xsl:variable>
        <PersonName number="{$position}">
            <Alphabetic>
                <xsl:choose>
                    <xsl:when test="contains($otherPN, '^')">
                        <FamilyName>
                            <xsl:value-of select="substring-before($otherPN, '^')"/>
                        </FamilyName>
                        <GivenName>
                            <xsl:value-of select="substring-before(substring-after($otherPN, '^'), '^^')"/>
                        </GivenName>
                        <NamePrefix>
                            <xsl:value-of select="substring-before(substring-after($otherPN, '^^'), '^')"/>
                        </NamePrefix>
                        <NameSuffix>
                            <xsl:value-of select="substring-after(substring-after($otherPN, '^^'), '^')"/>
                        </NameSuffix>
                    </xsl:when>
                    <xsl:otherwise>
                        <FamilyName>
                            <xsl:value-of select="$otherPN"/>
                        </FamilyName>
                    </xsl:otherwise>
                </xsl:choose>
            </Alphabetic>
        </PersonName>
        <xsl:if test="string-length($otherPNs) > 0">
            <xsl:call-template name="personName">
                <xsl:with-param name="val" select="$otherPNs"/>
                <xsl:with-param name="position" select="$position+1"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template name="multiValue">
        <xsl:param name="key"/>
        <xsl:for-each select="$key">
            <xsl:choose>
                <xsl:when test="position() = 1">
                    <xsl:value-of select="@value"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="concat(' ', @value)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="pnAttr">
        <xsl:param name="tag"/>
        <xsl:param name="val"/>
        <DicomAttribute tag="{$tag}" vr="PN">
            <xsl:call-template name="personName">
                <xsl:with-param name="val" select="$val"/>
            </xsl:call-template>
        </DicomAttribute>
    </xsl:template>
    
    <xsl:template name="pnAttrComp">
        <xsl:param name="tag"/>
        <xsl:param name="fn"/>
        <xsl:param name="gn"/>
        <xsl:param name="np"/>
        <xsl:param name="ns"/>
        <DicomAttribute tag="{$tag}" vr="PN">
            <PersonName number="1">
                <Alphabetic>
                    <xsl:call-template name="pnComp">
                        <xsl:with-param name="name">FamilyName</xsl:with-param>
                        <xsl:with-param name="val" select="$fn"/>
                    </xsl:call-template>
                    <xsl:call-template name="pnComp">
                        <xsl:with-param name="name">GivenName</xsl:with-param>
                        <xsl:with-param name="val" select="$gn"/>
                    </xsl:call-template>
                    <xsl:call-template name="pnComp">
                        <xsl:with-param name="name">NamePrefix</xsl:with-param>
                        <xsl:with-param name="val" select="$np"/>
                    </xsl:call-template>
                    <xsl:call-template name="pnComp">
                        <xsl:with-param name="name">NameSuffix</xsl:with-param>
                        <xsl:with-param name="val" select="$ns"/>
                    </xsl:call-template>
                </Alphabetic>
            </PersonName>
        </DicomAttribute>
    </xsl:template>

    <xsl:template name="pnComp">
        <xsl:param name="name"/>
        <xsl:param name="val"/>
        <xsl:if test="$val and $val != '&quot;&quot;' and string-length($val) > 0">
            <xsl:element name="{$name}">
                <xsl:value-of select="$val"/>
            </xsl:element>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>