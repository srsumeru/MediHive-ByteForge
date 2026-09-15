# MediHive by ByteForge

**Official problem statement:** “From One Empty Shelf to a Regional Shortage”  
**Domain:** Healthcare/Disability

MediHive is a fictional hackathon prototype for medicine shortage alerts and approved redistribution among five facilities. Java 17, Spring Boot, Maven, JPA, PostgreSQL/H2 and HTML/CSS/vanilla JavaScript run from one server. No patient information is stored.

## Five separate demo logins

| Administrator | Username | Temporary password | Inventory editing |
| --- | --- | --- | --- |
| Central Medical Warehouse | `warehouse_admin` | `HiveWH!2026` | Own warehouse only |
| Unity Hospital | `unity_admin` | `UnityH!2026` | No |
| City Hospital | `city_hospital_admin` | `CityH!2026` | No |
| CityCare Pharmacy | `citycare_admin` | `CityCareP!2026` | No |
| MediPlus Pharmacy | `mediplus_admin` | `MediPlusP!2026` | No |

**These are fictional, local-only hackathon demonstration accounts.** They are intentionally included so judges can run the project; they do not unlock a public production system. Never reuse these passwords in a real deployment. Demo values are defined in backend `config/DemoAccounts.java` and encoded with BCrypt at startup. They do not appear in HTML or JavaScript. For deployment, use a proper account store and externally managed secrets.

The complete judge setup, workflow and permission guide is available in [`docs/MediHive-User-Manual.pdf`](docs/MediHive-User-Manual.pdf). The Round 1 upload checks are in [`SUBMISSION_CHECKLIST.md`](SUBMISSION_CHECKLIST.md).

Each hospital/pharmacy administrator owns exactly one facility. They can view only their facility inventory, create a request for it, approve/reject and dispatch outgoing supply from it, and confirm incoming deliveries to it. They cannot edit stock records. Critical requests are broadcast for awareness to all five accounts. The warehouse can view all facilities, all requests and every regional dispatch; it may edit warehouse stock and approve/dispatch only warehouse allocations. The warehouse dashboard includes a dedicated **Dispatches across all facilities** feed showing both in-transit and delivered allocations.

## Changes in v3

- The warehouse sees **Edit** only for its own inventory. Other facilities are view-only, and direct API edits of their stock still return 403.
- Save, offer, approve, dispatch, receipt, rejection and cancellation confirmations use built-in dialogs matching the MediHive theme. Quantity inputs show the maximum and projected reserve. Errors remain in the dialog. Cancel changes nothing.
- Warehouse offers always come from the warehouse, even while viewing another facility.
- Hospital transfers preserve the safety floor plus 50 extra units per medicine, after subtracting all approved reservations.
- Pending suggestions no longer block valid alternative critical offers. Approvals cannot overfill a request.
- Inventory edits and transfers lock the relevant stock rows. Repeated dispatches/receipts cannot change stock twice.
- Warehouse admins can record low stock when there are no approved reservations. An edit cannot invalidate existing approvals.
- Existing logins, facility ownership and PostgreSQL settings are retained.

## Start quickly with H2

Install Java 17 and Maven 3.9+ (with internet for initial dependencies). Extract the ZIP and run `mvn test` and `mvn spring-boot:run` inside `MediHive-ByteForge`. Open `http://localhost:8080/login` and sign in with one of the five accounts. Use **Log out** to switch accounts. H2 is the default in-memory demo database; seeded data resets on restart. Open the whole project folder in VS Code with Java extensions; do not open `index.html` directly as a `file:` URL.

In **Spring Tool Suite (STS)**: **File > Import > Maven > Existing Maven Projects**, select `MediHive-ByteForge`, wait for dependency download, then run `MediHiveApplication.java` as **Spring Boot App**.

For **PostgreSQL / pgAdmin 4**, create a `medihive` database (or run `create-database.sql` with a privileged PostgreSQL account), provide your own `DB_URL=jdbc:postgresql://localhost:5432/medihive`, `DB_USERNAME=postgres` and `DB_PASSWORD=<your password>` in the launch environment, then run `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`. In STS, set the `postgres` profile and those environment variables in Run Configurations. The ZIP contains no real database password. Hibernate updates demo tables; use migrations and a least-privilege DB account for production.

## Rules and request lifecycle

Each inventory record stores facility, medicine, quantity, average daily consumption, reorder level, expiry and last-update time. Estimated days remaining = quantity / daily consumption, rounded to one decimal; zero daily consumption displays a 999 sentinel. Safety floor = max(reorder level, ceil(5 x daily consumption)). Hospitals retain an additional 50 units of each medicine above that safety floor. Available to offer = max(0, quantity - safety floor - extra hospital reserve - approved reservations), or zero when expired. The extra reserve is 50 for hospitals and 0 for warehouses/pharmacies. For 300 units, a safety floor of 100 and no reservations, a hospital can send at most 150 units. The check runs for suggestions, offers, approvals and dispatch, protecting other approved transfers as well. Risk classification uses unrounded days remaining, while the display rounds to one decimal. Risk is `OUT_OF_STOCK` for zero, `CRITICAL` when at/below reorder or at most five days left, `HIGH` at most 12 days, otherwise `HEALTHY`. Expiry within 60 days generates a separate warning. These are illustrative demo thresholds, not medical advice.

