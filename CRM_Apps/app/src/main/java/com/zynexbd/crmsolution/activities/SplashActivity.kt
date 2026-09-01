package com.zynexbd.crmsolution.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zynexbd.crmsolution.databinding.ActivitySplashBinding
import com.zynexbd.crmsolution.utils.AppUpdateHelper
import com.zynexbd.crmsolution.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Run enter animations
        startSplashAnimations()

        // Check for updates in background
        AppUpdateHelper.checkForUpdate(this, lifecycleScope)

        // Proceed after short delay for user to read quote
        lifecycleScope.launch {
            delay(2200)
            navigateToNextScreen()
        }
    }

    private fun startSplashAnimations() {
        // Logo Scale & Fade
        val logoAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 800 })
            addAnimation(ScaleAnimation(0.7f, 1f, 0.7f, 1f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 850
                interpolator = DecelerateInterpolator()
            })
        }
        binding.imageSplashLogo.startAnimation(logoAnim)

        // Title & Tagline Slide Up
        val titleAnim = AnimationSet(true).apply {
            startOffset = 200
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 700 })
            addAnimation(TranslateAnimation(0f, 0f, 40f, 0f).apply {
                duration = 700
                interpolator = DecelerateInterpolator()
            })
        }
        binding.textCrmTitle.startAnimation(titleAnim)
        binding.textCrmSubtitle.startAnimation(titleAnim)
        binding.textWorkforceTag.startAnimation(titleAnim)

        // Quote Card Fade In
        val quoteAnim = AnimationSet(true).apply {
            startOffset = 450
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 800 })
            addAnimation(TranslateAnimation(0f, 0f, 50f, 0f).apply {
                duration = 800
                interpolator = AccelerateDecelerateInterpolator()
            })
        }
        binding.cardQuote.startAnimation(quoteAnim)
    }

    private fun navigateToNextScreen() {
        if (isFinishing || isDestroyed) return

        val targetIntent = if (sessionManager.isLoggedIn()) {
            if (sessionManager.isManagerOrAdmin()) {
                Intent(this, AdminCrmDashboardActivity::class.java)
            } else {
                Intent(this, UserHomeActivity::class.java)
            }
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(targetIntent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
