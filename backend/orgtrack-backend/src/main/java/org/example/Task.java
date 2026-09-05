package org.example;

import java.time.LocalDate;

public class Task {
    public int id;
    public String title;
    public String description;
    public LocalDate date;
    public int department_id;

    public Task() {
    }

    public Task(int id, String title, String description, LocalDate date, int department_id) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.department_id = department_id;
    }
}