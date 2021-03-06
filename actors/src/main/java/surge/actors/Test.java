package surge.actors;

import surge.actors.actors.LocalActor;
import surge.actors.dispatchers.ExecutorServiceScheduler;
import surge.actors.receivers.MatchingReceiver.ReceiveMessageVoid;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class Test {

  public static Receiver testFactory() {
    return Receiver.builder()
        .match(String.class, (s, ctx) -> { })
        .build();
  }

  public static void main(final String[] args) {
    final ForkJoinPool executor = new ForkJoinPool(
        Runtime.getRuntime().availableProcessors(),
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        null,
        true
    );
    final Scheduler scheduler = new ExecutorServiceScheduler(executor,
        Executors.newSingleThreadScheduledExecutor());

    scheduler.schedule(() -> {
      System.out.println("Scheduler executes");
    });

    final Actor actor = LocalActor.factory(scheduler).spawn((context) -> {
      return Receiver.builder()
          .equals("askignore", (msg, ctx) -> {
            // Do nothing.
          })
          .equals("ask", (msg, ctx) -> {
            ctx.getSender().tell("ask reply", ctx.getSelf());
          })
          .match(String.class, (msg, ctx) -> {
            System.out.println("Main actor received: " + msg);

            for (int i = 0; i < 100; ++ i) {
              final String actorName = "child-" + i;
              final Actor childActor = ctx.spawn(() -> childActor(actorName, true), actorName);
              childActor.tell("Hello, World! (for child)", ctx.getSender());
            }

            final Actor crashingActor = ctx.spawn(Test::crashingActor, "crashing");
            crashingActor.tell("crash", ctx.getSelf());
            crashingActor.tell("Message after crash", ctx.getSelf());
          })
          .build();
    }, "root");

    actor.tell("Hello, World!", actor);
    actor.ask("ask", Duration.ofSeconds(10)).thenAccept(response -> {
      System.out.println("Ask response: " + response);
    });
    actor.ask("askignore", Duration.ofSeconds(5)).handle((obj, throwable) -> {
      System.out.println("Ask failed: " + throwable);
      return null;
    });

    try {
      Thread.sleep(Long.MAX_VALUE);
    } catch (Exception e) {
    }
  }

  public static Receiver childActor(final String name, final boolean startChild) {
    return Receiver.builder()
        .match(String.class, (msg, ctx) -> {
          if (startChild) {
            final String actorName = name + "-0";
            final Actor childActor = ctx.spawn(() -> childActor(actorName, false), actorName);
            childActor.tell("Hello, World! (for child of child)", ctx.getSelf());
          }
          Thread.sleep((long)(Math.random() * 500));
          System.out.println(Thread.currentThread().getId() + ": Child actor " + ctx.getSelf() + " received: " + msg);

        })
        .build();
  }

  public static Receiver crashingActor() {
    return Receiver.builder()
        .match(String.class, (ReceiveMessageVoid) (msg, ctx) -> {
          if (msg.equals("crash")) {
            throw new RuntimeException("Fail!");
          } else {
            System.out.println("Crashing actor received: " + msg);
          }
        })
        .beforeRestart(ctx -> System.out.println("Before restart"))
        .afterRestart(ctx -> System.out.println("After restart"))
        .build();
  }

  public interface Bla {
    private void bla() {
    }
  }
}
