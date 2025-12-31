package com.example.firstapp;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class SlideGestureDetector extends GestureDetector.SimpleOnGestureListener {
    private static final int SWIPE_THRESHOLD = 100; // 滑动距离阈值
    private static final int SWIPE_VELOCITY_THRESHOLD = 100; // 滑动速度阈值
    private OnSwipeListener listener;

    public SlideGestureDetector(OnSwipeListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        boolean result = false;
        try {
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();
            // 优先检测水平滑动（左右滑动）
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // 向右滑动（从左向右）
                        listener.onSwipeRight();
                    } else {
                        // 向左滑动（从右向左）
                        listener.onSwipeLeft();
                    }
                    result = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    // 滑动监听接口
    public interface OnSwipeListener {
        void onSwipeRight(); // 向右滑动
        void onSwipeLeft();  // 向左滑动
    }
}