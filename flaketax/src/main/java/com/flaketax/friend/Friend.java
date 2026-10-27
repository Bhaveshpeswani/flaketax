package com.flaketax.friend;

import jakarta.persistence.*;

@Entity
@Table(name = "friends")
public class Friend {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "a_id", nullable = false)
    private Long a;
    @Column(name = "b_id", nullable = false)
    private Long b;
    @Column(name = "asked_by", nullable = false)
    private Long askedBy;
    @Column(nullable = false, length = 12)
    private String state;

    protected Friend() {}
    public Friend(long from, long to) {
        a = Math.min(from, to);
        b = Math.max(from, to);
        askedBy = from;
        state = "PENDING";
    }
    public Long getId() { return id; }
    public Long getA() { return a; }
    public Long getB() { return b; }
    public Long getAskedBy() { return askedBy; }
    public String getState() { return state; }
    public void accept() { state = "ACCEPTED"; }
}