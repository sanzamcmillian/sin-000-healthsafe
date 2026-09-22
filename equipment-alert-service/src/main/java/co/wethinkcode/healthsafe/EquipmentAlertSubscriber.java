package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

public class EquipmentAlertSubscriber implements MessageListener{
    private final String brokerUrl;
    private final String queueName;
    private final AlertStore alertStore;
    private final ObjectMapper objectMapper;

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public EquipmentAlertSubscriber(String brokerUrl, String queueName, AlertStore alertStore) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.alertStore = alertStore;
        this.objectMapper = new ObjectMapper();
    }

    public void start() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(queueName);

        consumer = session.createConsumer(queue);
        consumer.setMessageListener(this);
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage textMessage) {
                String payload = textMessage.getText();

                JsonNode root = objectMapper.readTree(payload);

                if (root.has("wardId") && root.has("equipmentType")) {
                    alertStore.addAlert(payload);

                    System.err.println("ALERT PROCESSED; " + payload);
                } else {
                    System.err.println("Ignored message with missing required fields: " + payload);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to process message (invalid format): " + e.getMessage());
        }
    }

    public void stop() throws JMSException {
        if (consumer != null) consumer.close();
        if (session != null) session.close();
        if (connection != null) connection.close();
    }
}
