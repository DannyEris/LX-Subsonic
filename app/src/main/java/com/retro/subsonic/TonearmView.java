package com.retro.subsonic;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class TonearmView extends View {

    // 方案一几何角度：暂停时外摆停靠在支架（-22度），播放时落针在黑胶外圈声轨（0度，与中心封面保持>20dp距离）
    private static final float ANGLE_PAUSED = -22.0f;
    private static final float ANGLE_PLAYING = 0.0f;

    private float currentAngle = ANGLE_PAUSED;
    private boolean isPlaying = false;
    private ValueAnimator animator;

    private Paint basePaint;
    private Paint armPaint;
    private Paint headPaint;
    private Paint needlePaint;
    private Paint restPaint;
    private Path armPath;

    // 预分配复用对象，兼容 Android 4.2 (API 17) 且防止 onDraw 频繁内存抖动
    private RectF headRect = new RectF();

    public TonearmView(Context context) {
        super(context);
        init();
    }

    public TonearmView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TonearmView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setStyle(Paint.Style.FILL);

        armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        armPaint.setStyle(Paint.Style.STROKE);
        armPaint.setStrokeCap(Paint.Cap.ROUND);

        headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headPaint.setStyle(Paint.Style.FILL);

        needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needlePaint.setStyle(Paint.Style.FILL);

        restPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        restPaint.setStyle(Paint.Style.STROKE);
        restPaint.setStrokeCap(Paint.Cap.ROUND);

        armPath = new Path();
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        float targetAngle = playing ? ANGLE_PLAYING : ANGLE_PAUSED;
        animator = ValueAnimator.ofFloat(currentAngle, targetAngle);
        animator.setDuration(480);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                currentAngle = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // 唱臂基座固定在唱片右上方
        float pivotX = w * 0.84f;
        float pivotY = h * 0.14f;

        // 1. 静态休眠托架（固定在机身上，位于外侧停靠点下方）
        float restX = pivotX + 16 * density;
        float restY = pivotY + 45 * density;
        restPaint.setColor(0xFF262A35);
        restPaint.setStrokeWidth(3.0f * density);
        canvas.drawLine(pivotX + 8 * density, pivotY + 20 * density, restX, restY, restPaint);
        basePaint.setColor(0xFF00E5FF);
        canvas.drawCircle(restX, restY, 2.5f * density, basePaint);

        // 2. 静态唱臂旋转底座
        basePaint.setColor(0xFF141720);
        canvas.drawCircle(pivotX, pivotY, 14 * density, basePaint);
        basePaint.setColor(0xFF2E3444);
        canvas.drawCircle(pivotX, pivotY, 10 * density, basePaint);
        basePaint.setColor(0xFF00E5FF);
        canvas.drawCircle(pivotX, pivotY, 3.5f * density, basePaint);

        // 3. 动态旋转组件（唱臂管身、配重陀、唱头）
        canvas.save();
        canvas.rotate(currentAngle, pivotX, pivotY);

        // 3.1 顶部金属配重陀（延伸至转轴后方）
        basePaint.setColor(0xFF4A4E5C);
        canvas.drawCircle(pivotX + 6 * density, pivotY - 14 * density, 8.5f * density, basePaint);
        basePaint.setColor(0xFF888C99);
        canvas.drawRect(pivotX + 2 * density, pivotY - 18 * density, pivotX + 10 * density, pivotY - 10 * density, basePaint);

        // 3.2 经典 S 型拟真铝合金唱杆
        float armLength = 152 * density;
        float endArmX = pivotX - 4 * density;
        float endArmY = pivotY + armLength;

        armPaint.setStrokeWidth(3.2f * density);
        armPaint.setShader(new LinearGradient(pivotX, pivotY, endArmX, endArmY,
                new int[]{0xFFF0F0F0, 0xFF9E9E9E, 0xFFE0E0E0}, null, Shader.TileMode.CLAMP));

        armPath.reset();
        armPath.moveTo(pivotX, pivotY);
        // S 型平滑双弯微弧曲线：杆身贴合外侧，全程距中心封面保持安全距离
        armPath.cubicTo(
                pivotX + 12 * density, pivotY + 45 * density,
                pivotX - 16 * density, pivotY + 105 * density,
                endArmX, endArmY
        );
        canvas.drawPath(armPath, armPaint);
        armPaint.setShader(null);

        // 3.3 经典黑胶唱头盒（朝唱片圆心偏置约 22 度）
        canvas.save();
        canvas.translate(endArmX, endArmY);
        canvas.rotate(22); // 唱头顺应角偏置

        // 唱头外壳（API 17 兼容 drawRoundRect）
        headPaint.setColor(0xFF1E222B);
        headRect.set(-6.5f * density, 0, 6.5f * density, 20 * density);
        canvas.drawRoundRect(headRect, 2.5f * density, 2.5f * density, headPaint);

        // 唱头高光指示带
        headPaint.setColor(0xFF00E5FF);
        canvas.drawCircle(0, 4.5f * density, 1.8f * density, headPaint);

        // 红色细小唱针尖（触及声轨）
        needlePaint.setColor(0xFFFF4081);
        canvas.drawRect(-1.2f * density, 19 * density, 1.2f * density, 24 * density, needlePaint);

        canvas.restore();
        canvas.restore();
    }
}
