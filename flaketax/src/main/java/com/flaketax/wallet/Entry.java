package com.flaketax.wallet;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "entries")
public class Entry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "pact_id")
    private Long pactId;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(nullable = false)
    private Long amount;
    @Column(name = "made_at", nullable = false)
    private Instant madeAt;

    protected Entry() {}

    public Entry(Long userId, Long pactId, String kind, long amount, Instant madeAt) {

        this.userId = userId;
        this.pactId = pactId;
        this.kind = kind;
        this.amount = amount;
        this.madeAt = madeAt;
    }

    public Long getId(){return id;}
    public Long getUserId() { return userId; }
    public Long getPactId() { return pactId; }
    public String getKind() { return kind; }
    public long getAmount() {return amount ;}
    public Instant getMadeAt() { return madeAt; }
}
