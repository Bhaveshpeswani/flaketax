package com.flaketax.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserApi {
    private final UserService service;
    public UserApi(UserService service) { this.service = service; }

    public record Signup(
            @NotBlank @Pattern(regexp = "[a-z0-9_]{3,20}") String handle,
            @NotBlank @Size(min = 10, max = 72) String password) {}

    @PostMapping("/users")
    public ResponseEntity<UserService.Profile> register(@Valid @RequestBody Signup body) {
        var user = service.register(body.handle(), body.password());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(user);
    }

    @GetMapping("/me")
    public UserService.Profile me(Principal principal) {
        return service.me(principal.getName());
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> close(Principal principal) {
        service.close(principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/entries")
    public List<UserService.EntryView> ledger(Principal principal) {
        return service.ledger(principal.getName());
    }
}