package com.spmf.iptv.account;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Path("/iptv/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class IptvAccountResource {

    @Inject
    IptvAccountStore store;

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "iptv.accounts.active.roles", defaultValue = "ADMIN")
    String allowedRoles;

    @GET
    @Path("/active")
    @Authenticated
    public List<ActiveIptvAccount> active() {

        boolean allowed = Arrays.stream(allowedRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .anyMatch(identity::hasRole);

        if (!allowed) {
            throw new ForbiddenException("not allowed to read IPTV accounts");
        }

        List<IptvAccount> accounts = store.activeForApp();

        return accounts.stream()
                .sorted(Comparator.comparing(
                        account -> account.label == null ? "" : account.label
                ))
                .map(account -> new ActiveIptvAccount(
                        account.id,
                        account.label,
                        account.type,
                        account.url,
                        account.expiresAt,
                        account.lastCheckedAt,
                        account.latencyMs,
                        account.isDefault
                ))
                .toList();
    }
}
