package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import jakarta.jms.*;
import jakarta.jms.IllegalStateException;
import org.apache.activemq.ActiveMQConnectionFactory;
import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.plugin.bundled.CorsPluginConfig;

import java.time.Instant;
import java.util.Map;

public class WardServiceApp {

    private static final StaffingInfoStore staffingInfoStore = new StaffingInfoStore();
    private static Connection mqConnection;
    private static Session mqSession;
    private static MessageProducer equipmentFailureProducer;

    public static void main(String[] args) {
        ScheduleEventSubscriber subscriber = null;
        try {
            subscriber = new ScheduleEventSubscriber(MqConfig.BROKER_URL, MqConfig.TOPIC, staffingInfoStore);
            subscriber.start();

            initMqPublisher();
        } catch (Exception e) {
            System.err.println("Failed to initialize ActiveMQ components: " + e.getMessage());
        }
        Javalin app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> {
                cors.add(CorsPluginConfig::anyHost);
            });
        }).start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Provides lists of wards and departments.)
        // Add domain endpoints for ward-service here.
        app.get("/wards/{id}/staffing", ctx -> {
            String wardId = ctx.pathParam("id");
            staffingInfoStore.getStaffingInfo(wardId).ifPresentOrElse(
                    ctx::json,
                    () -> ctx.status(404).result("No staffing information fount for ward: " + wardId)
            );
        });

        app.post("/wards/{id}/equipment-failure", ctx -> {
            String wardId = ctx.pathParam("id");
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String equipmentType = (String) body.getOrDefault("equipmentType", "UNKNOWN");
            String description = (String) body.getOrDefault("description", "No description provided");

            try {
                publishEquipmentFailure(wardId, equipmentType, description);
                ctx.status(202).json(Map.of("status", "Failure report dispatched to queue", "wardId", wardId));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Failed to send event " + e.getMessage()));
            }
        });

        ScheduleEventSubscriber finalSubscriber = subscriber;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (finalSubscriber != null) finalSubscriber.stop();
                closeMqPublisher();
                app.stop();
            } catch ( Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));
    }

    private static void initMqPublisher() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        mqConnection = factory.createConnection();
        mqConnection.start();

        mqSession = mqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = mqSession.createQueue(MqConfig.QUEUE);
        equipmentFailureProducer = mqSession.createProducer(queue);
    }

    private static void publishEquipmentFailure(String wardId, String equipmentType, String description) throws JMSException {
        if (mqSession == null || equipmentFailureProducer == null) {
            throw new IllegalStateException("MQ Publisher is not initialized");
        }

        String payload = String.format(
                "{\"wardId\":\"%s\",\"equipmentType\":\"%s\",\"description\":\"%s\",\"timestamp\":\"%s\"}",
                wardId, equipmentType, description, Instant.now()
        );

        TextMessage message = mqSession.createTextMessage(payload);
        equipmentFailureProducer.send(message);
    }

    private static void closeMqPublisher() throws JMSException {
        if (equipmentFailureProducer != null) equipmentFailureProducer.close();
        if (mqSession != null) mqSession.close();
        if (mqConnection != null) mqConnection.close();
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.
