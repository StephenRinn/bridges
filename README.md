# bridges-core

`bridges-core` is the foundation of the Bridges logging ecosystem. It provides a fiber-local structured logging API
built on Cats Effect `IO` and `IOLocal`, allowing request-scoped context to flow naturally across asynchronous
boundaries without relying on thread-local storage (MDC).

The module is intentionally backend-agnostic. Log events are produced by `BridgeLogger` and written through a pluggable
`LogSink`, making it easy to integrate with SLF4J or custom destinations.

---

## Features

- Fiber-local request context using `IOLocal`
- Structured logging with arbitrary key/value attributes
- Correlation ID and request ID propagation
- Multiple log levels
    - Trace
    - Debug
    - Info
    - Warn
    - Error
- Pluggable logging sinks
- Automatic request lifecycle helper
- Configurable log sampling
- Buffered low-level logs with automatic replay on sampled requests or errors
- Generic `F[_]` adapter for projects that use `Async`, `Sync`, or another Cats Effect datatype
- Ergonomic runtime or Direct dependency injected logger

---

## Module Layout

```
bridges-core
├── runtime
│   ├── Bridge
│   ├── BridgeRuntime
├── logger
│   ├── BridgeLogger
│   ├── BridgeLoggerImpl
│   ├── GenericBridgeLogger
│   └── BridgeLoggerConfig
├── contextStorage
│   ├── IOStorage
│   └── ContextOperations
├── logEvent
│   ├── LogEvent
│   └── LogLevel
└── logSink
    ├── LogSink
    ├── SLF4JSink
    ├── JSONSink
    └── IOBridgeSink
```

---

# Core Concepts

## Bridge

`Bridge` and `BridgeRuntime` is the primary api used for ergonomic logging support.

Supported operations include:

- `trace`
- `debug`
- `info`
- `warn`
- `error`
- `withRequest`
- `updateValues`
- `setCorrelationId`
- `setRequestId`

Example:

```scala
def run = {
  for {
    logger <- BridgeLogger.builder().minLevel(Info).build
    _ <- Bridge.initialize(logger)
    _ <- Server.run()
  } yield ExitCode.Success
}
```

```scala
class Service {
  def foo = {
    for {
      _ <- Bridge.info("Logged with BridgeLogger")
      _ <-
    } yield
  }
}
```

## BridgeLogger

`BridgeLogger` is the primary API used by libraries and applications preferring direct dependency injection.

`BridgeLogger` is the underlying implementation for `Bridge` and supports all the same operations.

Example:

```scala
for {
  _ <- logger.info("User authenticated")
  _ <- logger.updateValues("userId", user.id.toString)
  _ <- logger.debug("Loading profile")
} yield ()
```

Every log automatically includes the current request context.

---

## Request Context

Each request owns its own `IOStorage` instance.

Current context contains:

- correlation ID
- request ID
- arbitrary key/value attributes
- request start/end timestamps
- sampling state
- buffered log events

Because the context is stored in an `IOLocal`, it follows Cats Effect fibers rather than threads.

---

# Request Lifecycle

The preferred way to execute work is with `withRequest`.

```scala
logger.withRequest() {
  service.processRequest()
}
```

You may also provide your own identifiers and initial values.

```scala
logger.withRequest(
  correlationId = correlationId,
  requestId = requestId,
  values = Map(
    "service" -> "payments",
    "region" -> "us-east"
  )
) {
  service.process()
}
```

Everything executed inside the block automatically shares the same logging context.

---

# Structured Logging

Additional values may be added during request execution.

```scala
for {
  _ <- logger.updateValues("customerId", customer.id)
  _ <- logger.updateValues("orderId", order.id)
  _ <- logger.info("Order submitted")
} yield ()
```

These values are attached to subsequent log events for the lifetime of the request.

---

# Correlation IDs

Correlation IDs can be supplied externally or generated automatically.

```scala
logger.withRequest(
  correlationId = existingCorrelationId
) {
  routes.run(request)
}
```

They may also be updated later if needed.

```scala
logger.setCorrelationId(id)
```

---

# Sampling

`BridgeLogger` supports request-level sampling.

When a request first emits a log at or above the configured minimum level, a sampling decision is made.

If the request is sampled:

- subsequent logs continue to be emitted according to configuration
- previously buffered logs may be replayed

Sampling behavior is controlled through `BridgeLoggerConfig`.

---

# Buffered Replay

One of the distinguishing features of Bridges is buffered replay.

When buffering is enabled, logs below the configured minimum level are temporarily stored instead of immediately
emitted.

If the request later:

- becomes sampled, or
- emits an error,

the buffered logs are replayed before the triggering log event.

This allows applications to keep verbose diagnostics for failing requests without paying the cost of logging every
successful request.

Buffer size is configurable.

---

# Log Sinks

Logging output is abstracted through `LogSink`.

Included implementations include:

- `SLF4JSink`
- `JSONSink`
- `IOBridgeSink`

Applications may implement their own sink by extending `LogSink`.

---

# GenericBridgeLogger

Many Cats Effect applications expose services using `F[_]`.

While `BridgeLogger` itself operates in `IO`, a generic adapter is provided.

```scala
val loggerF =
  GenericBridgeLogger.fromBridge[IO](bridgeLogger)
```

This allows services parameterized over `F[_]` (with a `LiftIO` instance) to use the logger without depending directly
on the concrete implementation.

---

# Configuration

`BridgeLoggerConfig` controls runtime behavior, including:

- minimum log level
- sampling rate
- whether low-level logs are buffered
- whether buffered logs are replayed
- buffer size
- whether sampled requests continue logging below the minimum level

---

# Testing

The module includes tests covering:

- context operations
- log sinks
- logger behavior
- request context storage

---

# Current Scope

`bridges-core` currently provides the logging engine and request context implementation.

Automatic HTTP integration is provided separately by the `bridges-http4s` module.

---

# Design Goals

- Fiber-safe by default
- No thread-local MDC
- Structured logging first
- Backend-independent logging sinks
- Request-scoped contextual logging
- Efficient sampling with buffered replay
- Minimal API surface
- Easy integration into Cats Effect applications

# Roadmap

- Full testing suites
    - sampling and buffering tests
    - concurrency tests
    - stress tests
- Performance profiling
- log4cats integration
- OpenTelemetry
    - TraceId, SpanId, baggage
- Improve Sampling
    - adaptive sampline
    - ruleBased sampling
    - 