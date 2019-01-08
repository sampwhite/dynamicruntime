<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <#if head??>
    <#--noinspection FtlReferencesInspection-->
    ${head}
    <#else>
    <#--noinspection FtlReferencesInspection-->
    <title>${title}</title>
    </#if>
    <link href="/content/css/dynrt.css" rel="stylesheet">
</head>
<body>
<div class="sidenav">
    <h3>Dynamic Runtime</h3>
    <p>Changing your data model as your application runs.</p>
    <p>See list of <a href="/content/html/endpoints.html">endpoints</a>.</p>
    <p>See the <a href="/content/md/Home.md">Home</a> page for more information about this application.</p>
</div>
<div class="main">
    <#--noinspection FtlReferencesInspection-->
    ${body}
</div>
</body>
</html>