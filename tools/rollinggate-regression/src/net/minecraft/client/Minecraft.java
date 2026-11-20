package net.minecraft.client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Test double: never include in a mod JAR or production source set. */
public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    private final Thread mainThread = Thread.currentThread();
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    public static Minecraft getInstance() { return INSTANCE; }
    public boolean isSameThread() { return Thread.currentThread() == mainThread; }
    public void execute(Runnable task) {
        if (isSameThread()) task.run(); else tasks.add(task);
    }
    public void runQueued() {
        if (!isSameThread()) throw new AssertionError("Not on main thread");
        Runnable task;
        while ((task = tasks.poll()) != null) task.run();
    }
}
