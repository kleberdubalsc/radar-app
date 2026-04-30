package com.kleber.radar.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kleber.radar.R
import com.kleber.radar.data.model.TripGrade
import com.kleber.radar.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeData()
        checkPermissions()

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnEnableOverlay.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")))
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

        binding.btnEnableAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE
        binding.btnEnableOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE
        binding.tvStatusOk.visibility = if (hasAccessibility && hasOverlay) View.VISIBLE else View.GONE
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
