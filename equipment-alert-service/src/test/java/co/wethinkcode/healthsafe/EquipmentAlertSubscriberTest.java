package co.wethinkcode.healthsafe;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentAlertSubscriberTest {

    private static final String BROKER_URL = "vm://test-broker-" + System.nanoTime() + "?broker.persistent=false";
    private static final String QUEUE_NAME = "equipment-failure-queue";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private Connection testPublisherConnection;
    private Session testPublisherSession;
    private MessageProducer testPublisher;

    private AlertStore alertStore;
    private EquipmentAlertSubscriber subscriber;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Set up the embedded test publisher
        ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        testPublisherConnection = factory.createConnection();
        testPublisherConnection.start();
        testPublisherSession = testPublisherConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = testPublisherSession.createQueue(QUEUE_NAME);
        testPublisher = testPublisherSession.createProducer(queue);

        // 2. Set up the subscriber and its data store
        alertStore = new AlertStore();
        subscriber = new EquipmentAlertSubscriber(BROKER_URL, QUEUE_NAME, alertStore);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (subscriber != null) subscriber.stop();
        if (testPublisher != null) testPublisher.close();
        if (testPublisherSession != null) testPublisherSession.close();
        if (testPublisherConnection != null) testPublisherConnection.close();
    }

    @Test
    @DisplayName("consumes a valid equipment failure alert from the queue")
    void consumesValidAlert() throws Exception {
        subscriber.start(); // Start listening

        publishMessage("{\"wardId\":\"W-01\",\"equipmentType\":\"Defibrillator\",\"description\":\"Battery low\"}");

        List<String> received = awaitUntil(
                () -> alertStore.getReceivedAlerts(),
                alerts -> !alerts.isEmpty()
        );

        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("Defibrillator"));
    }

    @Test
    @DisplayName("guaranteed delivery: processes messages published while the subscriber was offline")
    void processesBacklogMessages() throws Exception {
        // Notice we publish BEFORE starting the subscriber
        publishMessage("{\"wardId\":\"W-02\",\"equipmentType\":\"Ventilator\",\"description\":\"Pressure drop\"}");
        publishMessage("{\"wardId\":\"W-02\",\"equipmentType\":\"ECG\",\"description\":\"Screen frozen\"}");

        // Now we boot up the subscriber
        subscriber.start();

        List<String> received = awaitUntil(
                () -> alertStore.getReceivedAlerts(),
                alerts -> alerts.size() == 2
        );

        assertEquals(2, received.size(), "Should have processed both messages held in the queue");
        assertTrue(received.get(0).contains("Ventilator"));
        assertTrue(received.get(1).contains("ECG"));
    }

    @Test
    @DisplayName("ignores malformed messages without crashing the consumer thread")
    void ignoresMalformedMessages() throws Exception {
        subscriber.start();

        publishMessage("not a valid json payload");
        publishMessage("{\"wardId\":\"W-05\",\"equipmentType\":\"MRI\",\"description\":\"Coolant leak\"}");

        // It should skip the garbage message but successfully process the valid one that followed
        List<String> received = awaitUntil(
                () -> alertStore.getReceivedAlerts(),
                alerts -> !alerts.isEmpty()
        );

        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("MRI"));
    }

    // ---------- Helpers ----------

    private void publishMessage(String payload) throws JMSException {
        TextMessage message = testPublisherSession.createTextMessage(payload);
        testPublisher.send(message);
    }

    private <T> T awaitUntil(Supplier<T> supplier, java.util.function.Predicate<T> condition) {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        T last = null;

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
        fail("Condition not met within " + POLL_TIMEOUT + ". Last value: " + last);
        return last;
    }
}