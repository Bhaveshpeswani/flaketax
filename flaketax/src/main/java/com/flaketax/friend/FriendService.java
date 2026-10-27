package com.flaketax.friend;

import com.flaketax.user.User;
import com.flaketax.user.UserRepo;
import com.flaketax.user.UserService;
import com.flaketax.web.ApiErrors.Fail;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {
    private final FriendRepo friends;
    private final UserRepo users;
    private final UserService accounts;
    public FriendService(FriendRepo friends, UserRepo users, UserService accounts) {
        this.friends = friends;
        this.users = users;
        this.accounts = accounts;
    }
    private Friend get(long me, long other) {
        return friends.findByAAndB(Math.min(me, other), Math.max(me, other))
                .orElseThrow(() -> new Fail(HttpStatus.NOT_FOUND, "No friend request found"));
    }
    @Transactional
    public void ask(String handle, String other) {
        User me = accounts.get(handle), them = accounts.get(other);
        if (me.getId().equals(them.getId())) throw new Fail(HttpStatus.BAD_REQUEST, "Cannot add yourself");
        if (friends.findByAAndB(Math.min(me.getId(), them.getId()), Math.max(me.getId(), them.getId())).isPresent())
            throw new Fail(HttpStatus.CONFLICT, "Request already exists");
        friends.save(new Friend(me.getId(), them.getId()));
    }
    @Transactional
    public void accept(String handle, String other) {
        User me = accounts.get(handle), them = accounts.get(other);
        Friend f = get(me.getId(), them.getId());
        if (!f.getState().equals("PENDING") || f.getAskedBy().equals(me.getId()))
            throw new Fail(HttpStatus.CONFLICT, "No incoming request to accept");
        f.accept();
    }
    @Transactional
    public void remove(String handle, String other) {
        User me = accounts.get(handle), them = accounts.get(other);
        friends.delete(get(me.getId(), them.getId()));
    }
    @Transactional(readOnly = true)
    public List<FriendView> list(String handle) {
        User me = accounts.get(handle);
        return friends.forUser(me.getId()).stream().map(f -> {
            long otherId = f.getA().equals(me.getId()) ? f.getB() : f.getA();
            User other = users.findById(otherId).orElseThrow();
            String state = f.getState().equals("ACCEPTED") ? "FRIEND" :
                    f.getAskedBy().equals(me.getId()) ? "SENT" : "INCOMING";
            return new FriendView(other.getHandle(), state);
        }).toList();
    }
    @Transactional(readOnly = true)
    public List<Long> ids(String handle) {
        User me = accounts.get(handle);
        return friends.forUser(me.getId()).stream()
                .filter(f -> f.getState().equals("ACCEPTED"))
                .map(f -> f.getA().equals(me.getId()) ? f.getB() : f.getA()).toList();
    }
    public record FriendView(String handle, String state) {}
}