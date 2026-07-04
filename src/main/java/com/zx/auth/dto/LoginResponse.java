package com.zx.auth.dto;

import java.util.List;

public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expireIn;
    private UserInfo userInfo;

    public static class UserInfo {
        private Long id;
        private String username;
        private List<String> roles;

        public UserInfo() {
        }

        public UserInfo(Long id, String username) {
            this.id = id;
            this.username = username;
        }

        public UserInfo(Long id, String username, List<String> roles) {
            this.id = id;
            this.username = username;
            this.roles = roles;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public List<String> getRoles() { return roles; }
        public void setId(Long id) { this.id = id; }
        public void setUsername(String username) { this.username = username; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getExpireIn() { return expireIn; }
    public void setExpireIn(long expireIn) { this.expireIn = expireIn; }
    public UserInfo getUserInfo() { return userInfo; }
    public void setUserInfo(UserInfo userInfo) { this.userInfo = userInfo; }
}
