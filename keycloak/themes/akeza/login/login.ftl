<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password'); section>
    <#if section = "form">
        <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <h2>Sign in</h2>
            <p class="lede">Use your organization username and password.</p>

            <label for="username">${msg("username")}</label>
            <input tabindex="1" id="username" name="username" type="text" autofocus autocomplete="username"
                   value="${(login.username!'')}"
                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" />

            <label for="password">${msg("password")}</label>
            <input tabindex="2" id="password" name="password" type="password" autocomplete="current-password"
                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" />

            <#if messagesPerField.existsError('username','password')>
                <p class="alert error" role="alert">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</p>
            </#if>

            <#if realm.rememberMe && !usernameEditDisabled??>
                <label class="remember">
                    <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if> />
                    ${msg("rememberMe")}
                </label>
            </#if>

            <button tabindex="4" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
        </form>
    </#if>
</@layout.registrationLayout>
