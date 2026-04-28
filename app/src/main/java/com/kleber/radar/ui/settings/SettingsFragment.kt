package com.kleber.radar.ui.settings

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.kleber.radar.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.settings.observe(viewLifecycleOwner) { s ->
            binding.etDailyGoal.setText(s.dailyGoal.toString())
            binding.etMinPerKm.setText(s.minEarningsPerKm.toString())
            binding.etMinPerHour.setText(s.minEarningsPerHour.toString())
            binding.etFuelCost.setText(s.fuelCostPerKm.toString())
            binding.etMaintenanceCost.setText(s.maintenanceCostPerKm.toString())
            binding.etInsurance.setText(s.insuranceCostPerMonth.toString())
            binding.etOtherCosts.setText(s.otherCostsPerMonth.toString())
            binding.switchVoice.isChecked = s.voiceNotificationEnabled
            binding.switchOverlay.isChecked = s.overlayEnabled
        }

        binding.btnSave.setOnClickListener {
            viewModel.save(
                dailyGoal = binding.etDailyGoal.text.toString().toDoubleOrNull() ?: 200.0,
                minPerKm = binding.etMinPerKm.text.toString().toDoubleOrNull() ?: 1.80,
                minPerHour = binding.etMinPerHour.text.toString().toDoubleOrNull() ?: 25.0,
                fuelCost = binding.etFuelCost.text.toString().toDoubleOrNull() ?: 0.45,
                maintenance = binding.etMaintenanceCost.text.toString().toDoubleOrNull() ?: 0.12,
                insurance = binding.etInsurance.text.toString().toDoubleOrNull() ?: 300.0,
                otherCosts = binding.etOtherCosts.text.toString().toDoubleOrNull() ?: 100.0,
                voiceEnabled = binding.switchVoice.isChecked,
                overlayEnabled = binding.switchOverlay.isChecked
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
