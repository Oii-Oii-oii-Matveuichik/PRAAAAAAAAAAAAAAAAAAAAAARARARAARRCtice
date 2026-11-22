package com.ancientcivilization.squad;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.particles.BlockParticleOption;
import net.minecraft.world.level.particles.ParticleTypes;

import java.util.Random;

/**
 * "Digging out" spawn animation: the ground cracks, heaves dust, then the squad emerges.
 * Server-side only — particles and sounds are emitted through vanilla systems, no
 * client-side mixin, no external library, nothing that can conflict with other mods.
 */
public class SquadAnimation {
    private static final Random RANDOM = new Random();

    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState digBlock;
    private final int durationTicks;
    private final int particleCount;
    private final float soundVolume;
    private final float soundPitch;
    private final Runnable onComplete;

    private int ticks;
    private boolean completeFired;

    public SquadAnimation(ServerLevel level, BlockPos pos, BlockState digBlock,
                          ModConfig.Animation config, Runnable onComplete) {
        this.level = level;
        this.pos = pos;
        this.digBlock = digBlock;
        this.durationTicks = Math.max(1, config.durationTicks);
        this.particleCount = Math.max(1, config.particleCount);
        this.soundVolume = config.soundVolume;
        this.soundPitch = config.soundPitch;
        this.onComplete = onComplete;
    }

    public boolean isFinished() {
        return ticks > durationTicks + 4;
    }

    public void tick() {
        ticks++;
        if (ticks > durationTicks + 4 && !completeFired) {
            completeFired = true;
            onComplete.run();
            return;
        }
        if (ticks <= durationTicks) {
            emitPhaseParticles();
            if (ticks % 10 == 0 || ticks == durationTicks) {
                playDigSound(ticks == durationTicks);
            }
        }
    }

    private void emitPhaseParticles() {
        double t = (double) ticks / durationTicks;
        double radius;
        if (t < 0.55) {
            // Cracking phase: a ring of block particles spreads across the ground.
            radius = 0.5 + t * 4.0;
        } else {
            // Heaving phase: the ring keeps breathing while dust rises.
            radius = 2.5 + Math.sin((t - 0.55) / 0.45 * Math.PI) * 1.5;
        }

        for (int i = 0; i < particleCount; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double r = radius * (0.7 + RANDOM.nextDouble() * 0.6);
            double x = pos.getX() + 0.5 + Math.cos(angle) * r;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * r;
            double y = pos.getY() - 0.2 + RANDOM.nextDouble() * 0.9;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, digBlock),
                    x, y, z,
                    (RANDOM.nextDouble() - 0.5) * 0.15,
                    0.04 + RANDOM.nextDouble() * 0.08,
                    (RANDOM.nextDouble() - 0.5) * 0.15);
        }

        if (t >= 0.5) {
            // Rising dust column.
            for (int i = 0; i < particleCount; i++) {
                double x = pos.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 1.6;
                double z = pos.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 1.6;
                double y = pos.getY() + 0.2 + t * 2.5;
                level.addParticle(ParticleTypes.FALLING_DUST,
                        x, y, z,
                        (RANDOM.nextDouble() - 0.5) * 0.1,
                        0.06,
                        (RANDOM.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    private void playDigSound(boolean burst) {
        SoundEvent event = digSound();
        float pitch = burst
                ? soundPitch * 0.7f
                : soundPitch * (0.8f + RANDOM.nextFloat() * 0.4f);
        level.playSound(null, pos, event, SoundSource.BLOCKS, soundVolume, pitch);
    }

    private SoundEvent digSound() {
        if (digBlock.is(Blocks.SAND)) {
            return SoundEvents.SAND_BREAK;
        }
        if (digBlock.is(Blocks.RED_SAND)) {
            return SoundEvents.SAND_BREAK;
        }
        if (digBlock.is(Blocks.GRAVEL)) {
            return SoundEvents.GRAVEL_BREAK;
        }
        if (digBlock.is(Blocks.DIRT) || digBlock.is(Blocks.SANDSTONE)) {
            return SoundEvents.STONE_BREAK;
        }
        if (digBlock.is(Blocks.SNOW)) {
            return SoundEvents.SNOW_BREAK;
        }
        if (digBlock.is(Blocks.STONE)) {
            return SoundEvents.STONE_BREAK;
        }
        return SoundEvents.GRAVEL_BREAK;
    }
}
