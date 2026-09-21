package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.StaffingInfoStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import jakarta.jms.*;

public class ScheduleEventSubscriber implements MessageListener{
    private final String brokerUrl;
    private final String topicName;
    private final StaffingInfoStore store;
    private final ObjectMapper objectMapper;

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;


    public ScheduleEventSubscriber(String brokerUrl, String topicName, StaffingInfoStore store) {
        this.brokerUrl = brokerUrl;
        this.topicName = topicName;
        this.store = store;
        this.objectMapper = new ObjectMapper();
    }

    void start() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(topicName);

        consumer = session.createConsumer(topic);
        consumer.setMessageListener(this);
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage textMessage) {
                String payload = textMessage.getText();
                parseAndStore(payload);
            }
        } catch (Exception e) {
            System.out.println("Failed to process incoming message: " + e.getMessage());
        }
    }

    private void parseAndStore(String json) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);

        String wardId = rootNode.path("wardId").asText();
        int alertLevel = rootNode.path("alertLevel").asInt();
        int doctorCount = rootNode.path("doctorCount").asInt();
        boolean supervisorRequired = rootNode.path("supervisorRequired").asBoolean();

        if (wardId == null || wardId.isEmpty()) {
            throw new IllegalArgumentException("Missing wardId in JSON payload");
        }

        StaffingInfo info = new StaffingInfo(alertLevel, doctorCount, supervisorRequired);
        store.updateStaffingInfo(wardId, info);
    }
    void stop() throws JMSException {
        if (consumer != null) consumer.close();
        if (session != null) session.close();
        if (connection != null) connection.close();
    }
}
