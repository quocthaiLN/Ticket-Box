---
name: api-and-interface-design
description: Design stable Java Spring Boot REST APIs, service contracts, and module boundaries. Use for endpoints, DTOs, validation, error handling, and integration-facing interfaces.
---

# Java Spring Boot API and Interface Design

## Outcome

Create additive, predictable contracts that keep HTTP concerns at the controller boundary, business rules in services, and persistence details private. Write the request/response contract before implementation; controllers must not expose JPA entities.

## Scope and defaults

Use this skill for Spring Boot REST APIs, service interfaces, module boundaries, and contracts between the frontend and backend. Prefer Spring Boot 3+ conventions, Java records for immutable DTOs, Jakarta Validation, `@RestControllerAdvice`, Spring Data pagination, and OpenAPI annotations when the project uses springdoc.

Do not impose GraphQL, a new API version, a global envelope, or a new library unless the existing project or requirement calls for it. Follow the project's existing error and authentication conventions when they are compatible with the rules below.

## Design workflow

Before coding, specify:

1. Resource, URI, HTTP method, request DTO, response DTO, status codes, authorization rule, and retry/idempotency behaviour.
2. Validation that belongs on the incoming DTO versus business invariants that belong in the service/domain layer.
3. Error codes and problem fields clients may rely on.
4. Pagination, sorting, filtering, and stable ordering for every collection endpoint.

Keep the dependency direction explicit:

```text
HTTP request → Controller → Application service → Domain/repository
                  ↓                 ↓
             Request DTO        Response DTO
```

Controllers translate HTTP to an application call. They should not contain business rules, directly use repositories, or return `Entity`, `Page<Entity>`, `Optional`, stack traces, or persistence exceptions.

## Resource and naming conventions

Use plural nouns and HTTP semantics:

```text
GET    /api/v1/tickets                 list
POST   /api/v1/tickets                 create
GET    /api/v1/tickets/{ticketId}      read
PATCH  /api/v1/tickets/{ticketId}      partial update
DELETE /api/v1/tickets/{ticketId}      delete
GET    /api/v1/tickets/{ticketId}/comments
POST   /api/v1/tickets/{ticketId}/comments
```

