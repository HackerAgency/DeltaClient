package aethereal.ui;
import aethereal.ui.setting.BooleanSetting;
import aethereal.resource.ClasspathResource;
import aethereal.render.ColorStack;
import aethereal.model.DragRenderContext;
import aethereal.util.DrawEngine;
import aethereal.Expensive;
import aethereal.render.Fonts;
import aethereal.render.GlTexture;
import aethereal.type.HudInfoType;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.model.MouseButtonInput2;
import aethereal.model.MouseMoveInput;
import aethereal.render.MsdfFont;
import aethereal.ui.setting.NumberSetting;
import aethereal.ui.setting.OrderedEnumSetting;
import aethereal.ui.setting.SeparatorSetting;
import aethereal.util.ServerUtil;
import aethereal.render.Theme;
import aethereal.render.ThemePalette;
import aethereal.util.WeightedEngine;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.util.math.MatrixStack;

public class WatermarkWidget extends Draggable {
    float computedWidth;
    float computedHeight;
    final GlTexture logoTexture;
    final GlTexture playerIcon;
    final GlTexture computerIcon;
    final GlTexture timeIcon;
    final GlTexture wifiIcon;
    final GlTexture cloudIcon;
    final GlTexture backgroundTexture;
    final MsdfFont font;
    public final OrderedEnumSetting<HudInfoType> hudLines;
    public final NumberSetting scale;
    public final BooleanSetting use12hFormat;

    public static final WatermarkStar[] stars = {new WatermarkStar(19.5f, 0.0f, 6.0f, 6.0f, 0.1f), new WatermarkStar(9.5f, 0.0f, 6.0f, 6.0f, 0.1f), new WatermarkStar(3.0f, 5.0f, 6.0f, 6.0f, 0.1f), new WatermarkStar(3.5f, -3.0f, 6.0f, 6.0f, 0.1f), new WatermarkStar(6.0f, 14.0f, 6.0f, 6.0f, 0.1f), new WatermarkStar(10.5f, 20.0f, 6.0f, 6.0f, 0.1f), new WatermarkStar(19.5f, 16.0f, 7.0f, 7.0f, 0.1f), new WatermarkStar(24.5f, 12.0f, 7.0f, 7.0f, 0.1f)};

    public WatermarkWidget(BooleanSupplier booleanSupplier) {
        super("Watermark", booleanSupplier);
        this.logoTexture = new GlTexture(new ClasspathResource("/icons/menu/new/logotype.png"));
        this.playerIcon = new GlTexture(new ClasspathResource("/icons/menu/new/tabs/player.png"));
        this.computerIcon = new GlTexture(new ClasspathResource("/icons/menu/new/computer.png"));
        this.timeIcon = new GlTexture(new ClasspathResource("/icons/menu/new/time.png"));
        this.wifiIcon = new GlTexture(new ClasspathResource("/icons/menu/new/wifi.png"));
        this.cloudIcon = new GlTexture(new ClasspathResource("/icons/menu/new/cloud.png"));
        this.backgroundTexture = new GlTexture(new ClasspathResource("/textures/hud_background.png"));
        this.font = Fonts.INTER_BOLD.get();
        this.hudLines = new OrderedEnumSetting(Lang.WIDGET_WATERMARK_HUD_LINES).values(HudInfoType.class).ordered(HudInfoType.LATENCY, HudInfoType.FRAMERATE, HudInfoType.CURRENT_TIME, HudInfoType.SERVER_NAME).selected(HudInfoType.LATENCY, HudInfoType.FRAMERATE);
        this.scale = new NumberSetting(Lang.WIDGET_WATERMARK_SCALE).currentValue(1.0f).range(0.5f, 2.0f).step(0.05f);
        this.use12hFormat = new BooleanSetting(Lang.WIDGET_WATERMARK_USE_12H_FORMAT).visible(() -> {
            return Boolean.valueOf(this.hudLines.isSelected(HudInfoType.CURRENT_TIME));
        });
        addSettings(this.scale, this.use12hFormat, new SeparatorSetting(), this.hudLines);
        this.x = 10.0f;
        this.y = 10.0f;
    }

