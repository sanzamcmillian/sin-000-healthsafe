package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;

public class AlertLevelServiceApp {

    public static void main(String[] args) {
        AlertLevelStore store = new AlertLevelStore();
        Javalin app = AlertLevelServiceApp.create(store).start(7032);;

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Tracks the hospital Emergency Status (0-8, 8 = full Code Blue).)
        // Add domain endpoints for alert-level-service here.
    }

    public static Javalin create(AlertLevelStore store) {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(new ObjectMapper()));
        });

        // Global handler to catch bad JSON format, data type mismatches, and empty bodies
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(400).result("Malformed or missing input fields");
        });

        // Register route operations
        app.get("/alert-level", ctx -> handleGetLevel(ctx, store));
        app.put("/alert-level", ctx -> handlePutLevel(ctx, store));

        return app;
    }

    private static void handleGetLevel(Context ctx, AlertLevelStore store) {
        ctx.json(new AlertLevelPayLoad(store.getLevel()));
    }

    private static void handlePutLevel(Context ctx, AlertLevelStore store) {
        // Enforce manual syntax validation if body data is explicitly empty or malformed
        String body = ctx.body();
        if (body.isBlank() || !body.contains("\"level\"")) {
            ctx.status(400).result("Missing target level field");
            return;
        }

        // Deserialise payload; types out of sync or syntax issues fall through to the exception filter
        AlertLevelPayLoad payload = ctx.bodyAsClass(AlertLevelPayLoad.class);

        // Attempt internal state alteration engine
        boolean updated = store.setLevel(payload.level());

        if (updated) {
            ctx.json(payload);
        } else {
            ctx.status(400).result("Provided level value is outside the allowed runtime parameters");
        }
    }
}
