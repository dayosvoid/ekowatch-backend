# Ekowatch backend architecture

Independent Spring Boot modular monolith with its own PostgreSQL database.
Modules own their domain, persistence and service internals. Other modules use the public `api` package.
Flyway migrations live in `src/main/resources/db/migration`.

Ownership: Adedayo — data, rules, migrations, concurrency tests; Esther — API, access, CI and deployments (shared reviews).

The Data & Rules foundation includes Flyway schema, validated JPA mappings, transactional JDBC services,
PostgreSQL concurrency tests, opt-in demo data, request correlation and enforced module boundaries.
Cross-module calls use api packages. See engineer-a-handoff.md for proposed contracts and limitations.
REST endpoints, authentication and deployment remain Engineer B's integration work.
