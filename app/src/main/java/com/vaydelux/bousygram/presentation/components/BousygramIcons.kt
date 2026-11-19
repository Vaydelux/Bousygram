package com.vaydelux.bousygram.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR

/**
 * Type-safe, zero-allocation reference to a Lucide Android vector drawable from
 * `com.composables:icons-lucide-android:2.2.1`. Uses native VectorDrawable
 * resources so R8/resource shrinking strips unused icons in release APKs.
 */
@Immutable
@JvmInline
value class BousygramIconRef(@DrawableRes val resId: Int)

/**
 * Semantic vector icon catalog backed by official Lucide icons.
 * Centralizes icon roles so every screen, navigation bar, and player control
 * shares consistent 2dp round-cap geometry with zero Unicode glyph drift.
 */
object BousygramIcons {
    // Shell & Navigation
    val Home = BousygramIconRef(LucideR.drawable.lucide_ic_house)
    val Library = BousygramIconRef(LucideR.drawable.lucide_ic_layout_grid)
    val Search = BousygramIconRef(LucideR.drawable.lucide_ic_search)
    val Profile = BousygramIconRef(LucideR.drawable.lucide_ic_user)
    val Settings = BousygramIconRef(LucideR.drawable.lucide_ic_settings)
    val Favorites = BousygramIconRef(LucideR.drawable.lucide_ic_heart)
    val Continue = BousygramIconRef(LucideR.drawable.lucide_ic_circle_play)
    val Offline = BousygramIconRef(LucideR.drawable.lucide_ic_download)
    val Info = BousygramIconRef(LucideR.drawable.lucide_ic_info)
    val Cast = BousygramIconRef(LucideR.drawable.lucide_ic_cast)

    // Direction & Actions
    val Back = BousygramIconRef(LucideR.drawable.lucide_ic_arrow_left)
    val Forward = BousygramIconRef(LucideR.drawable.lucide_ic_arrow_right)
    val ChevronLeft = BousygramIconRef(LucideR.drawable.lucide_ic_chevron_left)
    val ChevronRight = BousygramIconRef(LucideR.drawable.lucide_ic_chevron_right)
    val ChevronUp = BousygramIconRef(LucideR.drawable.lucide_ic_chevron_up)
    val ChevronDown = BousygramIconRef(LucideR.drawable.lucide_ic_chevron_down)
    val Close = BousygramIconRef(LucideR.drawable.lucide_ic_x)
    val Check = BousygramIconRef(LucideR.drawable.lucide_ic_check)
    val MoreVertical = BousygramIconRef(LucideR.drawable.lucide_ic_ellipsis_vertical)
    val Options = BousygramIconRef(LucideR.drawable.lucide_ic_sliders_horizontal)
    val Refresh = BousygramIconRef(LucideR.drawable.lucide_ic_refresh_cw)
    val Trash = BousygramIconRef(LucideR.drawable.lucide_ic_trash_2)
    val Minus = BousygramIconRef(LucideR.drawable.lucide_ic_minus)
    val Plus = BousygramIconRef(LucideR.drawable.lucide_ic_plus)

    // Telegram Sources & Explorer
    val Saved = BousygramIconRef(LucideR.drawable.lucide_ic_bookmark)
    val Channel = BousygramIconRef(LucideR.drawable.lucide_ic_tv)
    val Group = BousygramIconRef(LucideR.drawable.lucide_ic_users)
    val Topic = BousygramIconRef(LucideR.drawable.lucide_ic_message_square)
    val Archive = BousygramIconRef(LucideR.drawable.lucide_ic_archive)
    val FolderOpen = BousygramIconRef(LucideR.drawable.lucide_ic_folder_open)
    val Telegram = BousygramIconRef(LucideR.drawable.lucide_ic_send)
    val History = BousygramIconRef(LucideR.drawable.lucide_ic_history)
    val Mic = BousygramIconRef(LucideR.drawable.lucide_ic_mic)
    val Star = BousygramIconRef(LucideR.drawable.lucide_ic_star)

