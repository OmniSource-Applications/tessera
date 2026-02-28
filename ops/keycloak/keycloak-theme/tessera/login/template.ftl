<#-- tessera/login/template.ftl -->
<#macro layout>
  <!DOCTYPE html>
  <html>
  <head>
    <meta charset="utf-8">
    <title>Tessera</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/tessera.css">
  </head>

  <body class="tessera-body">
  <div class="tessera-container">
    <#nested>
  </div>
  </body>
  </html>
</#macro>