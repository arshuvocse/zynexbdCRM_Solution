package com.zynexbd.crmsolution.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.databinding.ActivityOnboardingGuideBinding
import com.zynexbd.crmsolution.databinding.ItemOnboardingSlideBinding
import com.zynexbd.crmsolution.helpers.BatteryOptimizationHelper
import com.zynexbd.crmsolution.utils.Constants
import com.zynexbd.crmsolution.utils.LanguageManager

data class OnboardingSlide(
    val stepBadgeEn: String,
    val stepBadgeBn: String,
    val titleEn: String,
    val titleBn: String,
    val descEn: String,
    val descBn: String,
    val iconRes: Int,
    val bgGlowRes: Int,
    val actionTextEn: String,
    val actionTextBn: String,
    val actionType: Int // 1: Location, 2: Camera, 3: Background Location, 4: Battery, 5: Explore/Finish
)

class OnboardingGuideActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingGuideBinding
    private lateinit var slides: List<OnboardingSlide>
    private lateinit var adapter: OnboardingAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        adapter.notifyDataSetChanged()
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        adapter.notifyDataSetChanged()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSlidesData()

        adapter = OnboardingAdapter(slides) { slide ->
            handleSlideAction(slide)
        }
        binding.viewPagerOnboarding.adapter = adapter

        TabLayoutMediator(binding.tabLayoutIndicator, binding.viewPagerOnboarding) { _, _ -> }.attach()

        updateLanguageUI()

        binding.buttonLanguage.setOnClickListener {
            showLanguageSelectionDialog {
                updateLanguageUI()
                adapter.notifyDataSetChanged()
            }
        }

        binding.buttonSkip.setOnClickListener {
            finishOnboarding()
        }

        binding.buttonPrevious.setOnClickListener {
            val current = binding.viewPagerOnboarding.currentItem
            if (current > 0) {
                binding.viewPagerOnboarding.currentItem = current - 1
            }
        }

        binding.buttonNext.setOnClickListener {
            val current = binding.viewPagerOnboarding.currentItem
            if (current < slides.size - 1) {
                binding.viewPagerOnboarding.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val isEn = LanguageManager.getLanguage(this@OnboardingGuideActivity) == LanguageManager.LANG_EN
                binding.buttonPrevious.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
                if (position == slides.size - 1) {
                    binding.buttonNext.text = if (isEn) "GET STARTED 🚀" else "শুরু করুন 🚀"
                } else {
                    binding.buttonNext.text = if (isEn) "NEXT STEP ➔" else "পরবর্তী ধাপ ➔"
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun updateLanguageUI() {
        val isEn = LanguageManager.getLanguage(this) == LanguageManager.LANG_EN
        binding.textHeaderSubtitle.text = if (isEn) "Field Officer User Guide" else "ফিল্ড অফিসার ব্যবহার নির্দেশিকা"
        binding.buttonLanguage.text = if (isEn) "🌐 EN 🇬🇧" else "🌐 বাংলা 🇧🇩"
        binding.buttonSkip.text = if (isEn) "SKIP ➔" else "এড়িয়ে যান ➔"
        binding.buttonPrevious.text = if (isEn) "PREVIOUS" else "পূর্ববর্তী"
        
        val isLast = binding.viewPagerOnboarding.currentItem == (slides.size - 1)
        binding.buttonNext.text = if (isLast) {
            if (isEn) "GET STARTED 🚀" else "শুরু করুন 🚀"
        } else {
            if (isEn) "NEXT STEP ➔" else "পরবর্তী ধাপ ➔"
        }
    }

    private fun setupSlidesData() {
        slides = listOf(
            OnboardingSlide(
                stepBadgeEn = "STEP 1 OF 5 • LOCATION",
                stepBadgeBn = "ধাপ ১ (৫টির মধ্যে) • লোকেশন",
                titleEn = "Live GPS & Attendance",
                titleBn = "লাইভ জিপিএস ও উপস্থিতি ট্র্যাকিং",
                descEn = "Enable high-accuracy GPS to allow automated field duty tracking and accurate geofence punch-in verification.",
                descBn = "সঠিক উপস্থিতি (Punch In) এবং ফিল্ডে লাইভ ট্র্যাকিং নিশ্চিত করতে জিপিএস লোকেশন পারমিশন অন করুন।",
                iconRes = R.drawable.ic_map_custom,
                bgGlowRes = R.drawable.bg_card_indigo,
                actionTextEn = "GRANT LOCATION PERMISSION",
                actionTextBn = "লোকেশন পারমিশন দিন",
                actionType = 1
            ),
            OnboardingSlide(
                stepBadgeEn = "STEP 2 OF 5 • CAMERA",
                stepBadgeBn = "ধাপ ২ (৫টির মধ্যে) • ক্যামেরা",
                titleEn = "Smart Selfie & Photo Proof",
                titleBn = "সেলফি পাঞ্চ ও কাস্টমার ফটো",
                descEn = "Take duty-in selfies with interactive cropping and snap dealer shop photos during field visits.",
                descBn = "ডিউটিতে সেলফি ছবি ক্রপ করে সাবমিট করা এবং ভিজিটের সময় দোকানের ছবি তোলার জন্য ক্যামেরা পারমিশন প্রয়োজন।",
                iconRes = R.drawable.ic_attendance_custom,
                bgGlowRes = R.drawable.bg_card_emerald,
                actionTextEn = "ALLOW CAMERA ACCESS",
                actionTextBn = "ক্যামেরা পারমিশন দিন",
                actionType = 2
            ),
            OnboardingSlide(
                stepBadgeEn = "STEP 3 OF 5 • BACKGROUND",
                stepBadgeBn = "ধাপ ৩ (৫টির মধ্যে) • ব্যাকগ্রাউন্ড",
                titleEn = "Continuous Background Tracking",
                titleBn = "নিরবচ্ছিন্ন ব্যাকগ্রাউন্ড ট্র্যাকিং",
                descEn = "Select 'Allow all the time' so that location tracking continues uninterrupted even when screen is turned off.",
                descBn = "মোবাইল লক বা স্ক্রিন অফ থাকলেও ডিউটি ট্র্যাকিং যাতে বন্ধ না হয়, সেজন্য 'Allow all the time' অপশন নির্বাচন করুন।",
                iconRes = R.drawable.ic_pulse,
                bgGlowRes = R.drawable.bg_card_amber,
                actionTextEn = "ENABLE BACKGROUND LOCATION",
                actionTextBn = "ব্যাকগ্রাউন্ড লোকেশন চালু করুন",
                actionType = 3
            ),
            OnboardingSlide(
                stepBadgeEn = "STEP 4 OF 5 • BATTERY",
                stepBadgeBn = "ধাপ ৪ (৫টির মধ্যে) • ব্যাটারি",
                titleEn = "Unrestricted Battery Mode",
                titleBn = "ব্যাটারি অপটিমাইজেশন মোড",
                descEn = "Exclude Smart Workforce CRM from OS battery savers to prevent Android from killing tracking service during your shift.",
                descBn = "ফোনের ব্যাটারি সেভার যাতে ডিউটি ট্র্যাকিং বন্ধ না করে, সেজন্য 'No Restrictions' বা ব্যাটারি পারমিশন দিন।",
                iconRes = R.drawable.ic_map_custom,
                bgGlowRes = R.drawable.bg_card_rose,
                actionTextEn = "ALLOW UNRESTRICTED BATTERY",
                actionTextBn = "ব্যাটারি পারমিশন দিন",
                actionType = 4
            ),
            OnboardingSlide(
                stepBadgeEn = "STEP 5 OF 5 • READY",
                stepBadgeBn = "ধাপ ৫ (৫টির মধ্যে) • সম্পন্ন",
                titleEn = "Customer Visits & Follow-Ups",
                titleBn = "কাস্টমার ভিজিট ও ফলো-আপ",
                descEn = "Easily log dealer visits, plan customer follow-up schedules, and view your monthly duty performance logs.",
                descBn = "কাস্টমারদের সাথে নতুন ভিজিট যোগ করুন, পরবর্তী ফলো-আপ শিডিউল তৈরি করুন এবং মাসিক হাজিরা রিপোর্ট দেখুন।",
                iconRes = R.drawable.ic_people_custom,
                bgGlowRes = R.drawable.bg_card_indigo,
                actionTextEn = "OPEN FIELD DASHBOARD 🚀",
                actionTextBn = "ড্যাশবোর্ডে প্রবেশ করুন 🚀",
                actionType = 5
            )
        )
    }

    private fun handleSlideAction(slide: OnboardingSlide) {
        when (slide.actionType) {
            1 -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            2 -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            3 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        Toast.makeText(this, "Please grant Location permission in Step 1 first.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Background location is automatically supported on this Android version.", Toast.LENGTH_SHORT).show()
                }
            }
            4 -> {
                try {
                    BatteryOptimizationHelper.requestExemption(this)
                } catch (e: Exception) {
                    openAppSettings()
                }
            }
            5 -> {
                finishOnboarding()
            }
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()

        val session = com.zynexbd.crmsolution.utils.SessionManager(this)
        if (session.isLoggedIn()) {
            val role = session.getRole()
            if (role == "Admin") {
                startActivity(Intent(this, AdminOverviewDashboardActivity::class.java))
            } else {
                startActivity(Intent(this, UserHomeActivity::class.java))
            }
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }

    inner class OnboardingAdapter(
        private val list: List<OnboardingSlide>,
        private val onActionClick: (OnboardingSlide) -> Unit
    ) : RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {

        inner class SlideViewHolder(val binding: ItemOnboardingSlideBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val b = ItemOnboardingSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SlideViewHolder(b)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            val slide = list[position]
            val isEn = LanguageManager.getLanguage(holder.itemView.context) == LanguageManager.LANG_EN

            holder.binding.textStepBadge.text = if (isEn) slide.stepBadgeEn else slide.stepBadgeBn
            holder.binding.textSlideTitle.text = if (isEn) slide.titleEn else slide.titleBn
            holder.binding.textSlideDescription.text = if (isEn) slide.descEn else slide.descBn
            holder.binding.imageSlideIcon.setImageResource(slide.iconRes)
            holder.binding.containerIconGlow.setBackgroundResource(slide.bgGlowRes)
            holder.binding.buttonSlideAction.text = if (isEn) slide.actionTextEn else slide.actionTextBn

            val context = holder.itemView.context
            val isConfigured = when (slide.actionType) {
                1 -> ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                2 -> ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                3 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                } else true
                4 -> BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@OnboardingGuideActivity)
                5 -> true
                else -> false
            }

            if (isConfigured) {
                holder.binding.textActionStatus.text = if (isEn) "✓ Status: Active / Configured" else "✓ স্ট্যাটাস: সক্রিয় ও প্রস্তুত"
                holder.binding.textActionStatus.setTextColor(ContextCompat.getColor(context, R.color.statusActive))
                if (slide.actionType != 5) {
                    holder.binding.buttonSlideAction.text = if (isEn) "✓ CONFIGURED (TAP TO RE-CHECK)" else "✓ সক্রিয় করা হয়েছে (চেক করুন)"
                }
            } else {
                holder.binding.textActionStatus.text = if (isEn) "⚡ Status: Setup Required" else "⚡ স্ট্যাটাস: সেটাপ করা প্রয়োজন"
                holder.binding.textActionStatus.setTextColor(ContextCompat.getColor(context, R.color.statusWarning))
            }

            holder.binding.buttonSlideAction.setOnClickListener {
                onActionClick(slide)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
