package virtualbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Isolated real-server virtual-list workload; no benchmark endpoints enter the library.
 */
@SpringBootApplication
public class VirtualApplication {
    /**
     * Starts the production-packaged benchmark with an explicitly supplied port.
     * @param arguments Spring configuration
     */
    public static void main(String[] arguments) {
        /*
         * Use the normal server and runtime rather than a simulated transport.
         */
        SpringApplication.run(VirtualApplication.class, arguments);
    }
}
