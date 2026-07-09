package com.willyshare.willykez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willyshare.willykez.ui.PulseIcons
import com.willyshare.willykez.ui.theme.SleekBg
import com.willyshare.willykez.ui.theme.SleekOnSurface
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.willyshare.willykez.ui.theme.SleekPrimaryContainer
import kotlinx.coroutines.launch

private data class OnboardPage(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val pages = listOf(
    OnboardPage(
        PulseIcons.Brand,
        "Share faster than ever",
        "Transfer any file to any nearby device at blazing speed, completely offline."
    ),
    OnboardPage(
        PulseIcons.Broadcasting,
        "Real device-to-device discovery",
        "Pulse finds nearby phones over Wi-Fi Direct, just like Quick Share \u2014 no internet, no cables."
    ),
    OnboardPage(
        PulseIcons.Device,
        "Or pair with a QR code",
        "Prefer a manual pair? Scan the receiver's code and start sending in seconds, like Xender."
    )
)

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleekBg)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SleekPrimaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(PulseIcons.Brand, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sparks", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SleekPrimary)
            }
            Text(
                text = "SKIP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SleekOnSurfaceVariant,
                modifier = Modifier.clickable { onGetStarted() }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIndex ->
            val page = pages[pageIndex]
            // Parallax feel: the icon and text scale/fade slightly based on how far
            // this page is from being centered, instead of snapping in at full size.
            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
            val pageAlpha = 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
            val pageScale = 1f - (kotlin.math.abs(pageOffset).coerceIn(0f, 1f) * 0.15f)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = pageAlpha
                            scaleX = pageScale
                            scaleY = pageScale
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(SleekPrimaryContainer, RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(page.icon, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(64.dp))
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = page.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SleekOnSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = page.body,
                    fontSize = 15.sp,
                    color = SleekOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    val isActive = pagerState.currentPage == index
                    val dotWidth by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isActive) 24.dp else 8.dp,
                        label = "dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = dotWidth, height = 8.dp)
                            .background(
                                if (isActive) SleekPrimary else SleekPrimary.copy(alpha = 0.2f),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val isLastPage = pagerState.currentPage == pages.lastIndex
            Button(
                onClick = {
                    if (isLastPage) {
                        onGetStarted()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
            ) {
                Text(
                    text = if (isLastPage) "GET STARTED \u2192" else "NEXT \u2192",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
            }
        }
    }
}
