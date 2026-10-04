package fr.nivcoo.utilsz.platform.bukkit.reward;

import fr.nivcoo.utilsz.platform.bukkit.reward.type.RewardTypeId;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class RewardDeliveryTest {
    private enum TestType implements RewardTypeId {MISSING, PACK, CUSTOM}

    private final RewardExecutionContext context = RewardExecutionContext.offline(UUID.randomUUID());

    @Test
    void preflightOfLaterActionPreventsEarlierMutation() {
        try (MockedStatic<Bukkit> bukkit = serverThread()) {
            AtomicInteger mutations = new AtomicInteger();
            AtomicInteger dirty = new AtomicInteger();
            RewardAction first = action(() -> {
                mutations.incrementAndGet();
                return RewardAction.DeliveryResult.success();
            });
            RewardAction missing = new RewardAction(TestType.MISSING, List.of(),
                    ignored -> RewardAction.CheckResult.failure("unavailable"),
                    ignored -> {
                        fail("Unavailable action must not execute");
                        return null;
                    });
            RewardDelivery.DeliveryReport report = delivery().deliver(List.of(first, missing), context, () -> true, dirty::incrementAndGet);
            assertFalse(report.success());
            assertFalse(report.attempted());
            assertEquals(0, mutations.get());
            assertEquals(0, dirty.get());
        }
    }

    @Test
    void guardStopsBetweenStepsAndConsumedPlanCannotReplay() {
        try (MockedStatic<Bukkit> bukkit = serverThread()) {
            AtomicInteger mutations = new AtomicInteger();
            AtomicInteger dirty = new AtomicInteger();
            RewardStep step = new RewardStep(RewardAction.CheckResult::ready,
                    () -> {
                        mutations.incrementAndGet();
                        return RewardAction.DeliveryResult.success();
                    });
            RewardAction reward = new RewardAction(TestType.PACK, List.of(), ignored -> List.of(step, step));
            RewardDelivery delivery = delivery();
            RewardDelivery.PreparedRewards plan = delivery.prepare(List.of(reward), context);
            RewardDelivery.DeliveryReport report = delivery.deliver(plan, () -> mutations.get() == 0, dirty::incrementAndGet);
            assertFalse(report.success());
            assertTrue(report.partial());
            assertTrue(report.attempted());
            assertFalse(delivery.deliver(plan, () -> true, dirty::incrementAndGet).success());
            assertEquals(1, mutations.get());
            assertEquals(1, dirty.get());
        }
    }

    @Test
    void exceptionAfterMutationIsUncertainAndStillMarksDirty() {
        try (MockedStatic<Bukkit> bukkit = serverThread()) {
            AtomicInteger mutations = new AtomicInteger();
            AtomicInteger dirty = new AtomicInteger();
            RewardAction reward = action(() -> {
                mutations.incrementAndGet();
                throw new IllegalStateException("after mutation");
            });
            RewardDelivery.DeliveryReport report = delivery().deliver(List.of(reward), context, () -> true, dirty::incrementAndGet);
            assertFalse(report.success());
            assertTrue(report.partial());
            assertTrue(report.attempted());
            assertEquals(1, mutations.get());
            assertEquals(1, dirty.get());
        }
    }

    @Test
    void continuePolicyKeepsIndependentActionsAfterCleanFailure() {
        try (MockedStatic<Bukkit> bukkit = serverThread()) {
            AtomicInteger mutations = new AtomicInteger();
            AtomicInteger dirty = new AtomicInteger();
            RewardAction failed = action(() -> RewardAction.DeliveryResult.failure("rejected"));
            RewardAction next = action(() -> {
                mutations.incrementAndGet();
                return RewardAction.DeliveryResult.success();
            });
            RewardDelivery.DeliveryReport report = new RewardDelivery(logger(), RewardDelivery.FailurePolicy.CONTINUE)
                    .deliver(List.of(failed, next), context, () -> true, dirty::incrementAndGet);
            assertFalse(report.success());
            assertTrue(report.partial());
            assertEquals(1, report.completedActions());
            assertEquals(1, mutations.get());
            assertEquals(1, dirty.get());
        }
    }

    private static RewardAction action(Supplier<RewardAction.DeliveryResult> delivery) {
        return new RewardAction(TestType.CUSTOM, List.of(), ignored -> RewardAction.CheckResult.ready(), ignored -> delivery.get());
    }

    private static MockedStatic<Bukkit> serverThread() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        return bukkit;
    }

    private static RewardDelivery delivery() {
        return new RewardDelivery(logger());
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }
}