    // Cinema Player & Media
    val Play = BousygramIconRef(LucideR.drawable.lucide_ic_play)
    val Pause = BousygramIconRef(LucideR.drawable.lucide_ic_pause)
    val Previous = BousygramIconRef(LucideR.drawable.lucide_ic_skip_back)
    val Next = BousygramIconRef(LucideR.drawable.lucide_ic_skip_forward)
    val SeekBack = BousygramIconRef(LucideR.drawable.lucide_ic_rotate_ccw)
    val SeekForward = BousygramIconRef(LucideR.drawable.lucide_ic_rotate_cw)
    val Rewind = BousygramIconRef(LucideR.drawable.lucide_ic_rewind)
    val FastForward = BousygramIconRef(LucideR.drawable.lucide_ic_fast_forward)
    val StartOver = BousygramIconRef(LucideR.drawable.lucide_ic_rotate_ccw)
    val Episodes = BousygramIconRef(LucideR.drawable.lucide_ic_list_video)
    val Subtitles = BousygramIconRef(LucideR.drawable.lucide_ic_captions)
    val Audio = BousygramIconRef(LucideR.drawable.lucide_ic_volume_2)
    val VolumeMute = BousygramIconRef(LucideR.drawable.lucide_ic_volume_x)
    val Brightness = BousygramIconRef(LucideR.drawable.lucide_ic_sun)
    val Speed = BousygramIconRef(LucideR.drawable.lucide_ic_gauge)
    val Aspect = BousygramIconRef(LucideR.drawable.lucide_ic_maximize_2)
    val Decoder = BousygramIconRef(LucideR.drawable.lucide_ic_cpu)
    val Diagnostics = BousygramIconRef(LucideR.drawable.lucide_ic_activity)
    val Help = BousygramIconRef(LucideR.drawable.lucide_ic_circle_question_mark)
    val Locate = BousygramIconRef(LucideR.drawable.lucide_ic_crosshair)
    val Film = BousygramIconRef(LucideR.drawable.lucide_ic_film)
    val PrivacyHidden = BousygramIconRef(LucideR.drawable.lucide_ic_eye_off)

    // Extras Catalog
    val Movies = BousygramIconRef(LucideR.drawable.lucide_ic_clapperboard)
    val TvShows = BousygramIconRef(LucideR.drawable.lucide_ic_monitor_play)
    val Anime = BousygramIconRef(LucideR.drawable.lucide_ic_sparkles)

    // Settings, Auth & Status
    val Theme = BousygramIconRef(LucideR.drawable.lucide_ic_palette)
    val Layers = BousygramIconRef(LucideR.drawable.lucide_ic_layers)
    val Smartphone = BousygramIconRef(LucideR.drawable.lucide_ic_smartphone)
    val PawPrint = BousygramIconRef(LucideR.drawable.lucide_ic_paw_print)
    val Waves = BousygramIconRef(LucideR.drawable.lucide_ic_waves)
    val GridPattern = BousygramIconRef(LucideR.drawable.lucide_ic_grid_3x3)
    val Wand = BousygramIconRef(LucideR.drawable.lucide_ic_wand_sparkles)
    val Flower = BousygramIconRef(LucideR.drawable.lucide_ic_flower_2)
    val Storage = BousygramIconRef(LucideR.drawable.lucide_ic_hard_drive)
    val Shield = BousygramIconRef(LucideR.drawable.lucide_ic_shield_check)
    val Logout = BousygramIconRef(LucideR.drawable.lucide_ic_log_out)
    val Phone = BousygramIconRef(LucideR.drawable.lucide_ic_phone)
    val QrCode = BousygramIconRef(LucideR.drawable.lucide_ic_qr_code)
    val Warning = BousygramIconRef(LucideR.drawable.lucide_ic_triangle_alert)
    val Error = BousygramIconRef(LucideR.drawable.lucide_ic_circle_alert)
}

@Composable
fun BousygramIcon(
    icon: BousygramIconRef,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp
) {
    Icon(
        painter = painterResource(icon.resId),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}
