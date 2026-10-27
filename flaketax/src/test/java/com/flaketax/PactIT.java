package com.flaketax;

import com.flaketax.friend.FriendService;
import com.flaketax.pact.PactService;
import com.flaketax.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "flaketax.proof-dir=./target/test-proofs")
@Testcontainers
@Import(PactIT.TestClock.class)
class PactIT {
    @Container
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", db::getJdbcUrl);
        props.add("spring.datasource.username", db::getUsername);
        props.add("spring.datasource.password", db::getPassword);
    }
    @TestConfiguration
    static class TestClock {
        @Bean @Primary Clock testClock() { return new MovingClock(); }
    }
    static class MovingClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T12:00:00Z"));
        void set(Instant time) { now.set(time); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
    @Autowired UserService users;
    @Autowired PactService pacts;
    @Autowired FriendService friends;
    @Autowired Clock clock;
    private final Instant start = Instant.parse("2026-10-20T12:00:00Z");
    @BeforeEach void resetClock() { ((MovingClock) clock).set(start); }
    private String name() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private void finishTime() { ((MovingClock) clock).set(start.plusSeconds(600 + 86400 + 1)); }

    @Test
    void settlesOnlyOnce() {
        String host = name(), winner = name(), loser = name();
        users.register(host,"longpassword1");
        long winnerId = users.register(winner,"longpassword1").id();
        users.register(loser,"longpassword1");
        var pact = pacts.create(host,"Study tonight",100,start.plusSeconds(600));
        assertEquals(900,users.me(host).balance());
        pacts.join(pact.id(),winner);
        pacts.join(pact.id(),loser);
        pacts.proof(pact.id(),winner,"Finished Java");
        pacts.review(pact.id(),host,winnerId,true);
        finishTime();
        assertEquals("SETTLED",pacts.settle(pact.id(),host).status());
        assertEquals(0,pacts.settle(pact.id(),host).pot());
        assertEquals(1200,users.me(winner).balance());
        assertEquals(900,users.me(host).balance());
        assertEquals(900,users.me(loser).balance());
    }

    @Test
    void returnsAllPointsWhenNobodyWins() {
        String host = name(), player = name();
        users.register(host,"longpassword1");
        users.register(player,"longpassword1");
        var pact=pacts.create(host,"Try again tomorrow",100,start.plusSeconds(600));
        pacts.join(pact.id(),player);
        finishTime();
        pacts.settle(pact.id(),host);
        assertEquals(1000,users.me(host).balance());
        assertEquals(1000,users.me(player).balance());
    }

    @Test
    void twoSettlementsCannotPayTwice() throws Exception {
        String host = name(), winner = name(), loser = name();
        users.register(host,"longpassword1");
        long winnerId = users.register(winner,"longpassword1").id();
        users.register(loser,"longpassword1");
        var pact=pacts.create(host,"Race test",100,start.plusSeconds(600));
        pacts.join(pact.id(),winner);
        pacts.join(pact.id(),loser);
        pacts.proof(pact.id(),winner,"Done");
        pacts.review(pact.id(),host,winnerId,true);
        finishTime();
        try (var pool=Executors.newFixedThreadPool(2)) {
            var signal=new CountDownLatch(1);
            var a=pool.submit(()->{signal.await();return pacts.settle(pact.id(),host);});
            var b=pool.submit(()->{signal.await();return pacts.settle(pact.id(),host);});
            signal.countDown();
            assertEquals("SETTLED",a.get(15,TimeUnit.SECONDS).status());
            assertEquals("SETTLED",b.get(15,TimeUnit.SECONDS).status());
        }
        assertEquals(1200,users.me(winner).balance());
    }

    @Test
    void hostCanWinAfterPeerReview() {
        String host=name(), peer=name();
        long hostId=users.register(host,"longpassword1").id();
        users.register(peer,"longpassword1");
        var pact=pacts.create(host,"Host participates",100,start.plusSeconds(600));
        pacts.join(pact.id(),peer);
        pacts.proof(pact.id(),host,"I did it");
        assertThrows(com.flaketax.web.ApiErrors.Fail.class,
                ()->pacts.review(pact.id(),host,hostId,true));
        pacts.review(pact.id(),peer,hostId,true);
        finishTime();
        pacts.settle(pact.id(),host);
        assertEquals(1100,users.me(host).balance());
        assertEquals(900,users.me(peer).balance());
    }

    @Test
    void cancelRefundsEveryoneAndArchiveHidesIt() {
        String host=name(), peer=name();
        users.register(host,"longpassword1");
        users.register(peer,"longpassword1");
        var pact=pacts.create(host,"Cancelled challenge",100,start.plusSeconds(600));
        pacts.join(pact.id(),peer);
        assertEquals("CANCELLED",pacts.cancel(pact.id(),host).status());
        assertEquals(1000,users.me(host).balance());
        assertEquals(1000,users.me(peer).balance());
        pacts.archive(pact.id(),host);
        assertTrue(pacts.mine(host).stream().noneMatch(p->p.id().equals(pact.id())));
        assertEquals("CANCELLED",pacts.get(pact.id(),peer).status());
    }

    @Test
    void changingTimeIsAllowedOnlyBeforeFriendsJoin() {
        String host=name(),peer=name();
        users.register(host,"longpassword1");
        users.register(peer,"longpassword1");
        var pact=pacts.create(host,"Change timer",100,start.plusSeconds(600));
        pacts.changeDeadline(pact.id(),host,3);
        pacts.join(pact.id(),peer);
        assertThrows(com.flaketax.web.ApiErrors.Fail.class,
                ()->pacts.changeDeadline(pact.id(),host,5));
    }

    @Test
    void friendsCanDiscoverTheirPacts() {
        String host=name(),peer=name();
        users.register(host,"longpassword1");
        users.register(peer,"longpassword1");
        friends.ask(peer,host);
        friends.accept(host,peer);
        var pact=pacts.create(host,"Friend pact",100,start.plusSeconds(600));
        assertTrue(pacts.byFriends(peer).stream().anyMatch(p->p.id().equals(pact.id())));
    }

    @Test
    void proofAtDeadlineIsTooLate() {
        String host=name(),player=name();
        users.register(host,"longpassword1");
        users.register(player,"longpassword1");
        var pact=pacts.create(host,"Late proof test",100,start.plusSeconds(600));
        pacts.join(pact.id(),player);
        ((MovingClock)clock).set(start.plusSeconds(600));
        assertThrows(com.flaketax.web.ApiErrors.Fail.class,
                ()->pacts.proof(pact.id(),player,"Late"));
    }

    @Test
    void onlyJoinedPlayersCanSeePrivatePhotos() {
        String host=name(),player=name(),outsider=name();
        users.register(host,"longpassword1");
        long playerId=users.register(player,"longpassword1").id();
        users.register(outsider,"longpassword1");
        var pact=pacts.create(host,"Photo proof",100,start.plusSeconds(600));
        pacts.join(pact.id(),player);
        byte[] png=new byte[]{(byte)137,80,78,71,13,10,26,10,0};
        var file=new org.springframework.mock.web.MockMultipartFile(
                "photo","proof.png","image/png",png);
        pacts.submit(pact.id(),player,"",file);
        assertArrayEquals(png,pacts.photo(pact.id(),host,playerId).data());
        assertThrows(com.flaketax.web.ApiErrors.Fail.class,
                ()->pacts.photo(pact.id(),outsider,playerId));
    }

    @Test
    void cantCancelAfterProofArrives() {
        String host=name(),player=name();
        users.register(host,"longpassword1");
        users.register(player,"longpassword1");
        var pact=pacts.create(host,"Cannot rewrite result",100,start.plusSeconds(600));
        pacts.join(pact.id(),player);
        pacts.proof(pact.id(),player,"Done");
        assertThrows(com.flaketax.web.ApiErrors.Fail.class,
                ()->pacts.cancel(pact.id(),host));
        assertEquals(200,pacts.get(pact.id(),host).pot());
    }

    @Test
    void publicLobbyDoesNotExposePrivatePactDetails() {
        String host=name(),outsider=name();
        users.register(host,"longpassword1");
        users.register(outsider,"longpassword1");
        var pact=pacts.create(host,"Public goal",100,start.plusSeconds(600));
        assertTrue(pacts.open().stream().anyMatch(p->p.id().equals(pact.id())));
        assertThrows(com.flaketax.web.ApiErrors.Fail.class,
                ()->pacts.get(pact.id(),outsider));
    }

    @Test
    void accountCanCloseAndReuseHandle() {
        String handle=name();
        long first=users.register(handle,"longpassword1").id();
        users.close(handle);
        long second=users.register(handle,"longpassword1").id();
        assertNotEquals(first,second);
    }
    @Test
    void cannotCloseWhenPactOpen() {
        String host=name();
        users.register(host,"longpassword1");
        pacts.create(host,"Open challenge",100,start.plusSeconds(600));
        assertThrows(com.flaketax.web.ApiErrors.Fail.class,()->users.close(host));
        assertEquals(900,users.me(host).balance());
    }
}