package com.quin.catancraft.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Collect the actual lines' vertex attributes without a client window or a Minecraft world. */
public final class OutlineColorTest {
    private static final class Vertices implements VertexConsumer {
        int vertices;
        boolean set;
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            if (r != 255 || g != 48 || b != 48 || a <= 0) throw new AssertionError("Unexpected boundary RGBA");
            set = true;
            return this;
        }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {
            if (!set) throw new AssertionError("Uncolored boundary vertex");
            ++vertices;
            set = false;
        }
        @Override public VertexConsumer uv(float x, float y) { throw new AssertionError(); }
        @Override public VertexConsumer uv2(int x, int y) { throw new AssertionError(); }
        @Override public VertexConsumer overlayCoords(int x, int y) { throw new AssertionError(); }
        @Override public void defaultColor(int r, int g, int b, int a) { throw new AssertionError(); }
        @Override public void unsetDefaultColor() { throw new AssertionError(); }

        void expect(int count) {
            if (vertices != count) throw new AssertionError("Expected " + count + " vertices, saw " + vertices);
            vertices = 0;
        }
    }

    public static void main(String[] args) {
        var pose = new PoseStack();
        var out = new Vertices();
        OutlineRenderer.drawInteriorEdge(pose, out, new InteriorEdges.Edge(true, 5, 0, 12, 81, 83, true));
        out.expect(10); // solid 12-block edge plus four vertical ticks
        OutlineRenderer.drawInteriorEdge(pose, out, new InteriorEdges.Edge(true, 5, 0, 12, 81, 83, false));
        out.expect(16); // eight short dashes
        var active = new OutlineShape(OutlineShape.Kind.ACTIVE_MONUMENT, -14, 69, -14, 14, 93, 14);
        var inactive = new OutlineShape(OutlineShape.Kind.INACTIVE_MONUMENT, -14, 69, -14, 14, 93, 14);
        OutlineRenderer.monument(pose, out, active, 81);
        out.expect(584); // three 96-segment rings plus four uprights
        OutlineRenderer.monument(pose, out, inactive, 81);
        out.expect(96);
        var siege = new OutlineShape(OutlineShape.Kind.SIEGE_SURVEY, 0, 79, 0, 12, 125, 12);
        OutlineRenderer.siege(pose, out, siege, 81, new Vec3(0, 81, 0));
        out.expect(64); // four sides, two long dashes per side with a low/top/bottom line and upright
        System.out.println("PASS 770 actual in-world line vertices use red #FF3030 across all five boundary styles");
    }
}
