package com.alonie.aws;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncWorldSave implements ModInitializer {
	public static final String MOD_ID = "async-world-save";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ExecutorService saveExecutor;

	@Override
	public void onInitialize() {
		saveExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread thread = new Thread(r, "AWS-SaveWorker");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
		LOGGER.info("Async World Save initialized - disk writes will run off-thread");
	}

	/**
	 * Save NBT data to a file asynchronously using temp file + atomic replace.
	 * Called from mixin hooks that intercept the original save calls.
	 */
	public static void saveNbtAsync(NbtCompound nbt, Path dir, String fileName, String oldFileName) {
		saveExecutor.submit(() -> {
			try {
				// Write to temp file first
				Path tempFile = Files.createTempFile(dir, fileName, ".dat");
				NbtIo.writeCompressed(nbt, tempFile);

				// Atomic replace: backup old -> temp becomes new
				Path newFile = dir.resolve(fileName);
				Path oldFile = dir.resolve(oldFileName);
				Util.backupAndReplace(newFile, tempFile, oldFile);

			} catch (IOException e) {
				LOGGER.error("Failed to async save {} in {}", fileName, dir, e);
			}
		});
	}

	/**
	 * Save player NBT data asynchronously.
	 */
	public static void savePlayerNbtAsync(NbtCompound nbt, Path playerFile) {
		saveExecutor.submit(() -> {
			try {
				Path tempFile = Files.createTempFile(playerFile.getParent(), playerFile.getFileName().toString(), ".dat");
				NbtIo.writeCompressed(nbt, tempFile);
				Util.backupAndReplace(playerFile, tempFile, playerFile.resolveSibling(playerFile.getFileName() + ".old"));
			} catch (IOException e) {
				LOGGER.error("Failed to async save player data to {}", playerFile, e);
			}
		});
	}
}
