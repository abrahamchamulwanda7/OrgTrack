package org.example;

import java.time.LocalDate;

public class Member {
    public int id;
    public String name;
    public String role;
    public String contact;
    public LocalDate date_joined;
    public int department_id;

    public Member() {
    }

    public Member(int id, String name, String role, String contact, LocalDate date_joined, int department_id) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.contact = contact;
        this.date_joined = date_joined;
        this.department_id = department_id;
    }
}