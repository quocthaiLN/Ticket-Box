# TicketBox

## Project overview

- Backend: 
  - Java 21
  - Spring Boot 4.0.6, Maven; 
  - Spring Web MVC, Spring Data JPA, Spring Security/OAuth2, Validation, AMQP, Mail, Flyway; - - Lombok, JJWT 0.11.5, MinIO SDK 8.5.17.
- Frontend: TypeScript 5, React 19, React Router 7, Vite 6, Tailwind CSS 4.3; `lucide-react`, `motion`, `qrcode`.
- Local data and infrastructure: 
  - PostgreSQL 15 (primary database)
  - Redis 7 (cache)
  - RabbitMQ 3.12 (messaging)
  - MinIO (file storage)
  - H2 for backend tests

## Quick start

- Docker, NginX runs at `http://localhost:8080`
```bash
docker compose up -d
```

- Backend runs at `http://localhost:8082`
```bash
cd backend
# Check 
./mvnw test
# Run
./mvnw spring-boot:run
```

- Frontend runs at `http://localhost:3001`
```bash
cd frontend
# Check
npm install
npm run build
# Run
npm run dev
```

## Startup Workflow
- Before writing code:
  - Confirm the working directory with `pwd`.
  - Read `PROGRESS.md` for the latest verified state and next step.
  - Review recent commits with `git log --oneline -5`.
  - Run `./init.sh`.
  - Run the required smoke or end-to-end verification before starting new work.

- If baseline verification is already failing, notify me and fix that first. Do not stack new feature work on top of a broken starting state.

## Required Artifacts
- `PROGRESS.md` and `.agents/progress/*.md`: session log and current verified status
- `init.sh`: standard startup and verification path

## Working Rules
- Work on one feature at a time.
- Do not mark a feature complete just because code was added.
- Keep changes within the selected feature scope unless a blocker forces a narrow supporting fix.
- Do not silently change verification rules during implementation.
- Prefer durable repo artifacts over chat summaries.
- Avoid Fully Qualified Class Names (FQCN) in code and tests; always use explicit `import` and simple class names.
- Always validate plan & implementation with `DECISIONS.md`.

## Definition Of Done
- A feature is done only when all of the following are true:
  - The target behavior is implemented.
  - The required verification actually ran.
  - Evidence is recorded in `PROGRESS.md`.
  - The repository remains restartable from the standard startup path.
  - Use skill implementation-explan to instruct how to review code.
  
## End Of Session
- Before ending a session:
  - Update `PROGRESS.md`.
  - Record any unresolved risk or blocker.
  - Commit with a descriptive message once the work is in a safe state.
  - Explain the implemented code clearly.
Leave the repo clean enough for the next session to run ./init.sh immediately.

## Docs

- Specification `blueprint/specs/*.md` - Required when reading when planning and implement new features.
- API Design Patterns `blueprint/api-design/*.md` - Required reading when adding endpoints.
- Database Design (`blueprint/database-design.md`) - Required when modifying database operations.
- Project Structure (`blueprint/structure.md`) - Required when inspect codebase.
- Exception Design (`blueprint/exception.md`) - Required when planning for implement code.