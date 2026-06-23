package com.zx.auth.dto;

public class LoginRequest {
    private String loginType; // qq_email_code, phone_code, password
    private Boolean rememberMe;

    // password login
    private String account;
    private String password;

    // code login
    private String target; // email or phone
    private String code;

    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public Boolean getRememberMe() { return rememberMe == null ? false : rememberMe; }
    public void setRememberMe(Boolean rememberMe) { this.rememberMe = rememberMe; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}

