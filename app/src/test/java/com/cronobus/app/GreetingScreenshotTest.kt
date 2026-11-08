package com.cronobus.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.cronobus.app.ui.components.AdminFinancialDispatchCard
import com.cronobus.app.ui.components.DriverShiftStatusCard
import com.cronobus.app.ui.components.PassengerEtaCard
import com.cronobus.app.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DriverShiftStatusCard(
              unitId = "U-14",
              routeName = "Ruta 42 - Ramal Centro",
              headwayText = "+01:15 min",
              statusLabel = "A TIEMPO",
              pendingSyncCount = 2,
              isOnline = true,
            )
            AdminFinancialDispatchCard(
              activeUnitsText = "8 / 8",
              saasTotalText = "$6,000 MXN",
              netFinesText = "$150 MXN",
              bonusTotalText = "$360 MXN",
              forceMajeureActive = true,
              nextDispatchUnit = "U-14",
            )
            PassengerEtaCard(
              routeName = "Ruta 42 - Centro/Diana",
              stopName = "Estación Diana",
              etaMinutes = 4,
              occupancyLabel = "MEDIA",
              isStaleCache = true,
            )
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun driver_shift_status_light_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
          DriverShiftStatusCard(
            unitId = "U-14",
            routeName = "Ruta 42 - Ramal Centro",
            headwayText = "+01:15 min",
            statusLabel = "A TIEMPO",
            pendingSyncCount = 2,
            isOnline = true,
          )
        }
      }
    }

    composeTestRule
      .onRoot()
      .captureRoboImage(filePath = "src/test/screenshots/driver_shift_light.png")
  }

  @Test
  fun admin_financial_dispatch_light_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
          AdminFinancialDispatchCard(
            activeUnitsText = "8 / 8",
            saasTotalText = "$6,000 MXN",
            netFinesText = "$150 MXN",
            bonusTotalText = "$360 MXN",
            forceMajeureActive = true,
            nextDispatchUnit = "U-14",
          )
        }
      }
    }

    composeTestRule
      .onRoot()
      .captureRoboImage(filePath = "src/test/screenshots/admin_dispatch_light.png")
  }

  @Test
  fun passenger_eta_light_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
          PassengerEtaCard(
            routeName = "Ruta 42 - Centro/Diana",
            stopName = "Estación Diana",
            etaMinutes = 4,
            occupancyLabel = "MEDIA",
            isStaleCache = true,
          )
        }
      }
    }

    composeTestRule
      .onRoot()
      .captureRoboImage(filePath = "src/test/screenshots/passenger_eta_light.png")
  }

  @Test
  fun all_three_views_dark_mode_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DriverShiftStatusCard(
              unitId = "U-14",
              routeName = "Ruta 42 - Ramal Centro",
              headwayText = "+00:45 min",
              statusLabel = "EXENTO FM",
              pendingSyncCount = 4,
              isOnline = false,
            )
            AdminFinancialDispatchCard(
              activeUnitsText = "8 / 8",
              saasTotalText = "$6,000 MXN",
              netFinesText = "$0 MXN",
              bonusTotalText = "$360 MXN",
              forceMajeureActive = true,
              nextDispatchUnit = "U-09",
            )
            PassengerEtaCard(
              routeName = "Ruta 42 - Centro/Diana",
              stopName = "Estación Diana",
              etaMinutes = 3,
              occupancyLabel = "BAJA",
              isStaleCache = false,
            )
          }
        }
      }
    }

    composeTestRule
      .onRoot()
      .captureRoboImage(filePath = "src/test/screenshots/all_views_dark_mode.png")
  }
}
