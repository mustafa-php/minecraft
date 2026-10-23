package com.simsek.simsekordu.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.Rank;
import com.simsek.simsekordu.entity.SimsekNpc;
import com.simsek.simsekordu.menu.SimsekNpcMenu;
import com.simsek.simsekordu.network.SimsekNpcPayloads;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NPC screen with an info tab and an inventory tab. No texture is used: the panel, slots and
 * bars are drawn with {@link GuiGraphics#fill} in the vanilla style.
 */
public class SimsekNpcScreen extends AbstractContainerScreen<SimsekNpcMenu> {
    private enum Tab {
        INFO,
        INVENTORY
    }

    private static final int TEXT_COLOR = 0x404040;
    private static final int PAD = 6;
    private static final int CONTENT_WIDTH = SimsekNpcMenu.PANEL_WIDTH - 2 * PAD;

    private Tab tab = Tab.INFO;
    private Button infoTabButton;
    private Button inventoryTabButton;
    private final Map<Job, Button> jobButtons = new EnumMap<>(Job.class);
    private Button followButton;
    private Button waitButton;
    private EditBox nameBox;
    private Button renameButton;

    public SimsekNpcScreen(SimsekNpcMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SimsekNpcMenu.PANEL_WIDTH;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null) {
            onClose();
            return;
        }

        this.infoTabButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.tab.info"),
                button -> setTab(Tab.INFO)).bounds(this.leftPos, this.topPos - 22, 70, 20).build());
        this.inventoryTabButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.tab.inventory"),
                button -> setTab(Tab.INVENTORY)).bounds(this.leftPos + 72, this.topPos - 22, 70, 20).build());

        this.jobButtons.clear();
        int x = this.leftPos + PAD;
        for (Job job : Job.values()) {
            Button button = Button.builder(Component.translatable(job.getTranslationKey()),
                    b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.SetJobPayload(npc.getId(), job.ordinal())))
                    .bounds(x, this.topPos + 88, 46, 18).build();
            this.jobButtons.put(job, addRenderableWidget(button));
            x += 47;
        }

        this.followButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.command.follow"),
                b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.SetWaitingPayload(npc.getId(), false)))
                .bounds(this.leftPos + PAD, this.topPos + 112, 92, 18).build());
        this.waitButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.command.wait"),
                b -> PacketDistributor.sendToServer(new SimsekNpcPayloads.SetWaitingPayload(npc.getId(), true)))
                .bounds(this.leftPos + PAD + 96, this.topPos + 112, 92, 18).build());

        this.nameBox = new EditBox(this.font, this.leftPos + PAD, this.topPos + 138, 110, 16,
                Component.translatable("gui.simsekordu.rename"));
        this.nameBox.setMaxLength(SimsekNpcPayloads.MAX_NAME_LENGTH);
        this.nameBox.setValue(npc.getNpcName());
        addRenderableWidget(this.nameBox);
        this.renameButton = addRenderableWidget(Button.builder(Component.translatable("gui.simsekordu.rename"),
                b -> submitRename()).bounds(this.leftPos + PAD + 112, this.topPos + 137, 76, 18).build());

        setTab(this.tab);
        updateButtonStates();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String typedName = this.nameBox != null ? this.nameBox.getValue() : null;
        super.resize(minecraft, width, height);
        if (typedName != null && this.nameBox != null) {
            this.nameBox.setValue(typedName);
        }
    }

    private void setTab(Tab newTab) {
        this.tab = newTab;
        boolean info = newTab == Tab.INFO;
        this.menu.setInventoryTabActive(!info);
        setFocused(null);
        this.jobButtons.values().forEach(button -> button.visible = info);
        this.followButton.visible = info;
        this.waitButton.visible = info;
        this.renameButton.visible = info;
        this.nameBox.visible = info;
        this.nameBox.setFocused(false);
        updateButtonStates();
    }

    private void updateButtonStates() {
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null) {
            return;
        }
        this.infoTabButton.active = this.tab != Tab.INFO;
        this.inventoryTabButton.active = this.tab != Tab.INVENTORY;
        this.jobButtons.forEach((job, button) -> button.active = npc.getJob() != job);
        this.followButton.active = npc.isWaiting();
        this.waitButton.active = !npc.isWaiting();
    }

    private void submitRename() {
        SimsekNpc npc = this.menu.getNpc();
        String name = SimsekNpcPayloads.sanitizeName(this.nameBox.getValue());
        if (npc != null && !name.isEmpty()) {
            PacketDistributor.sendToServer(new SimsekNpcPayloads.RenamePayload(npc.getId(), name));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null || !npc.isAlive()) {
            onClose();
            return;
        }
        updateButtonStates();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (this.tab == Tab.INFO && this.nameBox.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                submitRename();
                return true;
            }
            // Keep typing in the box from triggering screen shortcuts (e.g. the inventory key closing the screen).
            return this.nameBox.keyPressed(keyCode, scanCode, modifiers) || this.nameBox.canConsumeInput()
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        if (this.tab == Tab.INVENTORY) {
            for (Slot slot : this.menu.slots) {
                drawSlotBackground(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        SimsekNpc npc = this.menu.getNpc();
        if (npc == null) {
            return;
        }
        int labelX = this.tab == Tab.INVENTORY ? SimsekNpcMenu.SLOT_X - 1 : PAD;
        drawFitted(graphics, npc.getName(), labelX, 6);
        if (this.tab == Tab.INVENTORY) {
            graphics.drawString(this.font, Component.translatable("gui.simsekordu.inventory"), labelX, 18, TEXT_COLOR, false);
            graphics.drawString(this.font, this.playerInventoryTitle, labelX, SimsekNpcMenu.PLAYER_INV_Y - 12, TEXT_COLOR, false);
            return;
        }

        Rank rank = npc.getRank();
        int health = Mth.ceil(npc.getHealth());
        int maxHealth = Mth.ceil(npc.getMaxHealth());
        int xp = npc.getXp();
        int xpNeeded = SimsekNpc.xpForNextLevel(npc.getNpcLevel());

        drawFitted(graphics, Component.translatable("gui.simsekordu.health", health, maxHealth), PAD, 18);
        drawFitted(graphics, Component.translatable("gui.simsekordu.xp", xp, xpNeeded), PAD, 30);
        drawXpBar(graphics, PAD, 41, CONTENT_WIDTH, 6, xpNeeded > 0 ? (float) xp / xpNeeded : 0.0F);

        drawFitted(graphics, Component.translatable("gui.simsekordu.bonuses.title",
                Component.translatable(rank.getTranslationKey())), PAD, 52);
        drawFitted(graphics, Component.translatable("gui.simsekordu.bonuses.line",
                percentBonus(rank.getMaxHealthMultiplier()),
                percentBonus(rank.getAttackDamageMultiplier()),
                percentBonus(rank.getMovementSpeedMultiplier())), PAD, 63);

        drawFitted(graphics, Component.translatable("gui.simsekordu.job", Component.translatable(npc.getJob().getTranslationKey())), PAD, 77);
    }

    private static int percentBonus(double multiplier) {
        return (int) Math.round((multiplier - 1.0D) * 100.0D);
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y) {
        graphics.drawString(this.font,
                Language.getInstance().getVisualOrder(this.font.substrByWidth(text, CONTENT_WIDTH)),
                x, y, TEXT_COLOR, false);
    }

    private static void drawXpBar(GuiGraphics graphics, int x, int y, int width, int height, float fraction) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF373737);
        int filled = Mth.floor((width - 2) * Mth.clamp(fraction, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, 0xFF55FF55);
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0xFFAAFFAA);
        }
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + width - 2, y + 3, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + height - 3, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + width - 3, y + 2, x + width - 1, y + height - 1, 0xFF555555);
    }

    private static void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x, y, x + 17, y + 1, 0xFF373737);
        graphics.fill(x, y, x + 1, y + 17, 0xFF373737);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
    }
}
