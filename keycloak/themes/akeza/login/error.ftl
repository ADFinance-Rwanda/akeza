<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "form">
        <h2>Something went wrong</h2>
        <p class="lede">${kcSanitize(message.summary)?no_esc}</p>
        <#if client?? && client.baseUrl?has_content>
            <p><a href="${client.baseUrl}">Return to the application</a></p>
        <#else>
            <p class="lede">Close this tab and open the application again.</p>
        </#if>
    </#if>
</@layout.registrationLayout>
