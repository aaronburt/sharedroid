package uk.co.aaronburt.sharedroid.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small uppercase label above a [Group]. */
@Composable
fun GroupTitle(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
        color = Sd.colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp,
    )
}

/** A bordered card that holds rows, separated by hairlines. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().padding(bottom = 14.dp).clip(shape).background(Sd.colors.surface).border(1.dp, Sd.colors.line, shape),
        content = content,
    )
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Sd.colors.line))
}

@Composable
fun RowText(title: String?, subtitle: String? = null, modifier: Modifier = Modifier, titleColor: Color = Sd.colors.ink, wrap: Boolean = true) {
    Column(modifier) {
        if (title != null) {
            Text(
                title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = if (wrap) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(
                subtitle, Modifier.padding(top = if (title != null) 2.dp else 0.dp), color = Sd.colors.muted, fontSize = 12.sp, lineHeight = 16.sp,
                maxLines = if (wrap) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One settings row: text on the left, a control on the right. */
@Composable
fun PrefRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    PrefRow(Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)) {
        RowText(title, subtitle, Modifier.weight(1f))
        SdSwitch(checked)
    }
}

/** Display only; the row that contains it is what toggles. */
@Composable
fun SdSwitch(checked: Boolean) {
    val x by animateDpAsState(if (checked) 19.dp else 3.dp, label = "switch")
    Box(Modifier.size(40.dp, 24.dp).clip(CircleShape).background(if (checked) Sd.colors.accent else Sd.colors.line)) {
        Box(Modifier.offset(x = x, y = 3.dp).size(18.dp).clip(CircleShape).background(Color.White))
    }
}

/** A two or more option toggle, as used for Dark / True black. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sd.colors.chip).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) Sd.colors.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Sd.colors.ink else Sd.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun IconBtn(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(36.dp).clip(CircleShape).background(Sd.colors.chip).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), label, Modifier.size(17.dp), tint = Sd.colors.icon)
    }
}

/** The tinted rounded square that sits at the start of a row. */
@Composable
fun Thumb(@DrawableRes icon: Int?, size: Dp = 46.dp, letter: String? = null) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (size > 40.dp) 12.dp else 10.dp)).background(Sd.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(20.dp), tint = Sd.colors.accent)
        else Text(letter.orEmpty(), color = Sd.colors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    password: Boolean = false,
    plainText: Boolean = false,
    bold: Boolean = false,
    lines: Int = 1,
) {
    val style = TextStyle(
        color = Sd.colors.ink,
        fontSize = if (mono) 13.sp else 14.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
    )
    BasicTextField(
        value, onValueChange,
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sd.colors.chip).padding(horizontal = 12.dp, vertical = 11.dp),
        textStyle = style,
        singleLine = lines == 1,
        minLines = lines,
        cursorBrush = SolidColor(Sd.colors.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = when {
            password -> KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
            plainText -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            lines > 1 -> KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
            else -> KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
        },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Sd.colors.muted.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
}

/** A labelled field inside a [Group]. */
@Composable
fun Field(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, color = Sd.colors.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(" $hint", color = Sd.colors.muted, fontSize = 11.5.sp)
        }
        content()
    }
}

@Composable
fun SdButton(text: String, modifier: Modifier = Modifier, secondary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (secondary) Sd.colors.chip else Sd.colors.accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = (if (secondary) Sd.colors.ink else Sd.colors.onAccent).copy(alpha = if (enabled) 1f else 0.6f),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}
