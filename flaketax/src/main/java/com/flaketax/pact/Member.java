package com.flaketax.pact;

import com.flaketax.user.User;
import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(columnNames = {"pact_id", "user_id"}))
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pact_id", nullable = false)
    private Pact pact;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 500)
    private String proof;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "photo_key", length = 80)
    private String photoKey;
    @Column(name = "photo_type", length = 30)
    private String photoType;

    protected Member() {}
    public Member(Pact pact, User user) {
        this.pact = pact;
        this.user = user;
        status = "JOINED";
    }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getStatus() { return status; }
    public String getProof() { return proof; }
    public Instant getSentAt() { return sentAt; }
    public String getPhotoKey() { return photoKey; }
    public String getPhotoType() { return photoType; }
    public void submit(String note, Instant now, String key, String type) {
        if (!status.equals("JOINED")) throw new IllegalStateException("Proof already submitted");
        proof = note;
        sentAt = now;
        photoKey = key;
        photoType = type;
        status = "SUBMITTED";
    }
    public void review(boolean approved) {
        if (!status.equals("SUBMITTED")) throw new IllegalStateException("No proof awaiting review");
        status = approved ? "APPROVED" : "REJECTED";
    }
}