    @Override
    public void layout(DragRenderContext class809Var) {
        String strMethod005;
        if (isVisible()) {
            Mc class815Var = Mc.INSTANCE;
            float width = this.x + 8.0f + 19.0f + 8.0f + 12.0f + 3.0f + this.font.getWidth("https://t.me/soezproject", 11.0f) + 8.0f;
            for (HudInfoType e : this.hudLines.orderedValues()) {
                if (e != HudInfoType.NICKNAME && this.hudLines.isSelected(e) && (strMethod005 = textFor(e, class815Var)) != null && !strMethod005.isEmpty()) {
                    width += 12.0f + 3.0f + this.font.getWidth(strMethod005, fontSizeFor(e)) + 8.0f;
                }
            }
            this.computedWidth = width - this.x;
            this.computedHeight = 23.0f;
        }
    }

    @Override
    public void render(DragRenderContext class809Var) throws MatchException {
        if (isVisible()) {
            Mc class815Var = Mc.INSTANCE;
            DrawEngine class154VarDrawEngine = class809Var.drawEngine();
            ColorStack class115VarColorStack = class154VarDrawEngine.colorStack();
            MatrixStack matrixStack = class809Var.matrixStack();
            Theme class760VarTheme = class809Var.theme();
            ThemePalette class764VarPalette = class760VarTheme.palette();
            float fCurrentValue = this.scale.currentValue();
            matrixStack.push();
            matrixStack.translate(this.x, this.y, 0.0f);
            matrixStack.scale(fCurrentValue, fCurrentValue, 1.0f);
            matrixStack.translate(-this.x, -this.y, 0.0f);
            float f = this.y + (this.computedHeight / 2.0f);
            int iComputeColor = class115VarColorStack.computeColor(class764VarPalette.surfaceBackground().tone(801).argb());
            int iComputeColor2 = class115VarColorStack.computeColor(class764VarPalette.surfaceBackground().tone(900).argb());
            class154VarDrawEngine.roundedRectangle(matrixStack.peek().getPositionMatrix(), this.x, this.y, this.computedWidth, this.computedHeight, 8.0f, 2.5f, class115VarColorStack.computeColor(class764VarPalette.surfaceOutline().tone(600).argb()), iComputeColor2, iComputeColor2, iComputeColor, iComputeColor);
            class154VarDrawEngine.beginStencil();
            class154VarDrawEngine.roundedRectangle(matrixStack.peek().getPositionMatrix(), this.x, this.y + 2.0f, this.computedWidth, this.computedHeight - 4.0f, 8.0f, 2.5f, class115VarColorStack.computeColor(class764VarPalette.surfaceOutline().tone(600).argb()), iComputeColor2, iComputeColor2, iComputeColor, iComputeColor);
            class154VarDrawEngine.prepareStencil(1);
            class154VarDrawEngine.texture(matrixStack.peek().getPositionMatrix(), this.x, this.y, this.computedWidth, this.computedHeight, class154VarDrawEngine.bindTexture(this.backgroundTexture.textureWithSTB()), class115VarColorStack.white());
            float fMethod002 = renderLogo(class154VarDrawEngine, matrixStack, class115VarColorStack, class760VarTheme, class764VarPalette);
            class154VarDrawEngine.textureVerticalC(matrixStack.peek().getPositionMatrix(), this.playerIcon, fMethod002, f, 12, 12, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
            float f2 = fMethod002 + 15.0f;
            String strUsername = "https://t.me/soezproject";
            class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.font, strUsername, f2, f - (this.font.getHeight(11.0f) / 2.0f), 11.0f, 0.05f, class115VarColorStack.computeColor(class764VarPalette.text().tone(300).argb()));
            float width = f2 + this.font.getWidth(strUsername, 11.0f) + 8.0f;
            for (HudInfoType e : this.hudLines.orderedValues()) {
                if (e != HudInfoType.NICKNAME && this.hudLines.isSelected(e)) {
                    GlTexture class073VarMethod004 = iconFor(e);
                    String strMethod005 = textFor(e, class815Var);
                    if (strMethod005 != null && !strMethod005.isEmpty()) {
                        class154VarDrawEngine.textureVerticalC(matrixStack.peek().getPositionMatrix(), class073VarMethod004, width, f, 12, 12, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
                        float f3 = width + 15.0f;
                        int iMethod003 = fontSizeFor(e);
                        class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.font, strMethod005, f3, f - (this.font.getHeight(iMethod003) / 2.0f), iMethod003, 0.05f, class115VarColorStack.computeColor(class764VarPalette.text().tone(300).argb()));
                        width = f3 + this.font.getWidth(strMethod005, iMethod003) + 8.0f;
                    }
                }
            }
            class154VarDrawEngine.endStencil();
            matrixStack.pop();
        }
    }