Normal/high requests are routed to a pending warehouse allocation. Critical requests are visible to every account; safe sources are ranked by usable surplus and one or more pending proposals are created until the requested amount is allocated as far as stock allows. Source owners approve a specific amount no higher than proposed and safe surplus, or reject. For critical requests, another source may offer an alternative to a pending suggestion. Pending proposals do not reserve stock and may overlap. Repeating an offer updates that source's pending proposal. Only approved, dispatched and delivered amounts count toward the request total. Each source owner still explicitly approves its proposal; offers do not dispatch automatically. Fully covered requests show no further offer action, and unused proposals remain visible as history. Total approvals cannot exceed requested quantity. The source dispatches its approved allocation once, subtracting stock. Only the specific receiving account can confirm it once, adding stock. A request closes when the full amount is confirmed, or the requester cancels before an allocation is in transit; a partial delivery remains visible. Allocation status and timestamps provide a transaction history. Backend checks apply even to direct Postman calls.

## API / Postman

Import `postman/MediHive.postman_collection.json`. Log in at `/login` first and carry `JSESSIONID` and `XSRF-TOKEN` cookies into Postman; send the XSRF cookie's value as `X-XSRF-TOKEN` for POST/PUT. The website does this automatically. Inspect returned IDs because PostgreSQL records may differ from the H2 demo.

| Route | Access |
| --- | --- |
| `GET /api/me`, `/facilities`, `/medicines` | Authenticated account, catalog |
| `GET /api/dashboard/{id}`, `/inventory?facilityId={id}` | Own facility, or all for warehouse |
| `PUT /api/inventory/{id}` | Warehouse account, warehouse record only |
| `GET /api/requests?facilityId={id}` | Own facility and critical broadcast; warehouse all |
| `POST /api/requests` | Only the requesting facility's account |
| `GET /api/suggestions?requesterId={id}&medicineId={id}` | Requester or warehouse can inspect safe sources |
| `GET /api/requests/{id}/offer-options?sourceId={id}` | Only the offering source's account; returns its reserve and maximum offer |
| `POST /api/requests/{id}/offers` | Only the offering source's account |
| `POST /api/allocations/{id}/approve`, `/reject`, `/dispatch` | Only the source facility's account |
| `POST /api/allocations/{id}/deliver` | Only the receiving facility's account |
| `POST /api/requests/{id}/cancel` | Only the requesting facility's account |

Create body: `{"requesterId":2,"medicineId":5,"quantity":100,"priority":"CRITICAL"}`; approve body: `{"quantity":60}`; offer body: `{"sourceId":3,"quantity":40}`. Every modifying action needs a valid CSRF header in addition to the session cookie.

## Demonstration for judges

1. Sign in as `unity_admin`. Unity Hospital shows critical adrenaline stock. The request form displays Unity Hospital as a read-only requester.
2. Show the seeded 100-unit critical request, with 60 pending from the warehouse and 40 from City Hospital. It is also visible to the pharmacy accounts for critical awareness.
3. Sign in as `warehouse_admin`: approve 60 and dispatch. Stock drops at the warehouse; the warehouse's regional dispatch feed shows it.
4. Sign in as `city_hospital_admin`: approve 40 and dispatch. The same dispatch appears in the warehouse feed. City Hospital stock drops from 120 to 80 in a fresh H2 demo, preserving its 30-unit safety floor plus 50 extra units. This account cannot edit stock or confirm receipt for Unity Hospital.
5. Sign in as `unity_admin`: confirm both incoming allocations. Unity inventory increases and the request closes at 100 delivered. Attempt a second receipt to demonstrate duplicate rejection.
6. Sign in as the two pharmacy accounts separately; neither can view the other's dashboard or edit stock. A pharmacy request with normal/high priority routes to the warehouse.

## Validation

The completed v3 checks passed: **14 backend integration tests and 5 frontend interaction tests**. Java compilation and JavaScript syntax checks also passed.

Run `mvn test` for backend integration tests. Tests cover account authentication, inventory permissions, critical offers, reserve boundaries, existing reservations, two simultaneous approvals and duplicate dispatch/receipt. The tests use H2 and Spring Security; they do not use your PostgreSQL database. Mockito uses its subclass mock maker so the tests do not require dynamic JVM agent attachment.

For the optional frontend interaction tests, open `src/test/frontend`, run `npm install`, then `npm test`. These use JSDOM and isolated API fixtures to check the actual HTML/JavaScript. Node.js is only needed for those optional tests, not to run the website. They check warehouse view-only controls, themed confirmations, quantity limits, the correct offer source, server errors and duplicate submission protection.

Before submitting, run the tests and complete the five-account workflow in a real browser on the exact computer that will be used for the demonstration. The H2 judge demo does not require PostgreSQL; verify PostgreSQL separately if you choose to use it.

## Updating from v2

Start with the included `START_HERE.txt`. Import the new source into a separate STS workspace so it does not conflict with your existing project. Stop the old server before launching v3 on port 8080. If you use a different port, keep your own setting in `application.properties` or the run configuration.

The default H2 demo resets on restart, as it did in v2. The PostgreSQL profile uses `ddl-auto=update`; this update does not delete or reset your database and does not require a new table. Existing data remains subject to the new reserve rule. In particular, an old hospital with insufficient spare stock will correctly have zero available to offer. The fresh demo's City Hospital adrenaline quantity is now 120 so its existing 40-unit proposal meets the new reserve rule. Existing PostgreSQL rows are not changed by this seed adjustment.
