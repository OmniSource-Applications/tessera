<#import "template.ftl" as layout>
<@layout.layout>
    <div class="login-wrapper">
        <div class="login-card">
            <#if message?has_content>
                ${kcSanitize(message.summary)?no_esc}
            <#else>
                An unexpected error occurred.
            </#if>
            <div style="margin-top: 16px;">
                <a href="${url.loginUrl}">Back to login</a>
            </div>
        </div>
    </div>
</@layout.layout>