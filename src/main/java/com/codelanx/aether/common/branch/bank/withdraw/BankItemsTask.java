package com.codelanx.aether.common.branch.bank.withdraw;

import com.codelanx.aether.common.Common.Banks;
import com.codelanx.aether.common.bot.Invalidators;
import com.codelanx.aether.common.bot.task.AetherTask;
import com.codelanx.aether.common.cache.Caches;
import com.codelanx.aether.common.cache.form.ContainerCache;
import com.codelanx.aether.common.input.UserInput;
import com.codelanx.aether.common.json.item.ItemStack;
import com.codelanx.aether.common.json.item.Material;
import com.codelanx.aether.common.json.item.Materials;
import com.runemate.game.api.hybrid.local.hud.interfaces.Bank;
import com.runemate.game.api.hybrid.local.hud.interfaces.SpriteItem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BankItemsTask extends AetherTask<ItemStack> {

    private static final ItemStack DEPOSIT_ALL = new ItemStack(null, Integer.MIN_VALUE);
    private final Map<Material, Integer> itemCache = new HashMap<>(); //quick item lookups
    private final Set<ItemStack> items = new TreeSet<>((i1, i2) -> {
        if (i1.isStackable() ^ i2.isStackable()) {
            return i1.isStackable() ? 1 : -1;
        } else if (i1.isStackable() && i2.isStackable()) {
            return 0;
        }
        return Integer.compare(i1.getQuantity(), i2.getQuantity());
    });

    public BankItemsTask(List<ItemStack> stacks) {
        this.items.addAll(stacks);
        stacks.forEach(i -> this.itemCache.put(i.getMaterial(), i.getQuantity()));
        this.register(Objects::isNull, () -> {
            UserInput.runemateInput(Bank::close);
            return Invalidators.ALL;
        });
        this.register(item -> item.getQuantity() < 0, item -> {
            UserInput.runemateInput(() -> Banks.depositItem(item.setQuantity(-item.getQuantity())));
        });
        this.registerDefault(item -> {
            UserInput.runemateInput(() -> Banks.withdrawItem(item));
            return Invalidators.SELF;
        });
    }

    @Override
    public Supplier<ItemStack> getStateNow() {
        return () -> {
            //check inventory contents first
            Map<Material, List<SpriteItem>> current = Caches.forInventory().getAll().collect(Collectors.groupingBy(Materials::getMaterial));
            int beforeSize = current.size();
            current.entrySet().removeIf(ent -> {
                return ContainerCache.count(ent.getValue().stream()) < this.itemCache.getOrDefault(ent.getKey(), 0);
            });
            if (!current.isEmpty()) {
                if (beforeSize >> 1 < current.size()) {
                    return DEPOSIT_ALL;
                }
                Material m = current.keySet().iterator().next();
                //return a deposited item
                return new ItemStack(m, -ContainerCache.count(current.get(m).stream()));
            }
            //check what needs withdrawing
            Iterator<ItemStack> stacks = this.items.iterator();
            while (stacks.hasNext()) {
                ItemStack i = stacks.next();
                if (Caches.forInventory().count(i.getMaterial()) < i.getQuantity()) {
                    return i;
                }
            }
            return null;
        };
    }
}
