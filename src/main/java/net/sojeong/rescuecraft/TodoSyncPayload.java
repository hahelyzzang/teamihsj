package net.sojeong.rescuecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

public record TodoSyncPayload(List<TodoEntry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TodoSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("rescuecraft", "todo_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TodoSyncPayload> CODEC =
            StreamCodec.of(TodoSyncPayload::encode, TodoSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TodoSyncPayload payload) {
        buf.writeInt(payload.entries().size());
        for (TodoEntry e : payload.entries()) {
            buf.writeUtf(e.getAnimalName());
            buf.writeBoolean(e.isWater());
            if (!e.isWater()) {
                Identifier key = BuiltInRegistries.ITEM.getKey(e.getItem());
                buf.writeUtf(key == null ? "minecraft:air" : key.toString());
                buf.writeInt(e.getGiven());
                buf.writeInt(e.getNeeded());
            }
        }
    }

    private static TodoSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        List<TodoEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String  name    = buf.readUtf();
            boolean isWater = buf.readBoolean();
            if (isWater) {
                list.add(new TodoEntry(name));
            } else {
                String itemId = buf.readUtf();
                int    given  = buf.readInt();
                int    needed = buf.readInt();
                Item   item   = BuiltInRegistries.ITEM
                        .get(Identifier.parse(itemId))
                        .map(net.minecraft.core.Holder::value)
                        .orElse(Items.AIR);
                list.add(new TodoEntry(name, item, given, needed));
            }
        }
        return new TodoSyncPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}