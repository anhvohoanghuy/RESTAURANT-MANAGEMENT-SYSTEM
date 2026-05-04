package com.example.feat1.DDD.auth.application;

import com.example.feat1.DDD.auth.application.dto.AuthRequest;
import com.example.feat1.DDD.auth.application.dto.AuthResponse;
import com.example.feat1.DDD.auth.application.auth_service.auth_provider.IAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;


@RequiredArgsConstructor
@Service
public class AuthService implements IAuthService{
    private final Map<String, IAuthProvider> authProviderMap;

    @Override
    public AuthResponse login(AuthRequest authRequest) {
        IAuthProvider provider = authProviderMap.get(authRequest.getAuthType().name());
        if(provider == null) throw new RuntimeException("AuthProvider not found");
        return  provider.authenticate(authRequest);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        return null;
    }

    @Override
    public void logout(String refreshToken) {

    }
}
