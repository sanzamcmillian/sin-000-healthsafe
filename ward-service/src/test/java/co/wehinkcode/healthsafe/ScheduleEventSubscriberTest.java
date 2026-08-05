package co.wehinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust to match your actual implementation.
 *
 * A class `ScheduleEventSubscriber` with:
 *
 *   ScheduleEventSubscriber(String brokerUrl, String topicName, StaffingInfoStore store)
 *   void start() throws Exception   // begins listening, e.g. sets a JMS MessageListener
 *   void stop() throws Exception    // closes connection/session cleanly
 *
 * A `StaffingInfoStore` (the "in-memory field" ward-service updates on
 * message arrival) with:
 *
 *   Optional<StaffingInfo> getStaffingInfo(String wardId)
 *
 * A `StaffingInfo` with:
 *   int     getDoctorCount()
 *   boolean isSupervisorRequired()
 *   int     getAlertLevel()
 *
 * If your actual design instead updates the Ward object itself (rather
 * than a separate StaffingInfo store), swap getStaffingInfo(id) for
 * whatever accessor exposes the post-update state — the test shape
 * (publish -> poll -> assert) stays the same.
 *
 * TEST STRATEGY: embedded VM broker again (no Docker). The test acts
 * as staffing-service by publishing directly via plain JMS, then polls
 * the store rather than sleeping a fixed duration — the subscriber
 * reacts asynchronously, so a fixed sleep is either too short
 * (flaky) or wastes time on every run.
 */

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleEventSubscriberTest {

    private static final String BROKER_URL = "vm://test-broker-" + System.nanoTime() + "?broker.persistent=false";
    private static final String TOPIC_NAME = "staffing-events-topic";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private Connection testPublisherConnection;
    private Session testPublisherSession;
    private MessageProducer testPublisher;
    private Topic topic;

    private StaffingInfoStore store;
    private ScheduleEventSubscriber subscriber;

    @BeforeEach
    void setUp() throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        testPublisherConnection = factory.createConnection();
        testPublisherConnection.start();
        testPublisherSession = testPublisherConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        topic = testPublisherSession.createTopic(TOPIC_NAME);
        testPublisher = testPublisherSession.createProducer(topic);

        store = new StaffingInfoStore();
        subscriber = new ScheduleEventSubscriber(BROKER_URL, TOPIC_NAME, store);
        subscriber.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        subscriber.stop();
        if (testPublisher != null) testPublisher.close();
        if (testPublisherSession != null) testPublisherSession.close();
        if (testPublisherConnection != null) testPublisherConnection.close();
    }

    @Test
    @DisplayName("an incoming schedule event updates the store for the correct ward")
    void incomingEventUpdatesStore() throws Exception {
        publishRawEvent("W-05", 6, 3, true);

        Optional<StaffingInfo> result = awaitUntilPresent(() -> store.getStaffingInfo("W-05"));

        assertTrue(result.isPresent(), "expected staffing info for W-05 to appear after the event was consumed");
        assertEquals(3, result.get().getDoctorCount());
        assertTrue(result.get().isSupervisorRequired());
        assertEquals(6, result.get().getAlertLevel());
    }

    @Test
    @DisplayName("a later event for the same ward overwrites the earlier one, not accumulates")
    void laterEventOverwritesEarlierOneForSameWard() throws Exception {
        publishRawEvent("W-05", 2, 1, false);
        awaitUntilPresent(() -> store.getStaffingInfo("W-05"));

        publishRawEvent("W-05", 7, 3, true);
        Optional<StaffingInfo> result = awaitUntil(
            () -> store.getStaffingInfo("W-05"),
            info -> info.isPresent() && info.get().getDoctorCount() == 3
        );

        assertTrue(result.isPresent());
        assertEquals(3, result.get().getDoctorCount());
        assertTrue(result.get().isSupervisorRequired());
    }

    @Test
    @DisplayName("events for different wards are tracked independently")
    void eventsForDifferentWardsAreIndependent() throws Exception {
        publishRawEvent("W-01", 1, 1, false);
        publishRawEvent("W-02", 8, 3, true);

        Optional<StaffingInfo> ward1 = awaitUntilPresent(() -> store.getStaffingInfo("W-01"));
        Optional<StaffingInfo> ward2 = awaitUntilPresent(() -> store.getStaffingInfo("W-02"));

        assertEquals(1, ward1.get().getDoctorCount());
        assertEquals(3, ward2.get().getDoctorCount());
    }

    @Test
    @DisplayName("a malformed message on the topic does not crash the subscriber or block later valid events")
    void malformedMessageDoesNotCrashSubscriber() throws Exception {
        TextMessage garbage = testPublisherSession.createTextMessage("not valid json at all");
        testPublisher.send(garbage);

        // subscriber should have logged/ignored the bad message and still be alive to handle this one:
        publishRawEvent("W-05", 4, 2, false);

        Optional<StaffingInfo> result = awaitUntilPresent(() -> store.getStaffingInfo("W-05"));
        assertTrue(result.isPresent(), "subscriber should still process valid events after a malformed one");
    }

    // ---------- helpers ----------

    private void publishRawEvent(String wardId, int alertLevel, int doctorCount, boolean supervisorRequired) throws JMSException {
        String json = String.format(
            "{\"wardId\":\"%s\",\"alertLevel\":%d,\"doctorCount\":%d,\"supervisorRequired\":%b,\"timestamp\":\"%s\"}",
            wardId, alertLevel, doctorCount, supervisorRequired, Instant.now());
        TextMessage message = testPublisherSession.createTextMessage(json);
        testPublisher.send(message);
    }

    private Optional<StaffingInfo> awaitUntilPresent(Supplier<Optional<StaffingInfo>> supplier) {
        return awaitUntil(supplier, Optional::isPresent);
    }

    private Optional<StaffingInfo> awaitUntil(
            Supplier<Optional<StaffingInfo>> supplier,
            java.util.function.Predicate<Optional<StaffingInfo>> condition) {

        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        Optional<StaffingInfo> last = Optional.empty();

        while (Instant.now().isBefore(deadline)) {
            last = supplier.get();
            if (condition.test(last)) {
                return last;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        fail("condition was not met within " + POLL_TIMEOUT + " (last value: " + last + ")");
        return last; // unreachable, fail() throws
    }
}
