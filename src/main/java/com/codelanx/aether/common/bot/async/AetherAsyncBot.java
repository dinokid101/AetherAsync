package com.codelanx.aether.common.bot.async;

import com.codelanx.aether.common.CachedInventory;
import com.codelanx.aether.common.bot.async.mouse.ClickHandler;
import com.codelanx.aether.common.item.ItemLoader;
import com.codelanx.aether.common.recipe.RecipeLoader;
import com.codelanx.commons.util.Reflections;
import com.runemate.game.api.hybrid.Environment;
import com.runemate.game.api.script.framework.AbstractBot;

import java.io.File;
import java.util.Arrays;

public abstract class AetherAsyncBot extends AbstractBot {

    private static AetherAsyncBot instance;
    private AetherScheduler scheduler;
    private final AetherBrain brain;
    private final CachedInventory inventory;
    private final ItemLoader items;
    private final RecipeLoader recipes;

    public AetherAsyncBot() {
        instance = this;
        this.scheduler = new AetherScheduler(this);
        this.items = new ItemLoader(this);
        this.recipes = new RecipeLoader(this);
        this.inventory = new CachedInventory();

        this.brain = new AetherBrain(this);
    }

    @Override
    public final void run() {
        Environment.getLogger().info("#run");
        this.scheduler.register(this);
        Environment.getLogger().info("End of bot thread reached, gonna loop that shit instead");
        while (!this.scheduler.isShutdown()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Environment.getLogger().info("Exception while sleeping provided bot thread:");
                Environment.getLogger().info(Reflections.stackTraceToString(e));
            }
        }
    }

    public void loop() {
        //handle events here I suppose
        if (ClickHandler.hasTasks()) {
            return;
        }
        this.brain.loop();
    }

    //try to avoid
    public static AetherAsyncBot get() {
        return AetherAsyncBot.instance;
    }

    public AetherBrain getBrain() {
        return this.brain;
    }

    public CachedInventory getInventory() {
        return this.inventory;
    }

    @Override
    public void onStart(String... strings) {
        super.onStart(strings);
        Environment.getLogger().info("#onStart(" + Arrays.toString(strings) + ")");
    }

    @Override
    public void onStop() {
        Environment.getLogger().info("#onStop");
        super.onStop();
        this.scheduler.stop();
    }

    @Override
    public void onPause() {
        super.onPause();
        this.scheduler.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.scheduler.resume(this);
    }

    public File getResourcePath() {
        return new File("resources");
    }

    public AetherScheduler getScheduler() {
        return this.scheduler;
    }

    public RecipeLoader getRecipes() {
        return this.recipes;
    }

    public ItemLoader getKnownItems() {
        return this.items;
    }
}
