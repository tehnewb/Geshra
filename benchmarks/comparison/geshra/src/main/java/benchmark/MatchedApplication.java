package benchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the isolated production-packaged Geshra comparator on its configured benchmark port.
 * No benchmark route is added to the library or its normal consumer example.
 */
@SpringBootApplication
public class MatchedApplication {
    /**
     * Starts Spring and Geshra with command-line supplied port settings.
     * @param arguments benchmark server configuration
     */
    public static void main(String[] arguments) {
        SpringApplication.run(MatchedApplication.class, arguments);
    }
}
