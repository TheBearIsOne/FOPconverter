---
name: Java Architecture Review
description: 'Review Java or Spring code for clean code, clean architecture, SOLID, boundary violations, framework leakage, and testability gaps.'
argument-hint: 'Provide Java files, symbols, or describe the package or component to review.'
agent: agent
---

Review the provided Java or Spring code using the java-clean-architecture skill.

Focus on:

- Clean architecture boundary violations
- SOLID design problems that affect maintainability
- Business logic in controllers, repositories, or infrastructure adapters
- Framework leakage into domain or use-case code
- Over-abstraction, speculative interfaces, or misplaced responsibilities
- Missing or mis-scoped tests for the changed behavior

Output requirements:

1. Present findings first, ordered by severity.
2. Explain why each issue matters in terms of behavior, maintainability, or testability.
3. Suggest the smallest refactoring direction that corrects the design.
4. If no significant issues are found, say so explicitly and note any residual risks or testing gaps.