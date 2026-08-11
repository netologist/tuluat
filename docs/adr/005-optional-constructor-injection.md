# ADR 005: Optional<T> for Constructor Injection

## Status

Accepted (2026-08-11)

## Context

Spring beans often have optional dependencies — services that may or may not be
present depending on deployment profile (e.g. `WorkflowClient` is only available
when Temporal is configured, `MeterRegistry` only when Prometheus is enabled).

The legacy pattern uses `@Autowired(required = false)` with null checks:

```java
@Autowired
public Foo(@Autowired(required = false) Bar bar) {
    this.bar = bar; // may be null
}
// ...
if (bar != null) { bar.doSomething(); }
```

This is error-prone (forgetting the null check = NPE) and doesn't express intent
in the type system.

## Decision

We use `java.util.Optional<T>` for all optional constructor-injected
dependencies:

```java
public Foo(Optional<Bar> bar) {
    this.bar = bar;
}
// ...
bar.ifPresent(b -> b.doSomething());
```

Spring 4.3+ natively supports `Optional<T>` injection — when the bean doesn't
exist, Spring automatically supplies `Optional.empty()`.

## Consequences

**Positive:**
- Type-safe: the field type `Optional<Bar>` documents that this dependency is optional
- No null checks: `.ifPresent()`, `.map()`, `.orElse()` express intent clearly
- Consistent with modern Spring Boot practices

**Negative:**
- IntelliJ IDEA flags `Optional<T>` as field/parameter type with warning "Optional used as type for field or parameter"
  - **Mitigated** via `.idea/inspectionProfiles/Project_Default.xml` (committed) and class-level `@SuppressWarnings("OptionalUsedAsFieldOrParameterType")`
- `Optional` is not `Serializable` — irrelevant for Spring beans (never serialized)

## Alternatives Considered

- **`@Autowired(required = false)`**: Rejected — null-unsafe, doesn't express intent
- **`ObjectProvider<T>`**: Rejected — Spring-specific, more verbose, less idiomatic
- **Setter injection**: Rejected — mutable, less testable

## References

- [Spring Framework: Constructor Injection with Optional](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- Brian Goetz: "Optional is intended for return types" — valid for API design, but Spring injection is a special case where Optional as field is the idiomatic choice
