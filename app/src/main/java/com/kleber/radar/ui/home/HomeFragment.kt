package com.kleber.radar.ui.home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kleber.radar.R
import com.kleber.radar.data.model.TripGrade
import com.kleber.radar.databinding.FragmentHomeBinding
import com.kleber.radar.util.AccessibilityDebugStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))
    private val installDateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeData()
        checkPermissions()
        refreshAccessibilityDebug()
        showInstalledBuildInfo()

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnEnableOverlay.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")))
        }

        binding.btnDisableBatteryOptimization.setOnClickListener {
            @Suppress("BatteryLife")
            startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${requireContext().packageName}")
            ))
        }

        binding.btnRefreshAccessibilityDebug.setOnClickListener {
            checkPermissions()
            refreshAccessibilityDebug()
        }

        binding.btnViewFullLog.setOnClickListener {
            showFullLog()
        }
    }

    private fun observeData() {
        viewModel.todayGross.observe(viewLifecycleOwner) { value ->
            binding.tvGrossValue.text = "R$ %.2f".format(value ?: 0.0)
        }
        viewModel.todayNet.observe(viewLifecycleOwner) { value ->
            binding.tvNetValue.text = "R$ %.2f".format(value ?: 0.0)
        }
        viewModel.todayCount.observe(viewLifecycleOwner) { count ->
            binding.tvTripCount.text = "$count corridas"
        }
        viewModel.todayAvgPerHour.observe(viewLifecycleOwner) { avg ->
            binding.tvAvgPerHour.text = "R$ %.2f/h".format(avg ?: 0.0)
        }
        viewModel.todayTrips.observe(viewLifecycleOwner) { trips ->
            val green = trips.count { it.grade == TripGrade.GREEN }
            val yellow = trips.count { it.grade == TripGrade.YELLOW }
            val red = trips.count { it.grade == TripGrade.RED }
            binding.tvGradeStats.text = "✅ $green  ⚠️ $yellow  ❌ $red"
        }
    }

    private fun checkPermissions() {
        val hasAccessibility = isAccessibilityEnabled()
        val hasOverlay = Settings.canDrawOverlays(requireContext())
        val hasBatteryExemption = isIgnoringBatteryOptimizations()

        binding.btnEnableAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE
        binding.tvRestrictedSettingsHint.visibility = if (hasAccessibility) View.GONE else View.VISIBLE
        binding.btnEnableOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE
        binding.btnDisableBatteryOptimization.visibility = if (hasBatteryExemption) View.GONE else View.VISIBLE
        binding.tvStatusOk.visibility =
            if (hasAccessibility && hasOverlay && hasBatteryExemption) View.VISIBLE else View.GONE
        binding.tvAccessibilityState.text = if (hasAccessibility) {
            "Serviço de acessibilidade: ATIVO"
        } else {
            "Serviço de acessibilidade: INATIVO"
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun showInstalledBuildInfo() {
        val context = requireContext()
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        }
        binding.tvInstalledAt.text =
            "Build instalado em: ${installDateFormat.format(Date(pkgInfo.lastUpdateTime))} " +
                "(v${pkgInfo.versionName} build $versionCode)"
    }

    private fun showFullLog() {
        val debugFile = File(requireContext().getExternalFilesDir(null), "radar_debug.txt")
        val content = if (debugFile.exists()) {
            debugFile.readText().ifBlank { "Arquivo existe mas está vazio." }
        } else {
            "Arquivo radar_debug.txt ainda não foi criado. Isso indica que o serviço de " +
                "acessibilidade nunca chegou a rodar (onServiceConnected não foi chamado)."
        }

        val textView = TextView(requireContext()).apply {
            text = content
            isTextSelectable = true
            setPadding(32, 24, 32, 24)
            textSize = 12f
        }
        val scroll = ScrollView(requireContext()).apply { addView(textView) }

        AlertDialog.Builder(requireContext())
            .setTitle("radar_debug.txt")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun refreshAccessibilityDebug() {
        val state = AccessibilityDebugStore.read(requireContext())
        binding.tvServiceConnectedAt.text = "Serviço conectado em: ${formatTime(state.serviceConnectedAt)}"
        binding.tvLastPackage.text = "Último pacote: ${state.lastPackage.ifBlank { "nenhum" }}"
        binding.tvLastEvent.text =
            "Último evento: ${formatTime(state.lastEventAt)} type=${state.lastEventType} class=${state.lastClass.ifBlank { "nenhuma" }}"
        binding.tvLastReadTime.text = "Última leitura com texto: ${formatTime(state.lastCaptureAt)}"
        binding.tvCapturedCount.text = "Textos capturados: ${state.lastTextCount}"
        binding.tvLastCapturedTexts.text = state.lastText.ifBlank { "Nenhum texto capturado ainda." }
    }

    private fun formatTime(timestamp: Long): String {
        return if (timestamp > 0L) dateFormat.format(Date(timestamp)) else "nunca"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${requireContext().packageName}/com.kleber.radar.accessibility.RadarAccessibilityService"
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(service)
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        refreshAccessibilityDebug()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
