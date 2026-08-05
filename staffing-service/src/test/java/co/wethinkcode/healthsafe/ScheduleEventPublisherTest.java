package co.wethinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust to match your actual implementation.
 *
 * A class `ScheduleEventPublisher` with:
 *
 *   ScheduleEventPublisher(String brokerUrl, String topicName)
 *   void publish(ScheduleEvent event) throws Exception (or a checked JMSException)
 *
 * A `ScheduleEvent` record/class with:
 *   String  getWardId()
 *   int     getAlertLevel()
 *   int     getDoctorCount()
 *   boolean isSupervisorRequired()
 *
 * Serialized to JSON on the wire, matching the agreed schema, e.g.:
 *   {"wardId":"W-05","alertLevel":6,"doctorCount":3,"supervisorRequired":true,"timestamp":"..."}
 *
 * TEST STRATEGY: rather than pointing at the real dockerized broker
 * (tcp://localhost:61616), this spins up an embedded/VM broker
 * (vm://localhost) that ActiveMQ Classic provides in-process — no
 * Docker, no network, isolated per test. The test itself acts as a
 * plain JMS subscriber on the same topic to verify what the publisher
 * actually sent, independent of any subscriber implementation.
 *
 * If your activemq-client version uses javax.jms instead of
 * jakarta.jms, swap the import prefix — nothing else changes.
 */

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleEventPublisherTest {

    // Random per-test-run VM broker name avoids cross-test bleed if the JVM
    // reuses the same embedded broker instance between test classes.
    private static final String BROKER_URL = "vm://test-broker-" + System.nanoTime() + "?broker.persistent=false";
    private static final String TOPIC_NAME = "staffing-events-topic";

    private Connection testConsumerConnection;
    private Session testConsumerSession;
    private MessageConsumer testConsumer;
    private ScheduleEventPublisher publisher;

    @BeforeEach
    void setUp() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        testConsumerConnection = factory.createConnection();
        testConsumerConnection.start();
        testConsumerSession = testConsumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = testConsumerSession.createTopic(TOPIC_NAME);
        testConsumer = testConsumerSession.createConsumer(topic);

        publisher = new ScheduleEventPublisher(BROKER_URL, TOPIC_NAME);
    }

    @AfterEach
    void tearDown() throws JMSException {
        if (testConsumer != null) testConsumer.close();
        if (testConsumerSession != null) testConsumerSession.close();
        if (testConsumerConnection != null) testConsumerConnection.close();
    }

    @Test
    @DisplayName("publishing a schedule event delivers a message to the topic with the correct fields")
    void publishDeliversMessageWithCorrectFields() throws Exception {
        ScheduleEvent event = new ScheduleEvent("W-05", 6, 3, true);

        publisher.publish(event);

        Message received = testConsumer.receive(2000); // 2s is generous for an in-process broker
        assertNotNull(received, "expected a message on the topic but none arrived within timeout");
        assertInstanceOf(TextMessage.class, received);

        String body = ((TextMessage) received).getText();
        assertTrue(body.contains("\"wardId\":\"W-05\"") || body.contains("\"wardId\": \"W-05\""));
        assertTrue(body.contains("6"));  // alertLevel
        assertTrue(body.contains("3"));  // doctorCount
        assertTrue(body.toLowerCase().contains("supervisorrequired"));
    }

    @Test
    @DisplayName("publishing does not throw even when there are no active subscribers yet")
    void publishSucceedsWithNoSubscribers() {
        // tear down the only subscriber to simulate ward-service not being up yet
        assertDoesNotThrow(() -> {
            testConsumer.close();
            publisher.publish(new ScheduleEvent("W-06", 2, 1, false));
        });
    }

    @Test
    @DisplayName("multiple published events arrive in the order they were sent")
    void multipleEventsArriveInOrder() throws Exception {
        publisher.publish(new ScheduleEvent("W-01", 1, 1, false));
        publisher.publish(new ScheduleEvent("W-02", 7, 3, true));

        Message first = testConsumer.receive(2000);
        Message second = testConsumer.receive(2000);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(((TextMessage) first).getText().contains("W-01"));
        assertTrue(((TextMessage) second).getText().contains("W-02"));
    }
}
