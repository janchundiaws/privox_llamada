package com.example.privox_app1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.privox_app1.AudioDistortionEngine
import com.example.privox_app1.VoiceChangerTestScreen

@Composable
fun MainTabsScreen(
    username: String,
    engine: AudioDistortionEngine,
    onSettingsClick: () -> Unit,
    requestCall: (String, String) -> Unit,
    onChatClick: (String, String) -> Unit,
    onLogoutClick: () -> Unit,
    isConnected: Boolean
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(80.dp)
                ) {
                    val activeColor = Color(0xFF2575FC)
                    val inactiveColor = Color.Gray

                NavigationBarItem(
                    icon = { 
                        Icon(
                            if (pagerState.currentPage == 0) Icons.Filled.Person else Icons.Outlined.Person, 
                            contentDescription = "Contactos",
                            tint = if (pagerState.currentPage == 0) activeColor else inactiveColor
                        ) 
                    },
                    label = { 
                        Text(
                            "Contactos", 
                            color = if (pagerState.currentPage == 0) activeColor else inactiveColor,
                            fontWeight = if (pagerState.currentPage == 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        ) 
                    },
                    selected = pagerState.currentPage == 0,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = activeColor.copy(alpha = 0.1f)
                    ),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
                NavigationBarItem(
                    icon = { 
                        Icon(
                            if (pagerState.currentPage == 1) Icons.Filled.Add else Icons.Outlined.Add, 
                            contentDescription = "Agregar",
                            tint = if (pagerState.currentPage == 1) activeColor else inactiveColor
                        ) 
                    },
                    label = { 
                        Text(
                            "Agregar", 
                            color = if (pagerState.currentPage == 1) activeColor else inactiveColor,
                            fontWeight = if (pagerState.currentPage == 1) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        ) 
                    },
                    selected = pagerState.currentPage == 1,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = activeColor.copy(alpha = 0.1f)
                    ),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                NavigationBarItem(
                    icon = { 
                        Icon(
                            if (pagerState.currentPage == 2) Icons.Filled.Search else Icons.Outlined.Search, 
                            contentDescription = "Solicitudes",
                            tint = if (pagerState.currentPage == 2) activeColor else inactiveColor
                        ) 
                    },
                    label = { 
                        Text(
                            "Solicitudes", 
                            color = if (pagerState.currentPage == 2) activeColor else inactiveColor,
                            fontWeight = if (pagerState.currentPage == 2) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        ) 
                    },
                    selected = pagerState.currentPage == 2,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = activeColor.copy(alpha = 0.1f)
                    ),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                )
/*                NavigationBarItem(
                    icon = { 
                        Icon(
                            if (pagerState.currentPage == 3) Icons.Filled.Mic else Icons.Outlined.Mic, 
                            contentDescription = "Voz",
                            tint = if (pagerState.currentPage == 3) activeColor else inactiveColor
                        ) 
                    },
                    label = { 
                        Text(
                            "Distorsión", 
                            color = if (pagerState.currentPage == 3) activeColor else inactiveColor,
                            fontWeight = if (pagerState.currentPage == 3) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        ) 
                    },
                    selected = pagerState.currentPage == 3,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = activeColor.copy(alpha = 0.1f)
                    ),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(3) }
                    }
                    )*/
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(paddingValues),
            beyondViewportPageCount = 2
        ) { page ->
            when (page) {
                0 -> WelcomeScreen(
                    username = username,
                    onSettingsClick = onSettingsClick,
                    onAddUserClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onRequestsClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    requestCall = requestCall,
                    onChatClick = onChatClick,
                    onLogoutClick = onLogoutClick,
                    isConnected = isConnected
                )
                1 -> UsersAddScreen(
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onSettingsClick = onSettingsClick,
                    onLogoutClick = onLogoutClick
                )
                2 -> UsersRequestsScreen(
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onSettingsClick = onSettingsClick,
                    onLogoutClick = onLogoutClick
                )
                /*3 -> VoiceChangerTestScreen(engine = engine)*/
            }
        }
    }
}
