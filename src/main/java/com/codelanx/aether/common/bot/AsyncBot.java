package com.codelanx.aether.common.bot;

import com.codelanx.aether.common.RunemateLoggerProxy;
import com.codelanx.aether.common.action.HMagic;
import com.codelanx.aether.common.bot.mission.Mission;
import com.codelanx.aether.common.bot.task.AetherTask;
import com.codelanx.aether.common.input.UserInput;
import com.codelanx.aether.common.cache.Caches;
import com.codelanx.aether.common.rest.RestLoader;
import com.codelanx.commons.logging.Debugger;
import com.codelanx.commons.logging.Logging;
import com.codelanx.commons.util.Readable;
import com.runemate.game.api.script.framework.AbstractBot;
import com.runemate.game.api.script.framework.tree.TreeBot;
import com.runemate.game.api.script.framework.tree.TreeTask;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AsyncBot extends AbstractBot {

    private AetherScheduler scheduler;
    private Brain brain;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private RestLoader data;
    private final List<Runnable> preExistingTasks = new LinkedList<>();

    public AsyncBot() {
        Aether.setBot(this);
    }

    public AsyncBot(TreeBot old) {
        this();
        //let's import the old treebot
        TreeTask task = old.createRootTask();
        this.preExistingTasks.add(() -> {
            this.getBrain().getLogicTree().register(Mission.of(AetherTask.of(task)));
        });
    }

    @Override
    public final void run() {
        Logging.info("#run");
        while (!this.scheduler.isShutdown()) {
            if (this.stopping.get()) {
                this.scheduler.stop();
            }
            if (!this.isPaused()) {
                Logging.fine("Lazily observed thread pool count: " + this.scheduler.getThreadPool().getActiveCount());
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Logging.info("Exception while sleeping provided bot thread:");
                Logging.info(Readable.stackTraceToString(e));
            }
        }
    }

    public void loop() {
        try {
            this.brain.loop();
        } catch (Throwable t) {
            Logging.log(Level.SEVERE, "Uncaught exception in bot loop", t);
            throw t;
        }
    }

    public Brain getBrain() {
        return this.brain;
    }

    public RestLoader getData() {
        return this.data;
    }

    @Override
    public final void onStart(String... strings) {
        super.onStart(strings);
        {
            //THIS IS ACTUALLY CONSTRUCTOR MATERIAL
            //but we can't modify loggers in constructors
            Logger l = new RunemateLoggerProxy(this.getLogger());
            Logging.setNab(() -> l);
            Debugger.DebugUtil.getOpts().setLogger(l);
            this.scheduler = new AetherScheduler(this);
            this.data = new RestLoader(this);
            this.data.loadLocal();
            this.brain = new Brain(this);
            HMagic.setInputSupplier(spell -> {
                UserInput.runemateInput(spell::activate);
                return true;
            });
        }
        Logging.info("#onStart(" + Arrays.toString(strings) + ")");
        this.preExistingTasks.forEach(Runnable::run);
        this.onBotStart(strings);
        this.scheduler.register(this);
    }

    @Override
    public final void onStop() {
        Logging.info("#onStop");
        this.stopping.set(true);
        this.onBotStop();
        this.scheduler.stop();
        this.brain.getLogicTree().clear();
        UserInput.wipe();
        Caches.invalidateAll();
        super.onStop();
    }

    //instead of being empty, we leave them as abstract to discourage autofillers from placing a supercall
    //a recurrant supercall to #onStart or similar is quite dangerous
    //thus the methods are forced to be filled in, and in many cases everything but #onBotStart may be blank
    //
    //additionally, it helps force authors to consider the different states of their bot
    public abstract void onBotStart(String... args);
    public abstract void onBotStop();
    public abstract void onBotPause();
    public abstract void onBotResume();

    @Override
    public final void onPause() {
        super.onPause();
        this.scheduler.pause();
        this.brain.getLogicTree().invalidate();
        UserInput.wipe();
        Caches.invalidateAll();
        this.onBotPause();
    }

    @Override
    public final void onResume() {
        super.onResume();
        this.onBotResume();
        this.scheduler.resume(this);
    }

    public File getResourcePath() {
        return new File("resources");
    }

    public AetherScheduler getScheduler() {
        return this.scheduler;
    }
}
