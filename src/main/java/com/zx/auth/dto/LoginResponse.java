package com.zx.auth.dto;

public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expireIn; // seconds
    private UserInfo userInfo;

    public static class UserInfo {
        private Long id;
        private String username;
        public UserInfo() {}
        public UserInfo(Long id, String username) { this.id = id; this.username = username; }
        public Long getId() { return id; }
        public String getUsername() { return username; }
        public void setId(Long id) { this.id = id; }
        public void setUsername(String username) { this.username = username; }
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

