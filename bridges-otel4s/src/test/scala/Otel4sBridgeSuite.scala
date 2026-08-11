import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.otel4s.sdk.testkit.OpenTelemetrySdkTestkit

class Otel4sBridgeSuite extends CatsEffectSuite {

  test("current returns nothing with no active span") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        bridge = new Otel4sBridge(tracer)
        ctx <- bridge.attributes
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
          bridge.attributes.map { context =>
            (context, span.context)
          }
        }
      } yield {
        val (context, spanContext) = result
        assert(context.nonEmpty)
        assertEquals(context("traceid").toString, spanContext.traceId.toString())
        assertEquals(context("spanid").toString, spanContext.spanId.toString())
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
              parentContext <- bridge.attributes
              childContext <- IO
                .defer(bridge.attributes)
                .start
                .flatMap(_.joinWithNever)
            } yield (parentContext, childContext, span.context)
          }
        }
      } yield {
        val (parentContext, childContext, spanContext) = result
        assert(parentContext.nonEmpty)
        assert(childContext.nonEmpty)
        assertEquals(parentContext("traceid").toString, spanContext.traceId.toString)
        assertEquals(parentContext("spanid").toString, spanContext.spanId.toString)
        assertEquals(childContext("traceid").toString, spanContext.traceId.toString)
        assertEquals(childContext("spanid").toString, spanContext.spanId.toString)
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
              parentContext <- bridge.attributes
              nestedResult <- tracer.span("child-span").use { childSpan =>
                bridge.attributes.map { childContext =>
                  (childContext, childSpan.context)
                }
              }
            } yield (parentContext, nestedResult, parentSpan.context)
          }
        }
      } yield {
        val (parentContext, (childContext, childSpanContext), parentSpanContext) = result
        assert(parentContext.nonEmpty)
        assert(childContext.nonEmpty)
        assertEquals(parentContext("spanid").toString, parentSpanContext.spanId.toString)
        assertEquals(childContext("traceid").toString, childSpanContext.traceId.toString)
        assertEquals(childContext("spanid").toString, childSpanContext.spanId.toString)
        assert(childContext("spanid").toString != parentContext("spanid").toString)
        assertEquals(childContext("traceid").toString, parentContext("traceid").toString)
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
              beforeChild <- bridge.attributes
              duringChild <- tracer.span("child-span").use { _ => bridge.attributes }
              afterChild <- bridge.attributes
            } yield (beforeChild, duringChild, afterChild, parentSpan.context)
          }
        }
      } yield {
        val (beforeChild, duringChild, afterChild, parentSpanContext) = result
        assert(beforeChild.nonEmpty)
        assert(duringChild.nonEmpty)
        assert(afterChild.nonEmpty)
        assertEquals(beforeChild("spanid").toString, parentSpanContext.spanId.toString)
        assertEquals(afterChild("spanid").toString, parentSpanContext.spanId.toString)
        assertNotEquals(duringChild("spanid").toString, parentSpanContext.spanId.toString)
        assertEquals(beforeChild("traceid").toString, afterChild("traceid").toString)
        assertEquals(beforeChild, afterChild)
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
                bridge.attributes.map(ctx => (ctx, spanA.context))
              }
              .start

            fiberB <- tracer
              .span("span-b")
              .use { spanB =>
                bridge.attributes.map(ctx => (ctx, spanB.context))
              }
              .start

            resultA <- fiberA.joinWithNever
            resultB <- fiberB.joinWithNever
          } yield (resultA, resultB)
        }
      } yield {
        val ((contextA, spanA), (contextB, spanB)) = result
        assertEquals(contextA("spanid").toString, spanA.spanId.toString)
        assertEquals(contextB("spanid").toString, spanB.spanId.toString)
        assertNotEquals(contextA("spanid").toString, contextB("spanid").toString)
      }
    }
  }

  test("current returns None after the active span ends") {
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      for {
        tracer <- testkit.tracerProvider.get("bridges-test")
        bridge = new Otel4sBridge(tracer)
        result <- for {
          before <- bridge.attributes
          during <- tracer.span("test-span").use { _ =>
            bridge.attributes
          }
          after <- bridge.attributes
        } yield (before, during, after)
      } yield {
        val (before, during, after) = result
        assert(before.isEmpty)
        assert(during.nonEmpty)
        assert(after.isEmpty)
      }
    }
  }
}
