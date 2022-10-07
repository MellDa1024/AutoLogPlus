package com.mellda.mixins.gui;

import com.mellda.modules.AutoLogPlus;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNewChat.class, priority = 1557)
public class MixinGuiNewChat {
    @Inject(method = "printChatMessage", at = @At(value = "HEAD"))
    private void printChatMessage(ITextComponent chatComponent, CallbackInfo ci) {
        if (AutoLogPlus.INSTANCE.isEnabled()) AutoLogPlus.chatListener(chatComponent);
    }
}