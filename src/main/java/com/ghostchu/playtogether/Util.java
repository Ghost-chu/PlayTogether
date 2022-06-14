package com.ghostchu.playtogether;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class Util {
    public static List<Advancement> getAdvancements(boolean withRoot) {
        Pattern pattern = Pattern.compile("\\w+\\/root");
        ArrayList<Advancement> advancements = new ArrayList<>();
        Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            String key = advancement.getKey().getKey();
            if (key.startsWith("recipes/") || !withRoot && pattern.matcher(key).matches()) continue;
            advancements.add(advancement);
        }
        return advancements;
    }
}
