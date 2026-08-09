import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.otel4s.sdk.testkit.OpenTelemetrySdkTestkit

class Otel4sBridgeSuite extends CatsEffectSuite {

  test("current returns nothing with no active span") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        bridge = new Otel4sBridge(tracer)
        ctx <- bridge.current
      } yield {
        assert(ctx.isEmpty)
      }
    }
  }

  test("Current returns the active spans and span ids") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        bridge = new Otel4sBridge(tracer)
        result <- tracer.span("test-span").use { span =>
          bridge.current.map { context =>
            (context, span.context)
          }
        }
      } yield {
        val (context, spanContext) = result
        assert(context.isDefined)
        assertEquals(context.get.traceId.get, spanContext.traceId.toString())
        assertEquals(context.get.spanId.get, spanContext.spanId.toString())
      }
    }
  }

  test("current preserves the active span context across fibers") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        result <- {
          val bridge = new Otel4sBridge(tracer)
          tracer.span("parent-span").use { span =>
            for {
              parentContext <- bridge.current
              childContext <- IO
                .defer(bridge.current)
                .start
                .flatMap(_.joinWithNever)
            } yield (parentContext, childContext, span.context)
          }
        }
      } yield {
        val (parentContext, childContext, spanContext) = result
        assert(parentContext.isDefined)
        assert(childContext.isDefined)
        assertEquals(parentContext.get.traceId, Some(spanContext.traceId.toString))
        assertEquals(parentContext.get.spanId, Some(spanContext.spanId.toString))
        assertEquals(childContext.get.traceId, Some(spanContext.traceId.toString))
        assertEquals(childContext.get.spanId, Some(spanContext.spanId.toString))
        assertEquals(childContext, parentContext)
      }
    }
  }

  test("current returns the nested span context") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        result <- {
          val bridge = new Otel4sBridge(tracer)
          tracer.span("parent-span").use { parentSpan =>
            for {
              parentContext <- bridge.current
              nestedResult <- tracer.span("child-span").use { childSpan =>
                bridge.current.map { childContext =>
                  (childContext, childSpan.context)
                }
              }
            } yield (parentContext, nestedResult, parentSpan.context)
          }
        }
      } yield {
        val (parentContext, (childContext, childSpanContext), parentSpanContext) = result
        assert(parentContext.isDefined)
        assert(childContext.isDefined)
        assertEquals(parentContext.get.spanId, Some(parentSpanContext.spanId.toString))
        assertEquals(childContext.get.traceId, Some(childSpanContext.traceId.toString))
        assertEquals(childContext.get.spanId, Some(childSpanContext.spanId.toString))
        assert(childContext.get.spanId != parentContext.get.spanId)
        assertEquals(childContext.get.traceId, parentContext.get.traceId)
      }
    }
  }
  test("current restores the parent span context after a nested span ends") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        result <- {
          val bridge = new Otel4sBridge(tracer)
          tracer.span("parent-span").use { parentSpan =>
            for {
              beforeChild <- bridge.current
              duringChild <- tracer.span("child-span").use { _ => bridge.current }
              afterChild <- bridge.current
            } yield (beforeChild, duringChild, afterChild, parentSpan.context)
          }
        }
      } yield {
        val (beforeChild, duringChild, afterChild, parentSpanContext) = result
        assert(beforeChild.isDefined)
        assert(duringChild.isDefined)
        assert(afterChild.isDefined)
        assertEquals(beforeChild.get.spanId, Some(parentSpanContext.spanId.toString))
        assertEquals(afterChild.get.spanId, Some(parentSpanContext.spanId.toString))
        assertNotEquals(duringChild.get.spanId.get, parentSpanContext.spanId.toString)
        assertEquals(beforeChild.get.traceId, afterChild.get.traceId)
        assertEquals(beforeChild.get, afterChild.get)
      }
    }
  }

  test("span context is isolated between fibers") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        result <- {
          val bridge = new Otel4sBridge(tracer)
          for {
            fiberA <- tracer
              .span("span-a")
              .use { spanA =>
                bridge.current.map(ctx => (ctx, spanA.context))
              }
              .start

            fiberB <- tracer
              .span("span-b")
              .use { spanB =>
                bridge.current.map(ctx => (ctx, spanB.context))
              }
              .start

            resultA <- fiberA.joinWithNever
            resultB <- fiberB.joinWithNever
          } yield (resultA, resultB)
        }
      } yield {
        val ((contextA, spanA), (contextB, spanB)) = result
        assertEquals(contextA.get.spanId, Some(spanA.spanId.toString))
        assertEquals(contextB.get.spanId, Some(spanB.spanId.toString))
        assertNotEquals(contextA.get.spanId, contextB.get.spanId)
      }
    }
  }

  test("current returns None after the active span ends") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        bridge = new Otel4sBridge(tracer)
        result <- for {
          before <- bridge.current
          during <- tracer.span("test-span").use { _ =>
            bridge.current
          }
          after <- bridge.current
        } yield (before, during, after)
      } yield {
        val (before, during, after) = result
        assert(before.isEmpty)
        assert(during.isDefined)
        assert(after.isEmpty)
      }
    }
  }
}
