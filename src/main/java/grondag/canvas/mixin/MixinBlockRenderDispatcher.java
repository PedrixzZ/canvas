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

package grondag.canvas.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import grondag.canvas.apiimpl.rendercontext.EntityBlockRenderContext;

@Mixin(BlockRenderDispatcher.class)
public abstract class MixinBlockRenderDispatcher {
	@Shadow private ModelBlockRenderer modelRenderer;
	@Shadow private BlockEntityWithoutLevelRenderer blockEntityRenderer;

	/**
	 * @author grondag
	 * @reason performance; less bad than inject and cancel at head
	 */
	@Overwrite
	public void renderSingleBlock(BlockState state, PoseStack poseStack, MultiBufferSource consumers, int light, int overlay) {
		final RenderShape blockRenderType = state.getRenderShape();

		if (blockRenderType != RenderShape.INVISIBLE) {
			switch (blockRenderType) {
				case MODEL:
					final BakedModel bakedModel = ((BlockRenderDispatcher) (Object) this).getBlockModel(state);
					EntityBlockRenderContext.get().render(modelRenderer, bakedModel, state, poseStack, consumers, overlay, light);
					break;
				case ENTITYBLOCK_ANIMATED:
					blockEntityRenderer.renderByItem(new ItemStack(state.getBlock()), ItemTransforms.TransformType.NONE, poseStack, consumers, light, overlay);
					break;
				default:
					break;
			}
		}
	}
}