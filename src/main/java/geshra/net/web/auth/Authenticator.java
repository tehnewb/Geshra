package geshra.net.web.auth;

/**
 * The {@code Authenticator} interface defines the contract for authentication
 * mechanisms. Implementations of this interface are responsible for verifying
 * user credentials and returning the result of the authentication process.
 * <p>
 * The primary purpose of this interface is to standardize the process of
 * authentication in systems that require credential validation. Implementing
 * classes should provide specific authentication logic, such as validating
 * against in-memory data stores, databases, or external systems.
 * <p>
 * This interface simplifies integration and consistency across different
 * authentication strategies by providing a unified method to handle authentication.
 *
 * @author Albert Beaupre
 */
public interface Authenticator {

    /**
     * Authenticates a user based on the provided username and password credentials.
     * <p>
     * This method checks the validity of the provided credentials against the underlying
     * authentication mechanism (e.g., database, in-memory store). It returns an
     * {@code AuthenticationResult} indicating whether the authentication was successful
     * and provides additional details about the outcome.
     *
     * @param username the username of the user attempting authentication
     * @param password the password associated with the username
     * @return an {@code AuthenticationResult} object containing the authentication outcome
     * and any relevant details or error messages
     */
    AuthenticationResult authenticate(String username, String password);

}
