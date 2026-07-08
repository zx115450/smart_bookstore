package com.zx.auth.dto;

import java.math.BigDecimal;
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
        private BigDecimal balance;

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

        public UserInfo(Long id, String username, List<String> roles, BigDecimal balance) {
            this.id = id;
            this.username = username;
            this.roles = roles;
            this.balance = balance;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public List<String> getRoles() { return roles; }
        public BigDecimal getBalance() { return balance; }
        public void setId(Long id) { this.id = id; }
        public void setUsername(String username) { this.username = username; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
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
