package xyz.mpv.rex.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.preferences.preference.deleteAndGet
import xyz.mpv.rex.presentation.components.ExpandableCard
import xyz.mpv.rex.presentation.components.SliderItem
import xyz.mpv.rex.ui.player.controls.CARDS_MAX_WIDTH
import xyz.mpv.rex.ui.player.controls.components.sheets.toFixed
import xyz.mpv.rex.ui.player.controls.panelCardsColors
import xyz.mpv.rex.ui.theme.spacing
import `is`.xyz.mpv.MPVLib
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import org.koin.compose.koinInject
import xyz.mpv.rex.utils.media.SubtitleEncodingUtils

@Composable
fun SubtitlesMiscellaneousCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val preferences = koinInject<SubtitlesPreferences>()
  var isExpanded by remember { mutableStateOf(true) }
  ExpandableCard(
    isExpanded,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        Icon(Icons.Default.Tune, null)
        Text(stringResource(R.string.player_sheets_sub_misc_card_title))
      }
    },
    onExpand = { isExpanded = !isExpanded },
    modifier.widthIn(max = CARDS_MAX_WIDTH),
    colors = panelCardsColors(),
  ) {
    ProvidePreferenceLocals {
      Column {
        var overrideAssSubs by remember {
          mutableStateOf(MPVLib.getPropertyString("sub-ass-override") == "force")
        }
        SwitchPreference(
          overrideAssSubs,
          onValueChange = {
            overrideAssSubs = it
            preferences.overrideAssSubs.set(it)
            MPVLib.setPropertyString("sub-ass-override", if (it) "force" else "scale")
            MPVLib.setPropertyString("secondary-sub-ass-override", "force")
          },
          { Text(stringResource(R.string.player_sheets_sub_override_ass)) },
        )
        var scaleByWindow by remember {
          mutableStateOf(MPVLib.getPropertyString("sub-scale-by-window") == "yes")
        }
        SwitchPreference(
          scaleByWindow,
          onValueChange = {
            scaleByWindow = it
            preferences.scaleByWindow.set(it)
            val value = if (it) "yes" else "no"
            MPVLib.setPropertyString("sub-scale-by-window", value)
            MPVLib.setPropertyString("sub-use-margins", value)
            MPVLib.setPropertyString("secondary-sub-scale-by-window", value)
            MPVLib.setPropertyString("secondary-sub-use-margins", value)
          },
          { Text(stringResource(R.string.player_sheets_sub_scale_by_window)) },
          summary = { Text(stringResource(R.string.player_sheets_sub_scale_by_window_summary)) },
        )
        
        val openAtVideoLocation by preferences.openPickerAtVideoLocation.collectAsState()
        SwitchPreference(
          openAtVideoLocation,
          onValueChange = { preferences.openPickerAtVideoLocation.set(it) },
          { Text(stringResource(R.string.pref_subtitles_open_at_video_location_title)) },
          summary = { Text(stringResource(R.string.pref_subtitles_open_at_video_location_summary)) },
        )

        val forceLtr by preferences.forceLtr.collectAsState()
        SwitchPreference(
          forceLtr,
          onValueChange = {
            preferences.forceLtr.set(it)
            val value = if (it) "yes" else "no"
            runCatching {
              MPVLib.setPropertyString("sub-vsfilter-bidi-compat", value)
              MPVLib.command("sub-reload")
            }
          },
          { Text(stringResource(R.string.pref_subtitles_force_ltr_title)) },
          summary = { Text(stringResource(R.string.pref_subtitles_force_ltr_summary)) },
        )

        val subtitleEncoding by preferences.subtitleEncoding.collectAsState()
        ListPreference(
          value = subtitleEncoding,
          onValueChange = { newEncoding ->
            preferences.subtitleEncoding.set(newEncoding)
            val codepage = if (newEncoding.isBlank() || newEncoding.equals("auto", ignoreCase = true)) "auto" else newEncoding
            runCatching {
              MPVLib.setPropertyString("sub-codepage", codepage)
              SubtitleEncodingUtils.applyEncodingChange(context, newEncoding)
            }
          },
          values = SubtitleEncodingUtils.ENCODINGS.map { it.code },
          valueToText = { AnnotatedString(SubtitleEncodingUtils.getDisplayName(it)) },
          title = { Text(stringResource(R.string.pref_subtitles_encoding_title)) },
          summary = {
            Text(
              SubtitleEncodingUtils.getDisplayName(subtitleEncoding),
              color = MaterialTheme.colorScheme.outline,
            )
          },
        )

        val secondarySid by MPVLib.propInt["secondary-sid"].collectAsState()
        val isSecondaryActive = (secondarySid ?: (MPVLib.getPropertyInt("secondary-sid") ?: 0)) > 0

        val subScale by MPVLib.propFloat["sub-scale"].collectAsState()
        val subPos by MPVLib.propInt["sub-pos"].collectAsState()
        val secondarySubScale by MPVLib.propFloat["secondary-sub-scale"].collectAsState()
        val secondarySubPos by MPVLib.propInt["secondary-sub-pos"].collectAsState()

        SliderItem(
          label = stringResource(R.string.player_sheets_sub_scale),
          value = subScale ?: preferences.subScale.get(),
          valueText = (subScale ?: preferences.subScale.get()).toFixed(2).toString(),
          onChange = {
            preferences.subScale.set(it)
            MPVLib.setPropertyFloat("sub-scale", it)
          },
          max = 5f,
          icon = {
            Icon(
              Icons.Default.FormatSize,
              null,
            )
          },
        )
        SliderItem(
          label = if (isSecondaryActive) stringResource(R.string.player_sheets_sub_primary_position) else stringResource(R.string.player_sheets_sub_position),
          value = subPos ?: preferences.subPos.get(),
          valueText = (subPos ?: preferences.subPos.get()).toString(),
          onChange = {
            preferences.subPos.set(it)
            MPVLib.setPropertyInt("sub-pos", it)
          },
          max = 150,
          icon = {
            Icon(
              Icons.Default.AlignVerticalCenter,
              null,
            )
          },
        )

        if (isSecondaryActive) {
          SliderItem(
            label = stringResource(R.string.player_sheets_secondary_sub_scale),
            value = secondarySubScale ?: preferences.secondarySubScale.get(),
            valueText = (secondarySubScale ?: preferences.secondarySubScale.get()).toFixed(2).toString(),
            onChange = {
              preferences.secondarySubScale.set(it)
              MPVLib.setPropertyFloat("secondary-sub-scale", it)
            },
            max = 5f,
            icon = {
              Icon(
                Icons.Default.FormatSize,
                null,
              )
            },
          )
          SliderItem(
            label = stringResource(R.string.player_sheets_secondary_sub_position),
            value = secondarySubPos ?: preferences.secondarySubPos.get(),
            valueText = (secondarySubPos ?: preferences.secondarySubPos.get()).toString(),
            onChange = {
              preferences.secondarySubPos.set(it)
              MPVLib.setPropertyInt("secondary-sub-pos", it)
            },
            max = 150,
            icon = {
              Icon(
                Icons.Default.AlignVerticalCenter,
                null,
              )
            },
          )
        }

        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(end = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.medium),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = {
              preferences.subPos.deleteAndGet().let {
                MPVLib.setPropertyInt("sub-pos", it)
              }
              preferences.subScale.deleteAndGet().let {
                MPVLib.setPropertyFloat("sub-scale", it)
              }
              preferences.secondarySubPos.deleteAndGet().let {
                MPVLib.setPropertyInt("secondary-sub-pos", it)
              }
              preferences.secondarySubScale.deleteAndGet().let {
                MPVLib.setPropertyFloat("secondary-sub-scale", it)
              }
              val defaultOverride = preferences.overrideAssSubs.deleteAndGet()
              overrideAssSubs = defaultOverride
              MPVLib.setPropertyString("sub-ass-override", if (defaultOverride) "force" else "scale")
              MPVLib.setPropertyString("secondary-sub-ass-override", "force")
              val defaultScaleByWindow = preferences.scaleByWindow.deleteAndGet()
              scaleByWindow = defaultScaleByWindow
              val scaleValue = if (defaultScaleByWindow) "yes" else "no"
              MPVLib.setPropertyString("sub-scale-by-window", scaleValue)
              MPVLib.setPropertyString("sub-use-margins", scaleValue)
              MPVLib.setPropertyString("secondary-sub-scale-by-window", scaleValue)
              MPVLib.setPropertyString("secondary-sub-use-margins", scaleValue)
            },
          ) {
            Row {
              Icon(Icons.Default.EditOff, null)
              Text(stringResource(R.string.generic_reset))
            }
          }
        }
      }
    }
  }
}
