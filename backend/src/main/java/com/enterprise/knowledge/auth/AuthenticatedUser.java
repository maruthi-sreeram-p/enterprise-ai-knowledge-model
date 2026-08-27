package com.enterprise.knowledge.auth;

import com.enterprise.knowledge.user.Role;
import com.enterprise.knowledge.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal.
 *
 * Carrying the tenant id here is the foundation of tenant isolation: services
 * take the tenant from this object, which was derived from a signed token, and
 * never from a request parameter or body field. A caller therefore has no way
 * to name a tenant they do not belong to.
 */
public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final UUID tenantId;
    private final String email;
    private final String name;
    private final Role role;
    private final boolean enabled;

    public AuthenticatedUser(UUID id, UUID tenantId, String email, String name, Role role, boolean enabled) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.enabled = enabled;
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getTenantId(), user.getEmail(),
                user.getName(), user.getRole(), user.isEnabled());
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    /**
     * Always null. The principal is rebuilt from a verified token on each
     * request, so the password hash is never loaded into the security context.
     */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
