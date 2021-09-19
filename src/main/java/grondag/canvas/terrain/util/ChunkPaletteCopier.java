/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.canvas.terrain.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import org.apache.commons.lang3.ObjectUtils;
import grondag.canvas.mixinterface.PalettedContainerExt;

public class ChunkPaletteCopier {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	public static final PaletteCopy AIR_COPY = i -> AIR;

	public static PaletteCopy captureCopy(LevelChunk chunk, int y) {
		if (chunk == null) {
			return AIR_COPY;
		}

		final int sectionIndex = (y - chunk.getMinBuildHeight()) >> 4;

		if (sectionIndex < 0) {
			return AIR_COPY;
		}

		final LevelChunkSection[] sections = chunk.getSections();

		if (sections == null || sectionIndex >= sections.length) {
			return AIR_COPY;
		}

		final LevelChunkSection sec = sections[sectionIndex];

		if (sec == null) {
			return AIR_COPY;
		}

		if (sec.isEmpty()) {
			final BlockState filler = sec.getBlockState(0, 0, 0);
			return filler == AIR ? AIR_COPY : i -> filler;
		}

		return ((PalettedContainerExt) sec.getStates()).canvas_paletteCopy();
	}

	/**
	 * Callback from canvas_paletteCopy().
	 */
	public static PaletteCopy captureCopy(Palette<BlockState> palette, BitStorage data, BlockState emptyVal) {
		if (palette == null || data == null) {
			return emptyVal == null ? AIR_COPY : i -> emptyVal;
		}

		return new PaletteCopyImpl(palette, data, emptyVal);
	}

	@FunctionalInterface
	public interface PaletteCopy {
		BlockState apply(int index);

		default void release() {
		}
	}

	private static class PaletteCopyImpl implements PaletteCopy {
		public final BlockState emptyVal;
		private final IntArrayList data;
		private final Palette<BlockState> palette;

		private PaletteCopyImpl(Palette<BlockState> palette, BitStorage data, BlockState emptyVal) {
			assert data != null;
			assert palette != null;
			this.palette = palette;
			this.data = PackedIntegerStorageHelper.claim(data);
			this.emptyVal = emptyVal;
		}

		@Override
		public BlockState apply(int index) {
			return ObjectUtils.defaultIfNull(palette.valueFor(data.getInt(index)), emptyVal);
		}

		@Override
		public void release() {
			PackedIntegerStorageHelper.release(data);
		}
	}
}
