package com.codelanx.aether.common.input;

import com.codelanx.aether.common.Randomization;
import com.codelanx.aether.common.input.type.CombatTarget;
import com.codelanx.aether.common.input.type.KeyboardTarget;
import com.codelanx.aether.common.input.type.MouseTarget;
import com.codelanx.aether.common.input.type.RunemateTarget;
import com.codelanx.commons.logging.Logging;
import com.codelanx.commons.util.Parallel;
import com.runemate.game.api.hybrid.entities.details.Interactable;
import com.runemate.game.api.hybrid.input.Mouse;
import com.runemate.game.api.hybrid.local.hud.InteractablePoint;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

//holds the old UserInput code
public enum UserInput2 {

    INSTANCE,
    ;

    private static final long MIN_CLICK_MS = 100;
    private static final long TASK_SWITCH_DELAY = 300;
    private final List<InputTarget> queue = new LinkedList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong lastInputMs = new AtomicLong();
    private Class<? extends InputTarget> lastInputType = null;

    public void registerClick(Interactable obj) {
        obj.click();
    }

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

    //hmmmmm
    public static RunemateTarget runemateInput(Supplier<Boolean> inputter) {
        return UserInput.runemateInput(null, inputter);
    }

    public static RunemateTarget runemateInput(String debugDescription, Supplier<Boolean> inputter) {
        RunemateTarget tar = new RunemateTarget(debugDescription, inputter);
        Parallel.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(tar);
        });
        return tar;
    }

    //time between two different inputs
    private long getInterval(Class<? extends InputTarget> targetType) {
        long delay = System.currentTimeMillis() - this.lastInputMs.get();
        /*if (this.lastInputType != targetType) {
            //we've got an input type switch
            Randomization r = Randomization.TASK_SWITCHING_DELAY;
            return (TASK_SWITCH_DELAY + r.getRandom(t -> t.nextInt(r.getValue().intValue())).intValue());
        }*/
        return 0;
    }

    public static long getInputIntervalDelay(Class<? extends InputTarget> targetType) {
        return INSTANCE.getInterval(targetType);
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
                if (mouse.getEntity().isVisible()) {
                    //precise hover
                    Mouse.move(mouse.getEntity());
                } else {
                    //let's kick the radius up via a rough npc oval
                    //get our raw interaction point
                    InteractablePoint point = mouse.getEntity().getInteractionPoint();
                    //TODO:
                    //InteractablePoint center;
                    //if we were able to get the center point here, it'd be GREAT.
                    //but alas we can't, so we resort to dirty dirty hacks
                    //
                    //get a hint as to a movement from our correct point to another correct point
                    InteractablePoint hint = mouse.getEntity().getInteractionPoint(point);
                    //now we reflect it:
                    hint.setLocation(
                            point.getX() + (point.getX() - hint.getX()),
                            point.getY() + (point.getY() - hint.getY()));
                    //and now move to our incorrect location
                    Mouse.move(hint);
                }
            } else {
                if (delay > UserInput.getMinimumClick()) {
                    mouse.attempt();
                }
                /*
                if (input.getType() == ClickType.SIMPLE) {
                    //schedule short
                } else {
                    //slightly longer scheduling
                }*/
            }
        } else if (!hover && !target.isAttempting()) {
            target.attempt();
        }
    }

    public static MouseTarget interact(Interactable obj, String value) {
        MouseTarget back = new MouseTarget(obj, value);
        Parallel.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

    public static CombatTarget combat(Interactable obj) {
        CombatTarget back = new CombatTarget(obj);
        Parallel.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

    public static MouseTarget click(Interactable obj) {
        MouseTarget back = new MouseTarget(obj);
        Parallel.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

    public static KeyboardTarget type(String input, boolean enter) {
        KeyboardTarget back = new KeyboardTarget(input, enter);
        Parallel.operateLock(INSTANCE.lock.writeLock(), () -> {
            INSTANCE.queue.add(back);
        });
        return back;
    }

    public static boolean hasTasks() {
        return Parallel.operateLock(INSTANCE.lock.readLock(), () -> {
            return !INSTANCE.queue.isEmpty();
        });
    }

    public static void wipe() {
        Parallel.operateLock(INSTANCE.lock.writeLock(), INSTANCE.queue::clear);
    }

    private InputTarget getNextTarget() {
        return this.getNextTarget(0);
    }

    private InputTarget getNextTarget(int offset) {
        return Parallel.operateLock(INSTANCE.lock.readLock(), () -> {
            return this.queue.size() <= offset ? null : this.queue.get(offset);
        });
    }
}
