package com.gikoko.giplayerpro

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ringOuter: View = findViewById(R.id.ringOuter)
        val ringInner: View = findViewById(R.id.ringInner)
        val glow: View = findViewById(R.id.glow)
        val logo: View = findViewById(R.id.ivLogo)
        val title: View = findViewById(R.id.tvTitle)
        val tagline: View = findViewById(R.id.tvTagline)
        val powered: View = findViewById(R.id.tvPowered)

        // Cyber rings - counter-rotating
        ObjectAnimator.ofFloat(ringOuter, View.ROTATION, 0f, 360f).apply {
            duration = 9000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(ringInner, View.ROTATION, 0f, -360f).apply {
            duration = 7000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Pulsing gold glow
        val pulseInterp = AccelerateDecelerateInterpolator()
        ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.9f, 1.25f).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE; interpolator = pulseInterp; start()
        }
        ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.9f, 1.25f).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE; interpolator = pulseInterp; start()
        }
        ObjectAnimator.ofFloat(glow, View.ALPHA, 0.5f, 1f).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE; interpolator = pulseInterp; start()
        }

        // 3D GI logo - scale-in + Y-axis flip
        logo.scaleX = 0.1f
        logo.scaleY = 0.1f
        logo.rotationY = -180f
        logo.animate().rotationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(1100)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        title.alpha = 0f
        title.translationY = 30f
        title.animate().alpha(1f).translationY(0f)
            .setStartDelay(500).setDuration(600).start()

        tagline.alpha = 0f
        tagline.animate().alpha(1f).setStartDelay(700).setDuration(600).start()

        // POWERED BY I KO KO - prominent pop + shine blink
        powered.alpha = 0f
        powered.scaleX = 0.6f
        powered.scaleY = 0.6f
        powered.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(1000).setDuration(500)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
        ObjectAnimator.ofFloat(powered, View.ALPHA, 1f, 0.45f).apply {
            startDelay = 1800; duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2600)
    }
}
