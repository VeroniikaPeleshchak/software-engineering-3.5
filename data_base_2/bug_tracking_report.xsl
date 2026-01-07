<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="html" indent="yes" encoding="UTF-8"/>

  <xsl:template match="/">
    <html>
      <head>
        <style>
          table {
            border-collapse: collapse;
            width: 100%;
            font-family: Arial, sans-serif;
          }
          th, td {
            border: 1px solid #ccc;
            padding: 8px;
            text-align: left;
          }
          th {
            background-color: #f0f0f0;
            font-weight: bold;
          }

          tr.critical { background-color: #ff9999; }
          tr.high { background-color: #ffcccb; }
          tr.medium { background-color: #fff5cc; }
          tr.low { background-color: #ccffcc; }

          tr { font-weight: normal; }

          ul { font-family: "Courier New", monospace; margin-top: 20px; }
          li { margin-bottom: 8px; }
        </style>
      </head>
      <body>
        <h2>Bug Report Table</h2>
        <table>
          <tr>
            <th>#</th>
            <th>Software</th>
            <th>Bug Name</th>
            <th>Priority</th>
            <th>Status</th>
            <th>Module</th>
            <th>Date</th>
            <th>Created By</th>
            <th>Assigned To</th>
          </tr>

          <xsl:for-each select="bug_tracking_system/software">
            <xsl:sort select="@id" data-type="number" order="ascending"/>
            <xsl:for-each select="bug">
              <xsl:sort select="@date" data-type="text" order="ascending"/>
              <tr>
                <xsl:attribute name="class">
                  <xsl:choose>
                    <xsl:when test="priority='Critical'">critical</xsl:when>
                    <xsl:when test="priority='High'">high</xsl:when>
                    <xsl:when test="priority='Medium'">medium</xsl:when>
                    <xsl:otherwise>low</xsl:otherwise>
                  </xsl:choose>
                </xsl:attribute>
                <td>
                  <xsl:number level="any" count="software" from="bug_tracking_system" format="1"/>
                </td>
                <td><xsl:value-of select="../name"/></td>
                <td><xsl:value-of select="name"/></td>
                <td><xsl:value-of select="priority"/></td>
                <td><xsl:value-of select="status"/></td>
                <td><xsl:value-of select="module"/></td>
                <td><xsl:value-of select="@date"/></td>
                <td><xsl:value-of select="concat(created_by/first_name,' ',created_by/last_name)"/></td>
                <td><xsl:value-of select="concat(assigned_to/first_name,' ',assigned_to/last_name)"/></td>
              </tr>
            </xsl:for-each>
          </xsl:for-each>
        </table>

        <h2>Corrections List</h2>
        <ul>
          <xsl:for-each select="//correction">
            <xsl:sort select="@date" data-type="text" order="descending"/>
            <li>
              <strong>Дата:</strong> <xsl:value-of select="@date"/> <br/>
              <strong>Коментар:</strong> <xsl:value-of select="comment"/> <br/>
              <strong>Виправив:</strong>
              <xsl:value-of select="concat(corrected_by/first_name, ' ', corrected_by/last_name)"/>
              <em> (<xsl:value-of select="corrected_by/email"/>)</em> <br/>
              <strong>Software:</strong> <xsl:value-of select="ancestor::bug/../name"/>
            </li>
          </xsl:for-each>
        </ul>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>



