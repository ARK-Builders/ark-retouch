package dev.arkbuilders.arkretouch.presentation.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentManager
import dev.arkbuilders.arkfilepicker.ArkFilePickerConfig
import dev.arkbuilders.arkfilepicker.presentation.filepicker.ArkFilePickerFragment
import dev.arkbuilders.arkfilepicker.presentation.filepicker.ArkFilePickerMode
import dev.arkbuilders.arkfilepicker.presentation.onArkPathPicked
import dev.arkbuilders.arkretouch.R
import dev.arkbuilders.arkretouch.utils.findNotExistCopyName
import dev.arkbuilders.arkretouch.utils.toast
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

@Composable
fun SavePathDialog(
    initialImagePath: Path?,
    fragmentManager: FragmentManager,
    onDismissClick: () -> Unit,
    onPositiveClick: (Path) -> Unit
) {
    var currentPath by remember { mutableStateOf(initialImagePath?.parent) }
    var imagePath by remember { mutableStateOf(initialImagePath) }
    val showOverwriteCheckbox = remember { mutableStateOf(initialImagePath != null) }
    var overwriteOriginalPath by remember { mutableStateOf(false) }
    var name by remember {
        mutableStateOf(
            initialImagePath?.let {
                it.parent.findNotExistCopyName(it.fileName).name
            } ?: "image.png"
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val context = LocalContext.current

    LaunchedEffect(overwriteOriginalPath) {
        if (overwriteOriginalPath) {
            imagePath?.let {
                currentPath = it.parent
                name = it.name
            }
            return@LaunchedEffect
        }
        imagePath?.let {
            name = it.parent.findNotExistCopyName(it.fileName).name
        }
    }

    key(showOverwriteCheckbox.value) {
        Dialog(onDismissRequest = onDismissClick) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color.White, RoundedCornerShape(5))
                    .padding(5.dp)
            ) {
                Text(
                    modifier = Modifier.padding(5.dp),
                    text = stringResource(R.string.location),
                    fontSize = 18.sp
                )
                TextButton(
                    onClick = {
                        ArkFilePickerFragment.newInstance(folderFilePickerConfig(currentPath)).show(fragmentManager, null)
                        fragmentManager.onArkPathPicked(lifecycleOwner) { path ->
                            currentPath = path
                            currentPath?.let {
                                imagePath = it.resolve(name)
                                showOverwriteCheckbox.value = Files.list(it).anyMatch { anyPath -> anyPath == imagePath }
                                if (showOverwriteCheckbox.value) { name = it.findNotExistCopyName(imagePath?.fileName!!).name }
                            }
                        }
                    }
                ) {
                    Text(
                        text = currentPath?.toString()
                            ?: stringResource(R.string.pick_folder)
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.padding(5.dp),
                    value = name,
                    onValueChange = {
                        name = it
                        if (name.isEmpty()) {
                            context.toast(
                                R.string.ark_retouch_notify_missing_file_name
                            )
                            return@OutlinedTextField
                        }
                        currentPath?.let { path ->
                            imagePath = path.resolve(name)
                            showOverwriteCheckbox.value = Files.list(path).anyMatch { anyPath -> anyPath == imagePath }
                            if (showOverwriteCheckbox.value) {
                                name = path.findNotExistCopyName(
                                    imagePath?.fileName!!
                                ).name
                            }
                        }
                    },
                    label = { Text(text = stringResource(R.string.name)) },
                    singleLine = true
                )
                if (showOverwriteCheckbox.value) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5))
                            .clickable {
                                overwriteOriginalPath = !overwriteOriginalPath
                            }
                            .padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = overwriteOriginalPath,
                            onCheckedChange = {
                                overwriteOriginalPath = !overwriteOriginalPath
                            }
                        )
                        Text(text = stringResource(R.string.overwrite_original_file))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        modifier = Modifier.padding(5.dp),
                        onClick = onDismissClick
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    Button(
                        modifier = Modifier.padding(5.dp),
                        onClick = {
                            if (name.isEmpty()) {
                                context.toast(
                                    R.string.ark_retouch_notify_missing_file_name
                                )
                                return@Button
                            }
                            if (currentPath == null) {
                                context.toast(
                                    R.string.ark_retouch_notify_choose_folder
                                )
                                return@Button
                            }
                            onPositiveClick(currentPath?.resolve(name)!!)
                        }
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
fun SaveProgress() {
    Dialog(onDismissRequest = {}) {
        Box(
            Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                Modifier.size(40.dp)
            )
        }
    }
}

fun folderFilePickerConfig(initialPath: Path?) = ArkFilePickerConfig(
    mode = ArkFilePickerMode.FOLDER,
    initialPath = initialPath,
    showRoots = true,
    rootsFirstPage = true
)