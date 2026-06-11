package net.sojeong.rescuecraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MissionSyncPayload(int stage, String animalName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MissionSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("rescuecraft", "mission_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissionSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeInt(p.stage()); buf.writeUtf(p.animalName()); },
                    buf -> new MissionSyncPayload(buf.readInt(), buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}