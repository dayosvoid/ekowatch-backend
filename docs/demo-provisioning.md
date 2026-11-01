# Fictional local demo provisioning (Step 8)

All people, places, activity and testimony in this fixture are fictional. This is a local demonstration dataset, not evidence about Lagos residents or streets. Do not point this runner at a shared, live or deployed database.

The runner is registered only when `local-demo` is active, `ekowatch.demo.enabled=true` is explicitly set, and neither `prod` nor `production` is active. It repeats those checks on invocation, before touching the datasource. Both opt-ins remain necessary; a production profile wins even when local-demo is also active. Do not weaken these checks for deployment. Esther owns deliberate deployed-demo provisioning and credential wiring separately.

The fixture is fixed at **2026-10-31T12:00:00Z**, or 13:00 Africa/Lagos. Coverage is **2026-10-16 through 2026-11-14**, 30 consecutive calendar days. It does not follow CURRENT_DATE or renew evidence on startup. Runtime reads still use the actual injected application Clock; the application is not frozen at the presentation time. At later wall-clock times floods expire and more schedules become missed. The two current floods at the anchor expire at 16:00Z and 17:00Z on 31 October. Do not claim those floods remain current outside those windows.

## Deliberate fresh local provisioning

1. Create a **new disposable PostgreSQL 17 instance/database**, with a unique local name, new isolated storage and loopback-only access. Keep any previous demonstration database intact. Do not reuse a shared database, attach existing volumes, run truncate/reset commands or alter an applied migration. Verify the chosen connection points to the new local instance before proceeding.
2. Supply that instance's DB_URL, DB_USERNAME and DB_PASSWORD privately through local configuration. Do not print values or paste them into source, logs or this document. Keep Java 25, Spring Boot 3.5.7, Flyway and ddl-auto=validate. On the empty database Flyway applies the existing forward migrations, currently V1–V5; seed SQL is not a migration.
3. From this repository, intentionally run:

   ```powershell
   mvn -B spring-boot:run '-Dspring-boot.run.profiles=local-demo' '-Dspring-boot.run.arguments=--ekowatch.demo.enabled=true'
   ```

   This is an explicit local provisioning/start command, not a deployed startup recommendation. The runner executes the insert-only seed transaction after migration/validation. On subsequent starts both opt-ins are still required. Neither omitting the profile nor enabling the flag alone seeds anything.
4. Check the counts and relationships below, then remove the opt-in for ordinary starts. Reruns are safe for existing data, but they do not restore a presentation changed by user activity. A clean initial presentation requires a fresh disposable database, not a reset of old data.

For a **new presentation date**, first prepare a separately reviewed fixture revision with explicit new timestamp/date anchors, preserving the UUID convention. Recalculate the 30-day interval and each street's weekday pattern; put completions and missed reports on matching, elapsed occurrences. Shift all report/confirmation evidence consistently, including an expired flood and fresh testimony. Do not merely change a flood timestamp or enable automatic renewal. Verify the revised fixed-anchor expectations in disposable PostgreSQL before deliberately provisioning a new empty local database with that fixture. Keep the old database and migrations untouched. The supplied version is specifically the 31 October dataset; there is no runtime reset/rebase switch.

## Fixed identities and expected records

UUIDs use the existing prefixes below, with a twelve-digit numeric suffix. The original users 1–5, ward 1, street 1, reports 1–3, confirmations 1–3 and schedules 1–7 retain their UUIDs. Every insert uses ON CONFLICT DO NOTHING, including natural-key conflicts; existing values are never corrected or overwritten. Previously seeded databases can therefore retain older anchors, preferences or names and may not match fresh-database counts. Use fresh provisioning for an exact presentation, not corrective updates against old data.

| Record | Fixed IDs / expected state on a fresh database |
| --- | --- |
| Users (`00000000-0000-0000-0000-…`) | Residents 1, 2, 3, 6; operators 4 and 7 assigned to ward 1; authority 5; operator 8 deliberately unassigned |
| Ward (`10000000-0000-0000-0000-…`) | 1, Fictional Demo Lagoon Ward |
| Streets (`20000000-0000-0000-0000-…`) | 1–8, including a long fictional name on 7 and no initial reports on 8 |
| Street patterns | 1/4/7/8 daily, 2/5 Monday–Wednesday–Friday, 3/6 Tuesday–Thursday–Saturday; operators 4/7 own streets 1–4/5–8 |
| Dated schedules | 172 occurrences across all 30 days, 86 per operator; original seven `30000000…` IDs retained; remaining `31000000…` suffix is street number × 100 + day offset 1–30 |
| Collections (`60000000…`) | 1: street 1, 29 October; 2: street 4, 30 October (disputed); 3: street 7, 30 October |
| Reports (`40000000…`) | 1–3 blocked drains on street 1; 4 current flood, 5 expired flood; 6 missed pickup; 7 completion dispute; 8 illegal dumping without submitted location/photo; 9 older flood renewed by newer resident evidence |
| Confirmations (`50000000…`) | 5 total: reports 1/2/3 have 0/1/2 confirmations; report 9 has 2 distinct residents with latest evidence 11:00Z on 31 October |
| Photos / idempotency records | Zero fabricated photos or historical idempotency keys |

At the presentation anchor, over the full interval, the derived schedule counts are **86 SCHEDULED, 2 DONE, 83 MISSED, 1 DISPUTED**. Authority counts are missed 83, disputed 1, blocked drains 3 and current floods 2. Completed performance counts include the dispute: operator 4 completed 2 and operator 7 completed 1. The hazard list contains five reports. These are verification expectations derived from real rows, never stored counters.

The seed assigns initial matching ward/street preferences to the four residents. Reruns preserve later preferences, password hashes, report edits, completions, confirmation evidence and newly created reports/idempotency snapshots. They never refresh created_at or confirmed_at. Tests compare every row before and after reruns, including after later actions and an advanced Clock.

## Credentials and photo handoff to Esther

All eight accounts have `!DEMO_LOGIN_DISABLED` as a deliberate unusable password marker. No password or usable demo hash is supplied and no account is claimed to log in. The current repository defines a BCryptPasswordEncoder bean in auth/web/SecurityConfig.java; Esther must verify the actual deployed encoder/configuration and privately provision compatible hashes through her approved procedure. Do not assume another project's encoder or put credentials in demo.sql. Insert-only reruns preserve provisioned hashes; they do not enable login themselves. No deployed credentials or authentication checks were performed here.

No genuinely sanitised demo asset was supplied, so photos is empty and all seeded photo_id values are null. No URL or storage key is invented. Esther's adapter must strip metadata (including EXIF/GPS), verify the sanitised bytes and content/size, provision the resulting asset and trusted metadata owned by the appropriate resident, then deliberately attach its real photo_id to a selected fictional report. Use the real resolver/storage integration; do not label unsanitised bytes safe or attach a placeholder URL. Document that provisioning separately. Until then the photo demonstration is explicitly unavailable, while report 8 intentionally remains the no-photo/location-denied case.

Esther still owns the principal/header/error integration described in the Step 7 handoff. The seed does not repair or bypass the required Idempotency-Key integration. No deployed seeding, login, photo sanitisation, HTTP walkthrough or frontend demonstration is claimed.
