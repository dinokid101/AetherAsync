package com.codelanx.aether.common.input;

import com.codelanx.aether.common.Randomization;
import com.codelanx.aether.common.input.type.CombatTarget;
import com.codelanx.aether.common.input.type.KeyboardTarget;
import com.codelanx.aether.common.input.type.MouseTarget;
import com.codelanx.aether.common.input.type.RunemateTarget;
import com.codelanx.commons.logging.Logging;
import com.codelanx.commons.util.Reflections;
import com.runemate.game.api.hybrid.entities.details.Interactable;
import com.runemate.game.api.hybrid.input.Mouse;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public enum UserInput {

    INSTANCE,
    ;

    private static final long MIN_CLICK_MS = 100;
    private static final long TASK_SWITCH_DELAY = 300;
    private final List<InputTarget> queue = new LinkedList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong lastInputMs = new AtomicLong(); //last successfully entered input
    private final AtomicLong lastInputTargetMs = new AtomicLong(); //last ms mark for input (can be in future)
    private Class<? extends InputTarget> lastInputType = null;

    public void registerClick(Interactable obj) {
        obj.click();
    }

    private void actOnTarget(InputTarget target, boolean hover) {
        long delay = System.currentTimeMillis() - this.lastInputMs.get();
        if (!hover && this.lastInputType != target.getClass()) {
            //we've got an input type switch
            Randomization r = Randomization.TASK_SWITCHING_DELAY;
            if (delay <= (TASK_SWITCH_DELAY + r.getRandom(t -> t.nextInt(r.getValue().intValue())).intValue())) {
                return;
            }
        }
        if (target instanceof MouseTarget) {
            MouseTarget mouse = (MouseTarget) target;
            //TODO: Move off bot thread
            if (hover) {
                mouse.hover();
            } else if (delay > UserInput.getMinimumClick()) {
                mouse.attempt();
            }
        } else if (!hover && !target.isAttempting()) {
            target.attempt();
        }
    }

    public InputTarget getNextTarget() {
        return this.getNextTarget(0);
    }

    public InputTarget getNextTarget(int offset) {
        return null;// INSTANCE.lock.read(() -> this.queue.size() <= offset ? null : this.queue.get(offset));
    }

    // -=- bot methods

    public static boolean attempt() {
        //Logging.info("Running user input...");
        InputTarget target = INSTANCE.getNextTarget();
        if (target == null) {
            Logging.info("Null next target");
            return false;
        }
        if (target.isAttempted()) {
            //hover secondary target
            if (!target.isSuccessful()) {
                Logging.info("Re-attempting input");
                target.attempt(); //immediate re-attempt
                return true;
            }
            Logging.info("Input successful");
            INSTANCE.lastInputMs.set(System.currentTimeMillis());
            InputTarget next = INSTANCE.getNextTarget(1);
            if (next != null) {
                Logging.info("Hovering next input...");
                INSTANCE.actOnTarget(target, true);
            }
            INSTANCE.lastInputType = target.getClass();
            INSTANCE.queue.remove(0);
        } else if (!target.isAttempting()) {
            INSTANCE.actOnTarget(target, false);
        }
        return true;
    }

    public static long getMinimumClick() {
        double mult = 1 / Mouse.getSpeedMultiplier();
        return (long) ((MIN_CLICK_MS + Randomization.MIN_CLICK.getValue().longValue()) * mult);
    }

    public static long getInputIntervalDelay(Class<? extends InputTarget> targetType) {
        return 0L;//INSTANCE.getInterval(targetType);
    }

    public static boolean hasTasks() {
        return Reflections.operateLock(INSTANCE.lock.readLock(), () -> {
            return !INSTANCE.queue.isEmpty();
        });
    }

    public static void wipe() {
        Reflections.operateLock(INSTANCE.lock.writeLock(), INSTANCE.queue::clear);
    }

    // -=- input methods

    //hmmmmm
    public static RunemateTarget runemateInput(Supplier<Boolean> inputter) {
        return UserInput.runemateInput(null, inputter);
    }

    public static RunemateTarget runemateInput(String debugDescription, Supplier<Boolean> inputter) {
        RunemateTarget tar = new RunemateTarget(debugDescription, inputter);
        Reflections.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(tar);
        });
        return tar;
    }

    public static MouseTarget interact(Interactable obj, String value) {
        MouseTarget back = new MouseTarget(obj, value);
        Reflections.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

    public static CombatTarget combat(Interactable obj) {
        CombatTarget back = new CombatTarget(obj);
        Reflections.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

    public static MouseTarget click(Interactable obj) {
        MouseTarget back = new MouseTarget(obj);
        Reflections.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

    public static KeyboardTarget type(String input, boolean enter) {
        KeyboardTarget back = new KeyboardTarget(input, enter);
        Reflections.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

}
