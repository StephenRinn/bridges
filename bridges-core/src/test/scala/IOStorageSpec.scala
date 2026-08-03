import cats.effect.IOLocal
import contextStorage.IOStorage
import munit.CatsEffectSuite

class IOStorageSpec extends CatsEffectSuite {
  test("fiber local state is isolated") {
    for {
      storage <- IOLocal(IOStorage.empty)

      fiber1 <-
        (storage.set(
          IOStorage.empty.copy(
            requestId = "request1",
            correlationId = "correlation1",
            values = Map(),
            startTime = Some(25L),
            endTime = Some(30L),
          ),
        ) >> storage.get).start

      fiber2 <- (storage.set(
        IOStorage.empty.copy(
          requestId = "request2",
          correlationId = "correlation2",
          values = Map(),
          startTime = Some(26L),
          endTime = Some(31L),
        ),
      ) >> storage.get).start

      state1 <- fiber1.joinWithNever
      state2 <- fiber2.joinWithNever
    } yield {
      assertNotEquals(state1, state2)
    }

  }
}
