package com.zx.auth.dto;

public class SendCodeRequest {
    private String loginType; // qq_email_code or phone_code
    private String target; // email or phone
    private String scene; // e.g. login

    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
}

