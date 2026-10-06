package com.retro.subsonic;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.AttributeSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Visualizer3DView extends GLSurfaceView implements GLSurfaceView.Renderer {

    // 默认基准仰角与允许调节的角度范围
    public static final float DEFAULT_PITCH = 45.0f;
    public static final float MIN_PITCH = 15.0f;
    public static final float MAX_PITCH = 80.0f;

    private volatile float currentPitch = DEFAULT_PITCH;
    private volatile float targetPitch = DEFAULT_PITCH;
    private volatile float currentEnergy = 0.0f;
    private volatile float targetEnergy = 0.0f;
    private volatile boolean isPlaying = false;

    // 预分配矩阵，避免在渲染循环中触发 Dalvik GC
    private final float[] mvpMatrix = new float[16];
    private final float[] projMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];

    // 网格参数
    private static final int GRID_COLS = 32;
    private static final int GRID_ROWS = 32;
    private static final float X_MIN = -3.2f;
    private static final float X_MAX = 3.2f;
    private static final float Z_MIN = -4.5f;
    private static final float Z_MAX = 1.5f;

    private FloatBuffer vertexBuffer;
    private int vertexCount = 0;
    private int program = 0;
    private int uMVPMatrixLoc = -1;
    private int uEnergyLoc = -1;
    private int uTimeLoc = -1;
    private int aPosLoc = -1;

    private long lastTimeMs = 0;
    private float elapsedTime = 0.0f;

    // GLSL 顶点着色器 (叠加瞬态动态波速，强化鼓点爆发力)
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform float uEnergy;\n" +
            "uniform float uTime;\n" +
            "attribute vec3 aPosition;\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    vec3 pos = aPosition;\n" +
            "    vec2 center = vec2(0.0, -1.2);\n" +
            "    float d = distance(pos.xz, center);\n" +
            "    float dynamicSpeed = uTime * 3.5 + uEnergy * 2.2;\n" +
            "    float ripple = sin(d * 3.8 - dynamicSpeed) * (0.12 + uEnergy * 0.95);\n" +
            "    float pulse = cos(pos.x * 2.2 + uTime * 2.0) * sin(pos.z * 1.8) * uEnergy * 0.4;\n" +
            "    pos.y = (ripple + pulse) * max(0.2, (2.8 - d * 0.6));\n" +
            "    gl_Position = uMVPMatrix * vec4(pos, 1.0);\n" +
            "    float h = clamp((pos.y + 0.3) / 1.2, 0.0, 1.0);\n" +
            "    vec3 cDeep = vec3(0.06, 0.16, 0.35);\n" +
            "    vec3 cCyan = vec3(0.0, 0.9, 1.0);\n" +
            "    vec3 cPink = vec3(1.0, 0.25, 0.5);\n" +
            "    vec3 finalC = mix(cDeep, cCyan, h);\n" +
            "    if (uEnergy > 0.6) {\n" +
            "        finalC = mix(finalC, cPink, (uEnergy - 0.6) * 2.0 * h);\n" +
            "    }\n" +
            "    float alpha = clamp(1.0 - (d / 4.8), 0.2, 0.95);\n" +
            "    vColor = vec4(finalC, alpha);\n" +
            "}\n";

    // GLSL 片元着色器
    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = vColor;\n" +
            "}\n";

    public Visualizer3DView(Context context) {
        super(context);
        init();
    }

    public Visualizer3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        buildGridGeometry();
    }

    private void buildGridGeometry() {
        float stepX = (X_MAX - X_MIN) / GRID_COLS;
        float stepZ = (Z_MAX - Z_MIN) / GRID_ROWS;

        int segments = (GRID_ROWS + 1) * GRID_COLS + (GRID_COLS + 1) * GRID_ROWS;
        vertexCount = segments * 2;
        float[] coords = new float[vertexCount * 3];
        int idx = 0;

        // 沿 X 轴横线
        for (int r = 0; r <= GRID_ROWS; r++) {
            float z = Z_MIN + r * stepZ;
            for (int c = 0; c < GRID_COLS; c++) {
                float x1 = X_MIN + c * stepX;
                float x2 = X_MIN + (c + 1) * stepX;
                coords[idx++] = x1; coords[idx++] = 0.0f; coords[idx++] = z;
                coords[idx++] = x2; coords[idx++] = 0.0f; coords[idx++] = z;
            }
        }

        // 沿 Z 轴纵线
        for (int c = 0; c <= GRID_COLS; c++) {
            float x = X_MIN + c * stepX;
            for (int r = 0; r < GRID_ROWS; r++) {
                float z1 = Z_MIN + r * stepZ;
                float z2 = Z_MIN + (r + 1) * stepZ;
                coords[idx++] = x; coords[idx++] = 0.0f; coords[idx++] = z1;
                coords[idx++] = x; coords[idx++] = 0.0f; coords[idx++] = z2;
            }
        }

        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(coords);
        vertexBuffer.position(0);
    }

    // ================= 外部交互与手势调节接口 =================

    public void adjustPitch(float delta) {
        float next = this.targetPitch + delta;
        if (next < MIN_PITCH) next = MIN_PITCH;
        if (next > MAX_PITCH) next = MAX_PITCH;
        this.targetPitch = next;
    }

    public void setPitchAngle(float pitch) {
        if (pitch < MIN_PITCH) pitch = MIN_PITCH;
        if (pitch > MAX_PITCH) pitch = MAX_PITCH;
        this.targetPitch = pitch;
    }

    public float getPitchAngle() {
        return targetPitch;
    }

    public void resetPitchAngle() {
        this.targetPitch = DEFAULT_PITCH;
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
    }

    public void updateEnergy(float energy) {
        if (energy < 0.0f) energy = 0.0f;
        if (energy > 1.0f) energy = 1.0f;
        this.targetEnergy = energy;
    }

    // ================= GLSurfaceView.Renderer 接口实现 =================

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.039f, 0.043f, 0.055f, 1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glLineWidth(2.0f);

        int vShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vShader);
        GLES20.glAttachShader(program, fShader);
        GLES20.glLinkProgram(program);

        uMVPMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        uEnergyLoc = GLES20.glGetUniformLocation(program, "uEnergy");
        uTimeLoc = GLES20.glGetUniformLocation(program, "uTime");
        aPosLoc = GLES20.glGetAttribLocation(program, "aPosition");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / (height > 0 ? height : 1);
        Matrix.perspectiveM(projMatrix, 0, 45.0f, ratio, 0.1f, 30.0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // 1. 视角插值与能量瞬态响应（Attack 0.82 爆发卡点，Decay 0.08 自然回落）
        currentPitch += (targetPitch - currentPitch) * 0.15f;
        if (isPlaying) {
            if (targetEnergy > currentEnergy) {
                currentEnergy += (targetEnergy - currentEnergy) * 0.82f;
            } else {
                currentEnergy += (targetEnergy - currentEnergy) * 0.08f;
            }
        } else {
            currentEnergy += (0.0f - currentEnergy) * 0.08f;
        }

        // 2. 时间步进
        long now = SystemClock.uptimeMillis();
        if (lastTimeMs == 0) lastTimeMs = now;
        float dt = (now - lastTimeMs) / 1000.0f;
        lastTimeMs = now;
        elapsedTime += dt * (isPlaying ? 1.5f : 0.35f);

        // 3. 计算相机观察矩阵 (依据动态俯仰角计算视点)
        float rad = (float) Math.toRadians(currentPitch);
        float eyeY = (float) (Math.sin(rad) * 4.6);
        float eyeZ = (float) (Math.cos(rad) * 4.6);
        Matrix.setLookAtM(viewMatrix, 0,
                0.0f, eyeY, eyeZ - 0.5f,
                0.0f, 0.0f, -1.2f,
                0.0f, 1.0f, 0.0f);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0);

        // 4. 着色器渲染
        if (program != 0 && vertexBuffer != null) {
            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0);
            GLES20.glUniform1f(uEnergyLoc, currentEnergy);
            GLES20.glUniform1f(uTimeLoc, elapsedTime);

            GLES20.glEnableVertexAttribArray(aPosLoc);
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer);

            GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount);
            GLES20.glDisableVertexAttribArray(aPosLoc);
        }
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void onResume() {
        super.onResume();
        lastTimeMs = 0;
    }

    @Override
    public void onPause() {
        super.onPause();
    }
}
