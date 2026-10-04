package fr.nivcoo.utilsz.platform.bukkit.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public final class RewardDelivery {
    private final Logger logger;
    private final FailurePolicy policy;

    public RewardDelivery(Logger logger) {
        this(logger, FailurePolicy.STOP);
    }

    public RewardDelivery(Logger logger, FailurePolicy policy) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public PreparedRewards prepare(List<RewardAction> actions, RewardExecutionContext context) {
        requireServerThread();
        List<PreparedAction> prepared = new ArrayList<>(actions.size());
        CheckReport check = CheckReport.ready();
        for (int index = 0; index < actions.size(); index++) {
            RewardAction action = actions.get(index);
            List<RewardStep> steps = List.of();
            RewardAction.CheckResult result;
            try {
                steps = action.prepare(context);
                result = check(steps);
            } catch (RuntimeException | LinkageError error) {
                result = RewardAction.CheckResult.failure(RewardAction.message(error));
                logger.log(Level.WARNING, "Could not prepare reward action " + action.type()
                        + " for " + context.playerId() + '.', error);
            }
            prepared.add(new PreparedAction(action.type().name(), steps, result));
            if (check.allowed() && !result.allowed())
                check = CheckReport.failure(index, action.type().name(), result.reason());
        }
        return new PreparedRewards(context, prepared, check);
    }

    public DeliveryReport deliver(List<RewardAction> actions, RewardExecutionContext context,
                                  BooleanSupplier guard, Runnable markDirty) {
        return deliver(prepare(actions, context), guard, markDirty);
    }

    public DeliveryReport deliver(PreparedRewards prepared, BooleanSupplier guard, Runnable markDirty) {
        requireServerThread();
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(markDirty, "markDirty");
        if (prepared.used)
            return new DeliveryReport(false, prepared.attempted, 0, prepared.actions.size(), "",
                    prepared.attempted, "Prepared rewards have already been consumed.");
        prepared.used = true;
        DeliveryReport result = new DeliveryReport(false, false, 0, prepared.actions.size(), "", false, "Delivery failed.");
        try {
            result = execute(prepared, guard);
        } catch (RuntimeException | LinkageError error) {
            result = new DeliveryReport(false, prepared.attempted, 0, prepared.actions.size(), "",
                    prepared.attempted, RewardAction.message(error));
            logger.log(Level.SEVERE, "Reward delivery failed for " + prepared.context.playerId() + '.', error);
        } finally {
            if (prepared.attempted) {
                try {
                    markDirty.run();
                } catch (RuntimeException | LinkageError error) {
                    logger.log(Level.SEVERE, "Reward persistence failed for " + prepared.context.playerId() + '.', error);
                    result = new DeliveryReport(false, true, result.completedActions(), result.totalActions(),
                            result.failedType().isEmpty() ? "PERSISTENCE" : result.failedType(), true, RewardAction.message(error));
                }
            }
        }
        return result;
    }

    private DeliveryReport execute(PreparedRewards prepared, BooleanSupplier guard) {
        int total = prepared.actions.size();
        CheckReport initial = preflight(prepared);
        if (!initial.allowed() && policy == FailurePolicy.STOP)
            return new DeliveryReport(false, false, 0, total, initial.actionType(), false, initial.reason());
        boolean attempted = false;
        boolean success = true;
        boolean partialWithinAction = false;
        int completed = 0;
        String failedType = "";
        String reason = "";
        for (PreparedAction action : prepared.actions) {
            RewardAction.CheckResult available = action.check().allowed() ? check(action.steps()) : action.check();
            if (!available.allowed()) {
                success = false;
                if (failedType.isEmpty()) {
                    failedType = action.type();
                    reason = available.reason();
                }
                if (policy == FailurePolicy.STOP) break;
                continue;
            }
            int completedSteps = 0;
            boolean actionComplete = true;
            for (RewardStep step : action.steps()) {
                try {
                    if (!current(prepared.context) || !guard.getAsBoolean())
                        return new DeliveryReport(false, attempted, completed, total, action.type(),
                                partialWithinAction || completedSteps > 0, "Player session or reward guard is not ready.");
                } catch (RuntimeException | LinkageError error) {
                    return new DeliveryReport(false, attempted, completed, total, action.type(),
                            partialWithinAction || completedSteps > 0, RewardAction.message(error));
                }
                RewardAction.CheckResult ready = check(List.of(step));
                if (!ready.allowed()) {
                    success = false;
                    actionComplete = false;
                    partialWithinAction |= completedSteps > 0;
                    if (failedType.isEmpty()) {
                        failedType = action.type();
                        reason = ready.reason();
                    }
                    if (policy == FailurePolicy.STOP) break;
                    continue;
                }
                attempted = true;
                prepared.attempted = true;
                RewardAction.DeliveryResult outcome;
                try {
                    outcome = step.delivery().get();
                    if (outcome == null)
                        outcome = RewardAction.DeliveryResult.partialFailure("Delivery returned no result.");
                } catch (RuntimeException | LinkageError error) {
                    outcome = RewardAction.DeliveryResult.partialFailure(RewardAction.message(error));
                    logger.log(Level.SEVERE, "Reward action " + action.type() + " failed for "
                            + prepared.context.playerId() + "; it will not be retried.", error);
                }
                if (outcome.delivered()) {
                    completedSteps++;
                } else {
                    success = false;
                    actionComplete = false;
                    partialWithinAction |= outcome.partial() || completedSteps > 0;
                    if (failedType.isEmpty()) {
                        failedType = action.type();
                        reason = outcome.reason();
                    }
                    if (policy == FailurePolicy.STOP) break;
                }
            }
            if (actionComplete) completed++;
            if (!actionComplete && policy == FailurePolicy.STOP) break;
        }
        return new DeliveryReport(success, attempted, completed, total, failedType, partialWithinAction, reason);
    }

    private static CheckReport preflight(PreparedRewards prepared) {
        for (int index = 0; index < prepared.actions.size(); index++) {
            PreparedAction action = prepared.actions.get(index);
            RewardAction.CheckResult result = action.check().allowed() ? check(action.steps()) : action.check();
            if (!result.allowed()) return CheckReport.failure(index, action.type(), result.reason());
        }
        return CheckReport.ready();
    }

    private static RewardAction.CheckResult check(List<RewardStep> steps) {
        for (RewardStep step : steps) {
            try {
                RewardAction.CheckResult result = step.preflight().get();
                if (result == null) return RewardAction.CheckResult.failure("Preflight returned no result.");
                if (!result.allowed()) return result;
            } catch (RuntimeException | LinkageError error) {
                return RewardAction.CheckResult.failure(RewardAction.message(error));
            }
        }
        return RewardAction.CheckResult.ready();
    }

    private static boolean current(RewardExecutionContext context) {
        Player player = context.player();
        return player == null || player.isOnline() && Bukkit.getPlayer(context.playerId()) == player;
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Reward preparation and delivery require the server thread.");
    }

    public enum FailurePolicy {STOP, CONTINUE}

    public static final class PreparedRewards {
        private final RewardExecutionContext context;
        private final List<PreparedAction> actions;
        private final CheckReport check;
        private boolean used;
        private boolean attempted;

        private PreparedRewards(RewardExecutionContext context, List<PreparedAction> actions, CheckReport check) {
            this.context = context;
            this.actions = List.copyOf(actions);
            this.check = check;
        }

        public CheckReport check() {
            return check;
        }
    }

    public record CheckReport(boolean allowed, int actionIndex, String actionType, String reason) {
        public static CheckReport ready() {
            return new CheckReport(true, -1, "", "");
        }

        public static CheckReport failure(int index, String type, String reason) {
            return new CheckReport(false, index, type, reason == null ? "" : reason);
        }
    }

    public record DeliveryReport(boolean success, boolean attempted, int completedActions, int totalActions,
                                 String failedType, boolean partialWithinAction, String reason) {
        public boolean partial() {
            return !success && (completedActions > 0 || partialWithinAction);
        }
    }

    private record PreparedAction(String type, List<RewardStep> steps, RewardAction.CheckResult check) {
    }
}
