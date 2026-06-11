package net.sojeong.rescuecraft;

import net.minecraft.world.item.Item;

/**
 * A single item in the HUD todo list.
 * Represents one food type (or water) that still needs to be delivered to a herd.
 */
public final class TodoEntry {
    private final String animalName;
    private final Item item;          // null = water bucket
    private final boolean isWater;
    private final int given;
    private final int needed;

    public TodoEntry(String animalName, Item item, int given, int needed) {
        this.animalName = animalName;
        this.item = item;
        this.isWater = false;
        this.given = given;
        this.needed = needed;
    }

    /** Water entry constructor. */
    public TodoEntry(String animalName) {
        this.animalName = animalName;
        this.item = null;
        this.isWater = true;
        this.given = 0;
        this.needed = 1;
    }

    public String getAnimalName() { return animalName; }
    public Item getItem()         { return item; }
    public boolean isWater()      { return isWater; }
    public int getGiven()         { return given; }
    public int getNeeded()        { return needed; }
    public boolean isDone()       { return isWater ? false : given >= needed; }
}
