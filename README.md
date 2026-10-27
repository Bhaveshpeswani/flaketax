FlakeTax
FlakeTax is a Spring Boot application for managing accountability challenges between friends using virtual points.

The idea is straightforward. A group agrees on a challenge, each participant commits a fixed number of points, and everyone has until the deadline to submit proof. Submissions are reviewed before the points are distributed.

I built the project primarily to work through backend problems involving concurrent requests, transactional consistency, authorization, scheduled jobs and persistent state.

How it works
Each user starts with 1,000 virtual points.

When a participant joins a pact, the application deducts their stake from their available balance and adds it to the pact's pot. These operations are handled within a database transaction.

Participants can submit a note or image before the deadline. Approved participants become eligible for a payout once the review period closes. If nobody qualifies, the stakes are refunded.

The point movements are recorded separately from the current account balances. This provides an audit trail for deposits, stakes, payouts and refunds.

Backend design
The application uses Java 21, Spring Boot, Spring Data JPA and PostgreSQL.

The main responsibilities are separated into controllers, services and repositories.

Spring Security handles authentication and access control.
JPA and Hibernate manage persistence and entity relationships.
PostgreSQL stores accounts, pacts, memberships and ledger entries.
Flyway manages schema changes.
Spring scheduling processes pacts whose review periods have ended.
Database transactions and row-level locking coordinate balance updates and settlement.
Settlement is designed to be idempotent. Repeating a successful settlement request should not generate another payout.

The browser interface is built with plain HTML, CSS and JavaScript. It communicates with the backend through REST endpoints.

Running locally
The project requires Java 21, Maven, PostgreSQL and Docker Desktop for the container-based integration tests.

Start the development database:

docker compose up -d db
Run the Spring Boot application from IntelliJ, or use:

mvn spring-boot:run
The application runs at:

http://localhost:8080

Database connection settings can be supplied through environment variables.

For local testing, REVIEW_MINUTES=1 shortens the review period for newly created pacts.

Testing
The project includes JUnit tests and PostgreSQL integration tests using Testcontainers.

mvn test
mvn verify
The main cases worth checking are duplicate settlement requests, concurrent balance updates, refunds when nobody qualifies, deadline enforcement and account authorization.

The API can also be exercised manually through Postman.

Current scope
FlakeTax uses virtual points, not real currency.

The current authentication and file-storage setup is intended for local development. A public deployment would require additional security work, including HTTPS, stronger session management, rate limiting and operational monitoring.

The project is an exercise in building and reasoning about a stateful backend, particularly the parts where incorrect transaction handling could produce inconsistent balances.
