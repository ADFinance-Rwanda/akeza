<#macro registrationLayout displayMessage=true>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Akeza · Sign in</title>
    <#if properties.styles?has_content>
        <#list properties.styles?split(" ") as style>
            <link rel="stylesheet" href="${url.resourcesPath}/${style}">
        </#list>
    </#if>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
</head>
<body>
<div class="shell">
    <aside class="pane">
        <div class="mark">A</div>
        <h1>Akeza</h1>
        <p>Project and task workspaces for your organization. Sign in with your Keycloak account.</p>
    </aside>
    <main class="panel">
        <div class="card">
            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="alert ${message.type}">${kcSanitize(message.summary)?no_esc}</div>
            </#if>
            <#nested "form">
        </div>
        <p class="foot">Authenticated by Keycloak · local demo only</p>
    </main>
</div>
</body>
</html>
</#macro>
