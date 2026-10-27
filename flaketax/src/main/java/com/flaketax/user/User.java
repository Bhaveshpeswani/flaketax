package com.flaketax.user;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 20)
    private String handle;
    @Column(name = "pass_hash", nullable = false, length = 100)
    private String pass;
    @Column(nullable = false)
    private long balance;

    protected User() {}

    public User(String handle, String pass) {
        this.handle = handle;
        this.pass = pass;
        this.balance = 1000;
    }

    public Long getId() { return id; }
    public String getHandle() { return handle; }
    public String getPass() { return pass; }
    public long getBalance() { return balance; }

    // Keep old pact and ledger IDs intact while retiring the login.
    public void close(String newHash) {
        this.handle = "~gone_" + id;
        this.pass = newHash;
    }

    public void debit(long points) {
        if (points <= 0 || balance < points) throw new IllegalArgumentException("Not enough points");
        balance -= points;
    }

    public void credit(long points) {
        if (points <= 0) throw new IllegalArgumentException("Points must be positive");
        balance = Math.addExact(balance, points);
    }
}