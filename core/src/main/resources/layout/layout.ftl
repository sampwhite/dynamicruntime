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
    <#--noinspection FtlReferencesInspection-->
    <h4>
        <#if username??>
            <#--noinspection FtlReferencesInspection-->
            Hello <span class="highlight">${username}</span><br/>
            <#--noinspection HtmlUnknownTarget-->
            <a href="/logout">Logout</a>
         </#if>
    </h4>
    <p>Changing your data model as your application runs.</p>
    <p>See list of <a href="/content/html/endpoints.html">endpoints</a>.</p>
    <p>See the <a href="/content/md/Home.md">Home</a> page for more information about this application.</p>
    <#if !username??>
        To login (or register as a new user) go to <a href="/content/html/login.html">Login</a>.
    </#if>
</div>
<div class="main">
    <#--noinspection FtlReferencesInspection-->
    ${body}
</div>
</body>
</html>