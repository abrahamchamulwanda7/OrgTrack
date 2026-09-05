package org.example;

import io.javalin.Javalin;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7000);

        app.get("/", ctx -> ctx.result("OrgTrack backend is running!"));

        app.get("/db-test", ctx -> {
            Properties props = new Properties();
            try (InputStream is = Main.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (is == null) {
                    ctx.result("db.properties not found on classpath");
                    return;
                }
                props.load(is);
            } catch (IOException e) {
                ctx.result("Could not read db.properties: " + e.getMessage());
                return;
            }

            String url = props.getProperty("db.url");
            String user = props.getProperty("db.user");
            String password = props.getProperty("db.password");

            try (Connection conn = DriverManager.getConnection(url, user, password)) {
                ctx.result("Database connected successfully!");
            } catch (SQLException e) {
                ctx.result("Database connection failed: " + e.getMessage());
            }
        });
    }
}