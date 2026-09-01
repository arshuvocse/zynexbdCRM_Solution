package com.zynexbd.crmsolution.utils

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.zynexbd.crmsolution.databinding.DialogPaymentExpiredBinding
import com.zynexbd.crmsolution.models.SubscriptionStatus
import com.zynexbd.crmsolution.network.ApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PaymentExpiredDialog : DialogFragment() {

    private var _binding: DialogPaymentExpiredBinding? = null
    private val binding get() = _binding!!

    private var subscriptionStatus: SubscriptionStatus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPaymentExpiredBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    fun setStatus(status: SubscriptionStatus) {
        this.subscriptionStatus = status
        if (_binding != null) {
            setupViews()
        }
    }

    private fun setupViews() {
        val isEn = LanguageManager.isEnglish(requireContext())
        val session = SessionManager(requireContext())
        val isAdmin = session.getRole()?.equals("Admin", ignoreCase = true) == true
        val status = subscriptionStatus

        val formattedDate = formatUtcDate(status?.paymentDueDate)

        if (isAdmin) {
            // ADMIN MODAL: Explicitly mentions "টাকা পেমেন্ট করতে হবে / Payment required"
            if (isEn) {
                binding.textModalTitle.text = "Payment Required!"
                binding.textModalSubtitle.text = "Subscription Expired"
                binding.textExpiryDate.text = "Expired On: $formattedDate"
                binding.textExpiryMessage.text = "Your subscription period has expired. You must make payment to renew the subscription and continue using all features."
                binding.buttonCallSupport.text = "💳 Call Support to Pay"
                binding.buttonRefreshStatus.text = "🔄 I Have Paid (Check Status)"
                binding.textFooterWarning.text = "🔒 Service will resume upon payment confirmation"
            } else {
                binding.textModalTitle.text = "টাকা পেমেন্ট করতে হবে!"
                binding.textModalSubtitle.text = "সাবস্ক্রিপশনের মেয়াদ শেষ"
                binding.textExpiryDate.text = "মেয়াদ উত্তীর্ণের তারিখ: $formattedDate"
                binding.textExpiryMessage.text = "আপনার কোম্পানির সাবস্ক্রিপশন মেয়াদ শেষ হয়েছে। অ্যাপের সকল ফিচার ও ট্র্যাকিং সার্ভিস চালু রাখতে অবিলম্বে টাকা পেমেন্ট করতে হবে।"
                binding.buttonCallSupport.text = "💳 পেমেন্টের জন্য কল করুন"
                binding.buttonRefreshStatus.text = "🔄 টাকা পেমেন্ট করেছি (রিফ্রেশ করুন)"
                binding.textFooterWarning.text = "🔒 টাকা পেমেন্ট নিশ্চিত না হওয়া পর্যন্ত সার্ভিস বন্ধ থাকবে"
            }
        } else {
            // USER / EMPLOYEE MODAL: "Contact with company owner"
            if (isEn) {
                binding.textModalTitle.text = "Contact With Company Owner"
                binding.textModalSubtitle.text = "Service Temporarily Paused"
                binding.textExpiryDate.text = "Status: Suspended on $formattedDate"
                binding.textExpiryMessage.text = "This service is currently paused. Please contact with company owner or administrator to restore your access."
                binding.buttonCallSupport.text = "📞 Contact Company Owner"
                binding.buttonRefreshStatus.text = "🔄 Check If Activated"
                binding.textFooterWarning.text = "🔒 Contact company owner to restore service"
            } else {
                binding.textModalTitle.text = "কোম্পানির ওনারের সাথে যোগাযোগ করুন"
                binding.textModalSubtitle.text = "সার্ভিস সাময়িক বন্ধ আছে"
                binding.textExpiryDate.text = "স্থগিতের তারিখ: $formattedDate"
                binding.textExpiryMessage.text = "সার্ভিসটি সাময়িকভাবে বন্ধ আছে। দয়া করে সার্ভিস পুনরায় চালু করতে কোম্পানির ওনার (Company Owner) এর সাথে যোগাযোগ করুন।"
                binding.buttonCallSupport.text = "📞 কোম্পানির ওনারকে কল করুন"
                binding.buttonRefreshStatus.text = "🔄 চালু হয়েছে কিনা যাচাই করুন"
                binding.textFooterWarning.text = "🔒 সার্ভিস চালু করতে কোম্পানির ওনারের সাথে যোগাযোগ করুন"
            }
        }

        val supportPhone = status?.adminPhone?.takeIf { it.isNotBlank() } ?: "01618888251"

        binding.buttonCallSupport.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$supportPhone"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to make call", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonWhatsAppSupport.setOnClickListener {
            try {
                val clean = supportPhone.replace("+", "").replace(" ", "").replace("-", "")
                val formatted = if (clean.startsWith("0")) "88$clean" else clean
                val url = "https://api.whatsapp.com/send?phone=$formatted&text=Hello,%20I%20want%20to%20renew%20my%20subscription%20payment."
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "WhatsApp not found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonRefreshStatus.setOnClickListener {
            checkStatusAgain()
        }
    }

    private fun checkStatusAgain() {
        val isEn = LanguageManager.isEnglish(requireContext())
        binding.buttonRefreshStatus.isEnabled = false
        binding.buttonRefreshStatus.text = if (isEn) "Checking status..." else "যাচাই করা হচ্ছে..."

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val resp = api.getSubscriptionStatus()
                if (resp.isSuccessful && resp.body() != null) {
                    val newStatus = resp.body()!!
                    if (!newStatus.isExpired) {
                        Toast.makeText(
                            requireContext(),
                            if (isEn) "Subscription is active! Access restored." else "পেমেন্ট সফল! সার্ভিস পুনরায় চালু করা হয়েছে।",
                            Toast.LENGTH_LONG
                        ).show()
                        dismissAllowingStateLoss()
                        return@launch
                    }
                }
                Toast.makeText(
                    requireContext(),
                    if (isEn) "Payment not yet received or updated." else "পেমেন্ট এখনও ডাটাবেজে আপডেট হয়নি। বিল পরিশোধ সম্পন্ন করুন।",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    if (isEn) "Connection error while checking status." else "সার্ভার সংযোগে ত্রুটি। আবার চেষ্টা করুন।",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.buttonRefreshStatus.isEnabled = true
                binding.buttonRefreshStatus.text = if (isEn) "🔄 I Have Paid (Check Status)" else "🔄 পেমেন্ট করেছি (রিফ্রেশ করুন)"
            }
        }
    }

    private fun formatUtcDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "N/A"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(dateStr)
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            formatter.format(date ?: return dateStr)
        } catch (e: Exception) {
            dateStr
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "PaymentExpiredDialog"

        fun show(activity: FragmentActivity, status: SubscriptionStatus): PaymentExpiredDialog {
            val existing = activity.supportFragmentManager.findFragmentByTag(TAG) as? PaymentExpiredDialog
            if (existing != null && existing.isAdded) {
                existing.setStatus(status)
                return existing
            }
            val dialog = PaymentExpiredDialog()
            dialog.setStatus(status)
            dialog.show(activity.supportFragmentManager, TAG)
            return dialog
        }
    }
}
