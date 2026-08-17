package io.github.unknownassets.totemic.client.mixin;

import io.github.unknownassets.totemic.Totemic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void totemic$renderLoadedMark(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
		int labelWidth = this.font.width(Totemic.MOD_ID);
		int labelX = this.width - labelWidth - 3;
		int labelY = this.height - 40;
		graphics.fill(labelX - 8, labelY + 2, labelX - 3, labelY + 7, 0xFF55FF55);
		graphics.drawString(this.font, Totemic.MOD_ID, labelX, labelY, 0xFF55FF55);
	}
}
