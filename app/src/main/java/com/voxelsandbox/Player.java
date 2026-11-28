package com.voxelsandbox;

/**
 * 玩家：第一人称控制器，含重力、跳跃与 AABB 体素碰撞。
 */
public class Player {
    public float x, y, z;          // 脚部位置
    public float vx, vy, vz;
    public float yaw, pitch;
    public boolean onGround;

    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE = 1.62f;
    private static final float GRAVITY = -28f;
    private static final float JUMP_V = 8.6f;
    private static final float MOVE_SPEED = 5.2f;
    private static final float FLY_SPEED = 9f;

    private final World world;

    public Player(World world, float x, float y, float z) {
        this.world = world;
        this.x = x; this.y = y; this.z = z;
    }

    /** moveX/moveZ 为相对相机朝向的输入（-1..1），fly 为创造模式飞行 */
    public void update(float dt, float moveX, float moveZ, boolean jump, boolean fly) {
        // 水平方向（忽略 pitch）
        float sinY = (float) Math.sin(yaw);
        float cosY = (float) Math.cos(yaw);
        float dx = -sinY * moveZ + cosY * moveX;
        float dz = -cosY * moveZ - sinY * moveX;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-4f) { dx /= len; dz /= len; }
        float speed = fly ? FLY_SPEED : MOVE_SPEED;

        if (fly) {
            vx = dx * speed;
            vz = dz * speed;
            vy = 0;
            if (jump) vy = FLY_SPEED;
        } else {
            vx = dx * speed;
            vz = dz * speed;
            vy += GRAVITY * dt;
            if (vy < -50) vy = -50;
            if (jump && onGround) { vy = JUMP_V; onGround = false; }
        }

        // 分轴移动 + 碰撞
        moveAxis(0, vx * dt);
        moveAxis(1, vy * dt);
        moveAxis(2, vz * dt);

        // 掉出世界保护
        if (y < -20) {
            y = world.getSurfaceHeight((int) x, (int) z) + 2;
            vy = 0;
        }
    }

    private void moveAxis(int axis, float amount) {
        if (amount == 0) return;
        if (axis == 0) x += amount;
        else if (axis == 1) y += amount;
        else z += amount;

        if (collides()) {
            // 回退并清零该轴速度
            if (axis == 0) { x -= amount; vx = 0; }
            else if (axis == 1) {
                y -= amount;
                if (amount < 0) onGround = true;
                vy = 0;
            } else { z -= amount; vz = 0; }
        } else if (axis == 1 && amount < 0) {
            onGround = false;
        }
    }

    private boolean collides() {
        float hw = WIDTH / 2f;
        int minX = (int) Math.floor(x - hw);
        int maxX = (int) Math.floor(x + hw);
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + HEIGHT);
        int minZ = (int) Math.floor(z - hw);
        int maxZ = (int) Math.floor(z + hw);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = minY; by <= maxY; by++)
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (Block.isSolid(world.getBlock(bx, by, bz))) return true;
                }
        return false;
    }

    public void applyTo(Camera cam) {
        cam.x = x;
        cam.y = y + EYE;
        cam.z = z;
        cam.yaw = yaw;
        cam.pitch = pitch;
    }
}
