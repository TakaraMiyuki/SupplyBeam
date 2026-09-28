package com.example.supplybeam.client;

import com.example.supplybeam.SupplyBeamConfig;
import com.example.supplybeam.SupplyBeamMod;
import com.example.supplybeam.entity.SupplyCrateEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * 补给光柱渲染器。四层视觉元素：
 * <ol>
 *   <li>光柱：双层十字面片（内芯窄亮 + 外晕宽柔），信标光柱管线全亮渲染，颜色由服务端配置同步；</li>
 *   <li>地面光环：脉动的全亮圆形光环；</li>
 *   <li>补给箱：菱形八面体（双四棱锥）金属外壳，全亮自发光 + 逐面明暗 + 稀有度染色，缓速自转；</li>
 *   <li>发光内核：外壳中央窗格透出的脉冲光核，夜间尤其醒目。</li>
 * </ol>
 * 视锥剔除使用覆盖整根光柱的包围盒（见 getBoundingBoxForCulling），
 * 否则视角稍偏时箱子离开视锥，整根光柱会一起消失（表现为"要来回调视角才能看见"）。
 */
public class SupplyCrateRenderer extends EntityRenderer<SupplyCrateEntity, SupplyCrateRenderer.CrateRenderState> {
    private static final Identifier CRATE_TEXTURE =
        Identifier.fromNamespaceAndPath(SupplyBeamMod.MODID, "textures/entity/crate.png");
    private static final Identifier BEAM_TEXTURE =
        Identifier.fromNamespaceAndPath(SupplyBeamMod.MODID, "textures/entity/beam.png");
    private static final Identifier RING_TEXTURE =
        Identifier.fromNamespaceAndPath(SupplyBeamMod.MODID, "textures/entity/ring.png");

    private static final float R = SupplyBeamConfig.CRATE_RADIUS;
    private static final float H = SupplyBeamConfig.CRATE_HALF_HEIGHT;
    private static final float BEAM_INNER = SupplyBeamConfig.BEAM_INNER_RADIUS;
    private static final float BEAM_OUTER = SupplyBeamConfig.BEAM_OUTER_RADIUS;
    private static final int FULL_BRIGHT = 15728880;
    private static final int TEX_SIZE = 128;
    private static final int CELL = 64;
    /** UV 向格内收缩量（像素），防止线性采样跨格渗色。 */
    private static final float UV_INSET = 1.0f;

    /** 八面体六个顶点：0=上尖 1=下尖 2=东 3=西 4=北 5=南。 */
    private static final float[][] VERTICES = {
        {0.0f, H, 0.0f}, {0.0f, -H, 0.0f}, {R, 0.0f, 0.0f}, {-R, 0.0f, 0.0f},
        {0.0f, 0.0f, -R}, {0.0f, 0.0f, R}
    };

    /**
     * 八个三角面：{顶点a, 顶点b, 顶点c, 贴图格, 面亮度}。
     * 环绕顺序均为从外侧看逆时针（外向法线）；上面四格用上两象限、下面四格用下两象限。
     */
    private static final int[][] FACES = {
        {0, 5, 2, 0, 233},  // 东南上：朝阳面
        {0, 3, 5, 1, 210},  // 西南上
        {0, 4, 3, 0, 188},  // 西北上：背光面
        {0, 2, 4, 1, 222},  // 东北上
        {1, 2, 5, 2, 158},  // 东南下
        {1, 5, 3, 3, 140},  // 西南下
        {1, 3, 4, 2, 122},  // 西北下
        {1, 4, 2, 3, 168}   // 东北下
    };

