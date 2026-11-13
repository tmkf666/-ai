package com.mocitianxia.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class LockPatternView extends View {

    private Paint paintNormal;
    private Paint paintSelected;
    private Paint paintError;
    private Paint paintLine;
    
    private float cx, cy; 
    private float offset; 
    private float radius; 
    
    private Point[][] points = new Point[3][3];
    private boolean isInit = false;
    private boolean isSelecting = false;
    private boolean isError = false;
    
    private float mouseX, mouseY;
    private List<Point> selectedPoints = new ArrayList<>();
    private Path path = new Path();
    
    private OnPatternListener listener;

    public interface OnPatternListener {
        void onPatternStart();
        void onPatternDetected(List<Point> pattern);
    }

    public void setOnPatternListener(OnPatternListener listener) {
        this.listener = listener;
    }

    public LockPatternView(Context context) { super(context); init(); }
    public LockPatternView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paintNormal = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintNormal.setStyle(Paint.Style.STROKE);
        paintNormal.setStrokeWidth(5);
        paintNormal.setColor(Color.parseColor("#475569")); 

        paintSelected = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSelected.setStyle(Paint.Style.FILL);
        paintSelected.setColor(Color.parseColor("#00FFCC")); 

        paintError = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintError.setStyle(Paint.Style.FILL);
        paintError.setColor(Color.parseColor("#EF4444")); 

        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(8);
        paintLine.setColor(Color.parseColor("#00FFCC"));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            initPoints(w, h);
        }
    }

    private void initPoints(int width, int height) {
        int size = Math.min(width, height);
        offset = size / 6f;
        radius = size / 20f;
        
        int index = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                cx = offset + j * offset * 2;
                cy = offset + i * offset * 2;
                points[i][j] = new Point(cx, cy, index++);
            }
        }
        isInit = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isInit) return;
        
        drawPoints(canvas);
        if (isSelecting || !selectedPoints.isEmpty()) {
            drawLine(canvas);
        }
    }

    private void drawPoints(Canvas canvas) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Point p = points[i][j];
                Paint paint = p.isSelected ? (isError ? paintError : paintSelected) : paintNormal;
                canvas.drawCircle(p.x, p.y, radius, paint);
                if (p.isSelected) {
                    Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    innerPaint.setStyle(Paint.Style.FILL);
                    innerPaint.setColor(isError ? Color.parseColor("#EF4444") : Color.parseColor("#00FFCC"));
                    canvas.drawCircle(p.x, p.y, radius / 2, innerPaint);
                }
            }
        }
    }

    private void drawLine(Canvas canvas) {
        path.reset();
        Point lastPoint = null;
        for (int i = 0; i < selectedPoints.size(); i++) {
            Point p = selectedPoints.get(i);
            if (i == 0) {
                path.moveTo(p.x, p.y);
            } else {
                path.lineTo(p.x, p.y);
            }
            lastPoint = p;
        }
        if (isSelecting && lastPoint != null) {
            path.lineTo(mouseX, mouseY);
        }
        paintLine.setColor(isError ? Color.parseColor("#EF4444") : Color.parseColor("#00FFCC"));
        canvas.drawPath(path, paintLine);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isInit) return false;

        mouseX = event.getX();
        mouseY = event.getY();
        Point p = null;
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                resetPoints();
                isSelecting = true;
                isError = false;
                if (listener != null) listener.onPatternStart();
                break;
            case MotionEvent.ACTION_MOVE:
                if (isSelecting) {
                    p = checkSelectPoint(mouseX, mouseY);
                    if (p != null && !p.isSelected) {
                        p.isSelected = true;
                        selectedPoints.add(p);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isSelecting) {
                    isSelecting = false;
                    if (selectedPoints.size() < 4) {
                        isError = true;
                        invalidate();
                        if (listener != null) listener.onPatternDetected(selectedPoints);
                        postDelayed(this::resetPoints, 800);
                    } else {
                        if (listener != null) listener.onPatternDetected(selectedPoints);
                    }
                }
                break;
        }
        invalidate();
        return true;
    }

    private Point checkSelectPoint(float x, float y) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Point p = points[i][j];
                if (p != null && Math.sqrt((x - p.x) * (x - p.x) + (y - p.y) * (y - p.y)) < offset) {
                    return p;
                }
            }
        }
        return null;
    }

    public void setError() {
        isError = true;
        invalidate();
        postDelayed(this::resetPoints, 800);
    }

    public void resetPoints() {
        if (!isInit) return;
        
        selectedPoints.clear();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (points[i][j] != null) {
                    points[i][j].isSelected = false;
                }
            }
        }
        isError = false;
        isSelecting = false;
        invalidate();
    }

    public static class Point {
        public float x, y;
        public int index;
        public boolean isSelected = false;

        public Point(float x, float y, int index) {
            this.x = x;
            this.y = y;
            this.index = index;
        }
    }
}