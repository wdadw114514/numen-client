package com.dwinovo.numen.task;
import com.dwinovo.numen.pathing.body.BodyView;
import com.dwinovo.numen.task.TaskResult;

/**
 * Placeholder for a tool whose executor hasn't been ported to the player body
 * yet. Fails with a clear message so the LLM gets a real answer instead of a
 * hang while the migration is in progress.
 */
@com.dwinovo.numen.api.Internal
public final class UnsupportedTask implements Task {

    private final String toolName;

    public UnsupportedTask(TaskRecord record) {
        this.toolName = record.getToolName();
    }

    @Override
    public void start(BodyView companion) {
        // nothing — fails on the first tick
    }

    @Override
    public TaskState tick(BodyView companion) {
        return TaskState.FAILED;
    }

    @Override
    public TaskResult result(TaskState finalState) {
        return TaskResult.fail(toolName + " is not available on the companion yet "
                + "(it's mid-migration to the new player body)");
    }

    @Override
    public void stop(BodyView companion, StopReason why) {
    }

    @Override
    public String name() {
        return "unsupported";
    }
}
