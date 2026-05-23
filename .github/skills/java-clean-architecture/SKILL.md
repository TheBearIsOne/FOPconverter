---
name: java-clean-architecture
description: 'Review, plan, implement, refactor, or assess Java code using clean code, clean architecture, SOLID, ports and adapters, domain boundaries, and testable design. Use when reviewing Java or Spring code, checking package structure, finding boundary leaks, extracting use cases, isolating framework code, or improving maintainability.'
argument-hint: 'Describe the Java feature, refactor, or design problem plus the affected layers or components.'
user-invocable: true
---

# Java Clean Architecture

## What This Skill Produces

This skill guides the agent to review or change Java code so that business rules stay explicit, dependencies point inward, and the resulting code is easy to test, review, and extend.

Use it for both new implementation work and refactors.

## When to Use

- Reviewing Java or Spring code for clean architecture, SOLID, or boundary violations
- Implementing a Java or Spring feature with clear layer boundaries
- Refactoring a service, controller, repository, or integration point
- Extracting a use case from framework-heavy code
- Deciding whether logic belongs in domain, application, or infrastructure layers
- Reducing coupling, duplication, or leakage of external concerns into business logic

## Inputs To Collect

Before changing code, identify:

1. The business outcome or use case being supported
2. The entry point and owning component currently responsible for the behavior
3. The affected architectural layers: domain, application, interface, infrastructure
4. External dependencies involved: database, filesystem, HTTP, messaging, framework APIs
5. The cheapest validation for the change: focused test, compile step, or behavior check

## Workflow

### 1. Frame The Use Case

- State the user or business outcome in one sentence
- Separate required behavior from incidental technical details
- List constraints such as transactionality, latency, idempotency, security, or backward compatibility

### 2. Place The Behavior In The Correct Layer

Use these placement rules:

- Domain layer: business rules, invariants, calculations, policies, value objects
- Application layer: orchestration of use cases, transaction boundaries, coordination between ports
- Interface layer: controllers, request parsing, response shaping, transport concerns
- Infrastructure layer: persistence, messaging, file IO, external services, framework adapters

If logic depends on transport, persistence, or framework details, keep it outside the domain.
If logic expresses business policy and can be tested without external systems, pull it inward.

### 3. Define Boundaries Explicitly

- Model clear inputs and outputs for the use case
- Prefer intention-revealing request and response models over leaking transport or persistence objects
- Define ports in the inner layer when the use case depends on an external capability
- Keep adapters thin; they translate between frameworks and the application model

### 4. Apply SOLID Pragmatically

- Single Responsibility: each class should have one reason to change
- Open/Closed: extend behavior with new collaborators or strategies when variation is real
- Liskov Substitution: abstractions must preserve expected behavior and invariants
- Interface Segregation: prefer small, focused ports over wide utility interfaces
- Dependency Inversion: inner layers own the abstractions they depend on

Do not create abstractions preemptively. Introduce an interface when it isolates an external dependency, represents a real boundary, or simplifies testing without distorting the design.

### 5. Keep The Code Clean

- Use names that describe domain intent, not implementation trivia
- Favor small methods that each express one step of the use case
- Replace flag arguments and multi-purpose methods with clearer flows
- Prefer value objects over loose primitive bundles when concepts have rules
- Keep state transitions explicit and validate invariants close to where they matter
- Fail fast on invalid inputs and translate failures at boundaries

### 6. Control Dependencies And Coupling

- Domain code must not depend on Spring, persistence annotations, HTTP types, or logging APIs unless there is an unavoidable and justified exception
- Application services should coordinate collaborators, not accumulate domain rules that belong elsewhere
- Repositories should persist and retrieve data, not contain unrelated orchestration
- Controllers and handlers should translate and delegate, not embed business decisions

When a class mixes concerns, split by behavior ownership, not by arbitrary method count.

### 7. Choose The Simplest Design That Preserves Boundaries

Use these decision rules:

- Prefer a concrete class first when there is one implementation and no architectural boundary to protect
- Introduce a port when crossing infrastructure or framework boundaries
- Extract a domain service when business behavior does not naturally belong to one entity or value object
- Keep mapping code near boundaries; do not spread conversion logic through the core use case
- Prefer composition over inheritance unless subtype behavior is truly substitutable

### 8. Test At The Right Level

- Unit test domain rules and application orchestration without booting the framework when possible
- Add integration or slice tests for repositories, controllers, and external adapters
- Test behavior and invariants, not private implementation details
- Cover error paths where translation or boundary behavior matters

Start with the cheapest focused validation that can falsify the design.

### 9. Review The Final Shape

Before finishing, check:

- Dependencies still point inward
- Business rules are not trapped in controllers, repositories, or framework glue
- Public method names reflect the use case clearly
- Classes have narrow, coherent responsibilities
- External concerns are translated at boundaries
- Tests cover the changed behavior at the correct level
- The change is simpler or clearer than what it replaced

If you are reviewing code rather than changing it, report the concrete architectural findings first, ordered by severity, and keep the findings grounded in behavior, maintainability, or testability risk.

## Branching Logic

If you are unsure whether to extract a new type:

- Extract when a concept has its own invariants, lifecycle, or language in the domain
- Keep local when the behavior is trivial, single-use, and clearer in place

If you are unsure whether to add an interface:

- Add it for external systems, framework seams, or stable use-case boundaries
- Skip it for speculative flexibility

If business logic appears in a controller, repository, or adapter:

- Move rule evaluation inward
- Leave only translation, transport, or persistence concerns at the edge

If application services become large:

- Push domain decisions into domain objects or domain services
- Keep orchestration in the application layer

## Quality Bar

The result should meet these standards:

- Clean code: readable names, low noise, explicit intent, small focused units
- Clean architecture: separation of concerns and stable dependency direction
- SOLID: abstractions justified by real boundaries, not ceremony
- Testability: core behavior can be verified cheaply
- Maintainability: a new developer can locate the use case and modify it safely

## Review Focus

When used as a review skill, prioritize:

- Business logic leaking into controllers, repositories, or adapters
- Spring or infrastructure concerns leaking into domain or use-case code
- Overly broad services, repositories, or interfaces
- Abstractions added for speculation rather than real boundaries
- Missing tests around business rules or boundary translation
- Naming or package structure that hides the use case

## Example Prompts

- Review this Java package with the Java clean architecture skill and identify SOLID or boundary violations.
- Use the Java clean architecture skill to refactor this Spring service so domain rules no longer depend on repository details.
- Use the Java clean architecture skill to add a new use case for document conversion while keeping transport and persistence concerns at the edges.