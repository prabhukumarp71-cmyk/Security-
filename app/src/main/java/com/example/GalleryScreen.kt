package com.example

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SecurityCamImage(val uri: android.net.Uri, val dateAdded: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<SecurityCamImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var fullScreenImage by remember { mutableStateOf<android.net.Uri?>(null) }

    BackHandler {
        if (fullScreenImage != null) {
            fullScreenImage = null
        } else {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val imageList = mutableListOf<SecurityCamImage>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            // Query MediaStore for images in the SecurityCam folder
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%SecurityCam%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    imageList.add(SecurityCamImage(contentUri, dateAdded))
                }
            }
            images = imageList
            isLoading = false
        }
    }

    if (fullScreenImage != null) {
        Box(modifier = Modifier.fillMaxSize().clickable { fullScreenImage = null }) {
            AsyncImage(
                model = fullScreenImage,
                contentDescription = "Full Screen Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = { fullScreenImage = null },
                modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Captured Photos") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (images.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos captured yet")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(images) { image ->
                        AsyncImage(
                            model = image.uri,
                            contentDescription = "Captured Photo",
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { fullScreenImage = image.uri },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}