    public float renderLogo(DrawEngine class154Var, MatrixStack matrixStack, ColorStack class115Var, Theme class760Var, ThemePalette class764Var) {
        float f = this.x + 8.0f;
        float f2 = this.y + 2.0f;
        class154Var.roundedRectangle(matrixStack.peek().getPositionMatrix(), f, f2, 19.0f, 19.0f, 8.0f, class115Var.computeColor(1052690));
        class154Var.roundedRectangle(matrixStack.peek().getPositionMatrix(), f, f2, 19.0f, 19.0f, 8.0f, class115Var.computeColor(class760Var.palette().accentBright().argb(), 0.03f));
        class154Var.radialRoundedRectangle(matrixStack.peek().getPositionMatrix(), f, f2, 19.0f, 19.0f, 8.0f, class115Var.computeColor(class764Var.accentBright().argb(), 0.3f), class115Var.computeColor(class764Var.accentBright().argb(), 0.0f));
        int iBindTexture = class154Var.bindTexture(this.logoTexture.textureWithSTB());
        int iArgb = class764Var.accentBright().argb();
        for (WatermarkStar class655Var : stars) {
            class154Var.texture(matrixStack.peek().getPositionMatrix(), this.x + class655Var.xOff(), this.y + class655Var.yOff(), class655Var.w(), class655Var.h(), iBindTexture, class115Var.computeColor(iArgb, class655Var.alpha()));
        }
        class154Var.textureVerticalCHorizontalC(matrixStack.peek().getPositionMatrix(), this.logoTexture, f + (19.0f / 2.0f), f2 + (19.0f / 2.0f), 11, 11, class115Var.computeColor(class764Var.accentBright().argb()));
        return f + 19.0f + 8.0f;
    }

    @Override
    public boolean click(MouseButtonInput2 class807Var, boolean z) {
        return false;
    }

    @Override
    public boolean cursor(MouseMoveInput class808Var, boolean z) {
        return z && !class808Var.intercepted();
    }

    @Override
    public void animate(WeightedEngine class141Var) {
    }

    @Override
    public void update() {
    }

    public GlTexture iconFor(HudInfoType class656Var) throws MatchException {
        switch (class656Var.ordinal()) {
            case 0:
                return this.playerIcon;
            case 1:
                return this.wifiIcon;
            case 2:
                return this.computerIcon;
            case 3:
                return this.timeIcon;
            case 4:
                return this.cloudIcon;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }
    }

    public int fontSizeFor(HudInfoType class656Var) {
        switch (class656Var) {
            case NICKNAME:
                return 11;
            default:
                return 10;
        }
    }

    public String textFor(HudInfoType class656Var, Mc class815Var) throws MatchException {
        switch (class656Var.ordinal()) {
            case 0:
                return "https://t.me/soezproject";
            case 1:
                return getPing() + "ms";
            case 2:
                return class815Var.getCurrentFps() + " FPS";
            case 3:
                return formatTime();
            case 4:
                return serverAddress();
            default:
                throw new MatchException((String) null, (Throwable) null);
        }
    }

    public String formatTime() {
        return LocalTime.now().format(this.use12hFormat.isValue() ? DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH) : DateTimeFormatter.ofPattern("HH:mm"));
    }

    public String serverAddress() {
        String serverIp = ServerUtil.getServerIp();
        return serverIp == null ? "" : serverIp.toLowerCase();
    }

    public int getPing() {
        if (Mc.INSTANCE.isWorldLoaded()) {
            return ((Integer) Mc.INSTANCE.getNetworkHandler().getPlayerList().stream().filter(playerListEntry -> {
                return playerListEntry.getProfile().getId().equals(Mc.INSTANCE.getPlayer().getUuid());
            }).findFirst().map((v0) -> {
                return v0.getLatency();
            }).orElse(0)).intValue();
        }
        return 0;
    }

    @Override
    public float width() {
        return this.computedWidth * this.scale.currentValue();
    }

    @Override
    public float height() {
        return this.computedHeight * this.scale.currentValue();
    }
}
