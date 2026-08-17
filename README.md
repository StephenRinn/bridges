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
- Automatic request lifecycle tracking
- Configurable log sampling
- Buffered low-level logs with configurable replay
- Generic `F[_]` adapter through `LiftIO`
- Runtime based or dependency-injected logger

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

`Bridge` provides a runtime-based API for applications that do not want to
pass a `BridgeLogger` through every layer of their application.

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

A logger is initialized once at application startup:

```scala
object Main extends IOApp.Simple

def run = {
  for {
    // Create sink implementation here
    logger = BridgeLogger.builder().withMinLevel(Info).build(sink)
    _ <- Bridge.initialize(logger)
    _ <- Server.run()
  } yield ()
}
}
```

Example:

```scala
class Service {
  def foo = {
    for {
      _ <- Bridge.info("Logged with BridgeLogger")
    } yield ()
  }
}
```

## Minimum Log Level

`minLevel` defines the boundary between logs that are emitted when sampled and those which are buffered
or dropped. Sampling defaults to 100% if not specified so min level would sample all logs on default values.

For example, with:

```scala
minLevel = Info
```

the behavior is:

| Level | Unsampled                               | Sampled                                   |
|-------|-----------------------------------------|-------------------------------------------|
| Trace | buffered if enabled otherwise discarded | emitted if sampleBelowMinLevel is enabled |
| Debug | buffered if enabled otherwise discarded | emitted if sampleBelowMinLevel is enabled |
| Info  | buffered                                | emitted                                   |
| Warn  | emitted                                 | emitted                                   |
| Error | emitted                                 | emitted                                   |

When bufferBelowMinLevel is enabled, logs at or below minLevel can be
retained for later replay.

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
- bridge config (in case of override)

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

`withRequest` also tracks request lifecycle timing and records successful,
failed, and cancelled completion.
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

There are also convenience methods to update values while logging

```scala
val values = ("customerId" -> customer.id).toMap
for {
  _ <- logger.traceUpdateContext("Request started", values)
} yield ()
```

Finally you can add fields to the request which are set only for the single logging call.

```scala
for {
  _ <- logger.trace("Request started", "userId" -> user.id, "service" -> "AuthService")
} yield ()
```

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

A sampling decision is made once for each request and stored in the request's
fiber-local context. The decision may also be supplied explicitly through
`withRequest`.

```scala
logger.withRequest(
  sampleRequest = Some(true)
) {
  service.process()
}
```

When no sampling decision is provided, Bridges generates one using the configured
`SampleRate`. The decision is made once and remains fixed for the lifetime
of the request.

For example:

```scala
sampleRate = 0.1F
```

approximately 10% of the requests will be sampled. Sampling applies to requests
not individual log events.

---

# Buffered Replay

One of the distinguishing features of Bridges is buffered replay.

When buffering is enabled, logs at or below the configured minimum level are temporarily stored instead of immediately
emitted.

If the request later reaches the replay level, Bridges replays the buffered logs before emitting the triggering event.

Example:

```text
minLevel = Info
replayAllLogLevel = Warn
bufferBelowMinLevel = true
```

an unsampled request may produce:

```text
Debug -> buffered
Info -> buffered
Warn -> replay Debug + Info, then emit Warn
```

This allows verbose request context to be retained without emitting it for every request.

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
  BridgeLogger.lift[F](bridgeLogger)
```

This allows services parameterized over `F[_]` (with a `LiftIO` instance) to use the logger without depending directly
on the concrete implementation.

---

# Configuration

`BridgeLoggerConfig` controls runtime behavior, including:

- minLevel - minimum log level and the level where sampling occurs
- sampleRate - sampling rate
- duplicateEntriesOnBufferDump - allows replaying all logs up to the buffer dump. 
  - This can be useful for ordering in logs at the expense of duplicated entries for previously emitted logs.
- bufferBelowMinLevel - whether logs at or below the minimum level are buffered
- sampleBelowMinLevel - whether sampled requests continue logging below the minimum level
- replayAllLogLevel - log level that triggers buffered replay
- bufferSize - buffer size

The config can be set at logger creation, in the request scope, and at the individual log level


Individual Log Level (Highest priority)
Request level/thread level
Logger setup (Lowest priority)

There is also an abstract class implementing log levels which will implicitly pull
BridgeLoggerConfigs from the call location.

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
- OpenTelemetry and otel4s integration
    - TraceId
    - SpanId
    - baggage
- Improve sampling
    - adaptive sampling
    - ruleBased sampling