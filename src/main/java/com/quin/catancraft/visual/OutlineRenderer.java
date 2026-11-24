package com.quin.catancraft.visual;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quin.catancraft.CatanCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local depth-tested lines for public zones and fixtures. Never outlines entities or cargo holders. */
@Mod.EventBusSubscriber(modid = CatanCraft.MOD_ID, value = Dist.CLIENT)
public final class OutlineRenderer {
    private static ZoneOutlines.Snapshot visible;
    private static List<InteriorEdges.Edge> interior = List.of();
    private static final int CIRCLE_SEGMENTS = 96;

    private OutlineRenderer() {}

    static void accept(ZoneOutlines.Snapshot snapshot) {
        visible = snapshot;
        interior = InteriorEdges.build(snapshot.shapes());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        visible = null;
        interior = List.of();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || visible == null || visible.shapes().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || !mc.level.dimension().location().toString().equals(visible.dimension())) return;
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (OutlineShape shape : visible.shapes()) {
                switch (shape.kind()) {
                    case ACTIVE_MONUMENT, INACTIVE_MONUMENT -> monument(pose, lines, shape, mc.player.getY());
                    case SIEGE_SURVEY -> siege(pose, lines, shape, mc.player.getY(), camera);
                    case DISPATCH, RECEIVING -> counter(pose, lines, shape, mc);
                }
            }
            for (var edge : interior) drawInteriorEdge(pose, lines, edge);
            buffers.endBatch(RenderType.lines());
        } finally {
            pose.popPose();
        }
    }

    private static void drawInteriorEdge(PoseStack stack, VertexConsumer lines, InteriorEdges.Edge edge) {
        int color = edge.active() ? 0xFFC848 : 0xBCC6CF;
        double floor = edge.floor() + .075;
        if (edge.active()) {
            edgeLine(stack, lines, edge, edge.begin(), edge.end(), floor, color);
            // Short ticks on the true boundary remain visible beside skirting or furniture.
            double firstTick = Math.ceil(edge.begin() / 3) * 3;
            for (double i = firstTick; i < edge.end(); i += 3) {
                double x = edge.alongX() ? i : edge.fixed(), z = edge.alongX() ? edge.fixed() : i;
                line(stack, lines, x, floor, z, x, Math.min(floor + .55, edge.ceiling()), z, color, .85f);
            }
        } else {
            double start = Math.floor(edge.begin() / 1.5) * 1.5;
            for (double i = start; i < edge.end(); i += 1.5) {
                double a = Math.max(i, edge.begin()), b = Math.min(i + .85, edge.end());
                if (b > a) edgeLine(stack, lines, edge, a, b, floor, color);
            }
        }
    }

    private static void edgeLine(PoseStack stack, VertexConsumer lines, InteriorEdges.Edge edge,
                                 double a, double b, double y, int color) {
        if (edge.alongX()) line(stack, lines, a,y,edge.fixed(), b,y,edge.fixed(), color,.94f);
        else line(stack, lines, edge.fixed(),y,a, edge.fixed(),y,b, color,.94f);
    }

    private static void counter(PoseStack pose, VertexConsumer lines, OutlineShape s, Minecraft mc) {
        BlockPos pos = BlockPos.containing(s.x0(), s.y0(), s.z0());
        if (!mc.level.hasChunkAt(pos)) return;
        var shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) return;
        AABB box = shape.bounds().move(pos).inflate(0.013);
        boolean dispatch = s.kind() == OutlineShape.Kind.DISPATCH;
        LevelRenderer.renderLineBox(pose, lines, box, dispatch ? 0.15f : 0.55f, 1f,
                dispatch ? 1f : 0.28f, 0.92f);
        // A receiving mark has a second, inset rim, so hue is not the only distinction.
        if (!dispatch && box.getXsize() > .2 && box.getZsize() > .2) {
            LevelRenderer.renderLineBox(pose, lines, box.minX + .07, box.maxY + .003,
                    box.minZ + .07, box.maxX - .07, box.maxY + .006, box.maxZ - .07,
                    .55f, 1f, .28f, .92f);
        }
    }

    private static void monument(PoseStack stack, VertexConsumer lines, OutlineShape s, double playerY) {
        boolean active = s.kind() == OutlineShape.Kind.ACTIVE_MONUMENT;
        int color = active ? 0xFFC848 : 0xBCC6CF;
        double cx = (s.x0() + s.x1()) / 2, cz = (s.z0() + s.z1()) / 2;
        double r = (s.x1() - s.x0()) / 2;
        double y = Mth.clamp(playerY + .13, s.y0(), s.y1());
        for (int i = 0; i < CIRCLE_SEGMENTS; ++i) {
            if (!active && (i / 3) % 2 == 1) continue;
            double a = i * (Math.PI * 2 / CIRCLE_SEGMENTS);
            double b = (i + 1) * (Math.PI * 2 / CIRCLE_SEGMENTS);
            double x = cx + r * Math.cos(a), z = cz + r * Math.sin(a);
            double nextX = cx + r * Math.cos(b), nextZ = cz + r * Math.sin(b);
            line(stack, lines, x, y, z, nextX, y, nextZ, color, .92f);
            if (active) {
                line(stack, lines, x, s.y1(), z, nextX, s.y1(), nextZ, color, .53f);
                line(stack, lines, x, s.y0(), z, nextX, s.y0(), nextZ, color, .53f);
                if (i % 24 == 0) line(stack, lines, x, s.y0(), z, x, s.y1(), z, color, .55f);
            }
        }
    }

    private static void siege(PoseStack stack, VertexConsumer lines, OutlineShape s, double playerY, Vec3 camera) {
        double y = Mth.clamp(playerY + .18, s.y0(), s.y1());
        for (double x : new double[]{s.x0(), s.x1()}) {
            if (Math.abs(camera.x - x) > 50) continue;
            for (double z = startDash(s.z0(), camera.z); z < Math.min(s.z1(), camera.z + 50); z += 6) {
                siegeDash(stack, lines, s, x, z, x, Math.min(s.z1(), z + 3.5), y);
            }
        }
        for (double z : new double[]{s.z0(), s.z1()}) {
            if (Math.abs(camera.z - z) > 50) continue;
            for (double x = startDash(s.x0(), camera.x); x < Math.min(s.x1(), camera.x + 50); x += 6) {
                siegeDash(stack, lines, s, x, z, Math.min(s.x1(), x + 3.5), z, y);
            }
        }
    }

    private static double startDash(double min, double position) {
        return min + 6 * Math.max(0, Math.floor((position - min - 50) / 6));
    }

    private static void siegeDash(PoseStack stack, VertexConsumer lines, OutlineShape s,
                                  double x, double z, double ex, double ez, double y) {
        int purple = 0xD7A2F6;
        line(stack, lines, x,y,z, ex,y,ez, purple,.88f);
        line(stack, lines, x,s.y1(),z, ex,s.y1(),ez, purple,.4f);
        line(stack, lines, x,s.y0(),z, ex,s.y0(),ez, purple,.4f);
        line(stack, lines, x,y,z, x,Math.min(y + .8,s.y1()),z, purple,.8f);
    }

    private static void line(PoseStack stack, VertexConsumer out, double ax, double ay, double az,
                             double bx, double by, double bz, int color, float alpha) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < .0001) return;
        float nx = (float) (dx / len), ny = (float) (dy / len), nz = (float) (dz / len);
        int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
        PoseStack.Pose pose = stack.last();
        int opacity = Math.round(alpha * 255);
        out.vertex(pose.pose(), (float) ax, (float) ay, (float) az).color(red, green, blue, opacity)
                .normal(pose.normal(), nx, ny, nz).endVertex();
        out.vertex(pose.pose(), (float) bx, (float) by, (float) bz).color(red, green, blue, opacity)
                .normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
