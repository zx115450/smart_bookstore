package com.zx.auth.service.login;

import com.zx.auth.dto.LoginRequest;
import com.zx.auth.entity.AuthUser;
import com.zx.auth.entity.AuthUserIdentity;
import com.zx.auth.repository.AuthUserIdentityRepository;
import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.service.AuthRedisService;
import com.zx.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PhoneCodeLoginHandler implements LoginHandler {
    private final AuthRedisService authRedisService;
    private final AuthUserIdentityRepository identityRepo;
    private final AuthUserRepository userRepo;

    @Override
    public boolean supports(String loginType) {
        return "phone_code".equals(loginType);
    }

    @Override
    public AuthUser handle(LoginRequest req, String clientIp) {
        String scene = "login";
        if (!authRedisService.verifyAndConsumeCode(
                "phone_code", scene, req.getTarget(), AuthService.sha256Hex(req.getCode()))) {
            return null;
        }

        Optional<AuthUserIdentity> ident = identityRepo.findByIdentityTypeAndIdentityValue("phone", req.getTarget());
        if (ident.isPresent()) {
            return ident.get().getUser();
        }

        AuthUser nu = new AuthUser();
        nu.setUsername(req.getTarget());
        nu = userRepo.save(nu);
        AuthUserIdentity ai = new AuthUserIdentity();
        ai.setUser(nu);
        ai.setIdentityType("phone");
        ai.setIdentityValue(req.getTarget());
        ai.setVerified(true);
        ai.setIsPrimary(true);
        identityRepo.save(ai);
        return nu;
    }
}
