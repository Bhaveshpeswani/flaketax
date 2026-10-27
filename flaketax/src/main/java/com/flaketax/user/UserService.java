package com.flaketax.user;

import com.flaketax.pact.PactRepo;
import com.flaketax.pact.MemberRepo;
import com.flaketax.wallet.Entry;
import com.flaketax.wallet.EntryRepo;
import com.flaketax.web.ApiErrors.Fail;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final PactRepo pacts;
    private final MemberRepo members;
    private final UserRepo users;
    private final EntryRepo entries;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public UserService(UserRepo users, EntryRepo entries, PasswordEncoder passwords, Clock clock,
                       PactRepo pacts, MemberRepo members) {
        this.pacts = pacts;
        this.members = members;
        this.users = users;
        this.entries = entries;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public Profile register(String handle, String pass) {
        String name = handle.toLowerCase(Locale.ROOT);
        if (users.findByHandle(name).isPresent()) throw new Fail(HttpStatus.CONFLICT, "Handle already used");
        User user = users.save(new User(name, passwords.encode(pass)));
        entries.save(new Entry(user.getId(), null, "START", 1000, clock.instant()));
        return new Profile(user.getId(), user.getHandle(), user.getBalance());
    }

    @Transactional(readOnly = true)
    public Profile me(String handle) {
        User u = get(handle);
        return new Profile(u.getId(), u.getHandle(), u.getBalance());
    }

    @Transactional(readOnly = true)
    public List<EntryView> ledger(String handle) {
        User u = get(handle);
        return entries.findTop50ByUserIdOrderByIdDesc(u.getId()).stream()
                .map(e -> new EntryView(e.getId(), e.getPactId(), e.getKind(), e.getAmount(), e.getMadeAt()))
                .toList();
    }

    @Transactional
    public void close(String handle) {
        User account = get(handle);
        User user = users.lock(account.getId()).orElseThrow();
        if (pacts.existsByHostIdAndStatus(user.getId(), "OPEN")
                || members.hasOpenPacts(user.getId())) {
            throw new Fail(HttpStatus.CONFLICT, "Finish your open pacts before closing your account");
        }
        // Never delete rows referred to by the append-only points ledger.
        user.close(passwords.encode(UUID.randomUUID().toString()));
    }

    public User get(String handle) {
        return users.findByHandle(handle).orElseThrow(() -> new Fail(HttpStatus.NOT_FOUND, "User not found"));
    }

    public record Profile(Long id, String handle, long balance) {}
    public record EntryView(Long id, Long pactId, String kind, long amount, java.time.Instant madeAt) {}
}