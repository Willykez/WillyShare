package com.willykez.willyshare;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public class AnimUtils {

    public static void fadeIn(View v) {
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(350)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public static void fadeOut(View v) {
        v.animate().alpha(0f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    public static void slideUp(View v) {
        v.setAlpha(0f);
        v.setTranslationY(60f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).translationY(0f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    /**
     * FIX: Previously used two independent ObjectAnimators started back-to-back.
     * On some devices the second animator's start() cancelled the first one's
     * property updates, causing a visible jank or crash in the animator thread.
     * Now we use an AnimatorSet.playTogether() so both animators are driven by
     * a single choreographer tick and cannot cancel each other.
     */
    public static void pulse(View v) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.08f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.08f, 1f);
        scaleX.setDuration(600);
        scaleY.setDuration(600);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);

        // Tag the AnimatorSet on the view so stopPulse() can cancel it cleanly
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        v.setTag(R.id.pulse_animator_tag, set);
        set.start();
    }

    public static void stopPulse(View v) {
        Object tag = v.getTag(R.id.pulse_animator_tag);
        if (tag instanceof AnimatorSet) {
            ((AnimatorSet) tag).cancel();
            v.setTag(R.id.pulse_animator_tag, null);
        }
        v.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    public static void buttonPress(View v) {
        v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                                .setInterpolator(new OvershootInterpolator(2f)).start())
                .start();
    }

    public static void countUpProgress(android.widget.ProgressBar pb, int from, int to) {
        ObjectAnimator anim = ObjectAnimator.ofInt(pb, "progress", from, to);
        anim.setDuration(500);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    public static void staggerChildren(android.view.ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(40f);
            child.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(i * 80L)
                    .setDuration(350)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
