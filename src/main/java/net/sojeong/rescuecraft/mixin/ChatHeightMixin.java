package net.sojeong.rescuecraft.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public class ChatHeightMixin {

    @Inject(method = "getLinesPerPage", at = @At("RETURN"), cancellable = true)
    private void rescuecraft$limitChatLines(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > 6) {
            cir.setReturnValue(6);
        }
    }
}