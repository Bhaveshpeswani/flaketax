package com.flaketax.pact;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Jobs {
    private static final Logger log = LoggerFactory.getLogger(Jobs.class);
    private final PactRepo pacts;
    private final PactService service;
    private final Clock clock;

    public Jobs(PactRepo pacts, PactService service, Clock clock) {
        this.pacts = pacts;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void settleBets() {
        for (Long id : pacts.due(clock.instant())) {
            try {
                service.sweep(id);
            } catch (Exception error) {
                log.error("Could not settle pact {}", id, error);
            }
        }
    }
}
