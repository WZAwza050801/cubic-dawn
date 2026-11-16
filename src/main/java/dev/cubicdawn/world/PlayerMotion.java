package dev.cubicdawn.world;

/**
 * 现有的分轴退回。步长不超过 0.2 时和原来的一截移动相同。
 * 更大的一步切成 0.2，避免高速下落或平移穿过一格厚的墙。
 */
public final class PlayerMotion {

    public static final float SLICE = 0.2f;

    private PlayerMotion() {
    }

    public static final class Pose {
        public float x;
        public float y;
        public float z;
        public float velocityY;

        public Pose(float x, float y, float z, float velocityY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.velocityY = velocityY;
        }
    }

    public static void step(
            Pose pose,
            float frameSeconds,
            float wishX,
            float wishZ,
            boolean jumpEdge,
            PlayerBody.SolidAt solid
    ) {
        float length = (float) Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (length > 0f) {
            float distance = PlayerBody.WALK_SPEED * frameSeconds;
            moveAxis(pose, wishX / length * distance, 0f, solid);
            moveAxis(pose, 0f, wishZ / length * distance, solid);
        }
        if (jumpEdge && PlayerBody.onGround(pose.x, pose.y, pose.z, solid)) {
            pose.velocityY = PlayerBody.JUMP_SPEED;
        } else {
            pose.velocityY -= PlayerBody.GRAVITY * frameSeconds;
        }
        moveVertical(pose, pose.velocityY * frameSeconds, solid);
    }

    public static void moveAxis(Pose pose, float deltaX, float deltaZ, PlayerBody.SolidAt solid) {
        float along = deltaX != 0f ? deltaX : deltaZ;
        if (along == 0f) {
            return;
        }
        float sign = Math.signum(along);
        float remaining = Math.abs(along);
        while (remaining > 0f) {
            float magnitude = Math.min(SLICE, remaining);
            float stepX = deltaX != 0f ? sign * magnitude : 0f;
            float stepZ = deltaZ != 0f ? sign * magnitude : 0f;
            pose.x += stepX;
            pose.z += stepZ;
            if (PlayerBody.overlapsSolid(pose.x, pose.y, pose.z, solid)) {
                pose.x -= stepX;
                pose.z -= stepZ;
                return;
            }
            remaining -= magnitude;
        }
    }

    public static void moveVertical(Pose pose, float deltaY, PlayerBody.SolidAt solid) {
        if (deltaY == 0f) {
            return;
        }
        float sign = Math.signum(deltaY);
        float remaining = Math.abs(deltaY);
        while (remaining > 0f) {
            float magnitude = Math.min(SLICE, remaining);
            float step = sign * magnitude;
            pose.y += step;
            if (PlayerBody.overlapsSolid(pose.x, pose.y, pose.z, solid)) {
                pose.y -= step;
                pose.velocityY = 0f;
                return;
            }
            remaining -= magnitude;
        }
    }
}
