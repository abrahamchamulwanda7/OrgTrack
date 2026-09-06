package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
        }).start(7000);

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

        // ================= AUTH =================

        app.post("/signup", ctx -> {
            User newUser = ctx.bodyAsClass(User.class);

            if (newUser.username == null || newUser.email == null || newUser.password == null) {
                ctx.status(400).result("username, email, and password are required");
                return;
            }

            String hashedPassword = BCrypt.hashpw(newUser.password, BCrypt.gensalt());

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, newUser.username);
                stmt.setString(2, newUser.email);
                stmt.setString(3, hashedPassword);
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        newUser.id = keys.getInt(1);
                    }
                }

                newUser.password = null;
                ctx.status(201).json(newUser);

            } catch (SQLException e) {
                ctx.status(500).result("Error creating user: " + e.getMessage());
            }
        });

        app.post("/login", ctx -> {
            User loginAttempt = ctx.bodyAsClass(User.class);

            if (loginAttempt.username == null || loginAttempt.password == null) {
                ctx.status(400).result("username and password are required");
                return;
            }

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, username, email, password_hash FROM users WHERE username = ?")) {

                stmt.setString(1, loginAttempt.username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        ctx.status(401).result("Invalid username or password");
                        return;
                    }

                    String storedHash = rs.getString("password_hash");

                    if (BCrypt.checkpw(loginAttempt.password, storedHash)) {
                        User loggedInUser = new User(
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getString("email"),
                                null
                        );
                        ctx.json(loggedInUser);
                    } else {
                        ctx.status(401).result("Invalid username or password");
                    }
                }

            } catch (SQLException e) {
                ctx.status(500).result("Error during login: " + e.getMessage());
            }
        });

        // ================= DEPARTMENTS =================

        app.get("/departments", ctx -> {
            List<Department> departments = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM departments");
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    departments.add(new Department(rs.getInt("id"), rs.getString("name")));
                }
                ctx.json(departments);

            } catch (SQLException e) {
                ctx.status(500).result("Error fetching departments: " + e.getMessage());
            }
        });

        app.post("/departments", ctx -> {
            Department newDept = ctx.bodyAsClass(Department.class);

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO departments (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, newDept.name);
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        newDept.id = keys.getInt(1);
                    }
                }
                ctx.status(201).json(newDept);

            } catch (SQLException e) {
                ctx.status(500).result("Error adding department: " + e.getMessage());
            }
        });

        // ================= MEMBERS =================

        app.get("/members", ctx -> {
            List<Member> members = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, name, role, contact, date_joined, department_id FROM members");
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Date sqlDate = rs.getDate("date_joined");
                    LocalDate dateJoined = (sqlDate != null) ? sqlDate.toLocalDate() : null;

                    members.add(new Member(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("role"),
                            rs.getString("contact"),
                            dateJoined,
                            rs.getInt("department_id")
                    ));
                }
                ctx.json(members);

            } catch (SQLException e) {
                ctx.status(500).result("Error fetching members: " + e.getMessage());
            }
        });

        app.post("/members", ctx -> {
            Member newMember = ctx.bodyAsClass(Member.class);

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO members (name, role, contact, date_joined, department_id) VALUES (?, ?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, newMember.name);
                stmt.setString(2, newMember.role);
                stmt.setString(3, newMember.contact);
                stmt.setDate(4, newMember.date_joined != null ? Date.valueOf(newMember.date_joined) : null);
                stmt.setInt(5, newMember.department_id);
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        newMember.id = keys.getInt(1);
                    }
                }
                ctx.status(201).json(newMember);

            } catch (SQLException e) {
                ctx.status(500).result("Error adding member: " + e.getMessage());
            }
        });

        app.put("/members/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Member updated = ctx.bodyAsClass(Member.class);

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE members SET name = ?, role = ?, contact = ?, date_joined = ?, department_id = ? WHERE id = ?")) {

                stmt.setString(1, updated.name);
                stmt.setString(2, updated.role);
                stmt.setString(3, updated.contact);
                stmt.setDate(4, updated.date_joined != null ? Date.valueOf(updated.date_joined) : null);
                stmt.setInt(5, updated.department_id);
                stmt.setInt(6, id);

                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    ctx.status(404).result("Member not found");
                } else {
                    updated.id = id;
                    ctx.json(updated);
                }

            } catch (SQLException e) {
                ctx.status(500).result("Error updating member: " + e.getMessage());
            }
        });

        app.delete("/members/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM members WHERE id = ?")) {

                stmt.setInt(1, id);
                int rows = stmt.executeUpdate();

                if (rows == 0) {
                    ctx.status(404).result("Member not found");
                } else {
                    ctx.status(204);
                }

            } catch (SQLException e) {
                ctx.status(500).result("Error deleting member: " + e.getMessage());
            }
        });

        // ================= TASKS =================

        app.get("/tasks", ctx -> {
            List<Task> tasks = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, title, description, date, department_id FROM tasks");
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Date sqlDate = rs.getDate("date");
                    LocalDate taskDate = (sqlDate != null) ? sqlDate.toLocalDate() : null;

                    tasks.add(new Task(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            taskDate,
                            rs.getInt("department_id")
                    ));
                }
                ctx.json(tasks);

            } catch (SQLException e) {
                ctx.status(500).result("Error fetching tasks: " + e.getMessage());
            }
        });

        app.post("/tasks", ctx -> {
            Task newTask = ctx.bodyAsClass(Task.class);

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO tasks (title, description, date, department_id) VALUES (?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, newTask.title);
                stmt.setString(2, newTask.description);
                stmt.setDate(3, newTask.date != null ? Date.valueOf(newTask.date) : null);
                stmt.setInt(4, newTask.department_id);
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        newTask.id = keys.getInt(1);
                    }
                }
                ctx.status(201).json(newTask);

            } catch (SQLException e) {
                ctx.status(500).result("Error adding task: " + e.getMessage());
            }
        });
    }

    private static Connection getConnection() throws SQLException {
        Properties props = new Properties();
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("db.properties")) {
            props.load(is);
        } catch (IOException e) {
            throw new SQLException("Could not load db.properties", e);
        }
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password");
        return DriverManager.getConnection(url, user, password);
    }
}
