package com.flaketax.pact;

import com.flaketax.user.User;
import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "pacts")
public class Pact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false)
    private long stake;
    @Column(nullable = false)
    private long pot;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Instant deadline;
    @Column(name = "review_end", nullable = false)
    private Instant reviewEnd;
    @Column(nullable = false)
    private boolean archived;

    protected Pact() {}
    public Pact(User host, String title, long stake, Instant deadline, Instant reviewEnd) {
        this.host = host;
        this.title = title;
        this.stake = stake;
        this.deadline = deadline;
        this.reviewEnd = reviewEnd;
        this.status = "OPEN";
        this.pot = 0;
        this.archived = false;
    }
    public Long getId() { return id; }
    public User getHost() { return host; }
    public String getTitle() { return title; }
    public long getStake() { return stake; }
    public long getPot() { return pot; }
    public String getStatus() { return status; }
    public Instant getDeadline() { return deadline; }
    public Instant getReviewEnd() { return reviewEnd; }
    public boolean isArchived() { return archived; }
    public void setDeadline(Instant deadline, Instant reviewEnd) {
        this.deadline = deadline;
        this.reviewEnd = reviewEnd;
    }
    public void hold(long amount) { pot = Math.addExact(pot, amount); }
    public void pay(long amount) {
        if (amount <= 0 || pot < amount) throw new IllegalStateException("Invalid payout");
        pot -= amount;
    }
    public void finish() {
        if (pot != 0) throw new IllegalStateException("Pot is not empty");
        status = "SETTLED";
    }
    public void cancel() {
        if (pot != 0) throw new IllegalStateException("Pot is not empty");
        status = "CANCELLED";
    }
    public void archive() {
        if (status.equals("OPEN")) throw new IllegalStateException("Cancel an open pact first");
        archived = true;
    }
}