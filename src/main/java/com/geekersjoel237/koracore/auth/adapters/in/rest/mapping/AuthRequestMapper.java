package com.geekersjoel237.koracore.auth.adapters.in.rest.mapping;

import com.geekersjoel237.koracore.auth.adapters.in.rest.api.login.LoginRequest;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.logout.LogoutRequest;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.refreshToken.RefreshRequest;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.register.RegisterRequest;
import com.geekersjoel237.koracore.auth.adapters.in.rest.api.verifyOtp.VerifyOtpRequest;
import com.geekersjoel237.koracore.auth.application.command.LoginCommand;
import com.geekersjoel237.koracore.auth.application.command.LogoutCommand;
import com.geekersjoel237.koracore.auth.application.command.RefreshTokensCommand;
import com.geekersjoel237.koracore.auth.application.command.RegisterCommand;
import com.geekersjoel237.koracore.auth.application.command.VerifyOtpCommand;
import com.geekersjoel237.koracore.shared.adapters.in.rest.mapping.RequestMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.mapping.RequestConversions.CORRELATION_ID;

@Mapper(config = RequestMapperConfig.class, uses = AuthRequestConversions.class)
public interface AuthRequestMapper {

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "fullName", source = "request.fullName")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "phonePrefix", source = "request.phonePrefix")
    @Mapping(target = "phoneNumber", source = "request.phoneNumber")
    @Mapping(target = "pin", source = "request.rawPin")
    RegisterCommand toCommand(RegisterRequest request, String correlationId);

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "code", source = "request.code")
    VerifyOtpCommand toCommand(VerifyOtpRequest request, String correlationId);

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "pin", source = "request.rawPin")
    LoginCommand toCommand(LoginRequest request, String correlationId);

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "refreshToken", source = "request.refreshToken")
    RefreshTokensCommand toCommand(RefreshRequest request, String correlationId);

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "refreshToken", source = "request.refreshToken")
    LogoutCommand toCommand(LogoutRequest request, String correlationId);
}
