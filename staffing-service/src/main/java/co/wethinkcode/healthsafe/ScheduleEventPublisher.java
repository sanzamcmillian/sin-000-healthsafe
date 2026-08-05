package co.wethinkcode.healthsafe;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

public class ScheduleEventPublisher {

    private final String brokerUrl;
    private final String topicName;

    public ScheduleEventPublisher(String brokerUrl, String topicName) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
    }

    public void publish(ScheduleEvent event) throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);

        try (Connection connection = factory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            Topic topic = session.createTopic(topicName);

            try (MessageProducer producer = session.createProducer(topic)) {
                String jsonPayload = toJson(event);
                TextMessage message = session.createTextMessage(jsonPayload);
                producer.send(message);
            }
        }
    }

    private String toJson(ScheduleEvent event) {
        return String.format(
                "{\"wardId\":\"%s\",\"alertLevel\":%d,\"doctorCount\":%d,\"supervisorRequired\":%b\"}",
                event.getWardId(),
                event.getAlertLevel(),
                event.getDoctorCount(),
                event.isSupervisorRequired()
        );
    }
}
