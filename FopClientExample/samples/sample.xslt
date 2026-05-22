<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:fo="http://www.w3.org/1999/XSL/Format">
  <xsl:template match="/report">
    <fo:root>
      <fo:layout-master-set>
        <fo:simple-page-master master-name="simple" page-width="21cm" page-height="29.7cm" margin="1.5cm">
          <fo:region-body/>
        </fo:simple-page-master>
      </fo:layout-master-set>
      <fo:page-sequence master-reference="simple">
        <fo:flow flow-name="xsl-region-body">
          <fo:block font-size="16pt" font-weight="bold" space-after="8pt">
            <xsl:value-of select="title"/>
          </fo:block>
          <fo:block>
            <xsl:value-of select="body"/>
          </fo:block>
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
</xsl:stylesheet>