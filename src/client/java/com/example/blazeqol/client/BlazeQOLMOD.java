package com.example.blazeqol.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.resources.Identifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlazeQOLMOD implements ClientModInitializer {

	// default enabled
	private boolean displayEnabled = true;
	private String bossName = null;
	private String attunement = null;
	private boolean twilightPoison = false;

	private final Pattern healthPattern = Pattern.compile("(\\d+\\.?\\d*M)");
	private final Pattern ashenPattern = Pattern.compile("§8§l(\\d+)");
	private final Pattern hitsPattern = Pattern.compile("\\d+");

	@Override
	public void onInitializeClient() {
		// Command Registration (Client sided)
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("display")
					.then(ClientCommands.argument("action", StringArgumentType.string())
							.executes(context -> {
								String arg = StringArgumentType.getString(context, "action").toLowerCase();
								if (arg.equals("on")) {
									displayEnabled = true;
									context.getSource().sendFeedback(Component.literal("§aHUD ON"));
								} else if (arg.equals("off")) {
									displayEnabled = false;
									resetData();
									context.getSource().sendFeedback(Component.literal("§cHUD OFF"));
								}
								return 1;
							}))
					.executes(context -> {
						context.getSource().sendFeedback(Component.literal("Display is " + (displayEnabled ? "ON" : "OFF")));
						return 1;
					}));
		});

		// Tick Logic
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!displayEnabled || client.level == null || client.player == null) return;

			double range = 8.0;
			resetData();

			// Checking every entity in a given range
			for (Entity ent : client.level.entitiesForRendering()) {
				if (ent.distanceTo(client.player) > range) continue;
				String raw = ent.getDisplayName().getString();
				if (raw.isEmpty()) continue;
				String plain = raw.replaceAll("§.", "").toUpperCase();

				// Boss Detection
				Matcher hpMatcher = healthPattern.matcher(raw);
				if (plain.contains("INFERNO DEMONLORD") && hpMatcher.find()) {
					double hpVal = parseHp(hpMatcher.group(1));
					String hpColor = "§a";

					if (hpVal < 20.1) hpColor = "§c§l";
					else if (hpVal >= 20.1 && hpVal <= 50) hpColor = "§e§l";

					bossName = "§c☠ §bInferno Demonlord IV " + hpColor + hpMatcher.group(1) + "§c❤";
				}

				// Attunement
				String[][] types = {{"ASHEN", "§8§l"}, {"SPIRIT", "§f§l"}, {"AURIC", "§e§l"}, {"CRYSTAL", "§b§l"}};
				for (String[] t : types) {
					if (plain.contains(t[0])) {
						Matcher m = hitsPattern.matcher(plain);
						attunement = t[1] + t[0] + (m.find() ? " " + m.group() : "");
					}
				}

				Matcher ashenM = ashenPattern.matcher(raw);
				if (ashenM.find()) attunement = "§8§lASHEN " + ashenM.group(1);

				if (raw.contains("ᛤ")) twilightPoison = true;
			}
		});

		// Rendering
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath("blazeqol", "boss_hud"),(guiGraphics, tickDelta) -> {
				if (!displayEnabled || bossName == null) return;

				Minecraft client = Minecraft.getInstance();
				int centerX = client.getWindow().getGuiScaledWidth() / 2;
				int centerY = client.getWindow().getGuiScaledHeight() / 2 + 20;

				renderLine(guiGraphics, client, bossName, centerX, centerY);
				int yOffset = 14;

				if (attunement != null) {
					renderLine(guiGraphics, client, attunement, centerX, centerY + yOffset);
					yOffset += 14;
				}

				if (twilightPoison) {
					renderLine(guiGraphics, client, "§5§l☠ TWILIGHT POISON ☠", centerX, centerY + yOffset);
				}

			}
		);
	}

	private void renderLine(GuiGraphicsExtractor graphics, Minecraft client, String text, int x, int y) {
		graphics.centeredText(client.font, Component.literal(text), x, y, 0xFFFFFFFF);
	}

	private double parseHp(String s) {
		try { return Double.parseDouble(s.replace("M", "")); }
		catch (Exception e) { return 0; }
	}

	private void resetData() {
		bossName = null;
		attunement = null;
		twilightPoison = false;
	}
}