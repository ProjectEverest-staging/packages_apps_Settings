package com.android.settings.deviceinfo.aboutphone

import android.content.Context
import android.widget.TextView
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settings.utils.EverestSpecUtils
import com.android.settingslib.core.AbstractPreferenceController
import com.android.settingslib.widget.LayoutPreference

class EverestInfoPreferenceController(context: Context) : AbstractPreferenceController(context) {

    companion object {
        private const val KEY_EVEREST_INFO = "everest_info"
        private const val KEY_STORAGE = "storage"
        private const val KEY_CHIPSET = "chipset"
        private const val KEY_BATTERY = "battery"
        private const val KEY_DISPLAY = "display"
    }

    override fun displayPreference(screen: PreferenceScreen) {
        super.displayPreference(screen)
        val everestInfoPreference = screen.findPreference<LayoutPreference>(KEY_EVEREST_INFO)

        everestInfoPreference?.let {
            val processor = it.findViewById<TextView>(R.id.chipset_summary)
            val storageAndRAM = it.findViewById<TextView>(R.id.cust_storage_summary)
            val battery = it.findViewById<TextView>(R.id.cust_battery_summary)
            val infoScreen = it.findViewById<TextView>(R.id.cust_display_summary)

            if (processor != null && storageAndRAM != null && battery != null && infoScreen != null) {
                val context = it.context

                processor.text = EverestSpecUtils.getProcessorModel(context)
                storageAndRAM.text = EverestSpecUtils.getStorageAndRAMInfo(context)
                battery.text = EverestSpecUtils.getBatteryCapacity(context)
                infoScreen.text = EverestSpecUtils.getScreenRes(context)
            }
        }
    }

    override fun isAvailable(): Boolean {
        return true
    }

    override fun getPreferenceKey(): String {
        return KEY_EVEREST_INFO
    }
}
