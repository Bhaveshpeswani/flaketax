package com.flaketax.friend;

import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class FriendApi {
    private final FriendService service;
    public FriendApi(FriendService service) { this.service = service; }
    @GetMapping
    public List<FriendService.FriendView> list(Principal me) { return service.list(me.getName()); }
    @PostMapping("/{handle}")
    public ResponseEntity<Void> ask(@PathVariable String handle, Principal me) {
        service.ask(me.getName(), handle); return ResponseEntity.noContent().build();
    }
    @PostMapping("/{handle}/accept")
    public ResponseEntity<Void> accept(@PathVariable String handle, Principal me) {
        service.accept(me.getName(), handle); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{handle}")
    public ResponseEntity<Void> remove(@PathVariable String handle, Principal me) {
        service.remove(me.getName(), handle); return ResponseEntity.noContent().build();
    }
}