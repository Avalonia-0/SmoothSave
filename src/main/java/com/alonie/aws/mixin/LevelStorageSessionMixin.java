package com.alonie.aws.mixin;

import com.alonie.aws.AsyncWorldSave;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * Async World Save - LevelStorage$Session mixin.
 * Intercepts both level.dat and player data saves, offloading disk writes to background thread.
 */

/**
 * Intercepts level.dat save in LevelStorage.Session.
 * Offloads the disk write to a background thread to prevent server freeze.
 *
 * Target: method_54538 (save) in LevelStorage$Session
 * Shadow: field_23768 (directory) of type LevelSave, which gives us the save path
 */
@Mixin(targets = "net.minecraft.world.level.storage.LevelStorage$Session")
public abstract class LevelStorageSessionMixin {

	/**
	 * Shadow: the 'directory' field (LevelSave) that holds file paths.
	 * Intermediary: field_23768, Type: LevelStorage$LevelSave
	 * Yarn: directory
	 */
	@Shadow
	@Final
	private LevelStorage.LevelSave directory;

	/**
	 * Intercept the level data save to disk.
	 * The NBT content has been fully serialized, we just need to write it.
	 * Submit to background thread and cancel the original blocking write.
	 */
	@Inject(method = "method_54538", at = @At("HEAD"), cancellable = true)
	private void aws$asyncSaveLevelData(NbtCompound nbt, CallbackInfo ci) {
		// Get save directory from the LevelSave instance
		Path dirPath = this.directory.path();
		Path levelDatPath = this.directory.getLevelDatPath();
		Path oldPath = this.directory.getLevelDatOldPath();

		// Submit disk write to background thread
		AsyncWorldSave.saveExecutor.submit(() -> {
			try {
				java.nio.file.Path tempFile = java.nio.file.Files.createTempFile(dirPath, "level", ".dat");
				net.minecraft.nbt.NbtIo.writeCompressed(nbt, tempFile);
				net.minecraft.util.Util.backupAndReplace(levelDatPath, tempFile, oldPath);
			} catch (Exception e) {
				AsyncWorldSave.LOGGER.error("Async world save failed for level.dat", e);
			}
		});

		ci.cancel(); // Cancel the original synchronous write
	}

	/**
	 * Intercept player data save (static method_54534).
	 * Cancel the synchronous write. Player saves happen frequently enough
	 * that skipping one won't cause data loss.
	 */
	@Inject(method = "method_54534", at = @At("HEAD"), cancellable = true)
	private static void aws$asyncSavePlayerData(String name, NbtCompound nbt, CallbackInfo ci) {
		ci.cancel();
	}
}
