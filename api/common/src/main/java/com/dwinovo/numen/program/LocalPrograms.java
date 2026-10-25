package com.dwinovo.numen.program;

import com.dwinovo.numen.agent.script.Program;
import com.dwinovo.numen.agent.script.SerialExecutor;
import com.dwinovo.numen.pathing.body.BodyView;
import com.dwinovo.numen.sdk.Dispatcher;
import com.google.gson.JsonParser;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 客户端假人的本地程序执行器：Lua 跑在本机，服务端函数就地落到 {@link BodyView}（假人的身体），
 * 客户端函数就地执行，不经过网络。它是"服务器不装 mod 也能让 AI 动起来"的那一半。
 */
public final class LocalPrograms {

    private static final MainQueue QUEUE = new MainQueue();
    private static final ModuleCache CACHE = new ModuleCache(ProgramLimits.MODULE_CACHE_BYTES);
    private static final Executor EXEC = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "numen-local-program");
        thread.setDaemon(true);
        return thread;
    });

    private LocalPrograms() {}

    /** 每客户端刻调一次：把排着的服务端调用放行。 */
    public static void tick() {
        QUEUE.drainAll();
    }

    public static void run(BodyView her, UUID companion, UUID owner, String programId, String code,
                           CallObserver observer, Consumer<RunResult> done) {
        RunModules.Opened opened;
        try {
            opened = RunModules.open(owner, ModuleSet.factory(), CACHE);
        } catch (IllegalArgumentException wrong) {
            done.accept(RunResult.refused("a module text does not match its fingerprint: " + wrong.getMessage()));
            return;
        }
        if (!opened.missing().isEmpty()) {
            done.accept(new RunResult.Missing(opened.missing()));
            return;
        }
        MainQueue.Lane lane = QUEUE.lane();
        ClientCalls client = new ClientCalls(her, companion, lane, LocalTransport.INSTANCE, opened.modules(),
                () -> her.level().getGameTime());
        ProgramCalls calls = new ObservedCalls(new RoutedCalls(
                new ServerCalls(her, lane, id -> { }), client), observer);
        Program program = new Program(programId, code, new ProgramPort(her, opened.modules(), calls),
                new SerialExecutor(EXEC), outcome -> {
                    client.close();
                    lane.post(() -> {
                        observer.ended(outcome);
                        done.accept(new RunResult.Ended(outcome));
                    });
                    lane.close();
                });
        program.start();
    }

    /** 客户端函数就地答：不经过网络，也不等主人客户端。 */
    private static final class LocalTransport implements ClientTransport {

        static final LocalTransport INSTANCE = new LocalTransport();

        @Override
        public void request(BodyView her, com.dwinovo.numen.network.payload.ClientCallPayload request,
                            Consumer<Answer> done) {
            Dispatcher.client(request.function(), JsonParser.parseString(request.argumentsJson()),
                    request.entityUuid(), reply -> done.accept(new Answer(reply, null)));
        }

        @Override
        public void cancel(String callId) {
        }
    }
}
