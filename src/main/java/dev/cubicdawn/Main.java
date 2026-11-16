package dev.cubicdawn;

import dev.cubicdawn.world.BlockIds;
import dev.cubicdawn.world.BlockWorld;
import dev.cubicdawn.world.ButtonEdge;
import dev.cubicdawn.world.CellView;
import dev.cubicdawn.world.EditHotbar;
import dev.cubicdawn.world.EditReceipt;
import dev.cubicdawn.world.PlayerBody;
import dev.cubicdawn.world.PlayerMotion;
import dev.cubicdawn.world.PlayerState;
import dev.cubicdawn.world.SnapshotIO;
import dev.cubicdawn.world.VoxelRay;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class Main {

    private static final int WINDOW_WIDTH = 960;
    private static final int WINDOW_HEIGHT = 540;
    private static final String WINDOW_TITLE = "Cubic Dawn";
    private static final float FIELD_OF_VIEW_DEGREES = 50f;
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 100f;
    private static final float MOUSE_SENSITIVITY = 0.002f;
    private static final float MAX_PITCH = (float) Math.toRadians(89.0);
    private static final float MAX_FRAME_SECONDS = 0.05f;
    private static final float REACH = 5f;
    private static final int VERTEX_COUNT = 36;
    private static final int FLOATS_PER_VERTEX = 6;
    private static final float[] GRASS_COLOR = {0.36f, 0.70f, 0.28f};
    private static final float[] DIRT_COLOR = {0.58f, 0.38f, 0.22f};
    private static final float[] STONE_COLOR = {0.62f, 0.64f, 0.66f};
    private static final float[] MARKER_POSITIVE_X_COLOR = {0.90f, 0.25f, 0.22f};
    private static final float[] MARKER_NEGATIVE_X_COLOR = {0.25f, 0.75f, 0.35f};
    private static final float[] MARKER_POSITIVE_Z_COLOR = {0.28f, 0.55f, 0.92f};
    private static final float[] MARKER_NEGATIVE_Z_COLOR = {0.92f, 0.82f, 0.28f};
    private static final float[] UNKNOWN_COLOR = {1f, 0f, 1f};
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] SELECT_COLOR = {0.84f, 0.71f, 0.42f};
    private static final float[] CROSSHAIR_COLOR = {0.91f, 0.91f, 0.85f};
    private static final float[] PAUSE_COLOR = {0.45f, 0.22f, 0.12f};

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec3 position;
            layout (location = 1) in vec3 color;

            uniform mat4 model;
            uniform mat4 view;
            uniform mat4 projection;

            out vec3 vertexColor;

            void main() {
                gl_Position = projection * view * model * vec4(position, 1.0);
                vertexColor = color;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec3 vertexColor;
            uniform vec3 materialColor;
            out vec4 fragmentColor;

            void main() {
                fragmentColor = vec4(vertexColor * materialColor, 1.0);
            }
            """;

    private long window;
    private boolean capabilitiesReady;
    private int program;
    private int cubeVao;
    private int cubeVbo;
    private int lineVao;
    private int lineVbo;
    private int quadVao;
    private int quadVbo;
    private int modelLocation = -1;
    private int viewLocation = -1;
    private int projectionLocation = -1;
    private int materialLocation = -1;
    private float[] viewMatrix = identity();
    private float eyeX = 2f;
    private float eyeY = 2f;
    private float eyeZ = 3f;
    private float velocityY;
    private float yaw;
    private float pitch;
    private double lastFrameTime;
    private double lastCursorX;
    private double lastCursorY;
    private boolean cursorReady;
    private boolean focused = true;
    private boolean paused;
    private boolean saveAllowed;
    private int selectedSlot;
    private int scrollPending;
    private int framesRemaining = Integer.MAX_VALUE;
    private long nextCommand = 1L;
    private String lastAimText = "";
    private BlockWorld blocks;
    private final ButtonEdge leftEdge = new ButtonEdge();
    private final ButtonEdge rightEdge = new ButtonEdge();
    private final ButtonEdge jumpEdge = new ButtonEdge();
    private final ButtonEdge pauseEdge = new ButtonEdge();
    private final ButtonEdge quitEdge = new ButtonEdge();
    private final ButtonEdge saveEdge = new ButtonEdge();
    private GLFWScrollCallback scrollCallback;
    private GLFWWindowFocusCallback focusCallback;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        try {
            initialize();
            loop();
        } finally {
            cleanup();
        }
    }

    private void initialize() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed.");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DEPTH_BITS, 24);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_TITLE, NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Window creation failed.");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        focused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
        updateCursorMode();
        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }
        scrollCallback = GLFWScrollCallback.create((handle, offsetX, offsetY) -> {
            if (offsetY > 0d) {
                scrollPending--;
            } else if (offsetY < 0d) {
                scrollPending++;
            }
        });
        focusCallback = GLFWWindowFocusCallback.create((handle, focusedNow) -> {
            focused = focusedNow;
            cursorReady = false;
            disarmPlayButtons();
            updateCursorMode();
        });
        glfwSetScrollCallback(window, scrollCallback);
        glfwSetWindowFocusCallback(window, focusCallback);

        GL.createCapabilities();
        capabilitiesReady = true;
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("LWJGL: " + Version.getVersion());
        System.out.println("OpenGL: " + GL11.glGetString(GL11.GL_VERSION));
        System.out.println("Edit test: left break, right place, 1-9 or wheel selects, Esc pauses, Q quits while paused, F5 saves.");
        System.out.println("Java Edition mechanics version: not set. This bar does not spend items.");

        float[] cube = cubeVertices();
        if (cube.length != VERTEX_COUNT * FLOATS_PER_VERTEX) {
            throw new IllegalStateException("Cube vertex array length is " + cube.length + ", expected 216.");
        }
        int[] cubeMesh = uploadMesh(cube);
        cubeVao = cubeMesh[0];
        cubeVbo = cubeMesh[1];
        int[] lineMesh = uploadMesh(lineVertices());
        lineVao = lineMesh[0];
        lineVbo = lineMesh[1];
        int[] quadMesh = uploadMesh(quadVertices());
        quadVao = quadMesh[0];
        quadVbo = quadMesh[1];

        blocks = BlockWorld.generateIsland();
        blocks.checkGeneratedIsland();
        BlockWorld roundTrip = BlockWorld.empty();
        roundTrip.applySnapshot(SnapshotIO.parse(SnapshotIO.encode(blocks, null, blocks.revision())));
        if (!roundTrip.sameSolids(blocks)) {
            throw new IllegalStateException("Saving and loading did not restore the generated blocks.");
        }
        placeSpawn();
        float[] angles = anglesLookingAtOrigin();
        yaw = angles[0];
        pitch = angles[1];
        viewMatrix = viewFromAngles();
        assertViewMatchesLookAtOrigin(viewMatrix);
        int[] framebuffer = framebufferSize();
        float[] projection = perspective(
                (float) Math.toRadians(FIELD_OF_VIEW_DEGREES),
                aspectRatio(framebuffer[0], framebuffer[1]),
                NEAR_PLANE,
                FAR_PLANE
        );
        assertCornersVisible(viewMatrix, projection);
        loadOrCreateWorld();

        program = createProgram();
        modelLocation = uniformLocation("model");
        viewLocation = uniformLocation("view");
        projectionLocation = uniformLocation("projection");
        materialLocation = uniformLocation("materialColor");
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        String frameLimit = System.getProperty("cubicdawn.frames");
        if (frameLimit != null && !frameLimit.isBlank()) {
            framesRemaining = Integer.parseInt(frameLimit);
        }
        lastFrameTime = glfwGetTime();
        System.out.println("Walk speed: " + PlayerBody.WALK_SPEED);
        System.out.println("Gravity: " + PlayerBody.GRAVITY);
        System.out.println("Jump speed: " + PlayerBody.JUMP_SPEED);
        System.out.println("Reach: " + REACH);
        System.out.println("Solid blocks: " + blocks.countSolid());
        System.out.println("Hotbar: " + blockName(EditHotbar.idAt(selectedSlot)));
    }

    private void loadOrCreateWorld() {
        Path path = worldPath();
        saveAllowed = true;
        boolean existing = Files.exists(path) || Files.exists(SnapshotIO.backupPath(path));
        if (!existing) {
            saveNow("new world");
            return;
        }
        try {
            SnapshotIO.Snapshot snapshot = SnapshotIO.load(path);
            blocks.applySnapshot(snapshot);
            if (snapshot.restoredFromBackup()) {
                System.out.println("Restored world from the previous snapshot.");
            }
            if (!snapshot.unknownIds().isEmpty()) {
                System.out.println("Unknown block ids kept: " + snapshot.unknownIds());
            }
            if (snapshot.player() != null) {
                PlayerState player = snapshot.player();
                eyeX = player.x();
                eyeY = player.y();
                eyeZ = player.z();
                yaw = player.yaw();
                pitch = player.pitch();
                selectedSlot = player.slot();
                System.out.println("Restored player at " + eyeX + " " + eyeY + " " + eyeZ);
            } else {
                placeSpawn();
                float[] angles = anglesLookingAtOrigin();
                yaw = angles[0];
                pitch = angles[1];
                System.out.println("Legacy save has no player; spawned on the island.");
            }
            System.out.println("Loaded " + path + ": " + blocks.countSolid());
        } catch (RuntimeException | IOException exception) {
            saveAllowed = false;
            throw new IllegalStateException(
                    "Refusing to replace a save that did not load: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            handleModeKeys();
            double now = glfwGetTime();
            float frameSeconds = (float) (now - lastFrameTime);
            lastFrameTime = now;
            if (frameSeconds > MAX_FRAME_SECONDS) {
                frameSeconds = MAX_FRAME_SECONDS;
            }
            if (frameSeconds < 0f) {
                frameSeconds = 0f;
            }
            if (playing()) {
                updateLook();
                updateMove(frameSeconds);
                handleHotbar();
                handleEdit();
            } else {
                cursorReady = false;
                disarmPlayButtons();
                scrollPending = 0;
            }
            viewMatrix = viewFromAngles();

            int[] framebuffer = framebufferSize();
            if (framebuffer[1] == 0) {
                glfwSwapBuffers(window);
                continue;
            }
            GL11.glViewport(0, 0, framebuffer[0], framebuffer[1]);
            GL11.glClearColor(0.04f, 0.06f, 0.09f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            float[] projection = perspective(
                    (float) Math.toRadians(FIELD_OF_VIEW_DEGREES),
                    aspectRatio(framebuffer[0], framebuffer[1]),
                    NEAR_PLANE,
                    FAR_PLANE
            );
            GL20.glUseProgram(program);
            uploadMatrix(viewLocation, viewMatrix);
            uploadMatrix(projectionLocation, projection);
            drawSolidBlocks();
            if (playing()) {
                drawOutline(aim());
            }
            drawHud(framebuffer[0], framebuffer[1]);
            glfwSwapBuffers(window);
            if (framesRemaining != Integer.MAX_VALUE) {
                framesRemaining--;
                if (framesRemaining <= 0) {
                    glfwSetWindowShouldClose(window, true);
                }
            }
        }
    }

    private void cleanup() {
        if (saveAllowed) {
            saveNow("exit");
        }
        if (capabilitiesReady) {
            deleteMesh(cubeVao, cubeVbo);
            deleteMesh(lineVao, lineVbo);
            deleteMesh(quadVao, quadVbo);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
        }
        if (window != NULL) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }
        glfwTerminate();
        GLFWErrorCallback errorCallback = glfwSetErrorCallback(null);
        if (errorCallback != null) {
            errorCallback.free();
        }
    }

    private boolean playing() {
        return focused && !paused;
    }

    private void handleModeKeys() {
        if (!focused) {
            pauseEdge.disarm();
            quitEdge.disarm();
            saveEdge.disarm();
            return;
        }
        if (pauseEdge.poll(key(GLFW_KEY_ESCAPE))) {
            paused = !paused;
            cursorReady = false;
            disarmPlayButtons();
            updateCursorMode();
            System.out.println(paused ? "Paused. Press Q to quit." : "Resumed.");
        }
        if (paused && quitEdge.poll(key(GLFW_KEY_Q))) {
            glfwSetWindowShouldClose(window, true);
        }
        if (saveEdge.poll(key(GLFW_KEY_F5))) {
            saveNow("manual");
        }
    }

    private void disarmPlayButtons() {
        leftEdge.disarm();
        rightEdge.disarm();
        jumpEdge.disarm();
    }

    private void updateCursorMode() {
        if (window == NULL) {
            return;
        }
        int mode = playing() ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL;
        glfwSetInputMode(window, GLFW_CURSOR, mode);
    }

    private void updateLook() {
        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        glfwGetCursorPos(window, cursorX, cursorY);
        if (!cursorReady) {
            lastCursorX = cursorX[0];
            lastCursorY = cursorY[0];
            cursorReady = true;
            return;
        }
        float deltaX = (float) (cursorX[0] - lastCursorX);
        float deltaY = (float) (cursorY[0] - lastCursorY);
        lastCursorX = cursorX[0];
        lastCursorY = cursorY[0];
        yaw += deltaX * MOUSE_SENSITIVITY;
        pitch -= deltaY * MOUSE_SENSITIVITY;
        if (pitch > MAX_PITCH) {
            pitch = MAX_PITCH;
        }
        if (pitch < -MAX_PITCH) {
            pitch = -MAX_PITCH;
        }
    }

    private void updateMove(float frameSeconds) {
        float[] forward = horizontalForward(yaw);
        float[] right = horizontalRight(yaw);
        float wishX = 0f;
        float wishZ = 0f;
        if (key(GLFW_KEY_W)) {
            wishX += forward[0];
            wishZ += forward[2];
        }
        if (key(GLFW_KEY_S)) {
            wishX -= forward[0];
            wishZ -= forward[2];
        }
        if (key(GLFW_KEY_D)) {
            wishX += right[0];
            wishZ += right[2];
        }
        if (key(GLFW_KEY_A)) {
            wishX -= right[0];
            wishZ -= right[2];
        }
        PlayerMotion.Pose pose = new PlayerMotion.Pose(eyeX, eyeY, eyeZ, velocityY);
        boolean jump = jumpEdge.poll(key(GLFW_KEY_SPACE));
        PlayerMotion.step(pose, frameSeconds, wishX, wishZ, jump, blocks::isSolidAt);
        eyeX = pose.x;
        eyeY = pose.y;
        eyeZ = pose.z;
        velocityY = pose.velocityY;
    }

    private void handleHotbar() {
        if (scrollPending != 0) {
            int before = selectedSlot;
            selectedSlot = EditHotbar.shift(selectedSlot, scrollPending);
            scrollPending = 0;
            if (selectedSlot != before) {
                System.out.println("Hotbar " + (selectedSlot + 1) + " " + blockName(EditHotbar.idAt(selectedSlot)));
            }
        }
        for (int index = 0; index < EditHotbar.SLOTS; index++) {
            if (key(GLFW_KEY_1 + index) && selectedSlot != index) {
                selectedSlot = index;
                System.out.println("Hotbar " + (selectedSlot + 1) + " " + blockName(EditHotbar.idAt(selectedSlot)));
            }
        }
    }

    private void handleEdit() {
        VoxelRay.Hit hit = aim();
        String text = describeAim(hit);
        if (!text.equals(lastAimText)) {
            lastAimText = text;
            System.out.println(text);
        }
        if (leftEdge.poll(mouse(GLFW_MOUSE_BUTTON_LEFT))) {
            breakAimed(hit);
        }
        if (rightEdge.poll(mouse(GLFW_MOUSE_BUTTON_RIGHT))) {
            placeAimed(hit);
        }
    }

    private void breakAimed(VoxelRay.Hit hit) {
        if (hit.kind() == VoxelRay.Kind.BLOCKED) {
            System.out.println("Break rejected: unloaded");
            return;
        }
        if (hit.kind() != VoxelRay.Kind.HIT) {
            System.out.println("Break rejected: miss");
            return;
        }
        if (hit.distance() > REACH) {
            System.out.println("Break rejected: reach");
            return;
        }
        EditReceipt receipt = blocks.breakBlock(nextCommand++, hit.x(), hit.y(), hit.z(), hit.revision());
        if (!receipt.accepted()) {
            System.out.println("Break rejected: " + explain(receipt.reason()));
            return;
        }
        System.out.println("Removed " + hit.x() + " " + hit.y() + " " + hit.z());
        saveNow("break");
    }

    private void placeAimed(VoxelRay.Hit hit) {
        int[] target = VoxelRay.placeTarget(hit);
        if (target == null) {
            String reason = hit.startedInside() ? "started_inside" : "miss";
            System.out.println("Place rejected: " + explain(reason));
            return;
        }
        if (hit.distance() > REACH) {
            System.out.println("Place rejected: reach");
            return;
        }
        CellView cell = blocks.query(target[0], target[1], target[2]);
        int id = EditHotbar.idAt(selectedSlot);
        EditReceipt receipt = blocks.placeBlock(
                nextCommand++,
                target[0],
                target[1],
                target[2],
                id,
                cell.revision(),
                (x, y, z) -> PlayerBody.overlapsBlock(eyeX, eyeY, eyeZ, x, y, z)
        );
        if (!receipt.accepted()) {
            System.out.println("Place rejected: " + explain(receipt.reason()));
            return;
        }
        System.out.println("Placed " + blockName(id) + " at " + target[0] + " " + target[1] + " " + target[2]);
        saveNow("place");
    }

    private VoxelRay.Hit aim() {
        float[] direction = lookDirection(yaw, pitch);
        return VoxelRay.castFromEngineEye(
                eyeX, eyeY, eyeZ,
                direction[0], direction[1], direction[2],
                REACH,
                blocks
        );
    }

    private void saveNow(String reason) {
        if (!saveAllowed || blocks == null) {
            return;
        }
        try {
            PlayerState player = new PlayerState(eyeX, eyeY, eyeZ, yaw, pitch, selectedSlot);
            SnapshotIO.SaveResult result = SnapshotIO.save(worldPath(), blocks, player);
            if (blocks.dirty() || result.coveredLaterEdits()) {
                System.out.println("Saved " + reason + " revision " + result.revision() + "; newer edits remain.");
            } else {
                System.out.println("Saved " + reason + " revision " + result.revision() + ".");
            }
        } catch (IOException | RuntimeException exception) {
            System.out.println("Save failed (" + reason + "): " + exception.getMessage());
        }
    }

    private void placeSpawn() {
        eyeX = 2f;
        eyeZ = 3f;
        eyeY = blocks.highestSolid(2, 3) + 1.4f + 0.25f;
    }

    private void drawSolidBlocks() {
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(cubeVao);
        for (int y = BlockWorld.BLOCK_MIN; y <= BlockWorld.BLOCK_MAX; y++) {
            for (int z = BlockWorld.BLOCK_MIN; z <= BlockWorld.BLOCK_MAX; z++) {
                for (int x = BlockWorld.BLOCK_MIN; x <= BlockWorld.BLOCK_MAX; x++) {
                    int id = blocks.getId(x, y, z);
                    if (id == BlockIds.AIR) {
                        continue;
                    }
                    float[] color = colorOf(id);
                    GL20.glUniform3f(materialLocation, color[0], color[1], color[2]);
                    uploadMatrix(modelLocation, translation(x, y, z));
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, VERTEX_COUNT);
                }
            }
        }
    }

    private void drawOutline(VoxelRay.Hit hit) {
        if (hit.kind() != VoxelRay.Kind.HIT) {
            return;
        }
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL30.glBindVertexArray(lineVao);
        GL20.glUniform3f(materialLocation, WHITE[0], WHITE[1], WHITE[2]);
        uploadMatrix(modelLocation, scaledTranslation(hit.x(), hit.y(), hit.z(), 1.02f));
        GL11.glDrawArrays(GL11.GL_LINES, 0, 24);
    }

    private void drawHud(int width, int height) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        uploadMatrix(viewLocation, identity());
        uploadMatrix(projectionLocation, ortho(width, height));
        GL30.glBindVertexArray(quadVao);
        int size = 36;
        int gap = 8;
        int total = EditHotbar.SLOTS * size + (EditHotbar.SLOTS - 1) * gap;
        int left = (width - total) / 2;
        int bottom = 18;
        for (int index = 0; index < EditHotbar.SLOTS; index++) {
            int x = left + index * (size + gap);
            if (index == selectedSlot) {
                drawQuad(x - 4, bottom - 4, size + 8, size + 8, SELECT_COLOR);
            }
            drawQuad(x, bottom, size, size, colorOf(EditHotbar.idAt(index)));
        }
        int centerX = width / 2;
        int centerY = height / 2;
        drawQuad(centerX - 8, centerY - 1, 6, 2, CROSSHAIR_COLOR);
        drawQuad(centerX + 2, centerY - 1, 6, 2, CROSSHAIR_COLOR);
        drawQuad(centerX - 1, centerY + 2, 2, 6, CROSSHAIR_COLOR);
        drawQuad(centerX - 1, centerY - 8, 2, 6, CROSSHAIR_COLOR);
        if (paused) {
            drawQuad(0, height - 24, width, 24, PAUSE_COLOR);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void drawQuad(int x, int y, int width, int height, float[] color) {
        GL20.glUniform3f(materialLocation, color[0], color[1], color[2]);
        uploadMatrix(modelLocation, quadTransform(x, y, width, height));
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private static Path worldPath() {
        String override = System.getProperty("cubicdawn.world");
        if (override == null || override.isBlank()) {
            return Path.of("world.txt");
        }
        return Path.of(override);
    }

    private boolean key(int code) {
        return glfwGetKey(window, code) == GLFW_PRESS;
    }

    private boolean mouse(int button) {
        return glfwGetMouseButton(window, button) == GLFW_PRESS;
    }

    private int[] uploadMesh(float[] vertices) {
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(vertices.length);
            buffer.put(vertices).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        }
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
        return new int[] {vao, vbo};
    }

    private static void deleteMesh(int vao, int vbo) {
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
        }
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
        }
    }

    private int createProgram() {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vertexShader);
        GL20.glAttachShader(shaderProgram, fragmentShader);
        GL20.glLinkProgram(shaderProgram);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(shaderProgram);
            GL20.glDeleteProgram(shaderProgram);
            throw new IllegalStateException("Shader link failed: " + log);
        }
        return shaderProgram;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return shader;
    }

    private int uniformLocation(String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location == -1) {
            throw new IllegalStateException("Uniform not found: " + name);
        }
        return location;
    }

    private static void uploadMatrix(int location, float[] matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            buffer.put(matrix).flip();
            GL20.glUniformMatrix4fv(location, false, buffer);
        }
    }

    private int[] framebufferSize() {
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        return new int[] {width[0], height[0]};
    }

    private static float aspectRatio(int width, int height) {
        if (height == 0) {
            return WINDOW_WIDTH / (float) WINDOW_HEIGHT;
        }
        return width / (float) height;
    }

    /**
     * 六个面，每面两个三角形。从立方体外侧看是逆时针，所以法线指向外面。
     * 一个顶点 6 个数：x, y, z, r, g, b。共 36 个顶点。
     */
    private static float[] cubeVertices() {
        float[] out = new float[VERTEX_COUNT * FLOATS_PER_VERTEX];
        int offset = 0;
        offset = face(out, offset, 0.90f, 0.25f, 0.22f,
                0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
                0.5f, -0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f);
        offset = face(out, offset, 0.25f, 0.75f, 0.35f,
                -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
                -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f);
        offset = face(out, offset, 0.92f, 0.82f, 0.28f,
                -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
                -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f);
        offset = face(out, offset, 0.55f, 0.35f, 0.75f,
                0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f,
                0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f);
        offset = face(out, offset, 0.95f, 0.55f, 0.18f,
                -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
                -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f);
        face(out, offset, 0.28f, 0.55f, 0.92f,
                0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
                0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f);
        return out;
    }

    private static float[] lineVertices() {
        float[] out = new float[24 * FLOATS_PER_VERTEX];
        int[][] edges = {
                {0, 0, 0, 1, 0, 0}, {0, 0, 0, 0, 1, 0}, {0, 0, 0, 0, 0, 1},
                {1, 0, 0, 1, 1, 0}, {1, 0, 0, 1, 0, 1}, {0, 1, 0, 1, 1, 0},
                {0, 1, 0, 0, 1, 1}, {0, 0, 1, 1, 0, 1}, {0, 0, 1, 0, 1, 1},
                {1, 1, 0, 1, 1, 1}, {1, 0, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1}
        };
        int offset = 0;
        for (int[] edge : edges) {
            offset = vertex(out, offset, edgeCoordinate(edge[0]), edgeCoordinate(edge[1]), edgeCoordinate(edge[2]), 1f, 1f, 1f);
            offset = vertex(out, offset, edgeCoordinate(edge[3]), edgeCoordinate(edge[4]), edgeCoordinate(edge[5]), 1f, 1f, 1f);
        }
        return out;
    }

    private static float edgeCoordinate(int bit) {
        return bit == 0 ? -0.5f : 0.5f;
    }

    private static float[] quadVertices() {
        float[] out = new float[6 * FLOATS_PER_VERTEX];
        int offset = 0;
        offset = vertex(out, offset, 0f, 0f, 0f, 1f, 1f, 1f);
        offset = vertex(out, offset, 1f, 0f, 0f, 1f, 1f, 1f);
        offset = vertex(out, offset, 1f, 1f, 0f, 1f, 1f, 1f);
        offset = vertex(out, offset, 0f, 0f, 0f, 1f, 1f, 1f);
        offset = vertex(out, offset, 1f, 1f, 0f, 1f, 1f, 1f);
        vertex(out, offset, 0f, 1f, 0f, 1f, 1f, 1f);
        return out;
    }

    private static int face(
            float[] out,
            int offset,
            float red,
            float green,
            float blue,
            float... coordinates
    ) {
        for (int vertexIndex = 0; vertexIndex < 6; vertexIndex++) {
            int coordinate = vertexIndex * 3;
            offset = vertex(
                    out,
                    offset,
                    coordinates[coordinate],
                    coordinates[coordinate + 1],
                    coordinates[coordinate + 2],
                    red,
                    green,
                    blue
            );
        }
        return offset;
    }

    private static int vertex(
            float[] out,
            int offset,
            float x,
            float y,
            float z,
            float red,
            float green,
            float blue
    ) {
        out[offset] = x;
        out[offset + 1] = y;
        out[offset + 2] = z;
        out[offset + 3] = red;
        out[offset + 4] = green;
        out[offset + 5] = blue;
        return offset + FLOATS_PER_VERTEX;
    }

    /** 列主序。下标 0、1、2、3 是第一列。 */
    private static float[] rotationY(float radians) {
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        return new float[] {
                cosine, 0f, -sine, 0f,
                0f, 1f, 0f, 0f,
                sine, 0f, cosine, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] identity() {
        return new float[] {
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] translation(float x, float y, float z) {
        return new float[] {
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, z, 1f
        };
    }

    private static float[] scaledTranslation(float x, float y, float z, float scale) {
        return new float[] {
                scale, 0f, 0f, 0f,
                0f, scale, 0f, 0f,
                0f, 0f, scale, 0f,
                x, y, z, 1f
        };
    }

    private static float[] quadTransform(float x, float y, float width, float height) {
        return new float[] {
                width, 0f, 0f, 0f,
                0f, height, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, 0f, 1f
        };
    }

    private static float[] ortho(float width, float height) {
        return new float[] {
                2f / width, 0f, 0f, 0f,
                0f, 2f / height, 0f, 0f,
                0f, 0f, -1f, 0f,
                -1f, -1f, 0f, 1f
        };
    }

    private float[] viewFromAngles() {
        float[] forward = lookDirection(yaw, pitch);
        return lookAt(
                eyeX, eyeY, eyeZ,
                eyeX + forward[0], eyeY + forward[1], eyeZ + forward[2],
                0f, 1f, 0f
        );
    }

    /** 水平前进。ψ = 0 时为 (0, 0, -1)，不包含俯仰。 */
    private static float[] horizontalForward(float yawAngle) {
        return new float[] {
                (float) Math.sin(yawAngle),
                0f,
                -(float) Math.cos(yawAngle)
        };
    }

    /** 水平向右。ψ = 0 时为 (1, 0, 0)。 */
    private static float[] horizontalRight(float yawAngle) {
        return new float[] {
                (float) Math.cos(yawAngle),
                0f,
                (float) Math.sin(yawAngle)
        };
    }

    /** ψ = 0、φ = 0 时看向 -Z。ψ 增加是向右转，φ 增加是抬头。 */
    private static float[] lookDirection(float yawAngle, float pitchAngle) {
        float cosinePitch = (float) Math.cos(pitchAngle);
        float cosineYaw = (float) Math.cos(yawAngle);
        float sineYaw = (float) Math.sin(yawAngle);
        float sinePitch = (float) Math.sin(pitchAngle);
        return new float[] {
                cosinePitch * sineYaw,
                sinePitch,
                -cosinePitch * cosineYaw
        };
    }

    private float[] anglesLookingAtOrigin() {
        float directionX = -eyeX;
        float directionY = -eyeY;
        float directionZ = -eyeZ;
        float length = (float) Math.sqrt(
                directionX * directionX + directionY * directionY + directionZ * directionZ
        );
        directionX /= length;
        directionY /= length;
        directionZ /= length;
        float pitchAngle = (float) Math.asin(directionY);
        float yawAngle = (float) Math.atan2(directionX, -directionZ);
        return new float[] {yawAngle, pitchAngle};
    }

    private void assertViewMatchesLookAtOrigin(float[] fromAngles) {
        float[] direct = lookAt(eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f);
        for (int index = 0; index < 16; index++) {
            if (Math.abs(fromAngles[index] - direct[index]) > 1.0e-4f) {
                throw new IllegalStateException(
                        "Yaw/pitch view disagrees with lookAt at index " + index
                                + ": " + fromAngles[index] + " vs " + direct[index]
                );
            }
        }
    }

    private static float[] lookAt(
            float eyeX, float eyeY, float eyeZ,
            float targetX, float targetY, float targetZ,
            float upX, float upY, float upZ
    ) {
        float forwardX = targetX - eyeX;
        float forwardY = targetY - eyeY;
        float forwardZ = targetZ - eyeZ;
        float forwardLength = (float) Math.sqrt(
                forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ
        );
        forwardX /= forwardLength;
        forwardY /= forwardLength;
        forwardZ /= forwardLength;
        float rightX = forwardY * upZ - forwardZ * upY;
        float rightY = forwardZ * upX - forwardX * upZ;
        float rightZ = forwardX * upY - forwardY * upX;
        float rightLength = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        rightX /= rightLength;
        rightY /= rightLength;
        rightZ /= rightLength;
        float upAxisX = rightY * forwardZ - rightZ * forwardY;
        float upAxisY = rightZ * forwardX - rightX * forwardZ;
        float upAxisZ = rightX * forwardY - rightY * forwardX;
        return new float[] {
                rightX, upAxisX, -forwardX, 0f,
                rightY, upAxisY, -forwardY, 0f,
                rightZ, upAxisZ, -forwardZ, 0f,
                -(rightX * eyeX + rightY * eyeY + rightZ * eyeZ),
                -(upAxisX * eyeX + upAxisY * eyeY + upAxisZ * eyeZ),
                forwardX * eyeX + forwardY * eyeY + forwardZ * eyeZ,
                1f
        };
    }

    private static float[] perspective(float fovYRadians, float aspect, float near, float far) {
        float tangent = (float) Math.tan(fovYRadians * 0.5f);
        return new float[] {
                1f / (aspect * tangent), 0f, 0f, 0f,
                0f, 1f / tangent, 0f, 0f,
                0f, 0f, -(far + near) / (far - near), -1f,
                0f, 0f, -(2f * far * near) / (far - near), 0f
        };
    }

    private static void transformPoint(float[] matrix, float x, float y, float z, float[] out) {
        out[0] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
        out[1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
        out[2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
        out[3] = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15];
    }

    private static void assertCornersVisible(float[] view, float[] projection) {
        assertPose(view, projection, rotationY(0f), "identity");
        assertPose(view, projection, rotationY((float) Math.toRadians(40.0)), "yaw 40 deg");
    }

    private static void assertPose(float[] view, float[] projection, float[] model, String label) {
        float[] world = new float[4];
        float[] camera = new float[4];
        float[] clip = new float[4];
        for (float x : new float[] {-0.5f, 0.5f}) {
            for (float y : new float[] {-0.5f, 0.5f}) {
                for (float z : new float[] {-0.5f, 0.5f}) {
                    transformPoint(model, x, y, z, world);
                    transformPoint(view, world[0], world[1], world[2], camera);
                    transformPoint(projection, camera[0], camera[1], camera[2], clip);
                    if (clip[3] <= 0f) {
                        throw new IllegalStateException(label + " corner has non-positive w.");
                    }
                    float ndcX = clip[0] / clip[3];
                    float ndcY = clip[1] / clip[3];
                    float ndcZ = clip[2] / clip[3];
                    if (Math.abs(ndcX) > 1f || Math.abs(ndcY) > 1f || ndcZ < -1f || ndcZ > 1f) {
                        throw new IllegalStateException(
                                label + " corner (" + x + ", " + y + ", " + z
                                        + ") landed outside NDC (" + ndcX + ", " + ndcY + ", " + ndcZ + ")."
                        );
                    }
                }
            }
        }
    }

    private static float[] colorOf(int id) {
        if (id == BlockIds.GRASS) {
            return GRASS_COLOR;
        }
        if (id == BlockIds.DIRT) {
            return DIRT_COLOR;
        }
        if (id == BlockIds.STONE) {
            return STONE_COLOR;
        }
        if (id == BlockIds.MARKER_POSITIVE_X) {
            return MARKER_POSITIVE_X_COLOR;
        }
        if (id == BlockIds.MARKER_NEGATIVE_X) {
            return MARKER_NEGATIVE_X_COLOR;
        }
        if (id == BlockIds.MARKER_POSITIVE_Z) {
            return MARKER_POSITIVE_Z_COLOR;
        }
        if (id == BlockIds.MARKER_NEGATIVE_Z) {
            return MARKER_NEGATIVE_Z_COLOR;
        }
        return UNKNOWN_COLOR;
    }

    private static String blockName(int id) {
        return switch (id) {
            case BlockIds.GRASS -> "grass";
            case BlockIds.DIRT -> "dirt";
            case BlockIds.STONE -> "stone";
            case BlockIds.MARKER_POSITIVE_X -> "marker +X";
            case BlockIds.MARKER_NEGATIVE_X -> "marker -X";
            case BlockIds.MARKER_POSITIVE_Z -> "marker +Z";
            case BlockIds.MARKER_NEGATIVE_Z -> "marker -Z";
            default -> "id " + id;
        };
    }

    private static String describeAim(VoxelRay.Hit hit) {
        if (hit.kind() == VoxelRay.Kind.MISS) {
            return "Aim miss";
        }
        if (hit.kind() == VoxelRay.Kind.BLOCKED) {
            return "Aim blocked at " + hit.x() + " " + hit.y() + " " + hit.z();
        }
        int[] place = VoxelRay.placeTarget(hit);
        String placeText = place == null ? "none" : place[0] + " " + place[1] + " " + place[2];
        return "Aim " + hit.x() + " " + hit.y() + " " + hit.z()
                + " normal " + hit.normalX() + " " + hit.normalY() + " " + hit.normalZ()
                + " place " + placeText
                + (hit.startedInside() ? " inside" : "");
    }

    private static String explain(String reason) {
        return switch (reason) {
            case "duplicate" -> "重复操作";
            case "outside" -> "世界范围外";
            case "unloaded" -> "区块未加载";
            case "stale" -> "方块已经变了";
            case "not_solid" -> "那里是空的";
            case "not_replaceable" -> "目标不能被替换";
            case "not_placeable" -> "当前快捷栏不能放置";
            case "unsupported" -> "缺少支撑";
            case "player_overlap" -> "会埋住玩家";
            case "started_inside" -> "视线从方块内部出发，没有可放置的面";
            case "miss" -> "没有指到方块";
            case "reach" -> "超出触及距离";
            default -> reason;
        };
    }
}
