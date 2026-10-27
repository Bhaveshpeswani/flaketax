package com.flaketax.pact;

import com.flaketax.friend.FriendService;
import com.flaketax.user.User;
import com.flaketax.user.UserRepo;
import com.flaketax.user.UserService;
import com.flaketax.wallet.Entry;
import com.flaketax.wallet.EntryRepo;
import com.flaketax.web.ApiErrors.Fail;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PactService {
    private final PactRepo pacts;
    private final MemberRepo members;
    private final UserRepo users;
    private final UserService accounts;
    private final EntryRepo entries;
    private final FriendService friends;
    private final ProofStore photos;
    private final Clock clock;
    private final long reviewMinutes;

    public PactService(PactRepo pacts, MemberRepo members, UserRepo users,
                       UserService accounts, EntryRepo entries, FriendService friends,
                       ProofStore photos, Clock clock,
                       @Value("${flaketax.review-minutes:1440}") long reviewMinutes) {
        if (reviewMinutes < 1 || reviewMinutes > 1440)
            throw new IllegalArgumentException("Review window must be 1 to 1440 minutes");
        this.pacts = pacts;
        this.members = members;
        this.users = users;
        this.accounts = accounts;
        this.entries = entries;
        this.friends = friends;
        this.photos = photos;
        this.clock = clock;
        this.reviewMinutes = reviewMinutes;
    }

    @Transactional
    public PactView create(String handle, String title, long stake, Instant deadline) {
        Instant now = clock.instant();
        if (!deadline.isAfter(now.plus(30, ChronoUnit.SECONDS)) ||
                deadline.isAfter(now.plus(30, ChronoUnit.DAYS)))
            throw new Fail(HttpStatus.BAD_REQUEST, "Deadline must be 30 seconds to 30 days ahead");
        User host = accounts.get(handle);
        User wallet = users.lock(host.getId()).orElseThrow();
        if (wallet.getBalance() < stake) throw conflict("Not enough points");
        Pact p = pacts.save(new Pact(wallet, title.trim(), stake, deadline,
                deadline.plus(reviewMinutes, ChronoUnit.MINUTES)));
        wallet.debit(stake);
        p.hold(stake);
        members.save(new Member(p, wallet));
        move(p.getId(), wallet.getId(), "HOLD", -stake);
        move(p.getId(), null, "HOLD", stake);
        return view(p);
    }

    @Transactional
    public PactView join(long id, String handle) {
        Pact p = locked(id);
        requireOpen(p);
        if (!clock.instant().isBefore(p.getDeadline())) throw conflict("Joining is closed");
        User u = accounts.get(handle);
        if (members.existsByPactIdAndUserId(id, u.getId())) throw conflict("Already joined");
        User wallet = users.lock(u.getId()).orElseThrow();
        if (wallet.getBalance() < p.getStake()) throw conflict("Not enough points");
        wallet.debit(p.getStake());
        p.hold(p.getStake());
        members.save(new Member(p, wallet));
        move(id, wallet.getId(), "HOLD", -p.getStake());
        move(id, null, "HOLD", p.getStake());
        return view(p);
    }

    @Transactional
    public PactView proof(long id, String handle, String note) {
        return submit(id, handle, note, null);
    }

    @Transactional
    public PactView submit(long id, String handle, String note, MultipartFile image) {
        Pact p = locked(id);
        requireOpen(p);
        Instant now = clock.instant();
        if (!now.isBefore(p.getDeadline())) throw conflict("Proof deadline has passed");
        User u = accounts.get(handle);
        Member m = members.findByPactIdAndUserId(id, u.getId())
                .orElseThrow(() -> new Fail(HttpStatus.FORBIDDEN, "Join this pact first"));
        if (!m.getStatus().equals("JOINED")) throw conflict("Proof already sent");
        if ((note == null || note.isBlank()) && (image == null || image.isEmpty()))
            throw new Fail(HttpStatus.BAD_REQUEST, "Add a note or photo");
        if (note != null && note.length() > 500)
            throw new Fail(HttpStatus.BAD_REQUEST, "Note is too long");
        ProofStore.Saved photo = photos.save(image);
        m.submit(note == null ? "" : note.trim(), now,
                photo == null ? null : photo.key(), photo == null ? null : photo.type());
        return view(p);
    }

    @Transactional
    public PactView review(long id, String handle, long userId, boolean approved) {
        Pact p = locked(id);
        requireOpen(p);
        if (!clock.instant().isBefore(p.getReviewEnd())) throw conflict("Review window has closed");
        User reviewer = accounts.get(handle);
        Member target = members.findByPactIdAndUserId(id, userId)
                .orElseThrow(() -> new Fail(HttpStatus.NOT_FOUND, "Player not found"));
        boolean ownProof = target.getUser().getId().equals(p.getHost().getId());
        if (reviewer.getId().equals(userId)) throw new Fail(HttpStatus.FORBIDDEN, "You cannot approve your own proof");
        if (ownProof) {
            if (!members.existsByPactIdAndUserId(id, reviewer.getId()))
                throw new Fail(HttpStatus.FORBIDDEN, "Only a player in this pact can review the host");
        } else if (!reviewer.getId().equals(p.getHost().getId())) {
            throw new Fail(HttpStatus.FORBIDDEN, "Only the host reviews other players");
        }
        if (!target.getStatus().equals("SUBMITTED")) throw conflict("No proof awaiting review");
        target.review(approved);
        return view(p);
    }

    @Transactional
    public PactView changeDeadline(long id, String handle, int minutes) {
        if (minutes < 1 || minutes > 1440)
            throw new Fail(HttpStatus.BAD_REQUEST, "Choose 1 to 1440 minutes");
        Pact p = locked(id);
        hostOnly(p, handle);
        requireOpen(p);
        if (!clock.instant().isBefore(p.getDeadline())) throw conflict("Deadline has passed");
        // Host joins automatically in new pacts. Nobody else can have joined.
        List<Member> list = members.findByPactIdOrderByUserIdAsc(id);
        if (list.stream().anyMatch(m -> !m.getUser().getId().equals(p.getHost().getId())) ||
                list.stream().anyMatch(m -> !m.getStatus().equals("JOINED")))
            throw conflict("Cannot change time after someone joins or submits proof");
        Instant deadline = clock.instant().plus(minutes, ChronoUnit.MINUTES);
        p.setDeadline(deadline, deadline.plus(reviewMinutes, ChronoUnit.MINUTES));
        return view(p);
    }

    @Transactional
    public PactView cancel(long id, String handle) {
        Pact p = locked(id);
        hostOnly(p, handle);
        requireOpen(p);
        if (!clock.instant().isBefore(p.getDeadline()) ||
                members.findByPactIdOrderByUserIdAsc(id).stream()
                        .anyMatch(m -> !m.getStatus().equals("JOINED")))
            throw conflict("Cannot cancel after proof is sent or the deadline has passed");
        for (Member m : members.findByPactIdOrderByUserIdAsc(id))
            pay(p, m.getUser().getId(), p.getStake(), "REFUND");
        p.cancel();
        return view(p);
    }

    @Transactional
    public void archive(long id, String handle) {
        Pact p = locked(id);
        hostOnly(p, handle);
        if (p.getStatus().equals("OPEN")) throw conflict("Cancel the pact before removing it");
        p.archive();
    }

    @Transactional
    public PactView settle(long id, String handle) {
        Pact p = locked(id);
        hostOnly(p, handle);
        return finish(p);
    }

    @Transactional
    public void sweep(long id) { finish(locked(id)); }

    @Transactional(readOnly = true)
    public PactView get(long id, String handle) {
        Pact p = pacts.findById(id).orElseThrow(this::notFound);
        allowed(p, handle);
        return view(p);
    }

    @Transactional(readOnly = true)
    public List<PactView> mine(String handle) {
        User u = accounts.get(handle);
        return pacts.mine(u.getId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<Brief> open() {
        return pacts.open(clock.instant()).stream().limit(100).map(this::brief).toList();
    }

    @Transactional(readOnly = true)
    public List<Brief> byFriends(String handle) {
        List<Long> ids = friends.ids(handle);
        if (ids.isEmpty()) return List.of();
        return pacts.friends(ids, clock.instant()).stream().limit(100).map(this::brief).toList();
    }

    @Transactional(readOnly = true)
    public Photo photo(long id, String handle, long userId) {
        get(id, handle);  // Only the host and joined players may see proof.
        Member m = members.findByPactIdAndUserId(id, userId)
                .orElseThrow(this::notFound);
        if (m.getPhotoKey() == null) throw notFound();
        return new Photo(photos.read(m.getPhotoKey()), m.getPhotoType());
    }

    private PactView finish(Pact p) {
        if (p.getStatus().equals("SETTLED") || p.getStatus().equals("CANCELLED")) return view(p);
        if (clock.instant().isBefore(p.getReviewEnd())) throw conflict("Review period is not finished");
        List<Member> all = members.findByPactIdOrderByUserIdAsc(p.getId());
        List<Member> winners = all.stream().filter(m -> m.getStatus().equals("APPROVED")).toList();
        if (winners.isEmpty()) {
            for (Member m : all) pay(p, m.getUser().getId(), p.getStake(), "REFUND");
        } else {
            List<Long> shares = Split.evenly(p.getPot(), winners.size());
            for (int i = 0; i < winners.size(); i++)
                pay(p, winners.get(i).getUser().getId(), shares.get(i), "PAYOUT");
        }
        p.finish();
        return view(p);
    }

    private void pay(Pact p, Long userId, long amount, String kind) {
        if (amount <= 0) return;
        User wallet = users.lock(userId).orElseThrow();
        p.pay(amount);
        wallet.credit(amount);
        move(p.getId(), null, kind, -amount);
        move(p.getId(), wallet.getId(), kind, amount);
    }

    private void move(Long pactId, Long userId, String kind, long amount) {
        entries.save(new Entry(userId, pactId, kind, amount, clock.instant()));
    }

    private Pact locked(long id) { return pacts.lock(id).orElseThrow(this::notFound); }
    private void requireOpen(Pact p) {
        if (!p.getStatus().equals("OPEN")) throw conflict("Pact is finished");
    }
    private void hostOnly(Pact p, String handle) {
        if (!p.getHost().getHandle().equals(handle))
            throw new Fail(HttpStatus.FORBIDDEN, "Only the host can do that");
    }
    private void allowed(Pact p, String handle) {
        if (p.getHost().getHandle().equals(handle)) return;
        User u = accounts.get(handle);
        if (!members.existsByPactIdAndUserId(p.getId(), u.getId()))
            throw new Fail(HttpStatus.FORBIDDEN, "Join this pact to see its details");
    }
    private Fail notFound() { return new Fail(HttpStatus.NOT_FOUND, "Pact not found"); }
    private Fail conflict(String message) { return new Fail(HttpStatus.CONFLICT, message); }

    private Brief brief(Pact p) {
        return new Brief(p.getId(), p.getHost().getHandle(), p.getTitle(), p.getStake(), p.getDeadline());
    }
    private PactView view(Pact p) {
        List<MemberView> list = members.findByPactIdOrderByUserIdAsc(p.getId()).stream()
                .map(m -> new MemberView(m.getUser().getId(), m.getUser().getHandle(),
                        m.getStatus(), m.getProof(), m.getSentAt(), m.getPhotoKey() != null))
                .toList();
        return new PactView(p.getId(), p.getHost().getHandle(), p.getTitle(), p.getStake(),
                p.getPot(), p.getStatus(), p.getDeadline(), p.getReviewEnd(), p.isArchived(), list);
    }

    public record Brief(Long id, String host, String title, long stake, Instant deadline) {}
    public record Photo(byte[] data, String type) {}
    public record MemberView(Long userId, String handle, String status, String proof,
                             Instant sentAt, boolean hasPhoto) {}
    public record PactView(Long id, String host, String title, long stake, long pot,
                           String status, Instant deadline, Instant reviewEnd, boolean archived,
                           List<MemberView> members) {}
}