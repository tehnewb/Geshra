package geshra.android;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Exercises connection publication versus activity destruction with no network or Android mocks.
 * Both race orders must disconnect the request, and redirect release must not clear a newer owner.
 */
public final class DownloadConnectionTest {
    /**
     * Activity destruction before worker publication cannot leave a live unowned connection.
     * @throws Exception if a constant fake request cannot be constructed
     */
    @Test
    public void rejectsPublicationAfterDestruction() throws Exception {
        DownloadConnection owner = new DownloadConnection();
        owner.close();
        FakeDownloadConnection request = new FakeDownloadConnection();
        assertFalse(owner.publish(request));
        assertTrue(request.disconnected());
    }

    /**
     * Destroying an owner disconnects a previously published request and remains idempotent.
     * @throws Exception if a constant fake request cannot be constructed
     */
    @Test
    public void cancelsPublishedConnection() throws Exception {
        DownloadConnection owner = new DownloadConnection();
        FakeDownloadConnection request = new FakeDownloadConnection();
        assertTrue(owner.publish(request));
        owner.close();
        owner.close();
        assertTrue(owner.isClosed());
        assertTrue(request.disconnected());
    }

    /**
     * A redirect must release its old connection before publishing a replacement.
     * @throws Exception if a constant fake request cannot be constructed
     */
    @Test
    public void preservesSinglePublishedConnection() throws Exception {
        DownloadConnection owner = new DownloadConnection();
        FakeDownloadConnection first = new FakeDownloadConnection();
        FakeDownloadConnection second = new FakeDownloadConnection();
        assertTrue(owner.publish(first));
        assertThrows(IllegalStateException.class, () -> owner.publish(second));
        owner.release(first);
        assertTrue(owner.publish(second));
        owner.release(first);
        owner.close();
        assertTrue(second.disconnected());
    }

    /**
     * Repeats concurrent publication and destruction; whichever wins, no request remains owned.
     * @throws Exception if worker execution or synchronization times out
     */
    @Test
    public void resolvesBothConcurrentPublicationOrders() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 100; iteration++) {
                DownloadConnection owner = new DownloadConnection();
                FakeDownloadConnection request = new FakeDownloadConnection();
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> publishing = workers.submit(() -> {
                    start.await();
                    return owner.publish(request);
                });
                Future<?> closing = workers.submit(() -> {
                    start.await();
                    owner.close();
                    return null;
                });
                start.countDown();
                publishing.get(5, TimeUnit.SECONDS);
                closing.get(5, TimeUnit.SECONDS);
                assertTrue(request.disconnected());
                assertTrue(owner.isClosed());
            }
        } finally {
            workers.shutdownNow();
        }
    }
}
