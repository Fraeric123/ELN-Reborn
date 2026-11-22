package net.fraeric.elnr.client;

import net.fraeric.elnr.ELNReborn;
import net.fraeric.elnr.common.entity.ExampleEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Example entity renderer. Replace with a model-based renderer once you have a model.
 */
public class ExampleEntityRenderer extends EntityRenderer<ExampleEntity> {

    private static final ResourceLocation TEXTURE = Objects.requireNonNull(
            ResourceLocation.tryBuild(ELNReborn.MODID, "textures/entity/example_entity.png"));

    public ExampleEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ExampleEntity entity) {
        return TEXTURE;
    }
}
