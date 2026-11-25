package com.nexusgate.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Local, non-positional interface sounds: pitched vanilla events layered to sound mechanical. */
public final class GateSounds {
    private GateSounds() {}

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }

    public static void boot() {
        play(SoundEvents.VAULT_ACTIVATE, 1.4f, 0.5f);
        play(SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, 1.6f, 0.35f);
    }

    public static void chamberReveal(int index) {
        play(SoundEvents.CROSSBOW_QUICK_CHARGE_1, 1.6f + index * 0.05f, 0.18f);
    }

    public static void index() {
        play(SoundEvents.CROSSBOW_LOADING_END, 1.25f, 0.55f);
        play(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.9f, 0.18f);
    }

    public static void hover() {
        play(SoundEvents.UI_BUTTON_CLICK, 2.0f, 0.06f);
    }

    public static void denied() {
        play(SoundEvents.LODESTONE_COMPASS_LOCK, 0.6f, 0.5f);
    }

    public static void engage() {
        play(SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.6f);
        play(SoundEvents.VAULT_OPEN_SHUTTER, 1.2f, 0.5f);
    }

    public static void irisOpen() {
        play(SoundEvents.TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 1.3f, 0.5f);
    }

    public static void dive() {
        play(SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6f, 0.7f);
        play(SoundEvents.CONDUIT_ACTIVATE, 1.8f, 0.4f);
    }

    public static void emerge() {
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.9f);
        play(SoundEvents.ENDER_EYE_DEATH, 1.6f, 0.6f);
    }

    public static void abort() {
        play(SoundEvents.RESPAWN_ANCHOR_DEPLETE, 1.4f, 0.5f);
    }
}
