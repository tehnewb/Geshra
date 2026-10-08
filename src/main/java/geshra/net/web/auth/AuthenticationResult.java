package geshra.net.web.auth;

/**
 * The {@code AuthenticationResult} record represents the result of an authentication
 * operation. It encapsulates whether the authentication was successful and an
 * accompanying resultCode describing the outcome.
 *
 * @param user       the user that was authenticated
 * @param resultCode the code corresponding to the authentication result
 * @author Albert Beaupre
 */
public record AuthenticationResult(User user, int resultCode) {
    /**
     * Reports whether authentication produced a user.
     * @return successful authentication
     */
    public boolean isSuccess() { return user != null; }
}