    public SupplyCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2f;
    }

    @Override
    public CrateRenderState createRenderState() {
        return new CrateRenderState();
    }

    @Override
    public void extractRenderState(SupplyCrateEntity entity, CrateRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.rarity = entity.rarity().ordinal();
        state.phase = entity.phase();
        state.groundY = entity.groundY();
        state.topY = entity.topY();
        state.phaseTick = entity.phaseTick();
        state.color = entity.color();
        state.growthTicks = entity.growthTicks();
    }

    /**
     * 视锥剔除包围盒覆盖整根光柱（含地面光环），
     * 修复"光柱要来回调整视角才能看见"的问题。
     */
    @Override
    protected AABB getBoundingBoxForCulling(SupplyCrateEntity entity) {
        double x = entity.getX(), z = entity.getZ();
        double minY = Math.min(entity.getY(), entity.groundY()) - 1.0;
        double maxY = Math.max(entity.getY() + SupplyCrateEntity.REST_CENTER_HEIGHT * 2.0,
            entity.topY() + 2.0);
        return new AABB(x - 2.5, minY, z - 2.5, x + 2.5, maxY, z + 2.5);
    }

    @Override
    public void submit(CrateRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.ageInTicks;
        int color = state.color & 0xFFFFFF;

        // 消散进度（0=完整，1=完全消失）
        float fade = 1.0f;
        if (state.phase == SupplyCrateEntity.PHASE_VANISHING) {
            fade = Mth.clamp(1.0f - (age - state.phaseTick) / SupplyBeamConfig.VANISH_TICKS, 0.0f, 1.0f);
        }

        // ===== 光柱与地面光环（本地坐标：实体原点=地面锚点） =====
        float beamBase = state.groundY - (float) state.y;
        float beamTop = state.topY - (float) state.y;
        float grow = 1.0f;
        if (state.phase == SupplyCrateEntity.PHASE_GATHERING) {
            float t = Mth.clamp((age - state.phaseTick) / Math.max(1, state.growthTicks), 0.0f, 1.0f);
            grow = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t); // easeOutCubic：先快后缓
        }
        float top = beamBase + (beamTop - beamBase) * grow;
        if (top > beamBase + 0.05f) {
            renderBeam(poseStack, collector, beamBase, top, color, age, fade);
            renderRing(poseStack, collector, beamBase + 0.06f, color, age, fade);
        }

        // ===== 补给箱 =====
        if (state.phase != SupplyCrateEntity.PHASE_GATHERING && fade > 0.01f) {
            renderCrate(poseStack, collector, state, color, age, fade);
        }
        super.submit(state, poseStack, collector, camera);
    }

    // ==================== 光柱 ====================

    private void renderBeam(PoseStack poseStack, SubmitNodeCollector collector,
                            float y0, float y1, int color, float age, float fade) {
        float pulse = 0.85f + 0.15f * Mth.sin(age * 0.09f);
        int inner = tint(color, 1.0f, 0.92f * pulse * fade);
        int halo = tint(color, 1.0f, 0.34f * pulse * fade);
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM_TEXTURE, true), (pose, buf) -> {
            beamQuad(pose, buf, inner, y0, y1, BEAM_INNER, 0.0f);
            beamQuad(pose, buf, inner, y0, y1, BEAM_INNER, 90.0f);
            beamQuad(pose, buf, halo, y0, y1, BEAM_OUTER, 45.0f);
            beamQuad(pose, buf, halo, y0, y1, BEAM_OUTER, 135.0f);
        });
    }

    /** 一条竖直面片（正反两面都提交，穿过光柱时依然可见）。 */
    private static void beamQuad(PoseStack.Pose pose, VertexConsumer buf, int argb,
                                 float y0, float y1, float radius, float angleDeg) {
        float angle = angleDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float ax = -radius * cos, az = radius * sin;
        float bx = radius * cos, bz = -radius * sin;
        // 正面
        vertex(pose, buf, argb, ax, y0, az, 0, 1);
        vertex(pose, buf, argb, bx, y0, bz, 1, 1);
        vertex(pose, buf, argb, bx, y1, bz, 1, 0);
        vertex(pose, buf, argb, ax, y1, az, 0, 0);
        // 背面（信标管线剔除背面）
        vertex(pose, buf, argb, bx, y0, bz, 1, 1);
        vertex(pose, buf, argb, ax, y0, az, 0, 1);
        vertex(pose, buf, argb, ax, y1, az, 0, 0);
        vertex(pose, buf, argb, bx, y1, bz, 1, 0);
    }

    // ==================== 地面光环 ====================

    private void renderRing(PoseStack poseStack, SubmitNodeCollector collector,
                            float y, int color, float age, float fade) {
        float pulse = 0.7f + 0.3f * Mth.sin(age * 0.12f);
        int argb = tint(color, 1.0f, 0.8f * pulse * fade);
        float size = 2.4f + 0.45f * Mth.sin(age * 0.12f);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(RING_TEXTURE),
            (pose, buf) -> {
                groundQuad(pose, buf, argb, size, y, 1.0f);
                groundQuad(pose, buf, argb, size, y, -1.0f);
            });
    }

    /** 水平圆环面片（贴图自带径向渐隐），normal 翻转兼容俯仰视角。 */
    private static void groundQuad(PoseStack.Pose pose, VertexConsumer buf, int argb, float size, float y, float normalY) {
        vertex(pose, buf, argb, -size, y, -size, 0, 0, 0, normalY, 0, FULL_BRIGHT);
        vertex(pose, buf, argb, size, y, -size, 1, 0, 0, normalY, 0, FULL_BRIGHT);
        vertex(pose, buf, argb, size, y, size, 1, 1, 0, normalY, 0, FULL_BRIGHT);
        vertex(pose, buf, argb, -size, y, size, 0, 1, 0, normalY, 0, FULL_BRIGHT);
    }

    // ==================== 补给箱（菱形八面体） ====================

    private void renderCrate(PoseStack poseStack, SubmitNodeCollector collector,
                             CrateRenderState state, int color, float age, float fade) {
        poseStack.pushPose();
        poseStack.translate(0.0, SupplyCrateEntity.REST_CENTER_HEIGHT, 0.0);
        if (state.phase == SupplyCrateEntity.PHASE_VANISHING) {
            poseStack.scale(fade, fade, fade);
        }
        // 降落时自转稍快，着陆后近乎静止地悬浮呼吸
        float spin = state.phase == SupplyCrateEntity.PHASE_DESCENDING ? age * 0.025f : age * 0.008f;
        poseStack.mulPose(Axis.YP.rotation(spin));
        if (state.phase == SupplyCrateEntity.PHASE_LANDED) {
            poseStack.translate(0.0, Mth.sin(age * 0.06f) * 0.06f, 0.0);
        }

        // 金属外壳：全亮自发光 + 逐面明暗 + 稀有度染色（夜晚/暗处不再隐没）
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(CRATE_TEXTURE),
            (pose, buf) -> octahedron(pose, buf, color, fade, false));

        // 发光内核：从中央窗格透出的稀有度光，缓慢脉冲
        float coreScale = 0.55f * (0.88f + 0.12f * Mth.sin(age * 0.15f)) * fade;
        poseStack.pushPose();
        poseStack.scale(coreScale, coreScale, coreScale);
        int coreColor = brighten(color, 0.45f);
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM_TEXTURE, true),
            (pose, buf) -> octahedron(pose, buf, coreColor, 0.95f, true));
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * 菱形八面体网格：8 个三角面、逐面法线与明暗。
     * uniformColor=true 时所有面同色（内核），否则按 FACES 亮度表做平板着色。
     */
    private static void octahedron(PoseStack.Pose pose, VertexConsumer buf, int color,
                                   float alpha, boolean uniformColor) {
        for (int[] face : FACES) {
            int a = face[0], b = face[1], c = face[2];
            int cell = face[3];
            float shade = uniformColor ? 1.0f : face[4] / 255.0f;
            int argb = tint(color, shade, alpha);

            float[] va = VERTICES[a], vb = VERTICES[b], vc = VERTICES[c];
            Vector3f normal = faceNormal(va, vb, vc);

            float[][] verts = {va, vb, vc};
            int cellX = (cell & 1) * CELL;
            int cellY = (cell & 2) * (CELL / 2);
            boolean apexUp = (a == 0); // 上面四格：上尖朝上；下面四格：下尖朝下
            float apexU = cellX + CELL / 2.0f, apexV = cellY + (apexUp ? UV_INSET : CELL - UV_INSET);
            float bU = cellX + UV_INSET, bV = cellY + (apexUp ? CELL - UV_INSET : UV_INSET);
            float cU = cellX + CELL - UV_INSET, cV = cellY + (apexUp ? CELL - UV_INSET : UV_INSET);
            float[][] uvs = {{apexU, apexV}, {bU, bV}, {cU, cV}};

            for (int i = 0; i < 3; i++) {
                float[] v = verts[i];
                vertex(pose, buf, argb, v[0], v[1], v[2], uvs[i][0] / TEX_SIZE, uvs[i][1] / TEX_SIZE,
                    normal.x, normal.y, normal.z, FULL_BRIGHT);
            }
        }
    }

    private static Vector3f faceNormal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6f) {
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return new Vector3f(nx / len, ny / len, nz / len);
    }

    // ==================== 顶点工具 ====================

    private static void vertex(PoseStack.Pose pose, VertexConsumer buf, int argb,
                               float x, float y, float z, float u, float v) {
        buf.addVertex(pose, x, y, z).setColor(argb).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
            .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buf, int argb,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light) {
        buf.addVertex(pose, x, y, z).setColor(argb).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    /** 稀有度 RGB × 明暗 × 透明度 → ARGB。 */
    private static int tint(int color, float shade, float alpha) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * shade));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * shade));
        int b = Math.min(255, (int) ((color & 0xFF) * shade));
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 向白色混合，用于发光内核提亮。 */
    private static int brighten(int color, float amount) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    public static class CrateRenderState extends EntityRenderState {
        public int rarity;
        public byte phase;
        public float groundY;
        public float topY;
        public int phaseTick;
        public int color;
        public int growthTicks;
    }
}