- Use kebab-case paths, camelCase JSON properties and query parameters, and `UUID` (or the project's chosen identifier type) in Java contracts.
- Use `is`/`has`/`can` prefixes for booleans; serialize enums as deliberate stable strings, not ordinal values.
- Prefer an action sub-resource only when it is a real domain command, e.g. `POST /tickets/{ticketId}/cancellation` rather than `/cancelTicket`.
- Use `PUT` only for complete replacement. Use `PATCH` for partial updates and distinguish an omitted field from an explicit `null` if clearing values is supported.

## DTO-first contracts

Use separate input and output DTOs. Records make these contracts concise and immutable.

```java
public record CreateTicketRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 4_000) String description,
    @NotNull TicketPriority priority
) {}

public record TicketResponse(
    UUID id,
    String title,
    String description,
    TicketPriority priority,
    TicketStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
```

Keep entities internal and map them in an explicit mapper or the service layer. Do not reuse a create request as an update request or a response: generated fields, server-controlled fields, and writable fields change independently.

For partial updates, use a dedicated DTO and document its null semantics. When JSON `null` means “clear”, use a presence-aware representation or JSON Merge Patch instead of treating `null` and missing values as identical.

## Controller boundary

Validate request DTOs at the HTTP boundary with `@Valid`; validate path/query constraints with `@Validated` and constraints such as `@Positive`. Authentication and authorization belong at this boundary or the method-security boundary. Services may assume syntactic input is valid but must enforce business invariants and authorization that cannot be guaranteed by transport-layer checks.

```java
@RestController
@RequestMapping("/api/v1/tickets")
@Validated
class TicketController {
    private final TicketService ticketService;

    @PostMapping
    ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketResponse ticket = ticketService.create(request);
        URI location = URI.create("/api/v1/tickets/" + ticket.id());
        return ResponseEntity.created(location).body(ticket);
    }

    @GetMapping("/{ticketId}")
    TicketResponse get(@PathVariable UUID ticketId) {
        return ticketService.get(ticketId);
    }
}
```

External API responses, message payloads, and configuration are untrusted inputs too. Parse and validate them before they affect business logic.

## Error contract

Return one documented error shape everywhere. Prefer Spring's `ProblemDetail` (`application/problem+json`) and add a stable, machine-readable `code`; do not make clients parse `detail` text.

```java
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(TicketNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(TicketNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, "Ticket was not found");
        problem.setTitle("Resource not found");
        problem.setProperty("code", "TICKET_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequest(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, "Request validation failed");
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("fieldErrors", toFieldErrors(ex));
        return ResponseEntity.unprocessableEntity().body(problem);
    }
}
```

Map errors consistently: `400` malformed request, `401` unauthenticated, `403` unauthorized, `404` absent resource, `409` state/uniqueness conflict, `422` valid JSON that fails input or domain validation, and `500` unexpected server failure. Log internal causes with request correlation; never expose stack traces, SQL details, secrets, or implementation class names.

## Pagination, filtering, and sorting

Collection endpoints must paginate and use deterministic sorting. Bind only an allowlist of sortable fields; never pass arbitrary client input into a query or `Sort` expression.

```java
public record PageResponse<T>(
    List<T> data,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
    static <T, R> PageResponse<R> from(Page<T> source, Function<T, R> mapper) {
        return new PageResponse<>(source.getContent().stream().map(mapper).toList(),
            source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
```

Do not serialize Spring Data's `Page` directly: its JSON representation is an implementation detail. Establish and document a maximum page size. If data changes frequently, add a unique tiebreaker (for example `createdAt, id`) or use cursor pagination where consistency across pages matters.

## Transactions, concurrency, and idempotency

- Put transaction boundaries on application-service methods, not controllers. Keep transactions short and do not call slow remote services while holding database locks.
- Use database constraints as the authority for uniqueness. Convert `DataIntegrityViolationException` into a deliberate `409` only when it represents an expected business conflict.
- For stale-write protection, expose a version/ETag and require it with an update (`If-Match`) or include an explicit version field. Do not silently overwrite concurrent edits.

For a state-changing operation that clients or queues may retry, treat `Idempotency-Key` as a real durable contract:

1. Require a client-generated key that remains unchanged across retries of the same intent.
2. Atomically insert a record with a unique key and a request hash before the side effect. A read-then-insert is a race.
3. Reject the same key with a different payload (`422`), and deliberately handle an in-progress duplicate (`409`, bounded wait, or `202` with a status URI).
4. Persist the final status/response for replay and retain it longer than every retry and dead-letter-redelivery path.

Do not generate a UUID or timestamp per server-side attempt and call it idempotency; that makes each retry a new operation.

## Compatibility rules

Hyrum's Law applies to every observable behavior. Treat field names, enum values, error codes, ordering, validation, and status codes as contracts after release.

- Prefer additions: add optional request fields and nullable/optional response fields.
- Never rename/remove a field or change its type, enum meaning, error shape, URI, or default ordering without a migration plan.
- Avoid parallel API versions unless a breaking migration is unavoidable. If versioning is required, make it explicit (`/api/v1`) and document deprecation dates and a migration path.

## Verification checklist

- [ ] Each endpoint has documented request, success, and error DTO/schema contracts.
- [ ] Controllers return DTOs only; entities and repository exceptions do not cross the HTTP boundary.
- [ ] `@Valid`/`@Validated` guards untrusted HTTP input and service/domain code enforces business invariants.
- [ ] Errors use one stable `ProblemDetail` contract with machine-readable codes.
- [ ] Collection responses use an explicit page DTO, size cap, filters, and deterministic allowed sorting.
- [ ] Status codes, `Location` headers, and `PATCH`/`PUT` semantics match the declared operation.
- [ ] State-changing retryable operations have an atomic, payload-guarded idempotency design.
- [ ] Tests cover valid requests, invalid input, authorization, not found, conflict, pagination, and duplicate/retry behavior.
