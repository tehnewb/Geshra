package geshra.net.web;

import geshra.net.web.auth.Authenticator;
import geshra.net.web.auth.AuthenticationResult;
import geshra.net.web.auth.DefaultAuthenticator;
import java.util.function.Consumer;

/**
 * One bounded credential verification, including a cancellation path that releases its HTTP request.
 * @param service owning session service
 * @param authenticator credential verifier
 * @param session browser session
 * @param username supplied username
 * @param password supplied password
 * @param nonce browser action identifier
 * @param completed HTTP completion callback
 */
record AuthenticationTask(SessionService service, Authenticator authenticator, SessionContext session, String username, String password, int nonce, Consumer<AuthenticationResult> completed) implements Runnable {
    @Override
    public void run() {
        AuthenticationResult result;
        try {
            result = authenticator.authenticate(username, password);
            if (result == null) throw new IllegalStateException("Authenticator returned null");
        } catch (RuntimeException failure) {
            result = new AuthenticationResult(null, DefaultAuthenticator.INVALID_PASSWORD);
            session.authenticationFailure(failure);
        }
        service.finish(session, result, nonce, completed);
    }

    /**
     * Completes a queued request when shutdown prevents verification.
     */
    void cancel() { completed.accept(new AuthenticationResult(null, DefaultAuthenticator.INVALID_PASSWORD)); }
}
