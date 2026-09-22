package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;
import jakarta.jms.JMSException;

public class EquipmentAlertServiceApp {

    public static void main(String[] args) {
        AlertStore alertStore = new AlertStore();

        EquipmentAlertSubscriber subscriber = new EquipmentAlertSubscriber(
                MqConfig.BROKER_URL,
                MqConfig.QUEUE,
                alertStore
        );

        try {
            subscriber.start();
            System.out.println("EquipmentAlertServiceApp is listening on queue: " + MqConfig.QUEUE);
        } catch (JMSException e) {
            System.err.println("Failed to start ActiveMQ subscriber: " + e.getMessage());
        }

        Javalin app = Javalin.create().start(7034);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alerts", ctx -> ctx.json(alertStore.getReceivedAlerts()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down EquipmentAlertServiceApp...");
            try {
                subscriber.stop();
                app.stop();
            } catch (JMSException e) {
                System.err.println("Error closing ActiveMQ resources: " + e.getMessage());
            }
        }));

        // TODO (Uses a Queue to guarantee delivery of critical medical equipment failure alerts.)
        // Mechanism: ActiveMQ Queue (guaranteed delivery)
    }
}

// MQ TODO: consumes ActiveMQ queue MqConfig.QUEUE at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// Producer: ward-service publishes here when it detects an equipment failure on one of its wards.
