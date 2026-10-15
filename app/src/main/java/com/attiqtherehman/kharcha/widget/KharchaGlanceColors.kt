package com.attiqtherehman.kharcha.widget

import androidx.glance.material3.ColorProviders
import com.attiqtherehman.kharcha.ui.theme.DarkColorScheme
import com.attiqtherehman.kharcha.ui.theme.LightColorScheme

/** Widget colours: the app's own Material schemes, so widgets match the app in light and dark. */
object KharchaGlanceColors {
    val providers = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
}
