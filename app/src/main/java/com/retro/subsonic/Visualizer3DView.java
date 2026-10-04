package com.retro.subsonic;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Visualizer3DView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final int RINGS = 36;    // 径向同心环数
    private static final int SECTORS = 64;  // 每环圆周采样数
    private static final int VERTEX_COUNT = (RINGS + 1) * SECTORS;

    private FloatBuffer vertexBuffer;
    private int programId;
    private int aPosLoc;
    private int uMvpMatrixLoc;
    private int uTimeLoc;
    private int uEnergyLoc;

    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    private float runningTime = 0f;
    private float targetEnergy = 0f;
    private float smoothEnergy = 0f;

    // 顶点着色器：透视顶点计算与波纹高度偏移
    private final String vertexShaderCode =
            "uniform mat4 uMvpMatrix;\n" +
            "uniform float uTime;\n" +
            "uniform float uEnergy;\n" +
            "attribute vec3 aPosition;\n" +
            "varying float vHeight;\n" +
            "varying float vDist;\n" +
            "void main() {\n" +
            "    float r = length(aPosition.xy);\n" +
            "    vDist = r;\n" +
            "    // 中心向外扩散的同心正弦衰减涟漪\n" +
            "    float wave1 = sin(r * 12.0 - uTime * 3.5) * (1.0 - smoothstep(0.0, 1.0, r));\n" +
            "    float wave2 = cos(r * 22.0 - uTime * 5.0) * 0.4 * (1.0 - r);\n" +
            "    float height = (wave1 + wave2) * (0.08 + uEnergy * 0.28);\n" +
            "    vHeight = height;\n" +
            "    vec3 pos = vec3(aPosition.x, aPosition.y, height);\n" +
            "    gl_Position = uMvpMatrix * vec4(pos, 1.0);\n" +
            "    gl_PointSize = 2.0;\n" +
            "}\n";

    // 片元着色器：纯黑灰渐变、高光水银反光质感
    private final String fragmentShaderCode =
            "precision mediump float;\n" +
            "varying float vHeight;\n" +
            "varying float vDist;\n" +
            "void main() {\n" +
            "    // 基于高度的高冷水银金属光晕\n" +
            "    float brightness = clamp(vHeight * 4.0 + 0.35, 0.05, 1.0);\n" +
            "    vec3 mercury = vec3(0.92, 0.94, 0.98) * brightness;\n" +
            "    // 边缘暗黑羽化过渡至纯黑\n" +
            "    float alpha = clamp((1.0 - vDist) * 1.5, 0.0, 1.0);\n" +
            "    gl_FragColor = vec4(mercury * alpha, alpha * 0.85);\n" +
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
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        initMesh();
    }

    private void initMesh() {
        float[] vertices = new float[VERTEX_COUNT * 3];
        int idx = 0;
        for (int r = 0; r <= RINGS; r++) {
            float radius = (float) r / (float) RINGS;
            for (int s = 0; s < SECTORS; s++) {
                float angle = (float) (s * 2.0 * Math.PI / SECTORS);
                vertices[idx++] = (float) (radius * Math.cos(angle));
                vertices[idx++] = (float) (radius * Math.sin(angle));
                vertices[idx++] = 0f;
            }
        }
        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);
    }

    public void updateEnergy(float energy) {
        this.targetEnergy = energy;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.039f, 0.043f, 0.055f, 1.0f); // 极深黑背景 #0a0b0e
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vShader);
        GLES20.glAttachShader(programId, fShader);
        GLES20.glLinkProgram(programId);

        aPosLoc = GLES20.glGetAttribLocation(programId, "aPosition");
        uMvpMatrixLoc = GLES20.glGetUniformLocation(programId, "uMvpMatrix");
        uTimeLoc = GLES20.glGetUniformLocation(programId, "uTime");
        uEnergyLoc = GLES20.glGetUniformLocation(programId, "uEnergy");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / (float) height;
        Matrix.frustumM(projectionMatrix, 0, -ratio * 0.5f, ratio * 0.5f, -0.5f, 0.5f, 1.0f, 10.0f);
        // 观察相机：倾斜 38 度仰俯角，呈现截图中的 3D 纵深水银盘
        Matrix.setLookAtM(viewMatrix, 0, 0f, -1.35f, 1.15f, 0f, 0.08f, 0f, 0f, 1f, 0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        runningTime += 0.02f;
        smoothEnergy = smoothEnergy * 0.85f + targetEnergy * 0.15f;

        GLES20.glUseProgram(programId);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);

        GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(uTimeLoc, runningTime);
        GLES20.glUniform1f(uEnergyLoc, smoothEnergy);

        GLES20.glEnableVertexAttribArray(aPosLoc);
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // 渲染极坐标点阵与线框构成的水银流动波纹
        for (int r = 0; r <= RINGS; r++) {
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, r * SECTORS, SECTORS);
        }

        GLES20.glDisableVertexAttribArray(aPosLoc);
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
