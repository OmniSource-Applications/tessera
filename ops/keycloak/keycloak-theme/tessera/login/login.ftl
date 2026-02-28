<#import "template.ftl" as layout>

<@layout.layout>
<#-- Logo -->
    <div class="login-wrapper">



        <div class="login-card">
            <div class="logo-block">
                <img src="${url.resourcesPath}/img/logo.png" alt="Tessera"/>
            </div>

            <#-- Keycloak error/info message -->
            <#if message?has_content>
                <div class="alert alert-error">
                    ${kcSanitize(message.summary)?no_esc}
                </div>
            </#if>

            <form id="kc-form-login" action="${url.loginAction}" method="post">
                <div class="field">
                    <label for="username">Username</label>
                    <input id="username"
                           name="username"
                           type="text"
                           autofocus
                           value="${(login.username!'')}" />
                </div>

                <div class="field">
                    <label for="password">Password</label>
                    <input id="password" name="password" type="password" />
                </div>

                <button type="submit" class="btn-primary">Sign In</button>
            </form>

        </div>

        <p class="footer-note">
            &copy; 2026 Tessera · All rights reserved.
        </p>

    </div>
</@layout.layout>