package io.github.brooswitminecraft.dynamicskateboards;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Placeholder look for the loose board, a thin plank-shaped block exactly filling the physics
 * box, rotated by the body's full orientation (pitch/roll included) so it visibly tumbles rather
 * than just translating. Minimal on purpose: the ticket's acceptance target is "bail into a
 * convincingly physical loose board," not final art.
 */
public class LooseSkateboardRenderer extends EntityRenderer<LooseSkateboardEntity> {
    private static final BlockState DECK = Blocks.OAK_PLANKS.defaultBlockState();

    public LooseSkateboardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LooseSkateboardEntity board, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.0, SkateConstants.BAIL_BOARD_HALF_HEIGHT, 0.0);
        pose.mulPose(board.orientation());
        float hx = (float) SkateConstants.BAIL_BOARD_HALF_WIDTH;
        float hy = (float) SkateConstants.BAIL_BOARD_HALF_HEIGHT;
        float hz = (float) SkateConstants.BAIL_BOARD_HALF_LENGTH;
        pose.pushPose();
        pose.translate(-hx, -hy, -hz);
        pose.scale(2 * hx, 2 * hy, 2 * hz);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(DECK, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        pose.popPose();
        super.render(board, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LooseSkateboardEntity board) {
        return ResourceLocation.withDefaultNamespace("textures/block/oak_planks.png");
    }
}
