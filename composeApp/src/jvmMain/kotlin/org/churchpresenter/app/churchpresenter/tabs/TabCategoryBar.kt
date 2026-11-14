package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.sharedui.models.Tabs

private val CATEGORY_BAR_HEIGHT = 32.dp
private val PILL_SHAPE = AppShape(16.dp)
private val BADGE_SHAPE = AppShape(8.dp)

/**
 * Segmented category bar filtering the main tabs into logical groups.
 * Replaces the need to horizontally scroll through 15+ tabs by keeping each view focused.
 */
@Composable
internal fun TabCategoryBar(
    selectedCategory: TabCategory,
    onCategorySelected: (TabCategory) -> Unit,
    visibleTabs: List<Tabs>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabCategory.entries.forEach { category ->
            val count = if (category == TabCategory.ALL) {
                visibleTabs.size
            } else {
                visibleTabs.count { category.matches(it) }
            }
            if (count > 0 || category == TabCategory.ALL) {
                TabCategoryPill(
                    category = category,
                    count = count,
                    isSelected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@Composable
private fun TabCategoryPill(
    category: TabCategory,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        label = "cat_pill_bg",
    )
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
    }

    Row(
        modifier = Modifier
            .height(CATEGORY_BAR_HEIGHT)
            .clip(PILL_SHAPE)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = contentColor,
        )
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
            color = contentColor,
        )
        CategoryCountBadge(count, isSelected)
    }
}

@Composable
private fun CategoryCountBadge(count: Int, isSelected: Boolean) {
    val badgeBg = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val badgeText = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(BADGE_SHAPE)
            .background(badgeBg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = badgeText,
        )
    }
}
