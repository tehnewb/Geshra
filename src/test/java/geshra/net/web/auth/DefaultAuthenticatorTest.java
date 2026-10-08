package geshra.net.web.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies default authenticator behavior using isolated state and observable outcomes.
 */
class DefaultAuthenticatorTest {
    /**
     * Verifies that preserves authentication and user updates with astandard map.
     */
    @Test
    void preservesAuthenticationAndUserUpdatesWithAStandardMap() {
        DefaultAuthenticator authenticator = new DefaultAuthenticator();
        User user = new User()
                .attribute("username", "Alice")
                .attribute("password", "first");
        authenticator.store(user);
        assertThat(authenticator.exists("ALICE"))
                .isTrue();
        assertThat(authenticator.authenticate("ALICE", "first"))
                .isEqualTo(new AuthenticationResult(user, DefaultAuthenticator.SUCCESS));
        assertThat(authenticator.authenticate("alice", "wrong").resultCode())
                .isEqualTo(DefaultAuthenticator.INVALID_PASSWORD);
        assertThat(authenticator.authenticate("missing", "first").resultCode())
                .isEqualTo(DefaultAuthenticator.DOES_NOT_EXIST);

        User updated = new User()
                .attribute("username", "ALICE")
                .attribute("password", "second");
        authenticator.store(updated);
        assertThat(authenticator.getDatabase())
                .hasSize(1);
        assertThat(authenticator.authenticate("alice", "second").user())
                .isSameAs(updated);
        authenticator.getDatabase()
                .remove("alice");
        assertThat(authenticator.exists("Alice"))
                .isFalse();
    }
}
