package com.flaketax.pact;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pacts")
public class PactApi {
    private final PactService service;
    public PactApi(PactService service) { this.service = service; }

    public record NewPact(@NotBlank @Size(max = 120) String title,
                          @Min(1) @Max(1000) long stake, @NotNull Instant deadline) {}
    public record Proof(@NotBlank @Size(max = 500) String note) {}
    public record Vote(@NotNull Boolean approved) {}
    public record Time(@Min(1) @Max(1440) int minutes) {}

    @PostMapping
    public ResponseEntity<PactService.PactView> create(@Valid @RequestBody NewPact body, Principal me) {
        var p = service.create(me.getName(), body.title(), body.stake(), body.deadline());
        return ResponseEntity.created(URI.create("/api/pacts/" + p.id())).body(p);
    }
    @GetMapping
    public List<PactService.PactView> mine(Principal me) { return service.mine(me.getName()); }
    @GetMapping("/open")
    public List<PactService.Brief> open() { return service.open(); }
    @GetMapping("/friends")
    public List<PactService.Brief> friends(Principal me) { return service.byFriends(me.getName()); }
    @GetMapping("/{id}")
    public PactService.PactView get(@PathVariable long id, Principal me) {
        return service.get(id, me.getName());
    }
    @PostMapping("/{id}/join")
    public PactService.PactView join(@PathVariable long id, Principal me) {
        return service.join(id, me.getName());
    }
    @PostMapping("/{id}/proof")
    public PactService.PactView proof(@PathVariable long id, @Valid @RequestBody Proof body, Principal me) {
        return service.proof(id, me.getName(), body.note());
    }
    @PostMapping(value = "/{id}/proof/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PactService.PactView proofPhoto(@PathVariable long id, @RequestParam(name = "note", defaultValue = "") String note,
                                           @RequestPart(name = "photo", required = false) MultipartFile photo,
                                           Principal me) {
        return service.submit(id, me.getName(), note, photo);
    }
    @GetMapping("/{id}/proof/{userId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable long id, @PathVariable long userId, Principal me) {
        var photo = service.photo(id, me.getName(), userId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.type()))
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff").body(photo.data());
    }
    @PostMapping("/{id}/review/{userId}")
    public PactService.PactView review(@PathVariable long id, @PathVariable long userId,
                                       @Valid @RequestBody Vote body, Principal me) {
        return service.review(id, me.getName(), userId, body.approved());
    }
    @PatchMapping("/{id}/deadline")
    public PactService.PactView deadline(@PathVariable long id, @Valid @RequestBody Time body, Principal me) {
        return service.changeDeadline(id, me.getName(), body.minutes());
    }
    @PostMapping("/{id}/cancel")
    public PactService.PactView cancel(@PathVariable long id, Principal me) {
        return service.cancel(id, me.getName());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable long id, Principal me) {
        service.archive(id, me.getName());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/settle")
    public PactService.PactView settle(@PathVariable long id, Principal me) {
        return service.settle(id, me.getName());
    }
}