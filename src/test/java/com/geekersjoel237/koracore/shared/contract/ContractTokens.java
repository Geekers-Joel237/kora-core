package com.geekersjoel237.koracore.shared.contract;

import com.geekersjoel237.koracore.auth.domain.enums.Role;
import com.geekersjoel237.koracore.auth.domain.model.User;
import com.geekersjoel237.koracore.auth.domain.vo.Tokens;
import com.geekersjoel237.koracore.auth.ports.out.security.TokenIssuer;
import com.geekersjoel237.koracore.shared.domain.vo.Id;

public final class ContractTokens {

    public static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111111";
    public static final String ADMIN_ID = "22222222-2222-2222-2222-222222222222";

    private final TokenIssuer issuer;

    ContractTokens(TokenIssuer issuer) {
        this.issuer = issuer;
    }

    public String customer() {
        return issue(CUSTOMER_ID, Role.CUSTOMER).accessToken().token().value();
    }

    public String admin() {
        return issue(ADMIN_ID, Role.ADMIN).accessToken().token().value();
    }

    public String customerRefreshToken() {
        return issue(CUSTOMER_ID, Role.CUSTOMER).refreshToken().token().value();
    }

    private Tokens issue(String id, Role role) {
        return issuer.issue(User.create(new Id(id), "Contract Holder", role.name().toLowerCase() + "@contract.test", role));
    }
}
